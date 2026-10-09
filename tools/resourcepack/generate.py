"""
Billify resource pack generator.

Draws every texture of the Billify resource pack with Pillow, writes the font and
model definitions, and exports the glyph metrics the plugin needs to position text
inside menu titles (src/main/resources/billify-font.properties).

Usage:
    python tools/resourcepack/generate.py <path to a Minecraft client jar (1.20+)>

The client jar is only read for the metrics of the vanilla font (ascii.png and
nonlatin_european.png). No Mojang textures are copied into the pack: the row fonts
reference minecraft:font/ascii.png directly, so the client uses its own copy.

Outputs:
    resourcepack/                         unpacked pack (committed)
    resourcepack/Billify-ResourcePack.zip ready-to-host zip
    src/main/resources/billify-font.properties
    docs/images/*.png                     preview renders used on the Spigot page
"""

import hashlib
import io
import json
import math
import os
import random
import shutil
import sys
import zipfile

from PIL import Image, ImageDraw

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
PACK = os.path.join(ROOT, "resourcepack")
PACK_SRC = os.path.join(PACK, "pack")
DOCS = os.path.join(ROOT, "docs", "images")

# ---------------------------------------------------------------------------
# Layout constants shared with the plugin (TitleBuilder / ReceiptMenu).
# A container title is drawn with its text top at y=6, so a glyph with ascent
# 13 starts at the very top of the GUI. "dy" values below are offsets from the
# normal title line, every dy gets its own shifted copy of the vanilla font.
# ---------------------------------------------------------------------------
GUI_W, GUI_H = 176, 126
ROW_DYS = [16, 29, 39, 49, 59, 74, 84, 104, 36, 53, 72, 82, 92, 102, 112]
ROW_BASE = 0xE100
ROW_STRIDE = 0x80
CHARSET = "".join(chr(c) for c in range(0x20, 0x7F)) + "€"

SPACE_NEG = {0xF801 + i: -(1 << i) for i in range(9)}   # -1 .. -256
SPACE_POS = {0xF821 + i: (1 << i) for i in range(9)}    # +1 .. +256

GLYPHS = {
    "MAIN": 0xE000,
    "RECEIPT_OPEN": 0xE010,
    "RECEIPT_OVERDUE": 0xE011,
    "RECEIPT_PAID": 0xE012,
    "RECEIPT_COLLECTED": 0xE013,
    "RECEIPT_CANCELLED": 0xE014,
    "STAMP_PAID": 0xE020,
    "STAMP_COLLECTED": 0xE021,
    "STAMP_CANCELLED": 0xE022,
}
STAMP_TOP = 80  # y of the stamp glyphs inside the receipt (empty band above TOTAL)

ITEMS = {  # custom model data -> texture name
    9100: "invisible",
    9101: "invoice_open",
    9102: "invoice_overdue",
    9103: "invoice_paid",
    9104: "invoice_collected",
    9105: "invoice_cancelled",
    9110: "tab_open",
    9111: "tab_paid",
    9112: "coin",
    9113: "close",
    9114: "previous",
    9115: "next",
    9116: "pay",
    9117: "back",
}

# ---------------------------------------------------------------------------
# Palette
# ---------------------------------------------------------------------------
C = {
    "outline": (14, 16, 20),
    "frame": (40, 45, 53),
    "frame_hi": (62, 68, 79),
    "frame_lo": (24, 27, 32),
    "slot": (30, 34, 40),
    "slot_lo": (18, 20, 24),
    "slot_hi": (70, 76, 88),
    "green": (46, 107, 58),
    "green_hi": (72, 140, 82),
    "red": (122, 31, 31),
    "red_hi": (160, 52, 48),
    "gray": (74, 79, 87),
    "gray_hi": (100, 106, 116),
    "gold": (224, 163, 58),
    "paper": (241, 230, 207),
    "paper_dot": (221, 208, 182),
    "paper_line": (150, 138, 116),
    "orange": (230, 140, 40),
    "strip_green": (63, 160, 72),
    "strip_blue": (61, 123, 201),
    "strip_red": (196, 52, 46),
    "strip_gray": (130, 130, 130),
    "button_green": (61, 154, 70),
    "button_green_hi": (110, 200, 110),
    "button_green_lo": (30, 96, 38),
    "panel": (29, 32, 38),
    "white": (255, 255, 255),
}

