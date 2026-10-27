#!/usr/bin/env python3
"""生成讲解石各变体的方块模型 / 方块状态 / 物品模型（JSON）+ 等轴测预览图。

用法: python3 tools/gen_speaker_model.py
输出:
  assets/explainstone/models/block/<变体>.json
  assets/explainstone/blockstates/<变体>.json
  assets/explainstone/models/item/<变体>.json
  build/preview/speaker_model.png（默认变体的三视角预览）
"""
import json
import math
import os
import struct
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "explainstone")
BLOCK_MODELS = os.path.join(ASSETS, "models", "block")
ITEM_MODELS = os.path.join(ASSETS, "models", "item")
BLOCKSTATES = os.path.join(ASSETS, "blockstates")
TEX_DIR = os.path.join(ASSETS, "textures", "block")
PREVIEW_PATH = os.path.join(ROOT, "build", "preview", "speaker_model.png")

# (方块 id, 外壳纹理, 正面网罩成品贴图, 中文名, 英文名)
VARIANTS = [
    ("speaker_block", "explainstone:block/speaker_rock",
     "explainstone:block/speaker_grille_speaker_block", "讲解石", "Explainstone"),
    ("stone_speaker_block", "minecraft:block/stone",
     "explainstone:block/speaker_grille_stone_speaker_block", "石头讲解石", "Stone Explainstone"),
    ("cobblestone_speaker_block", "minecraft:block/cobblestone",
     "explainstone:block/speaker_grille_cobblestone_speaker_block", "圆石讲解石", "Cobblestone Explainstone"),
    ("granite_speaker_block", "minecraft:block/granite",
     "explainstone:block/speaker_grille_granite_speaker_block", "花岗岩讲解石", "Granite Explainstone"),
    ("diorite_speaker_block", "minecraft:block/diorite",
     "explainstone:block/speaker_grille_diorite_speaker_block", "闪长岩讲解石", "Diorite Explainstone"),
    ("andesite_speaker_block", "minecraft:block/andesite",
     "explainstone:block/speaker_grille_andesite_speaker_block", "安山岩讲解石", "Andesite Explainstone"),
]

ELEMENTS = [
    # 没有底部石台：石头本体直接从 y=0 起，四边内收量各不同（不规则），
    # 前后(Z)比左右(X)薄；顶部两级收边，最高到 16 保持整格高度方便站立。
    # name,       from,                to,                  uv_seed
    ("belt_w",    (0.9, 0, 1.5),       (3.7, 9.2, 12.9),    23),
    ("belt_e",    (11.6, 0, 1.7),      (14.4, 8.9, 12.7),   37),
    ("belt_low",  (3.7, 0, 1.5),       (11.6, 3.0, 12.9),   41),
    ("belt_s",    (3.7, 0, 2.4),       (11.6, 9.2, 12.9),   53),
    ("shoulder",  (1.9, 9.2, 2.1),     (13.4, 12.2, 12.4),  61),
    ("cap1",      (3.0, 12.2, 2.8),    (12.0, 14.4, 12.0),  73),
    ("cap2",      (4.6, 14.4, 4.2),    (10.4, 16.0, 10.8),  79),
]
# 网罩盖片：薄薄一层，只有朝北的面，用带透明的孔洞贴图叠在 belt_s 前壁上
GRILLE_FROM = (3.75, 2.95, 1.75)
GRILLE_TO = (11.55, 9.15, 2.55)

FACES = ("north", "south", "east", "west", "up", "down")
FACE_AXIS = {
    "north": ("z", "from", False),
    "south": ("z", "to", True),
    "east": ("x", "to", True),
    "west": ("x", "from", False),
    "up": ("y", "to", True),
    "down": ("y", "from", False),
}


def face_size(frm, to, face):
    dx, dy, dz = (to[i] - frm[i] for i in range(3))
    if face in ("up", "down"):
        return dx, dz
    if face in ("north", "south"):
        return dx, dy
    return dz, dy


def boundary_cull(frm, to, face):
    axis, which, _ = FACE_AXIS[face]
    val = frm if which == "from" else to
    i = {"x": 0, "y": 1, "z": 2}[axis]
    if val[i] == (0 if which == "from" else 16):
        return face
    return None


