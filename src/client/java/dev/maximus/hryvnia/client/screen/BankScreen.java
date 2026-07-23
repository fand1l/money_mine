package dev.maximus.hryvnia.client.screen;

import dev.maximus.hryvnia.HryvniaMod;
import dev.maximus.hryvnia.client.ClientEconomy;
import dev.maximus.hryvnia.menu.BankMenu;
import dev.maximus.hryvnia.network.ModPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * The banker screen: deposit slots, withdrawals, card issuance and transfers
 * by card number. The live balance comes from BalancePayload via ClientEconomy.
 */
public class BankScreen extends AbstractContainerScreen<BankMenu> {
    private static final Identifier TEXTURE = HryvniaMod.id("textures/gui/bank_bg.png");

    private static final int COLOR_DARK = 0xFF3F3F3F;
    private static final int COLOR_ACCENT = 0xFF2E6B2E;

    private EditBox withdrawAmount;
    private EditBox transferCard;
    private EditBox transferAmount;

    public BankScreen(BankMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 222;
        ClientEconomy.balance = menu.data().balance();
    }

    @Override
    protected void init() {
        super.init();

        addRenderableWidget(new SimpleButton(leftPos + 112, topPos + 46, 56, 16,
                Component.translatable("hryvnia.gui.deposit"),
                button -> ClientPlayNetworking.send(new ModPayloads.BankActionPayload(
                        ModPayloads.BankActionPayload.DEPOSIT, 0, ""))));

        withdrawAmount = new EditBox(font, leftPos + 8, topPos + 66, 58, 14,
                Component.translatable("hryvnia.gui.amount_hint"));
        withdrawAmount.setMaxLength(10);
        withdrawAmount.setFilter(text -> text.matches("[0-9]*"));
        addRenderableWidget(withdrawAmount);

        addRenderableWidget(new SimpleButton(leftPos + 70, topPos + 65, 46, 16,
                Component.translatable("hryvnia.gui.withdraw"),
                button -> {
                    long amount = parseAmount(withdrawAmount.getValue());
                    if (amount > 0) {
                        ClientPlayNetworking.send(new ModPayloads.BankActionPayload(
                                ModPayloads.BankActionPayload.WITHDRAW, amount, ""));
                    }
                }));

        addRenderableWidget(new SimpleButton(leftPos + 120, topPos + 65, 48, 16,
                Component.translatable("hryvnia.gui.get_card"),
                button -> ClientPlayNetworking.send(new ModPayloads.BankActionPayload(
                        ModPayloads.BankActionPayload.CARD, 0, ""))));

        transferCard = new EditBox(font, leftPos + 8, topPos + 96, 160, 14,
                Component.translatable("hryvnia.gui.card_number_hint"));
        transferCard.setMaxLength(19);
        transferCard.setFilter(text -> text.matches("[0-9 ]*"));
        addRenderableWidget(transferCard);

        transferAmount = new EditBox(font, leftPos + 8, topPos + 114, 58, 14,
                Component.translatable("hryvnia.gui.amount_hint"));
        transferAmount.setMaxLength(10);
        transferAmount.setFilter(text -> text.matches("[0-9]*"));
        addRenderableWidget(transferAmount);

        addRenderableWidget(new SimpleButton(leftPos + 70, topPos + 113, 98, 16,
                Component.translatable("hryvnia.gui.transfer"),
                button -> {
                    long amount = parseAmount(transferAmount.getValue());
                    String card = transferCard.getValue();
                    if (amount > 0 && !card.isBlank()) {
                        ClientPlayNetworking.send(new ModPayloads.BankActionPayload(
                                ModPayloads.BankActionPayload.TRANSFER, amount, card));
                    }
                }));
    }

    private static long parseAmount(String text) {
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        extractBackground(graphics, mouseX, mouseY, delta);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        extractTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);

        graphics.text(font, Component.translatable("hryvnia.gui.deposit_hint").getString(), x + 8, y + 19, COLOR_DARK, false);

        String balance = Component.translatable("hryvnia.gui.balance", String.valueOf(ClientEconomy.balance)).getString();
        graphics.text(font, balance, x + 8, y + 51, COLOR_ACCENT, false);

        graphics.text(font, Component.translatable("hryvnia.gui.transfer_hint").getString(), x + 8, y + 87, COLOR_DARK, false);
    }
}
