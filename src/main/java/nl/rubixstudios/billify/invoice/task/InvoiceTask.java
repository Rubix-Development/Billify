package nl.rubixstudios.billify.invoice.task;

import nl.rubixstudios.billify.Billify;
import nl.rubixstudios.billify.data.Config;
import nl.rubixstudios.billify.data.Language;
import nl.rubixstudios.billify.invoice.InvoiceManager;
import nl.rubixstudios.billify.invoice.InvoiceUser;
import nl.rubixstudios.billify.invoice.object.Invoice;
import nl.rubixstudios.billify.invoice.object.InvoiceStatus;
import nl.rubixstudios.billify.util.ColorUtil;
import nl.rubixstudios.billify.util.EconomyUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class InvoiceTask extends BukkitRunnable {

    private final InvoiceManager invoiceManager;
    private long latestTimeSaved;

    public InvoiceTask(InvoiceManager invoiceManager) {
        this.invoiceManager = invoiceManager;
    }

    @Override
    public void run() {
        if (System.currentTimeMillis() - this.latestTimeSaved > 300000) {
            this.latestTimeSaved = System.currentTimeMillis();
            this.invoiceManager.saveInvoices(true);
        }

        if (this.invoiceManager.getInvoiceUsers().isEmpty()) return;

        for (InvoiceUser invoiceUser : this.invoiceManager.getInvoiceUsers()) {
            final List<Invoice> invoices = invoiceUser.getInvoices().stream()
                    .filter(invoice -> invoice.getInvoiceStatus() == InvoiceStatus.OPEN || invoice.getInvoiceStatus() == InvoiceStatus.OVERDUE)
                    .collect(Collectors.toList());
            if (invoices.isEmpty()) continue;

            for (Invoice invoice : invoices) {
                if (!new Date().after(invoice.getDateToPay())) continue;
                handleOverdueInvoice(invoiceUser, invoice);
            }
        }
    }

    private void handleOverdueInvoice(InvoiceUser invoiceUser, Invoice invoice) {
        if (!Config.getBoolean("DEBT_COLLECTION.ENABLED")) {
            // Debt collection disabled entirely -> keep old behaviour (mark overdue only).
            invoice.setInvoiceStatus(InvoiceStatus.OVERDUE);
            return;
        }

        invoice.setInvoiceStatus(InvoiceStatus.OVERDUE);

        // Apply a one-time collection fee, if configured.
        final double feePercent = Config.getDouble("DEBT_COLLECTION.COLLECTION_FEE_PERCENT");
        if (feePercent > 0 && !invoice.isCollectionFeeApplied()) {
            final double fee = invoice.getInvoiceAmount() * (feePercent / 100.0D);
            invoice.setInvoiceAmount(invoice.getInvoiceAmount() + fee);
            invoice.setCollectionFeeApplied(true);
            sendAgencyMessage(invoiceUser, "DEBT_COLLECTION.FEE_APPLIED", invoice);
        }

        // Try automatic collection (must run on the main thread for economy safety).
        if (Config.getBoolean("DEBT_COLLECTION.AUTO_COLLECT.ENABLED")) {
            Bukkit.getScheduler().runTask(Billify.getInstance(), () -> {
                if (invoice.getInvoiceStatus() != InvoiceStatus.OVERDUE) return;
                final String result = this.invoiceManager.payInvoice(invoiceUser.getPlayerId(), invoice.getInvoiceId(), true);
                if ("INVOICE_AUTO_PAID".equals(result)) {
                    sendAgencyMessage(invoiceUser, "DEBT_COLLECTION.AUTO_COLLECTED", invoice);
                } else {
                    sendReminderIfDue(invoiceUser, invoice);
                }
            });
        } else {
            sendReminderIfDue(invoiceUser, invoice);
        }
    }

    private void sendReminderIfDue(InvoiceUser invoiceUser, Invoice invoice) {
        if (!Config.getBoolean("DEBT_COLLECTION.REMINDER.ENABLED")) return;

        final long intervalMillis = Math.max(1, Config.getInteger("DEBT_COLLECTION.REMINDER.INTERVAL_MINUTES")) * 60000L;
        if (System.currentTimeMillis() - invoice.getLastReminderTime() < intervalMillis) return;

        final Player player = Bukkit.getPlayer(invoiceUser.getPlayerId());
        if (player == null || !player.isOnline()) return;

        invoice.setLastReminderTime(System.currentTimeMillis());
        for (String line : Language.getMessageList("INVOICE.DEBT_COLLECTION.REMINDER")) {
            player.sendMessage(ColorUtil.translate(applyPlaceholders(line, invoice)));
        }
    }

    private void sendAgencyMessage(InvoiceUser invoiceUser, String configPath, Invoice invoice) {
        final Player player = Bukkit.getPlayer(invoiceUser.getPlayerId());
        if (player == null || !player.isOnline()) return;

        final String message = Language.getMessage("INVOICE." + configPath);
        if (message == null || message.isEmpty()) return;
        player.sendMessage(ColorUtil.translate(applyPlaceholders(message, invoice)));
    }

    private String applyPlaceholders(String line, Invoice invoice) {
        String agencyName = Config.getString("DEBT_COLLECTION.AGENCY_NAME");
        if (agencyName == null) agencyName = "Debt Collection";
        return line
                .replace("%agency%", agencyName)
                .replace("%invoice_id%", String.valueOf(invoice.getInvoiceId()))
                .replace("%amount%", EconomyUtil.format(invoice.getInvoiceAmount()))
                .replace("%days_overdue%", String.valueOf(invoice.getDaysOverdue()));
    }
}
