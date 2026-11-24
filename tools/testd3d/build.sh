#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
SOURCE="$ROOT_DIR/tools/testd3d/TestDirect3D.cpp"
OUTPUT_DIR="$ROOT_DIR/app/src/main/assets/testd3d"
OUTPUT="$OUTPUT_DIR/WinMaliD3DTest.exe"
CXX="$(printenv CXX || printf '%s' i686-w64-mingw32-g++)"
OBJDUMP="$(printenv OBJDUMP || printf '%s' i686-w64-mingw32-objdump)"
command -v "$CXX" >/dev/null 2>&1 || {
    echo "Missing $CXX. Install g++-mingw-w64-i686." >&2
    exit 2
}
command -v "$OBJDUMP" >/dev/null 2>&1 || {
    echo "Missing $OBJDUMP. Install MinGW-w64 i686 binutils." >&2
    exit 2
}
mkdir -p "$OUTPUT_DIR"
"$CXX" -std=c++17 -Os -Wall -Wextra -D_WIN32_WINNT=0x0601 \
    -static -static-libgcc -static-libstdc++ -Wl,--subsystem,windows \
    -o "$OUTPUT" "$SOURCE" -ld3d9 -luser32 -lgdi32
test -s "$OUTPUT"
"$OBJDUMP" -f "$OUTPUT" | grep -q "pei-i386"
"$OBJDUMP" -p "$OUTPUT" | grep -qi "d3d9.dll"
python3 - "$OUTPUT" <<'PY'
import pathlib
import struct
import sys
path = pathlib.Path(sys.argv[1])
data = path.read_bytes()
if len(data) < 0x40 or data[:2] != b"MZ":
    raise SystemExit("Missing valid PE DOS header")
offset = struct.unpack_from("<I", data, 0x3C)[0]
if offset + 6 > len(data) or data[offset:offset + 4] != b"PE\0\0":
    raise SystemExit("Missing valid PE signature")
machine = struct.unpack_from("<H", data, offset + 4)[0]
if machine != 0x014C:
    raise SystemExit(f"Expected x86 PE machine 0x014C, got 0x{machine:04X}")
print(f"Built x86 Direct3D 9 diagnostic: {path.name} ({len(data)} bytes)")
PY
