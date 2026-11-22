#!/usr/bin/env bash
set -euo pipefail

desktop_dir=$(cd -- "$(dirname -- "$0")" && pwd)
jre_dir=$(cd -- "${1:?Pass the extracted Windows x64 JRE directory}" && pwd)
output_path=${2:?Pass the destination ZIP path}
case "$output_path" in
    /*) ;;
    *) output_path="$PWD/$output_path" ;;
esac
install_dir="$desktop_dir/build/install/PotionsAndDungeons"
test -f "$jre_dir/bin/javaw.exe"
test -f "$install_dir/lib/skiko-awt-runtime-windows-x64-"*.jar

version=$(sed -n 's/^version = "\([^"]*\)".*/\1/p' "$desktop_dir/build.gradle.kts")
test -n "$version"
stage_dir=$(mktemp -d)
trap 'rm -rf -- "$stage_dir"' EXIT
game_dir="$stage_dir/PotionsAndDungeons"
mkdir -p "$game_dir"
cp -R -- "$jre_dir" "$game_dir/runtime"
cp -R -- "$install_dir/lib" "$game_dir/lib"
GOOS=windows GOARCH=amd64 CGO_ENABLED=0 go build -trimpath \
    -ldflags="-s -w -H windowsgui -X main.version=$version" \
    -o "$game_dir/PotionsAndDungeons.exe" "$desktop_dir/launcher/main.go"
cp -- "$(go env GOROOT)/LICENSE" "$game_dir/Go-LICENSE.txt"
(cd -- "$stage_dir" && zip -qr "$stage_dir/game.zip" PotionsAndDungeons)
cp -- "$stage_dir/game.zip" "$output_path"
