"""
Preview renders of the Billify menus, drawn from the generated pack assets and the
vanilla font exactly the way the client composes the menu title. The positions
mirror ReceiptMenu / InvoiceController in the plugin.
"""

import os

from PIL import Image

SCALE = 3

DARK = (58, 47, 34)
LABEL = (122, 110, 92)
WHITE = (255, 255, 255)
ORANGE = (230, 140, 40)
RED = (200, 50, 45)
GREEN = (85, 255, 85)
YELLOW = (255, 255, 85)
GRAY = (170, 170, 170)


class Canvas:
    def __init__(self, gui, sheets, metrics):
        self.img = Image.new("RGBA", (176, 126 + 4), (0, 0, 0, 0))
        self.sheets = sheets
        self.metrics = metrics
        self.gui = gui

    def glyph(self, name, x, y):
        self.img.alpha_composite(self.gui[name], (x, y))

    def width(self, text):
        return sum(self.metrics[c]["advance"] if c in self.metrics else 6 for c in text)

    def text(self, x, y, text, col):
        for c in text:
            m = self.metrics.get(c) or self.metrics["?"]
            if m["file"]:
                p, sheet = self.sheets[m["file"]]
                cw, ch = sheet.width // len(p["chars"][0]), sheet.height // len(p["chars"])
                rx, ry = m["pos"]
                g = sheet.crop((rx * cw, ry * ch, rx * cw + cw, ry * ch + ch))
                tint = Image.new("RGBA", g.size, col + (255,))
                tint.putalpha(g.getchannel("A"))
                self.img.alpha_composite(tint, (x, y + 7 - p.get("ascent", 7)))
            x += m["advance"]
        return x

    def text_right(self, xr, y, text, col):
        self.text(xr - self.width(text) + 1, y, text, col)

    def text_center(self, xc, y, text, col):
        self.text(xc - self.width(text) // 2, y, text, col)

    def item(self, icon, slot):
        x, y = 8 + (slot % 9) * 18, 18 + (slot // 9) * 18
        self.img.alpha_composite(icon, (x, y))

    def save(self, path):
        out = self.img.resize((self.img.width * SCALE, self.img.height * SCALE), Image.NEAREST)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        out.save(path)


def receipt(gui, sheets, metrics, variant, data, path):
    cv = Canvas(gui, sheets, metrics)
    cv.glyph("RECEIPT_" + variant.upper(), 0, 0)
    cv.text(8, 6, data["header"], WHITE)
    cv.text(13, 22, "INVOICE", DARK)
    cv.text_right(110, 22, data["badge"], data.get("badge_col", LABEL))
    for i, (label, value) in enumerate(zip(["From", "To", "Date", "Due"], data["rows"])):
        cv.text(13, 35 + i * 10, label, LABEL)
        cv.text(42, 35 + i * 10, value, DARK)
    cv.text(13, 80, data["reason"], DARK)
    if data.get("fee"):
        cv.text(13, 90, data["fee"], RED)
    cv.text(13, 110, "TOTAL", DARK)
    cv.text_right(110, 110, data["total"], DARK)
    if variant in ("open", "overdue"):
        cv.text_center(142, 42, "Pay", WHITE)
        cv.text_center(142, 59, "Back", WHITE)
        cv.text_center(142, 88, "Balance", YELLOW)
        cv.text_center(142, 98, data["balance"], WHITE)
    else:
        for i, line in enumerate(data["top"]):
            cv.text_center(142, (22, 35, 45)[i], line[0], line[1])
        cv.text_center(142, 59, "Back", WHITE)
        for i, line in enumerate(data["info"]):
            cv.text_center(142, 78 + i * 10, line[0], line[1])
    if data.get("stamp"):
        st = gui["STAMP_" + data["stamp"]]
        cv.glyph("STAMP_" + data["stamp"], 61 - st.width // 2, 80)
    cv.save(path)


def render_all(gui, items, sheets, metrics, out):
    cv = Canvas(gui, sheets, metrics)
    cv.glyph("MAIN", 0, 0)
    cv.text(8, 6, "Invoices | Open", WHITE)
    cv.item(items["tab_open"], 3)
    cv.item(items["coin"], 4)
    cv.item(items["tab_paid"], 5)
    for s, name in zip(range(9, 14), ["invoice_open", "invoice_open", "invoice_overdue", "invoice_open", "invoice_overdue"]):
        cv.item(items[name], s)
    cv.item(items["previous"], 48)
    cv.item(items["close"], 49)
    cv.item(items["next"], 50)
    cv.save(os.path.join(out, "menu-invoices.png"))

    receipt(gui, sheets, metrics, "open", {
        "header": "Invoice #12", "badge": "7 days left", "badge_col": ORANGE,
        "rows": ["Kevin_dV", "Djorr", "08-10-2026", "15-10-2026"],
        "reason": "Car repair", "total": "$250.00", "balance": "$4,820.00",
    }, os.path.join(out, "receipt-open.png"))
    receipt(gui, sheets, metrics, "overdue", {
        "header": "Invoice #9", "badge": "2 days late", "badge_col": RED,
        "rows": ["Mia_Baker", "Djorr", "24-09-2026", "01-10-2026"],
        "reason": "Apartment rent", "fee": "+10% collection fee", "total": "$1,320.00", "balance": "$4,820.00",
    }, os.path.join(out, "receipt-overdue.png"))
    receipt(gui, sheets, metrics, "paid", {
        "header": "Invoice #8", "badge": "#0008",
        "rows": ["Taxi_Tom", "Djorr", "02-10-2026", "09-10-2026"],
        "reason": "Taxi ride downtown", "total": "$35.00", "stamp": "PAID",
        "top": [("Paid on", GREEN), ("02-10-26", WHITE), ("14:32", WHITE)],
        "info": [("Paid by", GREEN), ("Djorr", WHITE)],
    }, os.path.join(out, "receipt-paid.png"))
    receipt(gui, sheets, metrics, "collected", {
        "header": "Invoice #5", "badge": "#0005",
        "rows": ["City_Hall", "Djorr", "08-09-2026", "15-09-2026"],
        "reason": "Littering fine", "fee": "+10% collection fee", "total": "$66.00", "stamp": "COLLECTED",
        "top": [("Collected", RED), ("auto", WHITE)],
        "info": [("on", GRAY), ("20-09-26", WHITE)],
    }, os.path.join(out, "receipt-collected.png"))
    receipt(gui, sheets, metrics, "cancelled", {
        "header": "Invoice #3", "badge": "#0003",
        "rows": ["Officer_Sam", "Djorr", "01-09-2026", "08-09-2026"],
        "reason": "Speeding fine", "total": "$120.00", "stamp": "CANCELLED",
        "top": [("Cancelled", RED), ("03-09-26", WHITE)],
        "info": [("by", GRAY), ("Officer_..", WHITE)],
    }, os.path.join(out, "receipt-cancelled.png"))
