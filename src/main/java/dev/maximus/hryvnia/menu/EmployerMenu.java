package dev.maximus.hryvnia.menu;

import dev.maximus.hryvnia.ModMenus;
import dev.maximus.hryvnia.economy.CardHelper;
import dev.maximus.hryvnia.economy.CashHelper;
import dev.maximus.hryvnia.economy.EconomyState;
import dev.maximus.hryvnia.economy.HryvniaConfig;
import dev.maximus.hryvnia.network.ModPayloads;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The menu opened on any working villager: take the job, hand in produce for
 * pay, quit, and buy from the profession's shop. The client gets the rates,
 * shop entries and the player's current job as extended opening data.
 */
public class EmployerMenu extends AbstractContainerMenu {
    public static final int SELL_SLOTS = 5;

    /** One priced line, used both for job rates (count=1) and shop offers. */
    public record PriceEntry(String itemId, int count, int price) {
        public static final StreamCodec<RegistryFriendlyByteBuf, PriceEntry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, PriceEntry::itemId,
                ByteBufCodecs.VAR_INT, PriceEntry::count,
                ByteBufCodecs.VAR_INT, PriceEntry::price,
                PriceEntry::new);
    }

    public record EmployerData(String professionId, String yourJob, List<PriceEntry> rates, List<PriceEntry> shop) {
        public static final StreamCodec<RegistryFriendlyByteBuf, EmployerData> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, EmployerData::professionId,
                ByteBufCodecs.STRING_UTF8, EmployerData::yourJob,
                PriceEntry.STREAM_CODEC.apply(ByteBufCodecs.list()), EmployerData::rates,
                PriceEntry.STREAM_CODEC.apply(ByteBufCodecs.list()), EmployerData::shop,
                EmployerData::new);
    }

    private final Container sellContainer = new SimpleContainer(SELL_SLOTS);
    private final Villager villager;
    private final EmployerData data;
    /** Kept in sync server-side after hire/quit so the client can refresh labels. */
    private String yourJob;

    /** Client-side constructor used by the ExtendedMenuType factory. */
    public EmployerMenu(int containerId, Inventory playerInventory, EmployerData data) {
        this(containerId, playerInventory, null, data);
    }

    public EmployerMenu(int containerId, Inventory playerInventory, Villager villager, EmployerData data) {
        super(ModMenus.EMPLOYER, containerId);
        this.villager = villager;
        this.data = data;
        this.yourJob = data.yourJob();

        for (int i = 0; i < SELL_SLOTS; i++) {
            this.addSlot(new Slot(sellContainer, i, 8 + i * 18, 30));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 198));
        }
    }

    public EmployerData data() {
        return data;
    }

    public String professionId() {
        return data.professionId();
    }

    @Override
    public boolean stillValid(Player player) {
        return villager == null || (villager.isAlive() && player.distanceToSqr(villager) <= 64.0);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.clearContainer(player, sellContainer);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(slotIndex);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (slotIndex < SELL_SLOTS) {
                if (!this.moveItemStackTo(stack, SELL_SLOTS, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(stack, 0, SELL_SLOTS, false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return result;
    }

    // --- server-side action handling ------------------------------------

    public void handleAction(ServerPlayer player, int action, int index, boolean card) {
        EconomyState economy = EconomyState.get();
        if (economy == null) {
            return;
        }
        switch (action) {
            case ModPayloads.EmployerActionPayload.HIRE -> hire(player, economy);
            case ModPayloads.EmployerActionPayload.QUIT -> quit(player, economy);
            case ModPayloads.EmployerActionPayload.SELL -> sell(player, economy, card);
            case ModPayloads.EmployerActionPayload.BUY -> buy(player, economy, index, card);
            default -> {
            }
        }
    }

    private void hire(ServerPlayer player, EconomyState economy) {
        EconomyState.Account account = economy.account(player);
        if (account.job != null && !account.job.equals(professionId())) {
            player.displayClientMessage(Component.translatable("hryvnia.msg.already_employed", jobName(account.job)), false);
            playSound(player, false);
            return;
        }
        if (account.job == null) {
            account.job = professionId();
            economy.markDirty();
        }
        yourJob = account.job;
        player.displayClientMessage(Component.translatable("hryvnia.msg.hired", jobName(professionId())), false);
        playSound(player, true);
    }

    private void quit(ServerPlayer player, EconomyState economy) {
        EconomyState.Account account = economy.account(player);
        if (account.job != null) {
            account.job = null;
            economy.markDirty();
            yourJob = "";
            player.displayClientMessage(Component.translatable("hryvnia.msg.quit"), false);
        }
    }

    private void sell(ServerPlayer player, EconomyState economy, boolean card) {
        EconomyState.Account account = economy.account(player);
        if (account.job == null || !account.job.equals(professionId())) {
            player.displayClientMessage(Component.translatable("hryvnia.msg.not_employed"), false);
            playSound(player, false);
            return;
        }
        HryvniaConfig.ProfessionEconomy professionEconomy = HryvniaConfig.INSTANCE.professionEconomy(professionId());
        if (professionEconomy == null) {
            return;
        }
        if (card && !CardHelper.hasOwnCard(player)) {
            player.displayClientMessage(Component.translatable("hryvnia.msg.no_card"), false);
            playSound(player, false);
            return;
        }
        long earned = 0;
        for (int i = 0; i < sellContainer.getContainerSize(); i++) {
            ItemStack stack = sellContainer.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            Integer rate = rateFor(professionEconomy, stack);
            if (rate != null && rate > 0) {
                earned += (long) rate * stack.getCount();
                sellContainer.setItem(i, ItemStack.EMPTY);
            }
        }
        if (earned <= 0) {
            player.displayClientMessage(Component.translatable("hryvnia.msg.no_items"), false);
            playSound(player, false);
            return;
        }
        if (card) {
            economy.addBalance(player.getUUID(), earned);
        } else {
            CashHelper.giveCash(player, earned);
        }
        this.broadcastChanges();
        player.displayClientMessage(Component.translatable("hryvnia.msg.paid", String.valueOf(earned)), false);
        playSound(player, true);
    }

    private Integer rateFor(HryvniaConfig.ProfessionEconomy professionEconomy, ItemStack stack) {
        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        for (Map.Entry<String, Integer> entry : professionEconomy.jobRates.entrySet()) {
            if (entry.getKey().equals(itemId.toString())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private void buy(ServerPlayer player, EconomyState economy, int index, boolean card) {
        HryvniaConfig.ProfessionEconomy professionEconomy = HryvniaConfig.INSTANCE.professionEconomy(professionId());
        if (professionEconomy == null || index < 0 || index >= professionEconomy.shop.size()) {
            return;
        }
        HryvniaConfig.ShopEntry entry = professionEconomy.shop.get(index);
        Item item = itemById(entry.item);
        if (item == null) {
            return;
        }
        long price = entry.price;
        if (card) {
            if (!CardHelper.hasOwnCard(player)) {
                player.displayClientMessage(Component.translatable("hryvnia.msg.no_card"), false);
                playSound(player, false);
                return;
            }
            if (economy.balance(player.getUUID()) < price) {
                player.displayClientMessage(Component.translatable("hryvnia.msg.not_enough_balance"), false);
                playSound(player, false);
                return;
            }
            economy.addBalance(player.getUUID(), -price);
        } else {
            if (!CashHelper.takeCash(player, price)) {
                player.displayClientMessage(Component.translatable("hryvnia.msg.not_enough_cash"), false);
                playSound(player, false);
                return;
            }
        }
        ItemStack bought = new ItemStack(item, Math.max(1, entry.count));
        player.getInventory().placeItemBackInInventory(bought);
        player.displayClientMessage(Component.translatable("hryvnia.msg.bought",
                Component.translatable(item.getDescriptionId()), String.valueOf(price)), false);
        playSound(player, true);
    }

    private static Item itemById(String id) {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.getValue(identifier);
        return item == Items.AIR ? null : item;
    }

    private void playSound(ServerPlayer player, boolean happy) {
        if (villager != null) {
            player.level().playSound(null, villager.blockPosition(),
                    happy ? SoundEvents.VILLAGER_YES : SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0F, 1.0F);
        }
    }

    private static Component jobName(String professionId) {
        return professionDisplayName(professionId);
    }

    public static Component professionDisplayName(String professionId) {
        Identifier id = Identifier.tryParse(professionId);
        String path = id != null ? id.getPath() : professionId;
        return Component.translatable("entity.minecraft.villager." + path);
    }

    // --- opening ---------------------------------------------------------

    public static void open(ServerPlayer player, Villager villager, String professionId) {
        HryvniaConfig.ProfessionEconomy professionEconomy = HryvniaConfig.INSTANCE.professionEconomy(professionId);
        if (professionEconomy == null) {
            return;
        }
        EconomyState economy = EconomyState.get();
        String job = economy != null && economy.account(player).job != null ? economy.account(player).job : "";

        List<PriceEntry> rates = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : professionEconomy.jobRates.entrySet()) {
            rates.add(new PriceEntry(entry.getKey(), 1, entry.getValue()));
        }
        List<PriceEntry> shop = new ArrayList<>();
        for (HryvniaConfig.ShopEntry entry : professionEconomy.shop) {
            shop.add(new PriceEntry(entry.item, entry.count, entry.price));
        }
        EmployerData data = new EmployerData(professionId, job, rates, shop);

        player.openMenu(new ExtendedMenuProvider<EmployerData>() {
            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player p) {
                return new EmployerMenu(containerId, inventory, villager, data);
            }

            @Override
            public Component getDisplayName() {
                return professionDisplayName(professionId);
            }

            @Override
            public EmployerData getScreenOpeningData(ServerPlayer p) {
                return data;
            }
        });
    }
}
