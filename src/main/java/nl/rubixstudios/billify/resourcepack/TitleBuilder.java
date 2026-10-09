package nl.rubixstudios.billify.resourcepack;

import nl.rubixstudios.billify.util.ColorUtil;

/**
 * Builds a menu title that draws a background image plus text at absolute GUI positions.
 * All x values are GUI pixels (the title itself starts at x=8); dy is the vertical offset
 * from the normal title line and must be one of the rows the resource pack provides.
 */
public class TitleBuilder {

    private static final int TITLE_X = 8;
    private static final int NEGATIVE_BASE = 0xF801; // -1, -2, -4 ... -256
    private static final int POSITIVE_BASE = 0xF821; // +1, +2, +4 ... +256

    private final StringBuilder title = new StringBuilder();
    private int cursor = TITLE_X;

    public TitleBuilder glyph(String name, int x) {
        moveTo(x);
        title.append(ColorUtil.translate("&f")).append(GuiFont.glyph(name));
        cursor += GuiFont.glyphAdvance(name);
        return this;
    }

    public TitleBuilder text(int x, int dy, String color, String text) {
        moveTo(x);
        title.append(ColorUtil.translate(color));
        for (char c : ColorUtil.strip(text).toCharArray()) {
            title.append(GuiFont.shifted(c, dy));
            cursor += GuiFont.advance(c);
        }
        return this;
    }

    public TitleBuilder textRight(int right, int dy, String color, String text) {
        return text(right - GuiFont.width(ColorUtil.strip(text)) + 1, dy, color, text);
    }

    public TitleBuilder textCenter(int center, int dy, String color, String text) {
        return text(center - GuiFont.width(ColorUtil.strip(text)) / 2, dy, color, text);
    }

    /** Shortens text with ".." so it never draws wider than maxWidth pixels. */
    public static String fit(String text, int maxWidth) {
        text = ColorUtil.strip(text);
        if (GuiFont.width(text) <= maxWidth) return text;
        final int dots = GuiFont.width("..");
        while (!text.isEmpty() && GuiFont.width(text) + dots > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text + "..";
    }

    private void moveTo(int x) {
        int offset = x - cursor;
        while (offset != 0) {
            final int step = Math.min(8, 31 - Integer.numberOfLeadingZeros(Math.abs(offset)));
            title.append((char) ((offset < 0 ? NEGATIVE_BASE : POSITIVE_BASE) + step));
            offset += offset < 0 ? (1 << step) : -(1 << step);
        }
        cursor = x;
    }

    public String build() {
        return title.toString();
    }
}
