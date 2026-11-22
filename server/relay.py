"""TLS relay for Potions and Dungeons rooms."""

import argparse
import asyncio
import json
import secrets
import ssl
import struct
from dataclasses import dataclass, field


@dataclass
class Peer:
    reader: asyncio.StreamReader
    writer: asyncio.StreamWriter
    finished: asyncio.Future


@dataclass
class Room:
    control: asyncio.StreamWriter
    key: str = field(default_factory=lambda: secrets.token_hex(32))
    pending: dict = field(default_factory=dict)
    guests: set = field(default_factory=set)


async def read_message(reader):
    size, = struct.unpack("!I", await reader.readexactly(4))
    if not 1 <= size <= 4096:
        raise ValueError("Invalid handshake frame size")
    message = json.loads(await reader.readexactly(size))
    if not isinstance(message, dict):
        raise ValueError("Invalid handshake message")
    return message


async def send(writer, **message):
    data = json.dumps(message).encode()
    writer.write(struct.pack("!I", len(data)) + data)
    await asyncio.wait_for(writer.drain(), 5)


async def forward(reader, writer):
    while data := await reader.read(65536):
        writer.write(data)
        await asyncio.wait_for(writer.drain(), 15)


async def bridge(guest_reader, guest_writer, peer):
    tasks = [
        asyncio.create_task(forward(guest_reader, peer.writer)),
        asyncio.create_task(forward(peer.reader, guest_writer)),
    ]
    try:
        await asyncio.wait(tasks, return_when=asyncio.FIRST_COMPLETED)
    finally:
        for task in tasks:
            task.cancel()
        await asyncio.gather(*tasks, return_exceptions=True)


class Relay:
    def __init__(self):
        self.rooms = {}
        self.connections = 0

    async def handle(self, reader, writer):
        if self.connections >= 1024:
            writer.close()
            return
        self.connections += 1
        try:
            request = await asyncio.wait_for(read_message(reader), 5)
            role = request.get("role")
            if role == "host":
                await self.host(reader, writer)
            elif role == "guest":
                await self.guest(reader, writer, request)
            elif role == "peer":
                await self.peer(reader, writer, request)
            else:
                await send(writer, type="rejected")
        except (OSError, EOFError, TimeoutError, ValueError):
            pass
        finally:
            self.connections -= 1
            writer.close()

    async def host(self, reader, writer):
        if len(self.rooms) >= 128:
            await send(writer, type="rejected")
            return
        code = f"{secrets.randbelow(1_000_000):06d}"
        while code in self.rooms:
            code = f"{secrets.randbelow(1_000_000):06d}"
        room = self.rooms[code] = Room(writer)
        closed = asyncio.create_task(reader.read(1))
        try:
            await send(writer, type="room", code=code, key=room.key)
            while not (await asyncio.wait([closed], timeout=10))[0]:
                await send(writer, type="ping")
        finally:
            closed.cancel()
            await asyncio.gather(closed, return_exceptions=True)
            self.rooms.pop(code)
            for guest in room.guests:
                guest.close()
            for pending in room.pending.values():
                pending.cancel()

    async def guest(self, reader, writer, request):
        code = request.get("code")
        room = self.rooms.get(code) if isinstance(code, str) else None
        if room is None or len(room.guests) >= 8:
            await send(writer, type="rejected")
            return
        room.guests.add(writer)
        ticket = secrets.token_hex(16)
        pending = room.pending[ticket] = asyncio.get_running_loop().create_future()
        try:
            await send(room.control, type="peer", ticket=ticket)
            peer = await asyncio.wait_for(asyncio.shield(pending), 5)
            await send(writer, type="ready")
            await bridge(reader, writer, peer)
        finally:
            room.pending.pop(ticket, None)
            room.guests.discard(writer)
            if pending.done() and not pending.cancelled():
                peer = pending.result()
                peer.finished.set_result(None)
            else:
                pending.cancel()

    async def peer(self, reader, writer, request):
        code, key, ticket = (request.get(field) for field in ("code", "key", "ticket"))
        if not all(isinstance(value, str) for value in (code, key, ticket)):
            return
        room = self.rooms.get(code)
        if room is None or len(key) != len(room.key) or not key.isascii():
            return
        if not secrets.compare_digest(room.key, key):
            return
        pending = room.pending.get(ticket)
        if pending is None or pending.done():
            return
        peer = Peer(reader, writer, asyncio.get_running_loop().create_future())
        pending.set_result(peer)
        await peer.finished


async def main(args):
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.minimum_version = ssl.TLSVersion.TLSv1_2
    context.load_cert_chain(args.cert, args.key)
    server = await asyncio.start_server(
        Relay().handle, args.bind, args.port, ssl=context, ssl_handshake_timeout=5
    )
    async with server:
        await server.serve_forever()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bind", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=8443)
    parser.add_argument("--cert", required=True)
    parser.add_argument("--key", required=True)
    asyncio.run(main(parser.parse_args()))