# 5x7 pixel font used for the stamps (only the letters the stamps need).
PIX = {
    "A": ["01110", "10001", "10001", "11111", "10001", "10001", "10001"],
    "C": ["01111", "10000", "10000", "10000", "10000", "10000", "01111"],
    "D": ["11110", "10001", "10001", "10001", "10001", "10001", "11110"],
    "E": ["11111", "10000", "10000", "11110", "10000", "10000", "11111"],
    "I": ["111", "010", "010", "010", "010", "010", "111"],
    "L": ["10000", "10000", "10000", "10000", "10000", "10000", "11111"],
    "N": ["10001", "11001", "10101", "10011", "10001", "10001", "10001"],
    "O": ["01110", "10001", "10001", "10001", "10001", "10001", "01110"],
    "P": ["11110", "10001", "10001", "11110", "10000", "10000", "10000"],
    "T": ["11111", "00100", "00100", "00100", "00100", "00100", "00100"],
}


def rect(d, x0, y0, x1, y1, col):
    d.rectangle([x0, y0, x1, y1], fill=col)


def bevel(d, x0, y0, x1, y1, base, hi, lo, outline=None):
    """Raised box: base fill, light top/left edge, dark bottom/right edge."""
    if outline:
        rect(d, x0, y0, x1, y1, outline)
        x0, y0, x1, y1 = x0 + 1, y0 + 1, x1 - 1, y1 - 1
    rect(d, x0, y0, x1, y1, base)
    d.line([x0, y0, x1, y0], fill=hi)
    d.line([x0, y0, x0, y1], fill=hi)
    d.line([x0, y1, x1, y1], fill=lo)
    d.line([x1, y0, x1, y1], fill=lo)


def slot(d, x, y):
    """18x18 inventory slot at GUI position (x, y) of its outer border."""
    rect(d, x, y, x + 17, y + 17, C["slot"])
    d.line([x, y, x + 16, y], fill=C["slot_lo"])
    d.line([x, y, x, y + 16], fill=C["slot_lo"])
    d.line([x + 1, y + 17, x + 17, y + 17], fill=C["slot_hi"])
    d.line([x + 17, y + 1, x + 17, y + 17], fill=C["slot_hi"])


def frame(img, header):
    d = ImageDraw.Draw(img)
    rect(d, 0, 0, GUI_W - 1, GUI_H - 1, C["outline"])
    rect(d, 1, 1, GUI_W - 2, GUI_H - 2, C["frame"])
    d.line([1, 1, GUI_W - 2, 1], fill=C["frame_hi"])
    d.line([1, 1, 1, GUI_H - 2], fill=C["frame_hi"])
    base, hi = {"green": (C["green"], C["green_hi"]), "red": (C["red"], C["red_hi"]),
                "gray": (C["gray"], C["gray_hi"])}[header]
    rect(d, 1, 1, GUI_W - 2, 14, base)
    d.line([1, 1, GUI_W - 2, 1], fill=hi)
    d.line([1, 15, GUI_W - 2, 15], fill=C["gold"])
    d.line([1, 16, GUI_W - 2, 16], fill=C["frame_lo"])
    # header icon: a tiny receipt at the left of the title
    return d


def draw_pix_text(d, x, y, text, col, scale=1, spacing=1):
    for ch in text:
        if ch == " ":
            x += 3 * scale
            continue
        rows = PIX[ch]
        for ry, row in enumerate(rows):
            for rx, bit in enumerate(row):
                if bit == "1":
                    rect(d, x + rx * scale, y + ry * scale, x + (rx + 1) * scale - 1, y + (ry + 1) * scale - 1, col)
        x += (len(rows[0]) + spacing) * scale
    return x


def pix_width(text, scale=1, spacing=1):
    w = 0
    for ch in text:
        w += 3 * scale if ch == " " else (len(PIX[ch][0]) + spacing) * scale
    return w - spacing * scale


# ---------------------------------------------------------------------------
# GUI backgrounds
# ---------------------------------------------------------------------------

def gui_main():
    img = Image.new("RGBA", (GUI_W, GUI_H))
    d = frame(img, "green")
    for r in range(6):
        for c in range(9):
            x, y = 7 + c * 18, 17 + r * 18
            if r in (0, 5):
                continue
            slot(d, x, y)
    # tab strip and bottom bar: darker inset bars with button sockets
    for y in (17, 107):
        rect(d, 7, y, 168, y + 17, C["frame_lo"])
    for c in (3, 4, 5):
        bevel(d, 7 + c * 18, 17, 7 + c * 18 + 17, 34, C["frame_hi"], C["gray_hi"], C["outline"])
    for c in (3, 4, 5):
        bevel(d, 7 + c * 18, 107, 7 + c * 18 + 17, 124, C["frame_hi"], C["gray_hi"], C["outline"])
    rect(d, GUI_W - 1, 0, GUI_W - 1, GUI_H - 1, C["outline"])
    return img


