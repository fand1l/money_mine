package dev.maximus.hryvnia.network;

import dev.maximus.hryvnia.HryvniaMod;
import dev.maximus.hryvnia.menu.BankMenu;
import dev.maximus.hryvnia.menu.EmployerMenu;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public final class ModPayloads {
    private ModPayloads() {
    }

    /** Client -> server: actions inside the employer (job/shop) menu. */
    public record EmployerActionPayload(int action, int index, int qty, boolean card) implements CustomPacketPayload {
        public static final int HIRE = 0;
        public static final int QUIT = 1;
        public static final int SELL = 2;
        public static final int BUY = 3;

        public static final Type<EmployerActionPayload> TYPE = new Type<>(HryvniaMod.id("employer_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, EmployerActionPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, EmployerActionPayload::action,
                ByteBufCodecs.VAR_INT, EmployerActionPayload::index,
                ByteBufCodecs.VAR_INT, EmployerActionPayload::qty,
                ByteBufCodecs.BOOL, EmployerActionPayload::card,
                EmployerActionPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client -> server: actions inside the banker menu. */
    public record BankActionPayload(int action, long amount, String target) implements CustomPacketPayload {
        public static final int DEPOSIT = 0;
        public static final int WITHDRAW = 1;
        public static final int CARD = 2;
        public static final int TRANSFER = 3;

        public static final Type<BankActionPayload> TYPE = new Type<>(HryvniaMod.id("bank_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BankActionPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, BankActionPayload::action,
                ByteBufCodecs.VAR_LONG, BankActionPayload::amount,
                ByteBufCodecs.STRING_UTF8, BankActionPayload::target,
                BankActionPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server -> client: the player's current account balance (for tooltips and the bank screen). */
    public record BalancePayload(long balance) implements CustomPacketPayload {
        public static final Type<BalancePayload> TYPE = new Type<>(HryvniaMod.id("balance"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BalancePayload> CODEC =
                ByteBufCodecs.VAR_LONG.map(BalancePayload::new, BalancePayload::balance).cast();

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(EmployerActionPayload.TYPE, EmployerActionPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(BankActionPayload.TYPE, BankActionPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(BalancePayload.TYPE, BalancePayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(EmployerActionPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (player.containerMenu instanceof EmployerMenu menu && menu.stillValid(player)) {
                menu.handleAction(player, payload.action(), payload.index(), payload.qty(), payload.card());
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(BankActionPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (player.containerMenu instanceof BankMenu menu && menu.stillValid(player)) {
                menu.handleAction(player, payload.action(), payload.amount(), payload.target());
            }
        });
    }
}
