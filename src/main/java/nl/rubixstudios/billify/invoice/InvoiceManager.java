package nl.rubixstudios.billify.invoice;

import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import lombok.Getter;
import nl.rubixstudios.billify.Billify;
import nl.rubixstudios.billify.data.Config;
import nl.rubixstudios.billify.data.ConfigFile;
import nl.rubixstudios.billify.data.Language;
import nl.rubixstudios.billify.invoice.object.Invoice;
import nl.rubixstudios.billify.invoice.object.InvoiceStatus;
import nl.rubixstudios.billify.invoice.task.InvoiceTask;
import nl.rubixstudios.billify.util.ColorUtil;
import nl.rubixstudios.billify.util.EconomyUtil;
import nl.rubixstudios.billify.util.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;

@Getter
public class InvoiceManager {

    private List<InvoiceUser> invoiceUsers;
    private List<InvoicePermission> invoicePermissions;

    private final File invoiceUsersFile;

    private BukkitRunnable invoiceTask;

    public InvoiceManager() {
        this.invoiceUsersFile = FileUtils.getOrCreateFile(Config.INVOICE_DIR, "invoiceUsers.json");

        this.loadInvoices();
        this.loadInvoicePermissions();

        this.invoiceTask = new InvoiceTask(this);
        this.invoiceTask.runTaskTimerAsynchronously(Billify.getInstance(), 0L, 60 * 20L);
    }

    public void disable() {
        this.invoiceTask.cancel();
        this.invoiceTask = null;

        this.saveInvoices(true);

    }

    public void loadInvoicePermissions() {
        if (this.invoicePermissions == null) {
            this.invoicePermissions = new ArrayList<>();
        } else {
            this.invoicePermissions.clear();
        }

        ConfigFile config = Billify.getInstance().getConfigFile();
        if (config == null) {
            Billify.getInstance().getLogger().warning("Could not load invoice permissions, config file is null.");
            return;
        }

        ConfigurationSection section = config.getConfigurationSection("RANK_PERMISSIONS.RANKS");
        if (section == null || section.getKeys(false).isEmpty()) {
            Billify.getInstance().getLogger().warning("Could not load invoice permissions, please set them up in the config.");
            return;
        }

        section.getKeys(false).forEach(key -> {
            final String permission = section.getString(key + ".PERMISSION");
            final double limit = section.getDouble(key + ".LIMIT_AMOUNT");
            final boolean canCreateInvoice = section.getBoolean(key + ".CAN_CREATE_INVOICE");
            final boolean canCancelInvoice = section.getBoolean(key + ".CAN_CANCEL_INVOICE");
            final boolean canCheckInvoice = section.getBoolean(key + ".CAN_CHECK_INVOICE");

            final InvoicePermission invoicePermission = new InvoicePermission(key, permission, limit, canCreateInvoice, canCancelInvoice, canCheckInvoice);
            invoicePermissions.add(invoicePermission);
        });
    }

    public void loadInvoices() {
        try (FileReader reader = new FileReader(invoiceUsersFile)) {
            Type invoiceUserType = new TypeToken<List<InvoiceUser>>() {}.getType();
            List<InvoiceUser> loadedInvoiceUsers = Billify.getInstance().getGson().fromJson(reader, invoiceUserType);
            if (loadedInvoiceUsers != null && !loadedInvoiceUsers.isEmpty()) {
                invoiceUsers = loadedInvoiceUsers;
                Billify.getInstance().log(ColorUtil.translate("&aLoaded " + invoiceUsers.size() + " invoices."));
            } else {
                invoiceUsers = new ArrayList<>();
                Billify.getInstance().log(ColorUtil.translate("&eNo invoices found. Initialized an empty list."));
            }
        } catch (FileNotFoundException e) {
            invoiceUsers = new ArrayList<>();
            Billify.getInstance().log(ColorUtil.translate("&eNo invoices file found. Initialized an empty list."));
        } catch (JsonSyntaxException | IOException e) {
            e.printStackTrace();
        }
    }

