"""Small 8-bit RGB/RGBA PNG reader for repository asset checks (stdlib only)."""
import struct
import zlib

def read_rgba(path):
    png = path.read_bytes()
    if png[:8] != b'\x89PNG\r\n\x1a\n':
        raise ValueError("Expected PNG")
    width, height, depth, kind, comp, filt, interlace = struct.unpack(">IIBBBBB", png[16:29])
    if depth != 8 or kind not in (2, 6) or comp or filt or interlace:
        raise ValueError("Expected non-interlaced 8-bit RGB/RGBA")
    at, compressed = 8, b""
    while at < len(png):
        size = struct.unpack(">I", png[at:at+4])[0]
        if png[at+4:at+8] == b"IDAT": compressed += png[at+8:at+8+size]
        at += size+12
    raw = zlib.decompress(compressed)
    bpp = 4 if kind == 6 else 3
    stride = width*bpp
    previous = bytearray(stride)
    pixels = []
    def paeth(a,b,c):
        p=a+b-c
        distances=[abs(p-a),abs(p-b),abs(p-c)]
        return (a,b,c)[distances.index(min(distances))]
    for y in range(height):
        mode = raw[y*(stride+1)]
        row = bytearray(raw[y*(stride+1)+1:(y+1)*(stride+1)])
        for i in range(stride):
            a = row[i-bpp] if i >= bpp else 0
            b = previous[i]
            c = previous[i-bpp] if i >= bpp else 0
            prediction = (0, a, b, (a+b)//2, paeth(a,b,c))[mode]
            row[i] = (row[i]+prediction)&255
        pixels.append([tuple(row[x:x+bpp]) + (() if bpp == 4 else (255,)) for x in range(0,stride,bpp)])
        previous = row
    return width, height, pixels
