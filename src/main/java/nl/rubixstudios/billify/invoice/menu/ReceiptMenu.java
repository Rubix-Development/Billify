package nl.rubixstudios.billify.invoice.menu;

import nl.rubixstudios.billify.data.Config;
import nl.rubixstudios.billify.data.Language;
import nl.rubixstudios.billify.invoice.object.Invoice;
import nl.rubixstudios.billify.invoice.object.InvoiceStatus;
import nl.rubixstudios.billify.resourcepack.GuiFont;
import nl.rubixstudios.billify.resourcepack.TitleBuilder;
import nl.rubixstudios.billify.util.ColorUtil;
import nl.rubixstudios.billify.util.EconomyUtil;
import nl.rubixstudios.billify.util.item.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * The detail view of one invoice. With the resource pack it is drawn as a paper receipt
 * (background glyph + text in the menu title, invisible items as click/hover hotspots);
 * without it, it is a simple chest menu with the invoice, a pay and a back button.
 *
 * Layout (54 slots): columns 0-5 receipt, slots 6-8/15-17 pay, 24-26 back, 33-53 info panel.
 */
public final class ReceiptMenu {

    private static final int[] PAY_SLOTS = {6, 7, 8, 15, 16, 17};
    private static final int[] BACK_SLOTS = {24, 25, 26};
    private static final int[] INFO_SLOTS = {33, 34, 35, 42, 43, 44, 51, 52, 53};

    // GUI pixel positions, mirrored in tools/resourcepack/preview.py
    private static final int LEFT = 13;
    private static final int VALUE = 42;
    private static final int RIGHT = 110;
    private static final int PANEL = 142;
    private static final int PANEL_WIDTH = 50;

    private ReceiptMenu() {
    }

    public static boolean isPayable(Invoice invoice) {
        return invoice.getInvoiceStatus() == InvoiceStatus.OPEN || invoice.getInvoiceStatus() == InvoiceStatus.OVERDUE;
    }

    public static Inventory create(Invoice invoice, OfflinePlayer target, boolean pack) {
        final String title = pack ? packTitle(invoice, target)
                : msg("INVOICE.RECEIPT.TITLE").replace("%invoice_id%", String.valueOf(invoice.getInvoiceId()));
        final Inventory inventory = Bukkit.createInventory(null, 54, title);

        if (pack) {
            final ItemStack receipt = InvoiceMenuButtons.invoice(invoice, InvoiceMenuButtons.MODEL_INVISIBLE);
            final ItemStack info = InvoiceMenuButtons.glass(true);
            for (int i = 0; i < 54; i++) {
                inventory.setItem(i, i % 9 < 6 ? receipt : info);
            }
            final ItemStack pay = isPayable(invoice) ? InvoiceMenuButtons.pay(invoice, true) : info;
            for (int slot : PAY_SLOTS) inventory.setItem(slot, pay);
            final ItemStack back = InvoiceMenuButtons.back(true);
            for (int slot : BACK_SLOTS) inventory.setItem(slot, back);
            if (isPayable(invoice) && EconomyUtil.hasBalanceSupport()) {
                final ItemStack hotspot = new ItemBuilder(InvoiceMenuButtons.balance(target, true))
                        .setCustomModelData(InvoiceMenuButtons.MODEL_INVISIBLE).toItemStack();
                for (int slot : INFO_SLOTS) inventory.setItem(slot, hotspot);
            }
            return inventory;
        }

        final ItemStack glass = InvoiceMenuButtons.glass(false);
        for (int i = 0; i < 54; i++) inventory.setItem(i, glass);
        inventory.setItem(20, InvoiceMenuButtons.invoice(invoice, false));
        if (isPayable(invoice)) inventory.setItem(23, InvoiceMenuButtons.pay(invoice, false));
        if (isPayable(invoice) && EconomyUtil.hasBalanceSupport()) inventory.setItem(24, InvoiceMenuButtons.balance(target, false));
        inventory.setItem(49, InvoiceMenuButtons.back(false));
        return inventory;
    }