def build_faces(frm, to, seed):
    faces = {}
    for face in FACES:
        w, h = face_size(frm, to, face)
        if w <= 0 or h <= 0:
            continue
        ox = min((seed * 3) % 8, 16 - w)
        oy = min((seed * 5) % 8, 16 - h)
        data = {
            "texture": "#rock",
            "uv": [round(ox, 2), round(oy, 2), round(ox + w, 2), round(oy + h, 2)],
        }
        cull = boundary_cull(frm, to, face)
        if cull:
            data["cullface"] = cull
        faces[face] = data
    return faces


def check_coplanar(elements):
    """报告共面且重叠的面（都是相邻 box 的内部接缝，被实体包住不会闪）"""
    planes = {}
    for el in elements:
        f, t = el["from"], el["to"]
        for face in el["faces"]:
            axis, which, _ = FACE_AXIS[face]
            val = round((f if which == "from" else t)[{"x": 0, "y": 1, "z": 2}[axis]], 3)
            r = []
            for a in ("x", "y", "z"):
                if a == axis:
                    continue
                i = {"x": 0, "y": 1, "z": 2}[a]
                r += [f[i], t[i]]
            planes.setdefault((axis, val), []).append((tuple(r), el.get("_name", "?")))
    hits = []
    for key, lst in planes.items():
        for i in range(len(lst)):
            for j in range(i + 1, len(lst)):
                (a, na), (b, nb) = lst[i], lst[j]
                ov = (min(a[1], b[1]) - max(a[0], b[0]), min(a[3], b[3]) - max(a[2], b[2]))
                if ov[0] > 0.001 and ov[1] > 0.001:
                    hits.append((key, na, nb))
    return hits


def build_model(rock_texture, grille_texture):
    elements = []
    for name, frm, to, seed in ELEMENTS:
        elements.append({
            "_name": name,
            "from": list(frm),
            "to": list(to),
            "faces": build_faces(frm, to, seed),
        })
    grille_faces = build_faces(GRILLE_FROM, GRILLE_TO, 97)
    grille_faces["north"]["texture"] = "#grille"
    grille_faces["north"]["uv"] = [0, 0, 16, 16]
    elements.append({
        "_name": "grille",
        "from": list(GRILLE_FROM),
        "to": list(GRILLE_TO),
        "faces": grille_faces,
    })
    return {
        # 继承原版方块模型的手持/GUI 显示变换，否则物品拿在手里会是整格大小
        "parent": "minecraft:block/block",
        "credit": "generated by tools/gen_speaker_model.py",
        "textures": {
            "particle": rock_texture,
            "rock": rock_texture,
            "grille": grille_texture,
        },
        "elements": elements,
    }


def build_transmitter_model():
    """黑灰外壳 + 12 条黑色棱边 + 正面屏幕"""
    def box(frm, to, tex, faces_override=None, seed=5):
        f = build_faces(frm, to, seed)
        for face in list(f):
            f[face]["texture"] = tex
            f[face].pop("cullface", None)
        if faces_override:
            for face, texname in faces_override.items():
                if face in f:
                    f[face]["texture"] = texname
        return {"from": list(frm), "to": list(to), "faces": f}

    elements = []
    # 主体（内收 1 格），正面是屏幕
    body = box((1, 1, 1), (15, 15, 15), "#shell", {"north": "#screen"}, seed=11)
    body["faces"]["north"]["uv"] = [0, 0, 16, 16]
    elements.append(body)

    t = 1.0  # 棱边粗细
    lo, hi = 0.0, 16.0
    edges = []
    # 4 条竖棱
    for (x, z) in ((lo, lo), (hi - t, lo), (lo, hi - t), (hi - t, hi - t)):
        edges.append((x, lo, z, x + t, hi, z + t))
    # 4 条顶部横棱（沿 X）
    for z in (lo, hi - t):
        edges.append((lo, hi - t, z, hi, hi, z + t))
    # 4 条底部横棱（沿 X）
    for z in (lo, hi - t):
        edges.append((lo, lo, z, hi, lo + t, z + t))
    # 4 条沿 Z 的横棱（上/下 × 左/右）
    for x in (lo, hi - t):
        for y in (lo, hi - t):
            edges.append((x, y, lo, x + t, y + t, hi))
    # 上面第二圈补边（保证 12 条边完整覆盖立方体棱）
    for e in edges:
        elements.append(box(e[:3], e[3:], "#edge", seed=17))

    return {
        "parent": "minecraft:block/block",
        "credit": "generated by tools/gen_speaker_model.py",
        "textures": {
            "particle": "explainstone:block/transmitter_shell",
            "shell": "explainstone:block/transmitter_shell",
            "edge": "explainstone:block/transmitter_edge",
            "screen": "explainstone:block/transmitter_screen",
        },
        "elements": elements,
    }


