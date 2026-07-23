package dev.maximus.hryvnia.command;

import com.mojang.brigadier.arguments.LongArgumentType;
import dev.maximus.hryvnia.economy.EconomyState;
import dev.maximus.hryvnia.economy.HryvniaConfig;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The only command in the mod, admin-only (everything player-facing goes
 * through villagers): /hryvnia reload | balance <player> [set|add <amount>].
 */
public final class HryvniaCommand {
    private HryvniaCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> {
            dispatcher.register(Commands.literal("hryvnia")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.literal("reload").executes(context -> {
                        HryvniaConfig.load();
                        context.getSource().sendSuccess(() -> Component.translatable("hryvnia.msg.config_reloaded"), true);
                        return 1;
                    }))
                    .then(Commands.literal("balance")
                            .then(Commands.argument("player", EntityArgument.player())
                                    .executes(context -> {
                                        ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                        long balance = balanceOf(target);
                                        context.getSource().sendSuccess(() -> Component.literal(
                                                target.getGameProfile().getName() + ": " + balance + " ₴"), false);
                                        return (int) Math.min(Integer.MAX_VALUE, balance);
                                    })
                                    .then(Commands.literal("set")
                                            .then(Commands.argument("amount", LongArgumentType.longArg(0))
                                                    .executes(context -> {
                                                        ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                                        long amount = LongArgumentType.getLong(context, "amount");
                                                        EconomyState economy = EconomyState.get();
                                                        if (economy != null) {
                                                            economy.account(target);
                                                            economy.setBalance(target.getUUID(), amount);
                                                        }
                                                        sendBalance(context.getSource(), target);
                                                        return 1;
                                                    })))
                                    .then(Commands.literal("add")
                                            .then(Commands.argument("amount", LongArgumentType.longArg())
                                                    .executes(context -> {
                                                        ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                                        long amount = LongArgumentType.getLong(context, "amount");
                                                        EconomyState economy = EconomyState.get();
                                                        if (economy != null) {
                                                            economy.account(target);
                                                            economy.addBalance(target.getUUID(), amount);
                                                        }
                                                        sendBalance(context.getSource(), target);
                                                        return 1;
                                                    }))))));
        });
    }

    private static long balanceOf(ServerPlayer player) {
        EconomyState economy = EconomyState.get();
        return economy != null ? economy.balance(player.getUUID()) : 0;
    }

    private static void sendBalance(CommandSourceStack source, ServerPlayer target) {
        long balance = balanceOf(target);
        source.sendSuccess(() -> Component.literal(
                target.getGameProfile().getName() + ": " + balance + " ₴"), true);
    }
}