    public void saveInvoices(boolean log) {
        try (FileWriter writer = new FileWriter(invoiceUsersFile)) {
            Type invoiceUserType = new TypeToken<List<InvoiceUser>>() {}.getType();
            Billify.getInstance().getGson().toJson(invoiceUsers, invoiceUserType, writer);
            writer.flush();
            if (log) {
                Billify.getInstance().log(ColorUtil.translate("&aSaved " + invoiceUsers.size() + " invoices."));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addInvoice(UUID billingFrom, UUID billingTo, String description, double amount) {
        final InvoiceUser invoiceUser = this.getOrCreateInvoiceUser(billingTo);

        final int invoiceId = 1 + invoiceUser.getInvoices().stream().mapToInt(Invoice::getInvoiceId).max().orElse(0);
        final Date dateToday = new Date();

        final Invoice invoice = new Invoice(invoiceId, InvoiceStatus.OPEN, billingFrom, amount, description, dateToday);
        invoice.setDaysToPay(Config.getInteger("INVOICE_SETTINGS.STANDARD_DAYS_TO_PAY"));
        invoiceUser.getInvoices().add(invoice);
    }

    public String payInvoice(UUID billingUser, int invoiceId, boolean autoPay) {
        InvoiceUser invoiceUser = getOrCreateInvoiceUser(billingUser);
        Invoice invoice = invoiceUser.getInvoices()
                .stream()
                .filter(inv -> inv.getInvoiceId() == invoiceId)
                .findFirst()
                .orElse(null);

        if (invoice == null) {
            return "INVOICE_NOT_FOUND";
        }

        if (invoice.getInvoiceStatus() != InvoiceStatus.OPEN && invoice.getInvoiceStatus() != InvoiceStatus.OVERDUE) {
            return "INVOICE_ALREADY_PAID";
        }

        final OfflinePlayer payer = Bukkit.getOfflinePlayer(billingUser);

        if (autoPay) {
            final boolean allowNegative = Config.getBoolean("DEBT_COLLECTION.AUTO_COLLECT.ALLOW_NEGATIVE_BALANCE");
            if (!allowNegative && !EconomyUtil.has(payer, invoice.getInvoiceAmount())) {
                return "NOT_ENOUGH_MONEY";
            }
            if (!EconomyUtil.withdraw(payer, invoice.getInvoiceAmount())) {
                return "NOT_ENOUGH_MONEY";
            }
            completePayment(invoice, payer, InvoiceStatus.AUTOPAID);
            return "INVOICE_AUTO_PAID";
        }

        if (!EconomyUtil.has(payer, invoice.getInvoiceAmount())) {
            return "NOT_ENOUGH_MONEY";
        }

        if (!EconomyUtil.withdraw(payer, invoice.getInvoiceAmount())) {
            return "NOT_ENOUGH_MONEY";
        }

        completePayment(invoice, payer, InvoiceStatus.PAID);
        return "INVOICE_PAID";
    }

    private void completePayment(Invoice invoice, OfflinePlayer payer, InvoiceStatus status) {
        invoice.setInvoiceStatus(status);
        invoice.setDaysToPay(0);
        invoice.setPaymentDateTime(System.currentTimeMillis());
        invoice.setPaidBy(payer.getUniqueId());

        // Give money to invoice creator (works for offline creators as well)
        final OfflinePlayer creator = Bukkit.getOfflinePlayer(invoice.getInvoiceAuthor());
        EconomyUtil.deposit(creator, invoice.getInvoiceAmount());
    }

    public boolean cancelInvoice(Player player, UUID billingUser, int invoiceId, String reason) {
        InvoiceUser invoiceUser = getOrCreateInvoiceUser(billingUser);
        Invoice invoice = invoiceUser.getInvoices()
                .stream()
                .filter(inv -> inv.getInvoiceId() == invoiceId)
                .findFirst()
                .orElse(null);

        if (invoice == null) {
            return false;
        }

        invoice.setInvoiceStatus(InvoiceStatus.CANCELLED);
        invoice.setDaysToPay(0);
        invoice.setCanceledOnDateTime(System.currentTimeMillis());
        invoice.setCancelReason(reason);
        invoice.setCanceledBy(player.getUniqueId());
        return true;
    }


    public InvoiceUser getOrCreateInvoiceUser(UUID playerId) {
        InvoiceUser invoiceUser = getInvoiceUser(playerId);

        if (invoiceUser == null) {
            invoiceUser = new InvoiceUser(playerId);
            invoiceUsers.add(invoiceUser);
        }

        return invoiceUser;
    }

    public InvoiceUser getInvoiceUser(UUID playerId) {
        return invoiceUsers.stream()
                .filter(invoiceUser -> invoiceUser.getPlayerId().equals(playerId))
                .findFirst()
                .orElse(null);
    }

    public boolean isAlreadyInvoiceUser(UUID playerId) {
        return invoiceUsers.stream()
                .anyMatch(invoiceUser -> invoiceUser.getPlayerId().equals(playerId));
    }

    public boolean isAllowedToSentInvoice(Player player) {
        return invoicePermissions.stream().anyMatch(entry -> player.hasPermission(entry.getPermission()) && entry.isCanCreateInvoice());
    }

    public boolean isAllowedToCheckInvoice(Player player) {
        return invoicePermissions.stream().anyMatch(entry -> player.hasPermission(entry.getPermission()) && entry.isCanCheckInvoices());
    }

    public boolean isAllowedToCancelInvoice(Player player) {
        return invoicePermissions.stream().anyMatch(entry -> player.hasPermission(entry.getPermission()) && entry.isCanCancelInvoice());
    }

    public boolean exceededLimit(Player player, double amount) {
        return getLimit(player) < amount;
    }

    public double getLimit(Player player) {
        return invoicePermissions.stream()
                .filter(entry -> entry.getPermission() != null && player.hasPermission(entry.getPermission()))
                .mapToDouble(InvoicePermission::getLimit)
                .max()
                .orElse(0.0);
    }
}
