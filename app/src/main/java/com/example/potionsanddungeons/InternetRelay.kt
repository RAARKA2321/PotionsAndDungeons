package com.example.potionsanddungeons

import org.json.JSONException
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

internal class InternetRelay(endpoint: String) {
    private val host = endpoint.substringBeforeLast(':', endpoint)
    private val port = if (':' in endpoint) endpoint.substringAfterLast(':').toIntOrNull() else 8443

    fun connect(request: JSONObject): SSLSocket {
        if (host.isBlank() || port == null || port !in 1..65535) {
            throw IOException("Invalid relay address")
        }
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(host, port), 4500)
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            val tls = factory.createSocket(socket, host, port, true) as SSLSocket
            tls.soTimeout = 6500
            tls.sslParameters = tls.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
            tls.startHandshake()
            writeLanFrame(DataOutputStream(tls.outputStream), request.toString())
            return tls
        } catch (e: Exception) {
            runCatching { socket.close() }
            throw e
        }
    }

    fun read(socket: Socket): JSONObject = try {
        JSONObject(readLanFrame(DataInputStream(socket.getInputStream())))
    } catch (e: JSONException) {
        throw IOException("Invalid relay response", e)
    }
}