def build_songmelyn_model():
    """颂美铃：壁挂式教室广播音响（白色外壳 + 布网 + 顶部圆铭牌）

    注意：这里的贴图是"整张画好的面板"，所以每个面都用 uv 0..16 铺满，
    不做按尺寸平铺，否则正面会被裁切/拉伸得歪掉。
    """
    def box(frm, to, overrides, seed):
        f = build_faces(frm, to, seed)
        for face in list(f):
            f[face]["texture"] = "#body"
            f[face]["uv"] = [0, 0, 16, 16]
            f[face].pop("cullface", None)
        for face, tex in overrides.items():
            if face in f:
                f[face]["texture"] = tex
        return {"from": list(frm), "to": list(to), "faces": f}

    # 整块贴着方块背面（z 最大处 ~15.9），放到墙上时就像挂在墙面上
    elements = [
        # 背板：贴在墙上的那一面
        box((2.0, 1.6, 14.6), (14.0, 14.8, 15.9), {}, 11),
        # 箱体（正面是布网面板）
        box((1.6, 1.2, 11.2), (14.4, 15.2, 14.6), {"north": "#front"}, 23),
        # 顶部圆形铭牌，略微前凸
        box((6.6, 12.1, 10.8), (9.4, 14.9, 11.22), {"north": "#badge"}, 31),
    ]
    return {
        "parent": "minecraft:block/block",
        "credit": "generated by tools/gen_speaker_model.py",
        "textures": {
            "particle": "explainstone:block/songmelyn_body",
            "body": "explainstone:block/songmelyn_body",
            "front": "explainstone:block/songmelyn_front",
            "badge": "explainstone:block/songmelyn_badge",
        },
        "elements": elements,
    }


def build_blockstate(model_id):
    out = {}
    for face, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for playing in ("false", "true"):
            entry = {"model": "explainstone:block/" + model_id}
            if y:
                entry["y"] = y
            out["facing=%s,playing=%s" % (face, playing)] = entry
    return {"variants": out}


# ------------------------------------------------------------ PNG 读写
def read_png(path):
    data = open(path, "rb").read()
    assert data[:8] == b"\x89PNG\r\n\x1a\n"
    pos, w, h, color = 8, None, None, None
    idat = b""
    while pos < len(data):
        ln = struct.unpack(">I", data[pos:pos + 4])[0]
        tag = data[pos + 4:pos + 8]
        chunk = data[pos + 8:pos + 8 + ln]
        if tag == b"IHDR":
            w, h, depth, color = struct.unpack(">IIBB", chunk[:10])
            assert depth == 8 and color in (2, 6), "only 8bit RGB/RGBA"
        elif tag == b"IDAT":
            idat += chunk
        pos += 12 + ln
    src = 4 if color == 6 else 3
    raw = zlib.decompress(idat)
    stride = w * src
    out = bytearray(w * h * 4)
    prev = bytearray(stride)
    p = 0
    for y in range(h):
        ft = raw[p]
        p += 1
        line = bytearray(raw[p:p + stride])
        p += stride
        if ft == 1:
            for i in range(src, stride):
                line[i] = (line[i] + line[i - src]) & 0xFF
        elif ft == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 0xFF
        elif ft == 3:
            for i in range(stride):
                a = line[i - src] if i >= src else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 0xFF
        elif ft == 4:
            for i in range(stride):
                a = line[i - src] if i >= src else 0
                b = prev[i]
                c = prev[i - src] if i >= src else 0
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 0xFF
        for x in range(w):
            si = x * src
            di = (y * w + x) * 4
            out[di] = line[si]
            out[di + 1] = line[si + 1]
            out[di + 2] = line[si + 2]
            out[di + 3] = line[si + 3] if src == 4 else 255
        prev = line
    return w, h, out


def write_png(path, w, h, rgb):
    raw = bytearray()
    stride = w * 3
    for y in range(h):
        raw.append(0)
        raw += rgb[y * stride:(y + 1) * stride]

    def chunk(tag, d):
        return (struct.pack(">I", len(d)) + tag + d
                + struct.pack(">I", zlib.crc32(tag + d) & 0xFFFFFFFF))

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, "wb").write(png)


