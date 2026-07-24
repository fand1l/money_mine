package dev.maximus.hryvnia.menu;

import dev.maximus.hryvnia.ModMenus;
import dev.maximus.hryvnia.economy.CardHelper;
import dev.maximus.hryvnia.economy.CashHelper;
import dev.maximus.hryvnia.economy.EconomyState;
import dev.maximus.hryvnia.economy.HryvniaConfig;
import dev.maximus.hryvnia.economy.Market;
import dev.maximus.hryvnia.network.ModPayloads;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.UUID;

/**
 * The banker menu: deposit banknotes (and emeralds as scrap), withdraw cash,
 * get your personal bank card and wire money to any card number on the server.
 */
public class BankMenu extends AbstractContainerMenu {
    public static final int DEPOSIT_SLOTS = 9;

    /** The bank's public conditions, shown in the UI and enforced server-side. */
    public record BankTerms(int emeraldScrap, long cardPrice, long balanceLimit, long dailyLimit,
                            float withdrawFeePercent, float transferFeePercent) {
        public static final StreamCodec<RegistryFriendlyByteBuf, BankTerms> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, BankTerms::emeraldScrap,
                ByteBufCodecs.VAR_LONG, BankTerms::cardPrice,
                ByteBufCodecs.VAR_LONG, BankTerms::balanceLimit,
                ByteBufCodecs.VAR_LONG, BankTerms::dailyLimit,
                ByteBufCodecs.FLOAT, BankTerms::withdrawFeePercent,
                ByteBufCodecs.FLOAT, BankTerms::transferFeePercent,
                BankTerms::new);

