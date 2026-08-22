package nl.rubixstudios.billify.util.item;

import io.github.bananapuncher714.nbteditor.NBTEditor;
import nl.rubixstudios.billify.util.ColorUtil;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Easily create itemstacks, without messing your hands. <i>Note that if you do
 * use this in one of your projects, leave this notice.</i> <i>Please do credit
 * me if you do use this in one of your projects.</i>
 *
 * @author NonameSL
 */
@SuppressWarnings("deprecation")
public class ItemBuilder {
    private ItemStack is;

    /**
     * Create a new ItemBuilder from scratch.
     *
     * @param m The material to create the ItemBuilder with.
     */
    public ItemBuilder(Material m) {
        this(m, 1);
    }

    /**
     * Create a new ItemBuilder over an existing itemstack.
     *
     * @param is The itemstack to create the ItemBuilder over.
     */
    public ItemBuilder(ItemStack is) {
        this.is = is;
    }

    /**
     * Create a new ItemBuilder from scratch.
     *
     * @param m      The material of the item.
     * @param amount The amount of the item.
     */
    public ItemBuilder(Material m, int amount) {
        is = new ItemStack(m, amount);
    }

    /**
     * Create a new ItemBuilder from scratch.
     *
     * @param m      The material of the item.
     * @param amount The amount of the item.
     * @param damage The damage of the item.
     */
    public ItemBuilder(Material m, int amount, short damage) {
        is = new ItemStack(m, amount, damage);
    }

    /**
     * Clone the ItemBuilder into a new one.
     *
     * @return The cloned instance.
     */
    public ItemBuilder clone() {
        return new ItemBuilder(is);
    }

    /** Set NBT tag for the item.
     * @param key The key for the NBT tag.
     * @param value The value for the NBT tag.
     * @return The ItemBuilder instance.
     */
    public ItemBuilder setNBT(String key, Object value) {
        is = NBTEditor.set(is, value, key);
        return this;
    }

    public ItemBuilder setType(Material material) {
        is.setType(material);
        return this;
    }

    public ItemBuilder hideFlags() {
        is = NBTEditor.set(is, (byte) 63, "HideFlags");
        return this;
    }

    /**
     * Set the displayname of the item.
     *
     * @param name The name to change it to.
     */
    public ItemBuilder setName(String name) {
        ItemMeta im = is.getItemMeta();
        im.setDisplayName(ColorUtil.translate(name));
        is.setItemMeta(im);
        return this;
    }

    /**
     * Re-sets the lore.
     *
     * @param lore The lore to set it to.
     */
    public ItemBuilder setLore(String... lore) {
        ItemMeta im = is.getItemMeta();
        im.setLore(Arrays.stream(lore).map(ColorUtil::translate).collect(Collectors.toList()));
        is.setItemMeta(im);
        return this;
    }

    /**
     * Re-sets the lore.
     *
     * @param lore The lore to set it to.
     */
    public ItemBuilder setLore(List<String> lore) {
        ItemMeta im = is.getItemMeta();
        lore = lore.stream().map(ColorUtil::translate).collect(Collectors.toList());
        im.setLore(lore);
        is.setItemMeta(im);
        return this;
    }

    /**
     * Retrieves the itemstack from the ItemBuilder.
     *
     * @return The itemstack created/modified by the ItemBuilder instance.
     */
    public ItemStack toItemStack() {
        return is;
    }
}