# --------------------------------------------------------- 等轴测预览
def render(model, size=300, scale=8.6, yaw_deg=-40, pitch_deg=24, bg=(236, 239, 244)):
    tex_cache = {}

    def texture(name):
        if name not in tex_cache:
            files = {
                "#grille": "speaker_grille_speaker_block.png",
                "#shell": "transmitter_shell.png",
                "#edge": "transmitter_edge.png",
                "#screen": "transmitter_screen.png",
                "#rock": "speaker_rock.png",
                "#front": "songmelyn_front.png",
                "#badge": "songmelyn_badge.png",
                "#body": "songmelyn_body.png",
            }
            tex_cache[name] = read_png(os.path.join(TEX_DIR, files.get(name, "speaker_rock.png")))
        return tex_cache[name]

    yaw, pitch = math.radians(yaw_deg), math.radians(pitch_deg)
    cy_, sy_ = math.cos(yaw), math.sin(yaw)
    cp_, sp_ = math.cos(pitch), math.sin(pitch)

    def project(p):
        x, y, z = p[0] - 8.0, p[1] - 8.0, p[2] - 8.0
        xr = x * cy_ - z * sy_
        zr = x * sy_ + z * cy_
        yr = y * cp_ + zr * sp_
        return (size / 2 + xr * scale, size / 2 - yr * scale, -y * sp_ + zr * cp_)

    quads = {
        "north": lambda f, t: [(f[0], f[1], f[2]), (t[0], f[1], f[2]), (t[0], t[1], f[2]), (f[0], t[1], f[2])],
        "south": lambda f, t: [(t[0], f[1], t[2]), (f[0], f[1], t[2]), (f[0], t[1], t[2]), (t[0], t[1], t[2])],
        "east": lambda f, t: [(t[0], f[1], t[2]), (t[0], f[1], f[2]), (t[0], t[1], f[2]), (t[0], t[1], t[2])],
        "west": lambda f, t: [(f[0], f[1], f[2]), (f[0], f[1], t[2]), (f[0], t[1], t[2]), (f[0], t[1], f[2])],
        "up": lambda f, t: [(f[0], t[1], f[2]), (t[0], t[1], f[2]), (t[0], t[1], t[2]), (f[0], t[1], t[2])],
        "down": lambda f, t: [(f[0], f[1], f[2]), (f[0], f[1], t[2]), (t[0], f[1], t[2]), (t[0], f[1], f[2])],
    }
    shading = {"up": 1.0, "down": 0.42, "north": 0.80, "south": 0.60, "east": 0.68, "west": 0.90}

    canvas = bytearray(bytes(bg) * (size * size))
    zbuf = [1e18] * (size * size)
    for el in model["elements"]:
        f, t = el["from"], el["to"]
        for name, data in el["faces"].items():
            proj = [project(p) for p in quads[name](f, t)]
            (ax, ay, az), (bx, by, bz), (cx, cy, cz), (dx, dy, dz) = proj
            ex, ey = bx - ax, by - ay
            fx, fy = dx - ax, dy - ay
            det = ex * fy - ey * fx
            if abs(det) < 1e-9:
                continue
            tw, th, tpx = texture(data["texture"])
            u0, v0, u1, v1 = data["uv"]
            px0, py0 = u0 / 16.0 * tw, v0 / 16.0 * th
            px1, py1 = u1 / 16.0 * tw, v1 / 16.0 * th
            sh = shading[name]
            minx = max(0, int(math.floor(min(ax, bx, cx, dx))))
            maxx = min(size - 1, int(math.ceil(max(ax, bx, cx, dx))))
            miny = max(0, int(math.floor(min(ay, by, cy, dy))))
            maxy = min(size - 1, int(math.ceil(max(ay, by, cy, dy))))
            for y in range(miny, maxy + 1):
                for x in range(minx, maxx + 1):
                    rx, ry = x + 0.5 - ax, y + 0.5 - ay
                    s = (rx * fy - ry * fx) / det
                    tt = (ex * ry - ey * rx) / det
                    if s < 0 or s > 1 or tt < 0 or tt > 1:
                        continue
                    d = az + s * (bz - az) + tt * (dz - az)
                    idx = y * size + x
                    if d >= zbuf[idx]:
                        continue
                    sx_px = max(0, min(tw - 1, int(px0 + (px1 - px0 - 0.001) * s)))
                    sy_px = max(0, min(th - 1, int(py1 - 1 - (py1 - py0 - 1) * tt)))
                    k = (sy_px * tw + sx_px) * 4
                    if tpx[k + 3] < 128:  # 透明孔位：不画，露出背后的石面
                        continue
                    zbuf[idx] = d
                    di = idx * 3
                    canvas[di] = min(255, int(tpx[k] * sh))
                    canvas[di + 1] = min(255, int(tpx[k + 1] * sh))
                    canvas[di + 2] = min(255, int(tpx[k + 2] * sh))
    return canvas


