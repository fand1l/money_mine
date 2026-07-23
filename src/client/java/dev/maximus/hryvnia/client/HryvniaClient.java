package dev.maximus.hryvnia.client;

import dev.maximus.hryvnia.ModItems;
import dev.maximus.hryvnia.ModMenus;
import dev.maximus.hryvnia.client.screen.BankScreen;
import dev.maximus.hryvnia.client.screen.EmployerScreen;
import dev.maximus.hryvnia.economy.CardHelper;
import dev.maximus.hryvnia.network.ModPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.chat.Component;

public class HryvniaClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MenuScreens.register(ModMenus.EMPLOYER, EmployerScreen::new);
        MenuScreens.register(ModMenus.BANK, BankScreen::new);

        ClientPlayNetworking.registerGlobalReceiver(ModPayloads.BalancePayload.TYPE,
                (payload, context) -> ClientEconomy.balance = payload.balance());

        ItemTooltipCallback.EVENT.register((stack, tooltipContext, tooltipFlag, lines) -> {
            Integer value = ModItems.BANKNOTE_VALUES.get(stack.getItem());
            if (value != null) {
                if (stack.getCount() > 1) {
                    lines.add(Component.translatable("hryvnia.tooltip.total",
                            String.valueOf((long) value * stack.getCount())).withStyle(ChatFormatting.GREEN));
                }
                lines.add(Component.translatable("hryvnia.tooltip.currency").withStyle(ChatFormatting.GRAY));
                return;
            }
            if (CardHelper.isCard(stack)) {
                String number = CardHelper.cardNumber(stack);
                String owner = CardHelper.ownerName(stack);
                if (!number.isEmpty()) {
                    lines.add(Component.literal(number).withStyle(ChatFormatting.AQUA));
                }
                if (!owner.isEmpty()) {
                    lines.add(Component.translatable("hryvnia.tooltip.owner", owner).withStyle(ChatFormatting.GRAY));
                }
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.player != null
                        && minecraft.player.getUUID().toString().equals(CardHelper.ownerUuidString(stack))) {
                    lines.add(Component.translatable("hryvnia.tooltip.balance",
                            String.valueOf(ClientEconomy.balance)).withStyle(ChatFormatting.GREEN));
                }
            }
        });
    }
}
