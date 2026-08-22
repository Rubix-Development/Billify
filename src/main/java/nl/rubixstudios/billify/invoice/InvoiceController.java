package nl.rubixstudios.billify.invoice;

import com.cryptomorin.xseries.XMaterial;
import io.github.bananapuncher714.nbteditor.NBTEditor;
import lombok.Getter;
import nl.rubixstudios.billify.Billify;
import nl.rubixstudios.billify.data.Config;
import nl.rubixstudios.billify.data.Language;
import nl.rubixstudios.billify.invoice.menu.InvoiceMenuButtons;
import nl.rubixstudios.billify.invoice.object.Invoice;
import nl.rubixstudios.billify.invoice.object.InvoiceStatus;
import nl.rubixstudios.billify.util.ColorUtil;
import nl.rubixstudios.billify.util.EconomyUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Getter
public class InvoiceController implements Listener {

    private static final int INVOICES_PER_PAGE = 36;

    @Getter private static InvoiceController instance;
    private final InvoiceManager invoiceManager;

    private final Map<UUID, MenuView> openMenus = new HashMap<>();

    private static class MenuView {
        private final UUID target;
        private final boolean openTab;
        private final int page;

        private MenuView(UUID target, boolean openTab, int page) {
            this.target = target;
            this.openTab = openTab;
            this.page = page;
        }
    }

    public InvoiceController() {
        instance = this;
        this.invoiceManager = new InvoiceManager();
        Bukkit.getPluginManager().registerEvents(this, Billify.getInstance());
    }

    public void disable() {
        this.invoiceManager.disable();
    }

    public void openInvoiceMenu(Player player, OfflinePlayer targetPlayer, boolean openInvoice) {
        openInvoiceMenu(player, targetPlayer, openInvoice, 0);
    }

    public void openInvoiceMenu(Player player, OfflinePlayer targetPlayer, boolean openInvoice, int page) {
        final InvoiceUser invoiceUser = invoiceManager.getOrCreateInvoiceUser(targetPlayer.getUniqueId());
        final List<Invoice> invoices = filterInvoices(openInvoice, invoiceUser);

        final int maxPage = Math.max(0, (invoices.size() - 1) / INVOICES_PER_PAGE);
        page = Math.max(0, Math.min(page, maxPage));

        final String inventoryOf = "[" + targetPlayer.getName() + "]";
        final String section = openInvoice ? Language.getMessage("INVOICE.MENU.SECTIONS.OPEN") : Language.getMessage("INVOICE.MENU.SECTIONS.PAID");
        final Inventory invoiceMenu = Bukkit.createInventory(null, 54, ColorUtil.translate("&8&l» " + Language.getMessage("INVOICE.MENU.SECTIONS.INVOICE") + " &8| " + section + " " + inventoryOf));

        for (int i = 0; i < 54; i++) {
            if (i < 9 || i > 44) {
                handleNonClickableItems(invoiceMenu, i, targetPlayer, page, maxPage);
                continue;
            }

            int invoiceIndex = (page * INVOICES_PER_PAGE) + (i - 9);
            if (invoiceIndex < invoices.size()) {
                invoiceMenu.setItem(i, InvoiceMenuButtons.invoice(invoices.get(invoiceIndex)));
            }
        }

        // openInventory closes any current menu first (which fires InventoryCloseEvent),
        // so register the view only after the new inventory is open.
        player.openInventory(invoiceMenu);
        openMenus.put(player.getUniqueId(), new MenuView(targetPlayer.getUniqueId(), openInvoice, page));
    }

    private void handleNonClickableItems(Inventory invoiceMenu, int index, OfflinePlayer targetPlayer, int page, int maxPage) {
        switch (index) {
            case 3:
                invoiceMenu.setItem(index, InvoiceMenuButtons.openInvoices());
                break;
            case 4:
                if (Config.getBoolean("OPEN_INVOICE_MENU.MENU.SHOW_BALANCE") && EconomyUtil.hasBalanceSupport()) {
                    invoiceMenu.setItem(index, InvoiceMenuButtons.balance(targetPlayer));
                } else {
                    invoiceMenu.setItem(index, InvoiceMenuButtons.glass());
                }
                break;
            case 5:
                invoiceMenu.setItem(index, InvoiceMenuButtons.paidInvoices());
                break;
            case 48:
                invoiceMenu.setItem(index, page > 0 ? InvoiceMenuButtons.previousPage() : InvoiceMenuButtons.glass());
                break;
            case 49:
                invoiceMenu.setItem(index, InvoiceMenuButtons.close());
                break;
            case 50:
                invoiceMenu.setItem(index, page < maxPage ? InvoiceMenuButtons.nextPage() : InvoiceMenuButtons.glass());
                break;
            default:
                invoiceMenu.setItem(index, InvoiceMenuButtons.glass());
                break;
        }
    }

    private List<Invoice> filterInvoices(boolean openInvoice, InvoiceUser invoiceUser) {
        return openInvoice ?
                invoiceUser.getInvoices().stream().filter(invoice -> invoice.getInvoiceStatus() == InvoiceStatus.OPEN
                        || invoice.getInvoiceStatus() == InvoiceStatus.OVERDUE).collect(Collectors.toList()) :
                invoiceUser.getInvoices().stream().filter(invoice -> invoice.getInvoiceStatus() == InvoiceStatus.PAID
                        || invoice.getInvoiceStatus() == InvoiceStatus.AUTOPAID
                        || invoice.getInvoiceStatus() == InvoiceStatus.CANCELLED).collect(Collectors.toList());
    }

