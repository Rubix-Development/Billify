package nl.rubixstudios.billify.invoice.menu;

import com.cryptomorin.xseries.XMaterial;
import nl.rubixstudios.billify.data.Language;
import nl.rubixstudios.billify.invoice.object.Invoice;
import nl.rubixstudios.billify.invoice.object.InvoiceStatus;
import nl.rubixstudios.billify.util.ColorUtil;
import nl.rubixstudios.billify.util.EconomyUtil;
import nl.rubixstudios.billify.util.item.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class InvoiceMenuButtons {

    // Custom model data of the Billify resource pack (see tools/resourcepack/generate.py)
    public static final int MODEL_INVISIBLE = 9100;
    public static final int MODEL_TAB_OPEN = 9110;
    public static final int MODEL_TAB_PAID = 9111;
    public static final int MODEL_COIN = 9112;
    public static final int MODEL_CLOSE = 9113;
    public static final int MODEL_PREVIOUS = 9114;
    public static final int MODEL_NEXT = 9115;
    public static final int MODEL_PAY = 9116;
    public static final int MODEL_BACK = 9117;

    /** A classic item, or a resource pack icon on PAPER when the viewer has the pack. */
    private static ItemBuilder base(XMaterial classic, boolean pack, int model) {
        if (!pack) return new ItemBuilder(classic.parseMaterial());
        return new ItemBuilder(XMaterial.PAPER.parseMaterial()).setCustomModelData(model);
    }

    public static ItemStack previousPage(boolean pack) {
        return base(XMaterial.PAPER, pack, MODEL_PREVIOUS)
                .setName(Language.getMessage("INVOICE.BUTTONS.PREVIOUS_PAGE"))
                .setNBT("isPreviousPage", true)
                .toItemStack();
    }

    public static ItemStack nextPage(boolean pack) {
        return base(XMaterial.PAPER, pack, MODEL_NEXT)
                .setName(Language.getMessage("INVOICE.BUTTONS.NEXT_PAGE"))
                .setNBT("isNextPage", true)
                .toItemStack();
    }

    public static ItemStack close(boolean pack) {
        return base(XMaterial.BARRIER, pack, MODEL_CLOSE)
                .setName(Language.getMessage("INVOICE.BUTTONS.CLOSE"))
                .setNBT("isClose", true)
                .toItemStack();
    }

    public static ItemStack openInvoices(boolean pack) {
        return base(XMaterial.PAPER, pack, MODEL_TAB_OPEN)
                .setName(Language.getMessage("INVOICE.BUTTONS.OPEN_INVOICES"))
                .setNBT("status", InvoiceStatus.OPEN.toString())
                .toItemStack();
    }

    public static ItemStack paidInvoices(boolean pack) {
        return base(XMaterial.MAP, pack, MODEL_TAB_PAID)
                .setName(Language.getMessage("INVOICE.BUTTONS.PAID_INVOICES"))
                .setNBT("status", InvoiceStatus.PAID.toString())
                .toItemStack();
    }

    public static ItemStack balance(OfflinePlayer player, boolean pack) {
        final double balance = EconomyUtil.getBalance(player);

        final List<String> lore = new ArrayList<>();
        Language.getMessageList("INVOICE.BUTTONS.BALANCE.LORE").forEach(line -> lore.add(ColorUtil.translate(line
                .replace("%balance%", EconomyUtil.format(balance))
                .replace("%player%", player.getName() != null ? player.getName() : "Unknown")
        )));

        return base(XMaterial.SUNFLOWER, pack, MODEL_COIN)
                .setName(Language.getMessage("INVOICE.BUTTONS.BALANCE.TITLE")
                        .replace("%balance%", EconomyUtil.format(balance)))
                .setLore(lore)
                .setNBT("isBalance", true)
                .toItemStack();
    }

    public static ItemStack glass(boolean pack) {
        return base(XMaterial.GLASS_PANE, pack, MODEL_INVISIBLE)
                .setName(ColorUtil.translate(""))
                .setNBT("isGlass", true)
                .toItemStack();
    }


    public static ItemStack invoice(Invoice invoice, boolean pack) {
        return invoice(invoice, pack ? invoiceModel(invoice) : 0);
    }

    /** Pay button of the receipt; with the pack it is an invisible hotspot over the drawn button. */
    public static ItemStack pay(Invoice invoice, boolean pack) {
        return base(XMaterial.EMERALD, pack, pack ? MODEL_INVISIBLE : MODEL_PAY)
                .setName(Language.getMessage("INVOICE.BUTTONS.PAY.TITLE"))
                .setLore(replaceAll(Language.getMessageList("INVOICE.BUTTONS.PAY.LORE"), invoice))
                .setNBT("payInvoiceId", invoice.getInvoiceId())
                .toItemStack();
    }

    public static ItemStack back(boolean pack) {
        return base(XMaterial.ARROW, pack, pack ? MODEL_INVISIBLE : MODEL_BACK)
                .setName(Language.getMessage("INVOICE.BUTTONS.BACK"))
                .setNBT("isBack", true)
                .toItemStack();
    }

    private static List<String> replaceAll(List<String> lines, Invoice invoice) {
        final List<String> out = new ArrayList<>();
        lines.forEach(line -> out.add(line
                .replace("%invoice_id%", String.valueOf(invoice.getInvoiceId()))
                .replace("%amount%", EconomyUtil.format(invoice.getInvoiceAmount()))
                .replace("%reason%", invoice.getInvoiceReason())));
        return out;
    }

    public static int invoiceModel(Invoice invoice) {
        switch (invoice.getInvoiceStatus()) {
            case OVERDUE: return 9102;
            case PAID: return 9103;
            case AUTOPAID: return 9104;
            case CANCELLED: return 9105;
            default: return 9101;
        }
    }

    /**
     * The invoice item with its full lore. model > 0 sets the resource pack model
     * (MODEL_INVISIBLE turns it into an invisible hotspot on the drawn receipt).
     */
    public static ItemStack invoice(Invoice invoice, int model) {
        final ItemBuilder itemBuilder = new ItemBuilder(XMaterial.PAPER.parseMaterial());
        if (model > 0) itemBuilder.setCustomModelData(model);
        itemBuilder.setName(Language.getMessage("INVOICE.BUTTONS.INVOICE.TITLE")
                .replace("%invoice_id%", String.valueOf(invoice.getInvoiceId()))
        );

        final List<String> lore = new ArrayList<>();

        Language.getMessageList("INVOICE.BUTTONS.INVOICE.LORE").forEach(line -> lore.add(ColorUtil.translate(line
                .replace("%sender%", Bukkit.getOfflinePlayer(invoice.getInvoiceAuthor()).getName())
                .replace("%status%", invoice.getInvoiceStatus().getPrefix())
                .replace("%price%", String.valueOf(invoice.getInvoiceAmount()))
                .replace("%reason%", invoice.getInvoiceReason())
        )));

        if (invoice.getInvoiceStatus() == InvoiceStatus.PAID) {
            Language.getMessageList("INVOICE.BUTTONS.INVOICE.WHEN_PAID").forEach(line -> lore.add(ColorUtil.translate(line
                    .replace("%date%", invoice.getDateInvoicePaidToString())
                    .replace("%paidBy%", Bukkit.getOfflinePlayer(invoice.getPaidBy()).getName())
            )));
        } else if (invoice.getInvoiceStatus() == InvoiceStatus.AUTOPAID) {
            Language.getMessageList("INVOICE.BUTTONS.INVOICE.WHEN_AUTO_PAID").forEach(line -> {
                lore.add(ColorUtil.translate(line
                        .replace("%date%", invoice.getDateInvoicePaidToString())
                ));
            });
        } else if (invoice.getInvoiceStatus() == InvoiceStatus.CANCELLED) {
            Language.getMessageList("INVOICE.BUTTONS.INVOICE.WHEN_CANCELLED").forEach(line -> lore.add(ColorUtil.translate(line
                    .replace("%date%", invoice.getDateInvoiceCanceledToString())
                    .replace("%reason%", invoice.getCancelReason())
                    .replace("%canceledBy%", Bukkit.getOfflinePlayer(invoice.getCanceledBy()).getName())
            )));
        } else {
            Language.getMessageList("INVOICE.BUTTONS.INVOICE.WHEN_NOT_PAID_YET").forEach(line -> lore.add(ColorUtil.translate(line
                    .replace("%dateToPay%", invoice.getDateToPayInString())
            )));
        }

        itemBuilder.setLore(lore);
        itemBuilder.setNBT("invoiceId", invoice.getInvoiceId());

        return itemBuilder.toItemStack();
    }
}
