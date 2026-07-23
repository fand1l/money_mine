package dev.maximus.hryvnia.client.screen;

import dev.maximus.hryvnia.HryvniaMod;
import dev.maximus.hryvnia.menu.EmployerMenu;
import dev.maximus.hryvnia.network.ModPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen for working villagers: hand produce in for pay, hire/quit, and a
 * paginated shop. All texts are drawn during background extraction; actions
 * go to the server as EmployerActionPayload.
 */
public class EmployerScreen extends AbstractContainerScreen<EmployerMenu> {
    private static final Identifier TEXTURE = HryvniaMod.id("textures/gui/employer_bg.png");
    private static final int ROWS_PER_PAGE = 5;
    private static final int ROW_HEIGHT = 13;
    private static final int FIRST_ROW_Y = 78;

    private static final int COLOR_DARK = 0xFF3F3F3F;
    private static final int COLOR_ACCENT = 0xFF2E6B2E;

    private final List<SimpleButton> buyButtons = new ArrayList<>();
    private SimpleButton sellButton;
    private SimpleButton jobButton;
    private SimpleButton payButton;
    private SimpleButton pagePrev;
    private SimpleButton pageNext;

    private int page = 0;
    private boolean cardMode = false;
    /** Local view of the player's job, optimistically updated on clicks. */
    private String yourJob;

    public EmployerScreen(EmployerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 222;
        this.yourJob = menu.data().yourJob();
        this.inventoryLabelY = -1000; // hidden, the shop list needs the room
    }

    @Override
    protected void init() {
        super.init();
        buyButtons.clear();

        sellButton = addRenderableWidget(new SimpleButton(leftPos + 100, topPos + 26, 68, 18,
                Component.translatable("hryvnia.gui.sell"),
                button -> ClientPlayNetworking.send(new ModPayloads.EmployerActionPayload(
                        ModPayloads.EmployerActionPayload.SELL, 0, cardMode))));
        sellButton.setTooltip(Tooltip.create(ratesTooltip()));

        jobButton = addRenderableWidget(new SimpleButton(leftPos + 112, topPos + 46, 56, 16,
                Component.empty(), button -> onJobButton()));

        payButton = addRenderableWidget(new SimpleButton(leftPos + 8, topPos + 60, 86, 14,
                Component.empty(), button -> {
            cardMode = !cardMode;
            refresh();
        }));

        pagePrev = addRenderableWidget(new SimpleButton(leftPos + 140, topPos + 60, 12, 14,
                Component.literal("<"), button -> {
            page--;
            refresh();
        }));
        pageNext = addRenderableWidget(new SimpleButton(leftPos + 156, topPos + 60, 12, 14,
                Component.literal(">"), button -> {
            page++;
            refresh();
        }));

        for (int row = 0; row < ROWS_PER_PAGE; row++) {
            final int rowIndex = row;
            SimpleButton buy = addRenderableWidget(new SimpleButton(
                    leftPos + 132, topPos + FIRST_ROW_Y + row * ROW_HEIGHT - 1, 36, 12,
                    Component.translatable("hryvnia.gui.buy"),
                    button -> ClientPlayNetworking.send(new ModPayloads.EmployerActionPayload(
                            ModPayloads.EmployerActionPayload.BUY, page * ROWS_PER_PAGE + rowIndex, cardMode))));
            buyButtons.add(buy);
        }

        refresh();
    }

    private void onJobButton() {
        if (yourJob.isEmpty()) {
            ClientPlayNetworking.send(new ModPayloads.EmployerActionPayload(
                    ModPayloads.EmployerActionPayload.HIRE, 0, false));
            yourJob = menu.professionId();
        } else if (yourJob.equals(menu.professionId())) {
            ClientPlayNetworking.send(new ModPayloads.EmployerActionPayload(
                    ModPayloads.EmployerActionPayload.QUIT, 0, false));
            yourJob = "";
        }
        refresh();
    }

    private void refresh() {
        int entries = menu.data().shop().size();
        int pages = Math.max(1, (entries + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        page = Math.max(0, Math.min(page, pages - 1));
        pagePrev.active = page > 0;
        pageNext.active = page < pages - 1;

        for (int row = 0; row < ROWS_PER_PAGE; row++) {
            buyButtons.get(row).visible = page * ROWS_PER_PAGE + row < entries;
        }

        payButton.setMessage(Component.translatable(cardMode ? "hryvnia.gui.pay_card" : "hryvnia.gui.pay_cash"));

        boolean employedHere = yourJob.equals(menu.professionId());
        if (yourJob.isEmpty()) {
            jobButton.setMessage(Component.translatable("hryvnia.gui.hire"));
            jobButton.active = true;
        } else if (employedHere) {
            jobButton.setMessage(Component.translatable("hryvnia.gui.quit"));
            jobButton.active = true;
        } else {
            jobButton.setMessage(Component.translatable("hryvnia.gui.busy"));
            jobButton.active = false;
        }
        sellButton.active = employedHere;
    }

    private Component ratesTooltip() {
        var tooltip = Component.translatable("hryvnia.gui.rates_header");
        for (EmployerMenu.PriceEntry entry : menu.data().rates()) {
            tooltip.append(Component.literal("\n"))
                    .append(itemName(entry.itemId()))
                    .append(Component.literal(" — " + entry.price() + " ₴"));
        }
        return tooltip;
    }

    private static Component itemName(String id) {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier != null) {
            Item item = BuiltInRegistries.ITEM.getValue(identifier);
            if (item != Items.AIR) {
                return Component.translatable(item.getDescriptionId());
            }
        }
        return Component.literal(id);
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

        graphics.text(font, Component.translatable("hryvnia.gui.sell_hint").getString(), x + 8, y + 19, COLOR_DARK, false);

        String jobLine = yourJob.isEmpty()
                ? Component.translatable("hryvnia.gui.no_job").getString()
                : Component.translatable("hryvnia.gui.your_job",
                        EmployerMenu.professionDisplayName(yourJob).getString()).getString();
        graphics.text(font, trim(jobLine, 100), x + 8, y + 50, COLOR_DARK, false);

        List<EmployerMenu.PriceEntry> shop = menu.data().shop();
        int pages = Math.max(1, (shop.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        String header = Component.translatable("hryvnia.gui.shop").getString() + " " + (page + 1) + "/" + pages;
        graphics.text(font, header, x + 98, y + 63, COLOR_DARK, false);

        for (int row = 0; row < ROWS_PER_PAGE; row++) {
            int index = page * ROWS_PER_PAGE + row;
            if (index >= shop.size()) {
                break;
            }
            EmployerMenu.PriceEntry entry = shop.get(index);
            String line = (entry.count() > 1 ? entry.count() + "× " : "") + itemName(entry.itemId()).getString();
            graphics.text(font, trim(line, 82), x + 8, y + FIRST_ROW_Y + row * ROW_HEIGHT + 1, COLOR_DARK, false);
            String price = entry.price() + "₴";
            graphics.text(font, price, x + 128 - font.width(price), y + FIRST_ROW_Y + row * ROW_HEIGHT + 1, COLOR_ACCENT, false);
        }
    }

    private String trim(String text, int maxWidth) {
        String result = text;
        while (font.width(result) > maxWidth && result.length() > 1) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