        public static BankTerms fromConfig() {
            HryvniaConfig config = HryvniaConfig.INSTANCE;
            return new BankTerms(config.emeraldScrapValue, config.cardPrice, config.cardBalanceLimit,
                    config.transferDailyLimit, (float) config.withdrawFeePercent, (float) config.transferFeePercent);
        }
    }

    public record BankData(long balance, String cardNumber, BankTerms terms) {
        public static final StreamCodec<RegistryFriendlyByteBuf, BankData> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, BankData::balance,
                ByteBufCodecs.STRING_UTF8, BankData::cardNumber,
                BankTerms.STREAM_CODEC, BankData::terms,
                BankData::new);
    }

    private final Container depositContainer = new SimpleContainer(DEPOSIT_SLOTS);
    private final Villager villager;
    private final BankData data;

    /** Client-side constructor used by the ExtendedMenuType factory. */
    public BankMenu(int containerId, Inventory playerInventory, BankData data) {
        this(containerId, playerInventory, null, data);
    }

    public BankMenu(int containerId, Inventory playerInventory, Villager villager, BankData data) {
        super(ModMenus.BANK, containerId);
        this.villager = villager;
        this.data = data;

        for (int i = 0; i < DEPOSIT_SLOTS; i++) {
            this.addSlot(new Slot(depositContainer, i, 8 + i * 18, 30) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return CashHelper.noteValue(stack) > 0 || stack.getItem() == Items.EMERALD;
                }
            });
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

    public BankData data() {
        return data;
    }

    @Override
    public boolean stillValid(Player player) {
        return villager == null || (villager.isAlive() && player.distanceToSqr(villager) <= 64.0);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.clearContainer(player, depositContainer);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(slotIndex);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (slotIndex < DEPOSIT_SLOTS) {
                if (!this.moveItemStackTo(stack, DEPOSIT_SLOTS, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                boolean depositable = CashHelper.noteValue(stack) > 0 || stack.getItem() == Items.EMERALD;
                if (!depositable || !this.moveItemStackTo(stack, 0, DEPOSIT_SLOTS, false)) {
                    return ItemStack.EMPTY;
                }
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

    public void handleAction(ServerPlayer player, int action, long amount, String target) {
        EconomyState economy = EconomyState.get();
        if (economy == null) {
            return;
        }
        switch (action) {
            case ModPayloads.BankActionPayload.DEPOSIT -> deposit(player, economy);
            case ModPayloads.BankActionPayload.WITHDRAW -> withdraw(player, economy, amount);
            case ModPayloads.BankActionPayload.CARD -> issueCard(player, economy);
            case ModPayloads.BankActionPayload.TRANSFER -> transfer(player, economy, target, amount);
            default -> {
            }
        }
    }

    private void deposit(ServerPlayer player, EconomyState economy) {
        HryvniaConfig config = HryvniaConfig.INSTANCE;
        long total = 0;
        for (int i = 0; i < depositContainer.getContainerSize(); i++) {
            ItemStack stack = depositContainer.getItem(i);
            int noteValue = CashHelper.noteValue(stack);
            if (noteValue > 0) {
                total += (long) noteValue * stack.getCount();
            } else if (stack.getItem() == Items.EMERALD) {
                total += (long) config.emeraldScrapValue * stack.getCount();
            }
        }
        if (total <= 0) {
            player.sendSystemMessage(Component.translatable("hryvnia.msg.nothing_to_deposit"));
            playSound(player, false);
            return;
        }
        long fee = Market.fee(total, config.depositFeePercent);
        long credited = total - fee;
        if (config.cardBalanceLimit > 0 && economy.balance(player.getUUID()) + credited > config.cardBalanceLimit) {
            player.sendSystemMessage(Component.translatable("hryvnia.msg.deposit_limit",
                    String.valueOf(config.cardBalanceLimit)));
            playSound(player, false);
            return;
        }
        for (int i = 0; i < depositContainer.getContainerSize(); i++) {
            ItemStack stack = depositContainer.getItem(i);
            if (CashHelper.noteValue(stack) > 0 || stack.getItem() == Items.EMERALD) {
                depositContainer.setItem(i, ItemStack.EMPTY);
            }
        }
        economy.addBalance(player.getUUID(), credited);
        this.broadcastChanges();
        if (fee > 0) {
            player.sendSystemMessage(Component.translatable("hryvnia.msg.deposited_fee",
                    String.valueOf(credited), String.valueOf(fee)));
        } else {
            player.sendSystemMessage(Component.translatable("hryvnia.msg.deposited", String.valueOf(credited)));
        }
        playSound(player, true);
    }

    private void withdraw(ServerPlayer player, EconomyState economy, long amount) {
        if (amount <= 0) {
            return;
        }
        long fee = Market.fee(amount, HryvniaConfig.INSTANCE.withdrawFeePercent);
        if (economy.balance(player.getUUID()) < amount + fee) {
            player.sendSystemMessage(Component.translatable("hryvnia.msg.not_enough_balance"));
            playSound(player, false);
            return;
        }
        economy.addBalance(player.getUUID(), -(amount + fee));
        CashHelper.giveCash(player, amount);
        if (fee > 0) {
            player.sendSystemMessage(Component.translatable("hryvnia.msg.withdrawn_fee",
                    String.valueOf(amount), String.valueOf(fee)));
        } else {
            player.sendSystemMessage(Component.translatable("hryvnia.msg.withdrawn", String.valueOf(amount)));
        }
        playSound(player, true);
    }

    private void issueCard(ServerPlayer player, EconomyState economy) {
        if (CardHelper.hasOwnCard(player)) {
            player.sendSystemMessage(Component.translatable("hryvnia.msg.have_card"));
            playSound(player, false);
            return;
        }
        long price = HryvniaConfig.INSTANCE.cardPrice;
        if (price > 0 && !CashHelper.takeCash(player, price)) {
            player.sendSystemMessage(Component.translatable("hryvnia.msg.card_price", String.valueOf(price)));
            playSound(player, false);
            return;
        }
        String number = economy.getOrCreateCard(player);
        ItemStack card = CardHelper.createCard(number, player.getUUID(), player.getName().getString());
        player.getInventory().placeItemBackInInventory(card);
        if (price > 0) {
            player.sendSystemMessage(Component.translatable("hryvnia.msg.card_issued_paid",
                    number, String.valueOf(price)));
        } else {
            player.sendSystemMessage(Component.translatable("hryvnia.msg.card_issued", number));
        }
        playSound(player, true);
    }

    private void transfer(ServerPlayer player, EconomyState economy, String target, long amount) {
        if (amount <= 0) {
            return;
        }
        String number = EconomyState.normalizeCardInput(target);
        if (number == null) {
            player.sendSystemMessage(Component.translatable("hryvnia.msg.card_not_found"));
            playSound(player, false);
            return;
        }
        EconomyState.TransferOutcome outcome = economy.transfer(player, number, amount);
        switch (outcome.result()) {
            case OK -> {
                if (outcome.fee() > 0) {
                    player.sendSystemMessage(Component.translatable("hryvnia.msg.transfer_sent_fee",
                            String.valueOf(amount), number, String.valueOf(outcome.fee())));
                } else {
                    player.sendSystemMessage(Component.translatable("hryvnia.msg.transfer_sent",
                            String.valueOf(amount), number));
                }
                UUID recipient = economy.ownerOfCard(number);
                if (recipient != null && economy.server() != null) {
                    ServerPlayer online = economy.server().getPlayerList().getPlayer(recipient);
                    if (online != null) {
                        online.sendSystemMessage(Component.translatable("hryvnia.msg.transfer_received",
                                String.valueOf(amount), player.getName().getString()));
                    }
                }
                playSound(player, true);
            }
            case NO_SUCH_CARD -> {
                player.sendSystemMessage(Component.translatable("hryvnia.msg.card_not_found"));
                playSound(player, false);
            }
            case NOT_ENOUGH_MONEY -> {
                player.sendSystemMessage(Component.translatable("hryvnia.msg.not_enough_balance"));
                playSound(player, false);
            }
            case SELF -> {
                player.sendSystemMessage(Component.translatable("hryvnia.msg.transfer_self"));
                playSound(player, false);
            }
            case DAILY_LIMIT -> {
                player.sendSystemMessage(Component.translatable("hryvnia.msg.transfer_limit",
                        String.valueOf(HryvniaConfig.INSTANCE.transferDailyLimit)));
                playSound(player, false);
            }
            case RECIPIENT_FULL -> {
                player.sendSystemMessage(Component.translatable("hryvnia.msg.transfer_recipient_full",
                        String.valueOf(HryvniaConfig.INSTANCE.cardBalanceLimit)));
                playSound(player, false);
            }
        }
    }

    private void playSound(ServerPlayer player, boolean happy) {
        if (villager != null) {
            player.level().playSound(null, villager.blockPosition(),
                    happy ? SoundEvents.VILLAGER_YES : SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0F, 1.0F);
        }
    }

    // --- opening ---------------------------------------------------------

    public static void open(ServerPlayer player, Villager villager) {
        EconomyState economy = EconomyState.get();
        if (economy == null) {
            return;
        }
        EconomyState.Account account = economy.account(player);
        BankData data = new BankData(account.balance, account.card != null ? account.card : "",
                BankTerms.fromConfig());

        player.openMenu(new ExtendedMenuProvider<BankData>() {
            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player p) {
                return new BankMenu(containerId, inventory, villager, data);
            }

            @Override
            public Component getDisplayName() {
                return Component.translatable("entity.hryvnia.banker");
            }

            @Override
            public BankData getScreenOpeningData(ServerPlayer p) {
                return data;
            }
        });
    }
}