    private static String packTitle(Invoice invoice, OfflinePlayer target) {
        final InvoiceStatus status = invoice.getInvoiceStatus();
        final TitleBuilder title = new TitleBuilder();

        switch (status) {
            case OVERDUE: title.glyph("RECEIPT_OVERDUE", 0); break;
            case PAID: title.glyph("RECEIPT_PAID", 0); break;
            case AUTOPAID: title.glyph("RECEIPT_COLLECTED", 0); break;
            case CANCELLED: title.glyph("RECEIPT_CANCELLED", 0); break;
            default: title.glyph("RECEIPT_OPEN", 0); break;
        }

        final String text = msg("INVOICE.RECEIPT.COLORS.TEXT");
        final String label = msg("INVOICE.RECEIPT.COLORS.LABEL");

        title.text(8, 0, msg("INVOICE.RECEIPT.COLORS.HEADER"),
                TitleBuilder.fit(id(msg("INVOICE.RECEIPT.HEADER"), invoice), 160));

        // receipt
        title.text(LEFT, 16, text, msg("INVOICE.RECEIPT.HEADING"));
        if (status == InvoiceStatus.OPEN) {
            final int days = Math.max(0, invoice.getDaysLeft());
            final String badge = days == 0 ? msg("INVOICE.RECEIPT.DUE_TODAY") : msg("INVOICE.RECEIPT.DAYS_LEFT").replace("%days%", String.valueOf(days));
            title.textRight(RIGHT, 16, msg("INVOICE.RECEIPT.COLORS.DAYS_LEFT"), TitleBuilder.fit(badge, 55));
        } else if (status == InvoiceStatus.OVERDUE) {
            final String badge = msg("INVOICE.RECEIPT.DAYS_LATE").replace("%days%", String.valueOf(invoice.getDaysOverdue()));
            title.textRight(RIGHT, 16, msg("INVOICE.RECEIPT.COLORS.DAYS_LATE"), TitleBuilder.fit(badge, 55));
        } else {
            title.textRight(RIGHT, 16, label, id(msg("INVOICE.RECEIPT.NUMBER"), invoice));
        }

        final SimpleDateFormat date = format("INVOICE.RECEIPT.DATE_FORMAT", "dd-MM-yyyy");
        final String[][] rows = {
                {msg("INVOICE.RECEIPT.FROM"), name(invoice.getInvoiceAuthor())},
                {msg("INVOICE.RECEIPT.TO"), target.getName() != null ? target.getName() : "?"},
                {msg("INVOICE.RECEIPT.DATE"), date.format(new Date(invoice.getInvoiceDateTime()))},
                {msg("INVOICE.RECEIPT.DUE"), date.format(invoice.getDateToPay())},
        };
        final int[] rowDys = {29, 39, 49, 59};
        for (int i = 0; i < rows.length; i++) {
            title.text(LEFT, rowDys[i], label, TitleBuilder.fit(rows[i][0], VALUE - LEFT - 2));
            title.text(VALUE, rowDys[i], text, TitleBuilder.fit(rows[i][1], RIGHT - VALUE));
        }

        title.text(LEFT, 74, text, TitleBuilder.fit(invoice.getInvoiceReason(), RIGHT - LEFT));
        if (invoice.isCollectionFeeApplied()) {
            title.text(LEFT, 84, msg("INVOICE.RECEIPT.COLORS.FEE"), TitleBuilder.fit(msg("INVOICE.RECEIPT.FEE")
                    .replace("%percent%", trim(Config.getDouble("DEBT_COLLECTION.COLLECTION_FEE_PERCENT"))), RIGHT - LEFT));
        }
        title.text(LEFT, 104, text, msg("INVOICE.RECEIPT.TOTAL"));
        title.textRight(RIGHT, 104, text, TitleBuilder.fit(EconomyUtil.format(invoice.getInvoiceAmount()), 60));

        // side panel
        final List<String[]> top = new ArrayList<>();
        final List<String[]> info = new ArrayList<>();
        final SimpleDateFormat shortDate = format("INVOICE.RECEIPT.SHORT_DATE_FORMAT", "dd-MM-yy");
        final SimpleDateFormat time = format("INVOICE.RECEIPT.TIME_FORMAT", "HH:mm");
        final String accent = msg("INVOICE.RECEIPT.COLORS.PANEL_ACCENT");
        final String panel = msg("INVOICE.RECEIPT.COLORS.PANEL_TEXT");
        final String muted = msg("INVOICE.RECEIPT.COLORS.PANEL_MUTED");

        switch (status) {
            case PAID: {
                final Date paid = new Date(invoice.getPaymentDateTime());
                top.add(new String[]{accent, msg("INVOICE.RECEIPT.PANEL.PAID_ON")});
                top.add(new String[]{panel, shortDate.format(paid)});
                top.add(new String[]{panel, time.format(paid)});
                info.add(new String[]{accent, msg("INVOICE.RECEIPT.PANEL.PAID_BY")});
                info.add(new String[]{panel, name(invoice.getPaidBy())});
                break;
            }
            case AUTOPAID:
                top.add(new String[]{msg("INVOICE.RECEIPT.COLORS.PANEL_WARNING"), msg("INVOICE.RECEIPT.PANEL.COLLECTED")});
                top.add(new String[]{panel, msg("INVOICE.RECEIPT.PANEL.COLLECTED_SUB")});
                info.add(new String[]{muted, msg("INVOICE.RECEIPT.PANEL.ON")});
                info.add(new String[]{panel, shortDate.format(new Date(invoice.getPaymentDateTime()))});
                break;
            case CANCELLED:
                top.add(new String[]{msg("INVOICE.RECEIPT.COLORS.PANEL_WARNING"), msg("INVOICE.RECEIPT.PANEL.CANCELLED")});
                top.add(new String[]{panel, shortDate.format(new Date(invoice.getCanceledOnDateTime()))});
                info.add(new String[]{muted, msg("INVOICE.RECEIPT.PANEL.BY")});
                info.add(new String[]{panel, name(invoice.getCanceledBy())});
                if (invoice.getCancelReason() != null) info.add(new String[]{muted, invoice.getCancelReason()});
                break;
            default:
                title.textCenter(PANEL, 36, msg("INVOICE.RECEIPT.COLORS.PAY"), TitleBuilder.fit(msg("INVOICE.RECEIPT.PANEL.PAY"), PANEL_WIDTH));
                if (EconomyUtil.hasBalanceSupport()) {
                    info.add(null); // coin icon is part of the background
                    info.add(new String[]{msg("INVOICE.RECEIPT.COLORS.BALANCE"), msg("INVOICE.RECEIPT.PANEL.BALANCE")});
                    info.add(new String[]{panel, EconomyUtil.format(EconomyUtil.getBalance(target))});
                }
                break;
        }

        final int[] topDys = {16, 29, 39};
        for (int i = 0; i < top.size() && i < topDys.length; i++) {
            title.textCenter(PANEL, topDys[i], top.get(i)[0], TitleBuilder.fit(top.get(i)[1], PANEL_WIDTH));
        }
        title.textCenter(PANEL, 53, msg("INVOICE.RECEIPT.COLORS.BACK"), TitleBuilder.fit(msg("INVOICE.RECEIPT.PANEL.BACK"), PANEL_WIDTH));
        final int[] infoDys = {72, 82, 92, 102, 112};
        for (int i = 0; i < info.size() && i < infoDys.length; i++) {
            if (info.get(i) == null) continue;
            title.textCenter(PANEL, infoDys[i], info.get(i)[0], TitleBuilder.fit(info.get(i)[1], PANEL_WIDTH));
        }

        // stamp last so it is drawn on top of the receipt text
        final String stamp = status == InvoiceStatus.PAID ? "STAMP_PAID"
                : status == InvoiceStatus.AUTOPAID ? "STAMP_COLLECTED"
                : status == InvoiceStatus.CANCELLED ? "STAMP_CANCELLED" : null;
        if (stamp != null) {
            title.glyph(stamp, 61 - (GuiFont.glyphAdvance(stamp) - 2) / 2);
        }
        return title.build();
    }

    private static String msg(String path) {
        final String message = Language.getMessage(path);
        return message == null ? "" : message;
    }

    private static String id(String text, Invoice invoice) {
        return ColorUtil.strip(text)
                .replace("%invoice_id%", String.valueOf(invoice.getInvoiceId()))
                .replace("%invoice_number%", String.format("%04d", invoice.getInvoiceId()));
    }

    private static String name(UUID uuid) {
        if (uuid == null) return "?";
        final String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name != null ? name : "?";
    }

    private static String trim(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    private static SimpleDateFormat format(String path, String fallback) {
        final String pattern = ColorUtil.strip(msg(path));
        try {
            return new SimpleDateFormat(pattern.isEmpty() ? fallback : pattern);
        } catch (IllegalArgumentException ex) {
            return new SimpleDateFormat(fallback);
        }
    }
}