    private void handleInvoicePayment(Player player, OfflinePlayer targetPlayer, ItemStack itemStack) {
        if (targetPlayer != null && !player.getUniqueId().equals(targetPlayer.getUniqueId())) {
            player.sendMessage(Language.getMessage("INVOICE.CHECK.CANNOT_PAY_OTHERS_INVOICE"));
        } else {
            int invoiceId = NBTEditor.getInt(itemStack, "invoiceId");
            String codeStatus = invoiceManager.payInvoice(player.getUniqueId(), invoiceId, false);

            switch (codeStatus) {
                case "INVOICE_NOT_FOUND":
                    player.sendMessage(Language.getMessage("INVOICE.CHECK.INVOICE_NOT_FOUND"));
                    break;
                case "INVOICE_ALREADY_PAID":
                    player.sendMessage(Language.getMessage("INVOICE.CHECK.INVOICE_ALREADY_PAID"));
                    break;
                case "NOT_ENOUGH_MONEY":
                    player.sendMessage(Language.getMessage("INVOICE.CHECK.NOT_ENOUGH_MONEY"));
                    break;
                case "INVOICE_PAID":
                    player.sendMessage(Language.getMessage("INVOICE.CHECK.INVOICE_PAID")
                            .replace("%invoice_id%", String.valueOf(invoiceId)));
                    openInvoiceMenu(player, targetPlayer != null ? targetPlayer : player, true);
                    break;
                case "INVOICE_AUTO_PAID":
                    player.sendMessage(Language.getMessage("INVOICE.CHECK.INVOICE_AUTO_PAID")
                            .replace("%invoice_id%", String.valueOf(invoiceId)));
                    break;
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();

        final InvoiceUser invoiceUser = this.invoiceManager.getInvoiceUser(player.getUniqueId());
        if (invoiceUser == null) return;

        final long openInvoices = invoiceUser.getInvoices().stream()
                .filter(invoice -> invoice.getInvoiceStatus() == InvoiceStatus.OPEN || invoice.getInvoiceStatus() == InvoiceStatus.OVERDUE)
                .count();
        if (openInvoices == 0) return;

        player.sendMessage(Language.getMessage("INVOICE.CHECK.OPEN_INVOICE_NOTIFY")
                .replace("%amount%", String.valueOf(openInvoices))
                .replace("%command%", "/invoice"));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        openMenus.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        openMenus.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        final Player player = (Player) event.getWhoClicked();

        final MenuView view = openMenus.get(player.getUniqueId());
        if (view == null) return;

        event.setCancelled(true);

        final ItemStack itemStack = event.getCurrentItem();
        if (itemStack == null || itemStack.getType() == XMaterial.AIR.parseMaterial()) return;

        final OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(view.target);

        if (NBTEditor.contains(itemStack, "status")) {
            String status = NBTEditor.getString(itemStack, "status");
            if (status.equalsIgnoreCase(InvoiceStatus.OPEN.toString())) {
                openInvoiceMenu(player, targetPlayer, true);
            } else if (status.equalsIgnoreCase(InvoiceStatus.PAID.toString())) {
                openInvoiceMenu(player, targetPlayer, false);
            }
        } else if (NBTEditor.contains(itemStack, "isClose")) {
            player.closeInventory();
        } else if (NBTEditor.contains(itemStack, "isNextPage")) {
            openInvoiceMenu(player, targetPlayer, view.openTab, view.page + 1);
        } else if (NBTEditor.contains(itemStack, "isPreviousPage")) {
            openInvoiceMenu(player, targetPlayer, view.openTab, view.page - 1);
        } else if (NBTEditor.contains(itemStack, "invoiceId")) {
            handleInvoicePayment(player, targetPlayer, itemStack);
        }
    }

    @EventHandler
    public void onBlockClick(PlayerInteractEvent event) {
        final Player player = event.getPlayer();

        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        if (Config.getBoolean("OPEN_INVOICE_MENU.RIGHT_CLICK_ITEM.ENABLED")) {
            final Optional<XMaterial> clickedItem = XMaterial.matchXMaterial(Config.getString("OPEN_INVOICE_MENU.RIGHT_CLICK_ITEM.ITEM.MATERIAL"));
            final ItemStack item = event.getItem();

            if (clickedItem.isPresent() && item != null && item.getType() == clickedItem.get().parseMaterial()) {
                if (!checkOpenPermission(player, "OPEN_INVOICE_MENU.RIGHT_CLICK_ITEM.PERMISSION")) return;

                event.setCancelled(true);
                openInvoiceMenu(player, player, true);
                return;
            }
        }

        if (Config.getBoolean("OPEN_INVOICE_MENU.RIGHT_CLICK_BLOCK.ENABLED") && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            final Optional<XMaterial> clickedBlock = XMaterial.matchXMaterial(Config.getString("OPEN_INVOICE_MENU.RIGHT_CLICK_BLOCK.BLOCK.MATERIAL"));
            final Block block = event.getClickedBlock();

            if (clickedBlock.isPresent() && block != null && block.getType() == clickedBlock.get().parseMaterial()) {
                if (!checkOpenPermission(player, "OPEN_INVOICE_MENU.RIGHT_CLICK_BLOCK.PERMISSION")) return;

                event.setCancelled(true);
                openInvoiceMenu(player, player, true);
            }
        }
    }

    private boolean checkOpenPermission(Player player, String permissionPath) {
        if (!Config.getBoolean(permissionPath + ".NEED_PERMISSION")) return true;
        if (player.hasPermission(Config.getString(permissionPath + ".PERMISSION"))) return true;

        player.sendMessage(Language.getMessage("INVOICE.CHECK.NO_PERMISSION_TO_OPEN_INVOICE_MENU"));
        return false;
    }
}
