package dev.maximus.hryvnia.economy;

import dev.maximus.hryvnia.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.UUID;

/**
 * Bank cards are plain items carrying their data in the vanilla CUSTOM_DATA
 * component: the 16-digit card number plus the owner's UUID and name. The
 * balance itself never lives on the item — it stays in {@link EconomyState}.
 */
public final class CardHelper {
    public static final String KEY_NUMBER = "hryvnia_number";
    public static final String KEY_OWNER = "hryvnia_owner";
    public static final String KEY_OWNER_NAME = "hryvnia_owner_name";

    private CardHelper() {
    }

    public static ItemStack createCard(String number, UUID owner, String ownerName) {
        ItemStack stack = new ItemStack(ModItems.BANK_CARD);
        CompoundTag tag = new CompoundTag();
        tag.putString(KEY_NUMBER, number);
        tag.putString(KEY_OWNER, owner.toString());
        tag.putString(KEY_OWNER_NAME, ownerName);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    public static boolean isCard(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == ModItems.BANK_CARD;
    }

    public static String cardNumber(ItemStack stack) {
        if (!isCard(stack)) {
            return "";
        }
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.getStringOr(KEY_NUMBER, "");
    }

    public static String ownerUuidString(ItemStack stack) {
        if (!isCard(stack)) {
            return "";
        }
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.getStringOr(KEY_OWNER, "");
    }

    public static String ownerName(ItemStack stack) {
        if (!isCard(stack)) {
            return "";
        }
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.getStringOr(KEY_OWNER_NAME, "");
    }

    /** True if the player is carrying a card issued to them personally. */
    public static boolean hasOwnCard(ServerPlayer player) {
        String uuid = player.getUUID().toString();
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (isCard(stack) && uuid.equals(ownerUuidString(stack))) {
                return true;
            }
        }
        return false;
    }
}
