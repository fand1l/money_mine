package dev.maximus.hryvnia.economy;

import dev.maximus.hryvnia.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * All physical-cash operations: counting banknotes in an inventory, paying
 * with cash (with automatic change), and handing banknotes out using the
 * fewest bills possible.
 */
public final class CashHelper {
    /** Denominations from largest to smallest; matches the registered banknote items. */
    public static final int[] DENOMINATIONS = {2000, 1000, 500, 200, 100, 50, 20, 10, 5, 2, 1};

    private CashHelper() {
    }

    /** Value of one item of this stack if it is a banknote, otherwise 0. */
    public static int noteValue(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        Integer value = ModItems.BANKNOTE_VALUES.get(stack.getItem());
        return value != null ? value : 0;
    }

    /** Total cash the player is carrying. */
    public static long countCash(ServerPlayer player) {
        long total = 0;
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            int value = noteValue(stack);
            if (value > 0) {
                total += (long) value * stack.getCount();
            }
        }
        return total;
    }

    /**
     * Takes {@code price} in cash from the player. All banknotes are collected
     * and the difference is returned as change in the fewest bills, which also
     * conveniently consolidates the player's wallet.
     *
     * @return true if the player had enough cash and payment happened
     */
    public static boolean takeCash(ServerPlayer player, long price) {
        long total = countCash(player);
        if (total < price) {
            return false;
        }
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (noteValue(inventory.getItem(i)) > 0) {
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }
        giveCash(player, total - price);
        return true;
    }

    /** Gives the amount in banknotes using the fewest bills; overflow drops at the player. */
    public static void giveCash(ServerPlayer player, long amount) {
        long remaining = amount;
        for (int denomination : DENOMINATIONS) {
            long count = remaining / denomination;
            remaining %= denomination;
            Item note = ModItems.banknote(denomination);
            while (count > 0) {
                int stackCount = (int) Math.min(count, note.getDefaultMaxStackSize());
                ItemStack stack = new ItemStack(note, stackCount);
                player.getInventory().placeItemBackInInventory(stack);
                count -= stackCount;
            }
        }
    }
}