def paper(d, strip, torn=True):
    x0, y0, x1, y1 = 9, 19, 113, 121
    rect(d, x0 - 1, y0 - 1, x1 + 1, y1 + 1, C["outline"])
    rect(d, x0, y0, x1, y1, C["paper"])
    rect(d, x0, y0, x1, y0 + 2, strip)
    rnd = random.Random(7)
    for y in range(y0 + 5, y1 - 3, 4):
        for x in range(x0 + 2 + (y // 4) % 2 * 2, x1 - 1, 4):
            if rnd.random() < 0.55:
                d.point((x, y), fill=C["paper_dot"])
    for y in (32, 76, 104):
        for x in range(x0 + 3, x1 - 2, 2):
            d.point((x, y), fill=C["paper_line"])
    if torn:
        # zig-zag tear along the bottom edge
        for x in range(x0 - 1, x1 + 2):
            h = (x % 4) if (x % 8) < 4 else 3 - (x % 4)
            for y in range(y1 - h + 1, y1 + 2):
                d.point((x, y), fill=C["frame"])
            d.point((x, y1 - h), fill=C["outline"])


def gui_receipt(variant):
    header = {"open": "green", "overdue": "red", "paid": "green", "collected": "red", "cancelled": "gray"}[variant]
    strip = {"open": C["orange"], "overdue": C["strip_red"], "paid": C["strip_green"],
             "collected": C["strip_blue"], "cancelled": C["strip_gray"]}[variant]
    img = Image.new("RGBA", (GUI_W, GUI_H))
    d = frame(img, header)
    paper(d, strip)
    payable = variant in ("open", "overdue")
    if payable:
        # big pay button (slots 6-8, 15-17)
        bevel(d, 116, 18, 168, 52, C["button_green"], C["button_green_hi"], C["button_green_lo"], C["outline"])
        bevel(d, 135, 22, 149, 35, C["white"], C["white"], (200, 200, 200), C["outline"])
        for i, (cx, cy) in enumerate([(138, 28), (139, 29), (140, 30), (141, 29), (142, 28), (143, 27), (144, 26), (145, 25)]):
            rect(d, cx, cy, cx, cy + 1, C["button_green_lo"])
    else:
        bevel(d, 116, 18, 168, 52, C["panel"], C["frame_lo"], C["frame_hi"], C["outline"])
    # back button (slots 24-26)
    bevel(d, 116, 54, 168, 70, C["gray"], C["gray_hi"], C["frame_lo"], C["outline"])
    # info panel (slots 33-35, 42-44, 51-53)
    bevel(d, 116, 72, 168, 122, C["panel"], C["frame_lo"], C["frame_hi"], C["outline"])
    if payable:
        coin(d, 136, 75, scale=1)
    rect(d, GUI_W - 1, 0, GUI_W - 1, GUI_H - 1, C["outline"])
    return img


def coin(d, x, y, scale=1):
    """12x12 gold coin."""
    rows = [
        "....oooo....",
        "..oogggggo..",
        ".ogyyyyyygo.",
        ".ogyhhyyygo.",
        "ogyhyyyyyygo",
        "ogyhyyyyyygo",
        "ogyyyyyyyygo",
        "ogyyyyyyyygo",
        ".ogyyyyyygo.",
        ".oggyyyyggo.",
        "..oogggggo..",
        "....oooo....",
    ]
    cols = {"o": (90, 60, 10), "g": (200, 140, 30), "y": (246, 200, 60), "h": (255, 245, 190)}
    for ry, row in enumerate(rows):
        for rx, ch in enumerate(row):
            if ch in cols:
                rect(d, x + rx * scale, y + ry * scale, x + (rx + 1) * scale - 1, y + (ry + 1) * scale - 1, cols[ch])


def stamp(text, col):
    """Condensed rubber stamp: bold 1x-wide, 2x-tall letters in a double border."""
    tw = sum(3 if ch == " " else len(PIX[ch][0]) + 2 for ch in text) - 1
    w, h = tw + 12, 14 + 12
    img = Image.new("RGBA", (w, h))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([0, 0, w - 1, h - 1], radius=3, outline=col, width=2)
    d.rounded_rectangle([3, 3, w - 4, h - 4], radius=2, outline=col, width=1)
    x = 6
    for ch in text:
        if ch == " ":
            x += 3
            continue
        rows = PIX[ch]
        for ry, row in enumerate(rows):
            for rx, bit in enumerate(row):
                if bit == "1":
                    rect(d, x + rx, 6 + ry * 2, x + rx + 1, 6 + ry * 2 + 1, col)
        x += len(rows[0]) + 2
    # worn ink: knock out a few random pixels
    rnd = random.Random(sum(map(ord, text)))
    px = img.load()
    for yy in range(h):
        for xx in range(w):
            if px[xx, yy][3]:
                px[xx, yy] = (0, 0, 0, 0) if rnd.random() < 0.05 else col + (225,)
    return img.rotate(9, resample=Image.NEAREST, expand=True)


# ---------------------------------------------------------------------------
# Item icons (16x16)
# ---------------------------------------------------------------------------

def icon_receipt(accent, mark=None):
    img = Image.new("RGBA", (16, 16))
    d = ImageDraw.Draw(img)
    rect(d, 3, 1, 12, 14, C["outline"])
    rect(d, 4, 2, 11, 13, C["paper"])
    rect(d, 4, 2, 11, 3, accent)
    for y in (6, 8, 10):
        d.line([5, y, 10, y], fill=C["paper_line"])
    for x in range(3, 13, 2):
        d.point((x, 14), fill=(0, 0, 0, 0))
        d.point((x + 1, 13), fill=C["outline"]) if x + 1 <= 12 else None
    if mark == "check":
        for (x, y) in [(8, 11), (9, 12), (10, 11), (11, 10), (12, 9), (13, 8)]:
            rect(d, x, y, x, y + 1, accent)
    elif mark == "cross":
        for i in range(5):
            d.point((8 + i, 8 + i), fill=accent)
            d.point((12 - i, 8 + i), fill=accent)
    elif mark == "bang":
        rect(d, 12, 6, 14, 15, C["outline"])
        rect(d, 13, 7, 13, 11, accent)
        d.point((13, 13), fill=accent)
    return img


def icon_coin():
    img = Image.new("RGBA", (16, 16))
    coin(ImageDraw.Draw(img), 2, 2)
    return img


def icon_close():
    img = Image.new("RGBA", (16, 16))
    d = ImageDraw.Draw(img)
    bevel(d, 1, 1, 14, 14, C["strip_red"], (235, 110, 100), C["red"], C["outline"])
    for i in range(6):
        rect(d, 5 + i, 5 + i, 5 + i, 5 + i, C["white"])
        rect(d, 10 - i, 5 + i, 10 - i, 5 + i, C["white"])
    return img


def icon_arrow(direction):
    img = Image.new("RGBA", (16, 16))
    d = ImageDraw.Draw(img)
    bevel(d, 1, 3, 14, 12, C["gray"], C["gray_hi"], C["frame_lo"], C["outline"])
    pts = [(4, 7), (5, 6), (5, 8), (6, 5), (6, 9)]
    for x, y in pts:
        d.point((x, y), fill=C["white"])
    d.line([6, 7, 11, 7], fill=C["white"])
    d.line([5, 7, 11, 7], fill=C["white"])
    if direction == "next":
        img = img.transpose(Image.FLIP_LEFT_RIGHT)
    return img


def icon_pay():
    img = Image.new("RGBA", (16, 16))
    d = ImageDraw.Draw(img)
    bevel(d, 1, 1, 14, 14, C["button_green"], C["button_green_hi"], C["button_green_lo"], C["outline"])
    for (x, y) in [(4, 8), (5, 9), (6, 10), (7, 9), (8, 8), (9, 7), (10, 6), (11, 5)]:
        rect(d, x, y, x, y + 1, C["white"])
    return img


def build_items():
    return {
        "invisible": Image.new("RGBA", (16, 16)),
        "invoice_open": icon_receipt(C["orange"]),
        "invoice_overdue": icon_receipt(C["strip_red"], "bang"),
        "invoice_paid": icon_receipt(C["strip_green"], "check"),
        "invoice_collected": icon_receipt(C["strip_blue"], "check"),
        "invoice_cancelled": icon_receipt(C["strip_gray"], "cross"),
        "tab_open": icon_receipt(C["orange"]),
        "tab_paid": icon_receipt(C["strip_green"], "check"),
        "coin": icon_coin(),
        "close": icon_close(),
        "previous": icon_arrow("previous"),
        "next": icon_arrow("next"),
        "pay": icon_pay(),
        "back": icon_arrow("previous"),
    }


# ---------------------------------------------------------------------------
# Vanilla font metrics
# ---------------------------------------------------------------------------

def load_vanilla(jar):
    with zipfile.ZipFile(jar) as z:
        providers = json.loads(z.read("assets/minecraft/font/include/default.json"))["providers"]
        sheets = {}
        for p in providers:
            if p.get("type") == "bitmap" and p["file"] in ("minecraft:font/ascii.png", "minecraft:font/nonlatin_european.png"):
                path = "assets/minecraft/textures/" + p["file"].split(":")[1]
                sheets[p["file"]] = (p, Image.open(io.BytesIO(z.read(path))).convert("RGBA"))
    return sheets


def glyph_metrics(sheets):
    """Advance (in GUI pixels) and sheet position of every charset character."""
    info = {}
    for file, (p, img) in sheets.items():
        rows = p["chars"]
        cw, ch = img.width // len(rows[0]), img.height // len(rows)
        height = p.get("height", 8)
        scale = height / ch
        px = img.load()
        for ry, row in enumerate(rows):
            for rx, c in enumerate(row):
                if c not in CHARSET or c in info or c == " ":
                    continue
                width = 0
                for x in range(cw - 1, -1, -1):
                    if any(px[rx * cw + x, ry * ch + y][3] for y in range(ch)):
                        width = x + 1
                        break
                info[c] = {"file": file, "advance": int(0.5 + width * scale) + 1, "pos": (rx, ry)}
    info[" "] = {"file": None, "advance": 4}
    missing = [c for c in CHARSET if c not in info]
    if missing:
        raise SystemExit("Missing glyphs in vanilla font: %r" % missing)
    return info


def glyph_advance(img):
    """Advance of a bitmap glyph at native scale: rightmost opaque column + 1 + 1."""
    px = img.load()
    for x in range(img.width - 1, -1, -1):
        if any(px[x, y][3] for y in range(img.height)):
            return x + 2
    return 1


def row_char(k, c):
    return chr(ROW_BASE + k * ROW_STRIDE + CHARSET.index(c))


# ---------------------------------------------------------------------------
# Writing
# ---------------------------------------------------------------------------

def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2, ensure_ascii=True)
        f.write("\n")


def save_png(path, img):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def main():
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)
    sheets = load_vanilla(sys.argv[1])
    metrics = glyph_metrics(sheets)

    if os.path.isdir(PACK_SRC):
        shutil.rmtree(PACK_SRC)
    assets = os.path.join(PACK_SRC, "assets")

    write_json(os.path.join(PACK_SRC, "pack.mcmeta"), {
        "pack": {
            "pack_format": 46,
            "supported_formats": {"min_inclusive": 15, "max_inclusive": 999},
            "min_format": 15,
            "max_format": 999,
            "description": "§6Billify §7invoices & receipts",
        }
    })

    # -- GUI textures ------------------------------------------------------
    gui = {
        "MAIN": gui_main(),
        "RECEIPT_OPEN": gui_receipt("open"),
        "RECEIPT_OVERDUE": gui_receipt("overdue"),
        "RECEIPT_PAID": gui_receipt("paid"),
        "RECEIPT_COLLECTED": gui_receipt("collected"),
        "RECEIPT_CANCELLED": gui_receipt("cancelled"),
        "STAMP_PAID": stamp("PAID", (61, 154, 70)),
        "STAMP_COLLECTED": stamp("COLLECTED", (61, 123, 201)),
        "STAMP_CANCELLED": stamp("CANCELLED", (176, 48, 48)),
    }
    providers = []
    advances = {}
    for name, img in gui.items():
        tex = name.lower()
        save_png(os.path.join(assets, "billify", "textures", "font", "gui", tex + ".png"), img)
        top = 0 if not name.startswith("STAMP") else STAMP_TOP
        providers.append({
            "type": "bitmap",
            "file": "billify:font/gui/%s.png" % tex,
            "ascent": 13 - top,
            "height": img.height,
            "chars": [chr(GLYPHS[name])],
        })
        advances[name] = glyph_advance(img)

    advances_space = {}
    advances_space.update({chr(k): v for k, v in SPACE_NEG.items()})
    advances_space.update({chr(k): v for k, v in SPACE_POS.items()})
    providers.append({"type": "space", "advances": advances_space})

    # -- shifted copies of the vanilla font for the receipt text ------------
    for k, dy in enumerate(ROW_DYS):
        for file, (p, img) in sheets.items():
            chars = []
            for row in p["chars"]:
                chars.append("".join(row_char(k, c) if (c in CHARSET and c != " " and metrics[c]["file"] == file) else "\u0000" for c in row))
            providers.append({
                "type": "bitmap",
                "file": file,
                "ascent": p.get("ascent", 7) - dy,
                "height": p.get("height", 8),
                "chars": chars,
            })
        providers.append({"type": "space", "advances": {row_char(k, " "): 4}})

    write_json(os.path.join(assets, "minecraft", "font", "default.json"), {"providers": providers})

    # -- item models ----------------------------------------------------------
    items = build_items()
    for cmd, name in ITEMS.items():
        save_png(os.path.join(assets, "billify", "textures", "item", name + ".png"), items[name])
        write_json(os.path.join(assets, "billify", "models", "item", name + ".json"), {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": "billify:item/" + name},
        })
    # 1.14 - 1.21.3: overrides in the vanilla item model
    write_json(os.path.join(assets, "minecraft", "models", "item", "paper.json"), {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": "minecraft:item/paper"},
        "overrides": [{"predicate": {"custom_model_data": cmd}, "model": "billify:item/" + name}
                      for cmd, name in sorted(ITEMS.items())],
    })
    # 1.21.4+: item model definition
    write_json(os.path.join(assets, "minecraft", "items", "paper.json"), {
        "model": {
            "type": "minecraft:range_dispatch",
            "property": "minecraft:custom_model_data",
            "index": 0,
            "fallback": {"type": "minecraft:model", "model": "minecraft:item/paper"},
            "entries": [{"threshold": cmd, "model": {"type": "minecraft:model", "model": "billify:item/" + name}}
                        for cmd, name in sorted(ITEMS.items())],
        }
    })
    save_png(os.path.join(PACK_SRC, "pack.png"), pack_icon(items))

    # -- zip ----------------------------------------------------------------
    zpath = os.path.join(PACK, "Billify-ResourcePack.zip")
    with zipfile.ZipFile(zpath, "w", zipfile.ZIP_DEFLATED) as z:
        for base, _, files in os.walk(PACK_SRC):
            for f in sorted(files):
                full = os.path.join(base, f)
                info = zipfile.ZipInfo(os.path.relpath(full, PACK_SRC).replace(os.sep, "/"), (2024, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                with open(full, "rb") as fh:
                    z.writestr(info, fh.read())
    sha1 = hashlib.sha1(open(zpath, "rb").read()).hexdigest()

    # -- metrics for the plugin -------------------------------------------
    lines = ["# Generated by tools/resourcepack/generate.py - do not edit by hand.",
             "# Glyph metrics of the Billify resource pack, used to lay out menu titles.",
             "pack.sha1=" + sha1,
             "row.base=%d" % ROW_BASE,
             "row.stride=%d" % ROW_STRIDE,
             "row.dys=" + ",".join(str(d) for d in ROW_DYS),
             "charset=" + "".join("\\u%04x" % ord(c) for c in CHARSET),
             "advances=" + ",".join(str(metrics[c]["advance"]) for c in CHARSET)]
    for name, cp in GLYPHS.items():
        lines.append("glyph.%s=%d,%d" % (name, cp, advances[name]))
    with open(os.path.join(ROOT, "src", "main", "resources", "billify-font.properties"), "w", encoding="ascii", newline="\n") as f:
        f.write("\n".join(lines) + "\n")

    import preview
    preview.render_all(gui, items, sheets, metrics, DOCS)
    print("Pack written to %s (sha1 %s)" % (zpath, sha1))


def pack_icon(items):
    img = Image.new("RGBA", (64, 64), C["frame"])
    d = ImageDraw.Draw(img)
    rect(d, 0, 0, 63, 63, C["outline"])
    rect(d, 2, 2, 61, 61, C["green"])
    big = items["invoice_paid"].resize((48, 48), Image.NEAREST)
    img.alpha_composite(big, (8, 8))
    return img


if __name__ == "__main__":
    main()
