#!/usr/bin/env python3
"""生成讲解石 / 广播发信器全部贴图（纯标准库，无需 PIL/numpy）。

用法: python3 tools/gen_speaker_textures.py
输出（assets/explainstone/textures/block/）:
  speaker_rock.png                默认讲解石的灰色岩石外壳
  speaker_grille_<变体>.png       各变体"石面 + 冲孔网罩"成品正面贴图（不透明，不会发黑）
  transmitter_shell.png           发信器黑灰外壳
  transmitter_edge.png            发信器 12 条黑色棱边
  transmitter_screen.png          发信器正面屏幕（左上角 >broadcasting_system）
"""
import glob
import math
import os
import random
import struct
import zipfile
import zlib

RES = 128
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "explainstone", "textures", "block")
PREVIEW_DIR = os.path.join(ROOT, "build", "preview")

VARIANTS = [
    ("speaker_block", "explainstone:block/speaker_rock"),
    ("stone_speaker_block", "minecraft:block/stone"),
    ("cobblestone_speaker_block", "minecraft:block/cobblestone"),
    ("granite_speaker_block", "minecraft:block/granite"),
    ("diorite_speaker_block", "minecraft:block/diorite"),
    ("andesite_speaker_block", "minecraft:block/andesite"),
]


# ------------------------------------------------------------------ PNG 写
def write_png(path, w, h, data, channels=3, scale=1):
    if scale != 1:
        big = bytearray(w * scale * h * scale * channels)
        for y in range(h * scale):
            for x in range(w * scale):
                si = ((y // scale) * w + x // scale) * channels
                di = (y * w * scale + x) * channels
                big[di:di + channels] = data[si:si + channels]
        data, w, h = big, w * scale, h * scale
    raw = bytearray()
    stride = w * channels
    for y in range(h):
        raw.append(0)
        raw += data[y * stride:(y + 1) * stride]

    def chunk(tag, d):
        return struct.pack(">I", len(d)) + tag + d + struct.pack(">I", zlib.crc32(tag + d) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6 if channels == 4 else 2, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, "wb").write(png)


# ------------------------------------------------------------------ PNG 读
def read_png_bytes(data):
    """返回 (w, h, rgba)；支持灰度/调色板/RGB/RGBA，位深 1/2/4/8"""
    assert data[:8] == b"\x89PNG\r\n\x1a\n"
    pos, w, h, depth, color = 8, 0, 0, 8, 2
    idat, palette, trns = b"", b"", b""
    while pos < len(data):
        ln = struct.unpack(">I", data[pos:pos + 4])[0]
        tag = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + ln]
        if tag == b"IHDR":
            w, h, depth, color = struct.unpack(">IIBB", body[:10])
        elif tag == b"PLTE":
            palette = body
        elif tag == b"tRNS":
            trns = body
        elif tag == b"IDAT":
            idat += body
        pos += 12 + ln

    nch = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[color]
    bpp = max(1, nch * depth // 8)
    stride = (w * nch * depth + 7) // 8
    raw = zlib.decompress(idat)
    out = bytearray(w * h * 4)
    prev = bytearray(stride)
    p = 0
    maxv = (1 << depth) - 1
    for y in range(h):
        ft = raw[p]; p += 1
        line = bytearray(raw[p:p + stride]); p += stride
        if ft == 1:
            for i in range(bpp, stride):
                line[i] = (line[i] + line[i - bpp]) & 0xFF
        elif ft == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 0xFF
        elif ft == 3:
            for i in range(stride):
                a = line[i - bpp] if i >= bpp else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 0xFF
        elif ft == 4:
            for i in range(stride):
                a = line[i - bpp] if i >= bpp else 0
                b = prev[i]
                c = prev[i - bpp] if i >= bpp else 0
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 0xFF
        prev = line
        for x in range(w):
            bit = x * nch * depth
            if depth == 8:
                vals = line[x * nch:(x + 1) * nch]
            else:
                v = (line[bit // 8] >> (8 - depth - (bit % 8))) & maxv
                vals = bytes([int(v * 255 / maxv)])
            di = (y * w + x) * 4
            if color == 3:
                i3 = vals[0] if depth == 8 else vals[0]
                idx = i3 if depth == 8 else int(i3 * maxv / 255)
                out[di:di + 3] = palette[idx * 3:idx * 3 + 3]
                out[di + 3] = trns[idx] if idx < len(trns) else 255
            elif color == 0:
                out[di] = out[di + 1] = out[di + 2] = vals[0]
                out[di + 3] = 255
            elif color == 4:
                out[di] = out[di + 1] = out[di + 2] = vals[0]
                out[di + 3] = vals[1]
            elif color == 2:
                out[di:di + 3] = vals[:3]
                out[di + 3] = 255
            else:
                out[di:di + 4] = vals[:4]
    return w, h, out


def read_png(path):
    return read_png_bytes(open(path, "rb").read())


# --------------------------------------------------------------- 噪声工具
def pnoise(res, freq, seed):
    rng = random.Random(seed)
    g = [[rng.random() for _ in range(freq)] for _ in range(freq)]
    out = [0.0] * (res * res)
    step = freq / res
    for y in range(res):
        fy = y * step; y0 = int(fy) % freq; ty = fy - int(fy); ty = ty * ty * (3 - 2 * ty)
        for x in range(res):
            fx = x * step; x0 = int(fx) % freq; tx = fx - int(fx); tx = tx * tx * (3 - 2 * tx)
            a = g[y0][x0] * (1 - tx) + g[y0][(x0 + 1) % freq] * tx
            b = g[(y0 + 1) % freq][x0] * (1 - tx) + g[(y0 + 1) % freq][(x0 + 1) % freq] * tx
            out[y * res + x] = a * (1 - ty) + b * ty
    return out


def fbm(res, seed, octaves):
    acc = [0.0] * (res * res)
    for i, (f, a) in enumerate(octaves):
        n = pnoise(res, f, seed + i * 977)
        for k in range(res * res):
            acc[k] += n[k] * a
    lo, hi = min(acc), max(acc)
    span = (hi - lo) or 1.0
    return [(v - lo) / span for v in acc]


def rock_mottle(seed=5150, mid=126):
    fine = fbm(RES, seed, ((8, .30), (16, .30), (32, .25), (64, .15)))
    patch = fbm(RES, seed + 401, ((3, .6), (6, .4)))
    rgb = bytearray(RES * RES * 3)
    for i in range(RES * RES):
        g = max(0, min(255, mid + (fine[i] - .5) * 34 + (patch[i] - .5) * 22))
        rgb[i * 3] = int(g * .985); rgb[i * 3 + 1] = int(g); rgb[i * 3 + 2] = int(min(255, g * 1.02))
    return rgb


def add_cracks(rgb, seed=7, count=6):
    rng = random.Random(seed)
    for _ in range(count):
        x, y = rng.uniform(0, RES), rng.uniform(0, RES)
        ang = rng.uniform(0, math.tau)
        for _s in range(int(rng.uniform(RES * .25, RES * .6))):
            ang += rng.uniform(-.22, .22)
            x = (x + math.cos(ang)) % RES; y = (y + math.sin(ang)) % RES
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    if math.hypot(dx, dy) > .8:
                        continue
                    k = ((int(y) + dy) % RES * RES + (int(x) + dx) % RES) * 3
                    for c in range(3):
                        rgb[k + c] = max(0, min(255, rgb[k + c] - 26))
            k = (int(y + math.sin(ang + math.pi / 2) * 1.4) % RES * RES
                 + int(x + math.cos(ang + math.pi / 2) * 1.4) % RES) * 3
            for c in range(3):
                rgb[k + c] = max(0, min(255, rgb[k + c] + 14))


def add_speckles(rgb, seed=31, count=700):
    rng = random.Random(seed)
    for _ in range(count):
        k = (rng.randrange(RES) * RES + rng.randrange(RES)) * 3
        v = rng.choice((20, 14, -18, -24, 8, -8))
        for c in range(3):
            rgb[k + c] = max(0, min(255, rgb[k + c] + v))


# --------------------------------------------------------------- 冲孔网罩
def fill_poly(rgb, pts, color, alpha=1.0):
    ys = [p[1] for p in pts]
    for y in range(int(math.floor(min(ys))), int(math.ceil(max(ys))) + 1):
        if not (0 <= y < RES):
            continue
        xs = []
        n = len(pts)
        for i in range(n):
            ax, ay = pts[i]; bx, by = pts[(i + 1) % n]
            if (ay <= y < by) or (by <= y < ay):
                xs.append(ax + (bx - ax) * (y - ay) / (by - ay))
        xs.sort()
        for i in range(0, len(xs) - 1, 2):
            x0, x1 = int(math.floor(xs[i])), int(math.ceil(xs[i + 1]))
            for x in range(max(0, x0), min(RES, x1 + 1)):
                k = (y * RES + x) * 3
                for c in range(3):
                    rgb[k + c] = int(rgb[k + c] * (1 - alpha) + color[c] * alpha)


def hole_poly(cx, cy, r, seed):
    rng = random.Random(seed)
    n = rng.choice((6, 6, 7, 7, 8))
    pts = []
    for i in range(n):
        a = math.tau * i / n + rng.uniform(-.13, .13)
        rr = r * rng.uniform(.86, 1.10)
        pts.append((cx + math.cos(a) * rr, cy + math.sin(a) * rr))
    return pts


def bake_grille(rock, seed=4321):
    rgb = bytearray(rock)
    rng = random.Random(seed)
    c = RES / 2.0
    for y in range(RES):
        for x in range(RES):
            d = math.hypot(x - c + .5, y - c + .5) / (RES * .5)
            t = min(1.0, max(0.0, 1.0 - d * d))
            k = (y * RES + x) * 3
            for ch in range(3):
                rgb[k + ch] = int(rgb[k + ch] * (1.0 - .18 * t))
    holes = []
    hexr = RES * .089
    for row in range(-4, 5):
        for col in range(-4, 5):
            hx = c + col * hexr * 2.52 + (hexr * 1.26 if row % 2 else 0) + rng.uniform(-.9, .9)
            hy = c + row * hexr * 2.20 + rng.uniform(-.9, .9)
            if math.hypot(hx - c, hy - c) > RES * .545:
                continue
            holes.append((hx, hy, hexr * rng.uniform(.84, .99)))
    for hx, hy, r in holes:
        fill_poly(rgb, hole_poly(hx, hy - r * .22, r + 1.9, int(hx * 37 + hy)), (150, 150, 156), .45)
        fill_poly(rgb, hole_poly(hx, hy + r * .18, r + 1.5, int(hx * 37 + hy + 2)), (10, 10, 12), .45)
    for hx, hy, r in holes:
        fill_poly(rgb, hole_poly(hx, hy, r, int(hx * 37 + hy + 5)), (8, 8, 10), 1.0)
    return rgb


# ------------------------------------------------------- 原版贴图 → 128px
def load_vanilla(tex_id, size=128):
    name = tex_id.split("/")[-1]
    jars = [os.path.join(os.path.expanduser("~"), ".gradle", "caches", "fabric-loom", "1.21.1", "minecraft-client-only.jar")]
    jars += glob.glob(os.path.join(ROOT, ".gradle", "loom-cache", "minecraftMaven", "net", "minecraft",
                                   "minecraft-clientOnly-*", "*", "*.jar"))
    entry = "assets/minecraft/textures/block/%s.png" % name
    for jar in jars:
        try:
            with zipfile.ZipFile(jar) as zf:
                data = zf.read(entry)
        except (KeyError, OSError, zipfile.BadZipFile):
            continue
        w, h, rgba = read_png_bytes(data)
        out = bytearray(size * size * 3)
        for y in range(size):
            sy = y * h // size
            for x in range(size):
                sx = x * w // size
                si = (sy * w + sx) * 4
                di = (y * size + x) * 3
                out[di:di + 3] = rgba[si:si + 3]
        return out
    raise FileNotFoundError(tex_id)


# ------------------------------------------------------------ 发信器贴图
FONT = {
    ">": ["#....", "#....", ".#...", "..#..", ".#...", "#....", "#...."],
    "_": [".....", ".....", ".....", ".....", ".....", ".....", "#####"],
    "a": [".....", ".....", ".###.", "....#", ".####", "#...#", ".####"],
    "b": ["#....", "#....", "###..", "#..#.", "#..#.", "###..", "....."],
    "c": [".....", ".....", ".###.", "#....", "#....", ".###.", "....."],
    "d": ["....#", "....#", ".####", "#...#", "#...#", ".####", "....."],
    "e": [".....", ".....", ".###.", "#...#", "#####", "#....", ".####"],
    "g": [".....", ".....", ".####", "#...#", ".####", "....#", "###.."],
    "i": ["..#..", ".....", ".##..", "..#..", "..#..", ".###.", "....."],
    "m": [".....", ".....", "##.##", "#.#.#", "#.#.#", "#.#.#", "....."],
    "n": [".....", ".....", "####.", "#...#", "#...#", "#...#", "....."],
    "o": [".....", ".....", ".###.", "#..#.", "#..#.", ".###.", "....."],
    "r": [".....", ".....", "#.##.", "##...", "#....", "#....", "....."],
    "s": [".....", ".....", ".####", "#....", ".###.", "....#", "####."],
    "t": ["..#..", "..#..", "#####", "..#..", "..#..", "...#.", "....."],
    "y": [".....", ".....", "#...#", "#...#", ".####", "....#", "###.."],
    "S": [".####", "#....", "#....", ".###.", "....#", "....#", "####."],
    "E": ["#####", "#....", "#....", "####.", "#....", "#....", "#####"],
    "T": ["#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."],
    " ": [".....", ".....", ".....", ".....", ".....", ".....", "....."],
}


def draw_text(rgb, text, x0, y0, color, size=128):
    for ch in text:
        glyph = FONT.get(ch, FONT[" "])
        for row, line in enumerate(glyph):
            for col, px in enumerate(line):
                if px != "#":
                    continue
                x, y = x0 + col, y0 + row
                if 0 <= x < size and 0 <= y < size:
                    k = (y * size + x) * 3
                    rgb[k:k + 3] = bytes(color)
        x0 += 6


def make_transmitter_screen(size=128):
    rgb = bytearray(size * size * 3)
    noise = fbm(size, 881, ((8, .4), (16, .3), (32, .3)))
    for y in range(size):
        for x in range(size):
            base = 14 + noise[y * size + x] * 14
            scan = 3 if y % 3 == 0 else 0
            k = (y * size + x) * 3
            rgb[k] = int(base * .75)
            rgb[k + 1] = int(base * .9 + scan)
            rgb[k + 2] = int(base + scan)
    for i in range(size):
        for t in range(2):
            for (x, y) in ((i, t), (i, size - 1 - t), (t, i), (size - 1 - t, i)):
                k = (y * size + x) * 3
                rgb[k:k + 3] = bytes((40, 48, 54))
    draw_text(rgb, ">broadcasting_system", 6, 8, (110, 226, 190), size)
    rng = random.Random(99)
    for row in range(3):
        y = 34 + row * 10
        x = 6
        for _ in range(rng.randint(6, 14)):
            wdt = rng.randint(2, 6)
            for xx in range(x, min(size - 6, x + wdt)):
                for yy in range(y, y + 3):
                    k = (yy * size + xx) * 3
                    rgb[k:k + 3] = bytes((70, 120, 110))
            x += wdt + 2
    for y in range(size - 16, size - 10):
        for x in range(size - 16, size - 10):
            k = (y * size + x) * 3
            rgb[k:k + 3] = bytes((110, 226, 190))
    return rgb


def make_transmitter_shell(size=64):
    rgb = bytearray(size * size * 3)
    noise = fbm(size, 4242, ((4, .4), (8, .3), (16, .2), (32, .1)))
    for y in range(size):
        for x in range(size):
            g = 46 + noise[y * size + x] * 26
            k = (y * size + x) * 3
            rgb[k] = int(g * .98); rgb[k + 1] = int(g); rgb[k + 2] = int(g * 1.06)
    for i in range(size):
        if i % 16 == 0:
            for x in range(size):
                k = (i * size + x) * 3
                rgb[k:k + 3] = bytes((24, 26, 30))
            for y in range(size):
                k = (y * size + i) * 3
                rgb[k:k + 3] = bytes((24, 26, 30))
    for (cx, cy) in ((6, 6), (size - 7, 6), (6, size - 7), (size - 7, size - 7)):
        for dy in (-1, 0, 1):
            for dx in (-1, 0, 1):
                k = ((cy + dy) * size + cx + dx) * 3
                rgb[k:k + 3] = bytes((92, 96, 104)) if (dx or dy) else bytes((140, 144, 152))
    return rgb


def make_transmitter_edge(size=16):
    rgb = bytearray(size * size * 3)
    rng = random.Random(7)
    for i in range(size * size):
        g = 10 + rng.randint(0, 8)
        rgb[i * 3:i * 3 + 3] = bytes((g, g, g + 2))
    return rgb


def make_songmelyn_body(size=64):
    """白色塑料外壳：细微纹理 + 一圈分缝"""
    rgb = bytearray(size * size * 3)
    noise = fbm(size, 313, ((4, .5), (8, .3), (16, .2)))
    for y in range(size):
        for x in range(size):
            g = 226 + noise[y * size + x] * 16
            k = (y * size + x) * 3
            rgb[k] = int(g)
            rgb[k + 1] = int(min(255, g * .995))
            rgb[k + 2] = int(min(255, g * .97))
    for i in range(size):
        if 3 <= i <= 5:
            for x in range(size):
                k = (i * size + x) * 3
                rgb[k:k + 3] = bytes((204, 202, 196))
    return rgb


def make_songmelyn_front(size=128):
    """正面：白框 + 灰色布网 + 顶部圆铭牌 + SET 铭牌 + 底部出音槽"""
    rgb = bytearray(make_songmelyn_body(size))  # 先铺白色外壳
    # 布网区域
    x0, y0, x1, y1 = 16, 30, size - 16, size - 20
    noise = fbm(size, 777, ((16, .4), (32, .3), (64, .3)))
    for y in range(y0, y1):
        for x in range(x0, x1):
            base = 108 + noise[y * size + x] * 34
            # 编织纹理：交错细线
            weave = 10 if ((x // 2 + y // 2) % 2 == 0) else -10
            g = base + weave
            k = (y * size + x) * 3
            rgb[k] = int(g * .97)
            rgb[k + 1] = int(g)
            rgb[k + 2] = int(min(255, g * 1.04))
    # 布网四周压暗的边
    for i in range(3):
        for x in range(x0 - 1, x1 + 1):
            for y in (y0 - 1 - i, y1 + i):
                if 0 <= y < size:
                    k = (y * size + x) * 3
                    rgb[k:k + 3] = bytes((150, 150, 150))
        for y in range(y0 - 1, y1 + 1):
            for x in (x0 - 1 - i, x1 + i):
                if 0 <= x < size:
                    k = (y * size + x) * 3
                    rgb[k:k + 3] = bytes((150, 150, 150))
    # 顶部长圆铭牌（深灰）+ 白色三角logo
    bx, by, bw, bh = size // 2 - 13, 8, 26, 26
    for y in range(by, by + bh):
        for x in range(bx, bx + bw):
            dx = (x - (bx + bw / 2)) / (bw / 2)
            dy = (y - (by + bh / 2)) / (bh / 2)
            if dx * dx + dy * dy > 1.05:
                continue
            k = (y * size + x) * 3
            rim = dx * dx + dy * dy > 0.72
            rgb[k:k + 3] = bytes((228, 226, 218)) if rim else bytes((58, 58, 62))
    # 三角 logo
    for row in range(8):
        half = row * 1.1 + 1
        for x in range(int(size / 2 - half), int(size / 2 + half)):
            y = by + 12 + row
            if 0 <= x < size and 0 <= y < size:
                k = (y * size + x) * 3
                rgb[k:k + 3] = bytes((236, 236, 232))
    # SET 铭牌
    sx, sy = size // 2 - 12, y1 - 12
    for y in range(sy, sy + 9):
        for x in range(sx, sx + 24):
            k = (y * size + x) * 3
            rgb[k:k + 3] = bytes((52, 52, 56))
    draw_text(rgb, "SET", sx + 5, sy + 1, (232, 232, 228), size)
    # 底部出音槽
    for y in range(size - 16, size - 9):
        for x in range(size // 2 - 18, size // 2 + 18):
            k = (y * size + x) * 3
            rgb[k:k + 3] = bytes((176, 172, 164)) if y in (size - 15, size - 10) else bytes((120, 116, 110))
    return rgb


def make_songmelyn_badge(size=32):
    """顶部圆形铭牌（单独一块小方块的正面）"""
    rgb = bytearray(bytes((232, 230, 222)) * (size * size))
    cx = cy = size / 2
    for y in range(size):
        for x in range(size):
            dx = (x - cx + .5) / (size / 2)
            dy = (y - cy + .5) / (size / 2)
            d = dx * dx + dy * dy
            if d > 1.0:
                continue
            k = (y * size + x) * 3
            rgb[k:k + 3] = bytes((58, 58, 62))
    for row in range(9):
        half = row * 1.2 + 1
        for x in range(int(cx - half), int(cx + half)):
            y = int(cy) - 6 + row
            if 0 <= x < size and 0 <= y < size:
                k = (y * size + x) * 3
                rgb[k:k + 3] = bytes((236, 236, 232))
    return rgb


def main():
    rock = rock_mottle()
    add_cracks(rock)
    add_speckles(rock)
    write_png(os.path.join(TEX_DIR, "speaker_rock.png"), RES, RES, rock)

    for block_id, tex_id in VARIANTS:
        base = rock if tex_id == "explainstone:block/speaker_rock" else load_vanilla(tex_id, RES)
        write_png(os.path.join(TEX_DIR, "speaker_grille_%s.png" % block_id), RES, RES, bake_grille(base))

    write_png(os.path.join(TEX_DIR, "songmelyn_body.png"), 64, 64, make_songmelyn_body())
    write_png(os.path.join(TEX_DIR, "songmelyn_front.png"), 128, 128, make_songmelyn_front())
    write_png(os.path.join(TEX_DIR, "songmelyn_badge.png"), 32, 32, make_songmelyn_badge())
    write_png(os.path.join(PREVIEW_DIR, "tex_songmelyn.png"), 128, 128, make_songmelyn_front(), scale=2)

    write_png(os.path.join(TEX_DIR, "transmitter_shell.png"), 64, 64, make_transmitter_shell())
    write_png(os.path.join(TEX_DIR, "transmitter_edge.png"), 16, 16, make_transmitter_edge())
    screen = make_transmitter_screen()
    write_png(os.path.join(TEX_DIR, "transmitter_screen.png"), 128, 128, screen)

    write_png(os.path.join(PREVIEW_DIR, "tex_granite_grille.png"), RES, RES,
              bake_grille(load_vanilla("minecraft:block/granite", RES)), scale=2)
    write_png(os.path.join(PREVIEW_DIR, "tex_transmitter_screen.png"), 128, 128, screen, scale=2)
    write_png(os.path.join(PREVIEW_DIR, "tex_transmitter_shell.png"), 64, 64, make_transmitter_shell(), scale=2)
    print("textures ok")


if __name__ == "__main__":
    main()