def render_sheet(model, path):
    views = [(-40, 24), (-6, 7), (-18, 62)]
    tiles = []
    for i, (yaw, pitch) in enumerate(views):
        canvas = render(model, size=300, scale=8.6, yaw_deg=yaw, pitch_deg=pitch)
        tmp = os.path.join(ROOT, "build", "preview", f"_tile{i}.png")
        write_png(tmp, 300, 300, canvas)
        tiles.append(tmp)
    w, h = 300 * 3 + 8 * 2, 300
    out = bytearray(bytes((236, 239, 244)) * (w * h))
    for i, t in enumerate(tiles):
        tw, th, px = read_png(t)
        ox = i * (300 + 8)
        for y in range(min(h, th)):
            for x in range(min(300, tw)):
                si = (y * tw + x) * 4
                di = (y * w + ox + x) * 3
                out[di:di + 3] = px[si:si + 3]
        os.remove(t)
    write_png(path, w, h, out)


def main():
    os.makedirs(BLOCK_MODELS, exist_ok=True)
    os.makedirs(ITEM_MODELS, exist_ok=True)
    os.makedirs(BLOCKSTATES, exist_ok=True)

    for block_id, rock_texture, grille_texture, _zh, _en in VARIANTS:
        model = build_model(rock_texture, grille_texture)
        with open(os.path.join(BLOCK_MODELS, block_id + ".json"), "w", encoding="utf-8") as f:
            json.dump(model, f, ensure_ascii=False, indent=2)
            f.write("\n")
        with open(os.path.join(BLOCKSTATES, block_id + ".json"), "w", encoding="utf-8") as f:
            json.dump(build_blockstate(block_id), f, ensure_ascii=False, indent=2)
            f.write("\n")
        with open(os.path.join(ITEM_MODELS, block_id + ".json"), "w", encoding="utf-8") as f:
            json.dump({"parent": "explainstone:block/" + block_id}, f, ensure_ascii=False, indent=2)
            f.write("\n")

    # 颂美铃
    song = build_songmelyn_model()
    with open(os.path.join(BLOCK_MODELS, "songmelyn_block.json"), "w", encoding="utf-8") as f:
        json.dump(song, f, ensure_ascii=False, indent=2)
        f.write("\n")
    with open(os.path.join(BLOCKSTATES, "songmelyn_block.json"), "w", encoding="utf-8") as f:
        json.dump(build_blockstate("songmelyn_block"), f, ensure_ascii=False, indent=2)
        f.write("\n")
    with open(os.path.join(ITEM_MODELS, "songmelyn_block.json"), "w", encoding="utf-8") as f:
        json.dump({"parent": "explainstone:block/songmelyn_block"}, f, ensure_ascii=False, indent=2)
        f.write("\n")

    # 广播发信器
    tx = build_transmitter_model()
    with open(os.path.join(BLOCK_MODELS, "broadcast_transmitter.json"), "w", encoding="utf-8") as f:
        json.dump(tx, f, ensure_ascii=False, indent=2)
        f.write("\n")
    tx_bs = {"variants": {}}
    for face, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for playing in ("false", "true"):
            entry = {"model": "explainstone:block/broadcast_transmitter"}
            if y:
                entry["y"] = y
            tx_bs["variants"]["facing=%s,broadcasting=%s" % (face, playing)] = entry
    with open(os.path.join(BLOCKSTATES, "broadcast_transmitter.json"), "w", encoding="utf-8") as f:
        json.dump(tx_bs, f, ensure_ascii=False, indent=2)
        f.write("\n")
    with open(os.path.join(ITEM_MODELS, "broadcast_transmitter.json"), "w", encoding="utf-8") as f:
        json.dump({"parent": "explainstone:block/broadcast_transmitter"}, f, ensure_ascii=False, indent=2)
        f.write("\n")

    default = build_model(VARIANTS[0][1], VARIANTS[0][2])
    print("共面重叠:", len(check_coplanar(default["elements"])), "处（相邻 box 内部接缝）")
    render_sheet(default, PREVIEW_PATH)
    render_sheet(tx, os.path.join(ROOT, "build", "preview", "transmitter_model.png"))
    render_sheet(song, os.path.join(ROOT, "build", "preview", "songmelyn_model.png"))
    print("models:", len(VARIANTS))
    print("preview ->", PREVIEW_PATH)


if __name__ == "__main__":
    main()
