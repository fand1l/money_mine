package dev.maximus.hryvnia.client.screen;

import dev.maximus.hryvnia.HryvniaMod;
import dev.maximus.hryvnia.ModItems;
import dev.maximus.hryvnia.client.ClientEconomy;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Screen for working villagers: hand produce in for pay, hire/quit, and a
 * paginated shop. Each shop row shows an item icon plus a −/+ stepper set
 * (1/5/10/16/32/64) flanking a buy button that carries the total price; steps
 * that would exceed the item's stack size (or drop below 1) are disabled.
 */
public class EmployerScreen extends AbstractContainerScreen<EmployerMenu> {
    private static final Identifier TEXTURE = HryvniaMod.id("textures/gui/employer_bg.png");
    private static final int ROWS_PER_PAGE = 4;
    private static final int ROW_HEIGHT = 14;
    private static final int FIRST_ROW_Y = 80;
    private static final int STEP_W = 20;
    private static final int[] MINUS_STEPS = {64, 32, 16, 10, 5, 1};
    private static final int[] PLUS_STEPS = {1, 5, 10, 16, 32, 64};

    private static final int COLOR_DARK = 0xFF3F3F3F;
    private static final int COLOR_ACCENT = 0xFF2E6B2E;

    private final List<SimpleButton> buyButtons = new ArrayList<>();
    private final SimpleButton[][] minusButtons = new SimpleButton[ROWS_PER_PAGE][MINUS_STEPS.length];
    private final SimpleButton[][] plusButtons = new SimpleButton[ROWS_PER_PAGE][PLUS_STEPS.length];
    private SimpleButton sellButton;
    private SimpleButton jobButton;
    private SimpleButton payButton;
    private SimpleButton pagePrev;
    private SimpleButton pageNext;

    private int page = 0;
    private boolean cardMode = false;
    /** Local view of the player's job, optimistically updated on clicks. */
    private String yourJob;

    /** Pre-resolved base stacks (count 1) for the shop entries, aligned by index. */
    private final List<ItemStack> shopStacks = new ArrayList<>();
    /** shop index -> chosen purchase quantity (defaults to the entry's pack size). */
    private final Map<Integer, Integer> quantities = new HashMap<>();
    /** item id -> pay rate, for the live sell estimate. */
    private final Map<String, Integer> rateByItem = new HashMap<>();

    public EmployerScreen(EmployerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, EmployerMenu.WIDTH, 222);
        this.yourJob = menu.data().yourJob();
        this.inventoryLabelY = -1000; // hidden, the shop list needs the room

        for (EmployerMenu.PriceEntry entry : menu.data().shop()) {
            shopStacks.add(baseStack(entry));
        }
        for (EmployerMenu.PriceEntry entry : menu.data().rates()) {
            rateByItem.put(entry.itemId(), entry.price());
        }
    }

    private static ItemStack baseStack(EmployerMenu.PriceEntry entry) {
        Identifier identifier = Identifier.tryParse(entry.itemId());
        Item item = identifier != null ? BuiltInRegistries.ITEM.getValue(identifier) : Items.AIR;
        if (item == Items.AIR) {
            item = Items.BARRIER;
        }
        return new ItemStack(item);
    }

    private int maxStack(int index) {
        return Math.max(1, shopStacks.get(index).getMaxStackSize());
    }

    private int quantity(int index) {
        return quantities.computeIfAbsent(index, i -> {
            int packCount = menu.data().shop().get(i).count();
            return Math.max(1, Math.min(maxStack(i), packCount));
        });
    }

    private long priceOf(int index, int qty) {
        EmployerMenu.PriceEntry entry = menu.data().shop().get(index);
        return EmployerMenu.priceFor(entry.price(), entry.count(), qty);
    }

    private void adjust(int index, int delta) {
        int next = Math.max(1, Math.min(maxStack(index), quantity(index) + delta));
        quantities.put(index, next);
        refresh();
    }

    @Override
    protected void init() {
        super.init();
        buyButtons.clear();

        sellButton = addRenderableWidget(new SimpleButton(leftPos + 100, topPos + 26, 68, 18,
                Component.translatable("hryvnia.gui.sell"),
                button -> ClientPlayNetworking.send(new ModPayloads.EmployerActionPayload(
                        ModPayloads.EmployerActionPayload.SELL, 0, 0, cardMode))));
        sellButton.setTooltip(Tooltip.create(ratesTooltip()));

        jobButton = addRenderableWidget(new SimpleButton(leftPos + 110, topPos + 46, 58, 16,
                Component.empty(), button -> onJobButton()));

        payButton = addRenderableWidget(new SimpleButton(leftPos + 8, topPos + 64, 86, 14,
                Component.empty(), button -> {
            cardMode = !cardMode;
            refresh();
        }));

        pagePrev = addRenderableWidget(new SimpleButton(leftPos + 98, topPos + 64, 14, 14,
                Component.literal("<"), button -> {
            page--;
            refresh();
        }));
        pageNext = addRenderableWidget(new SimpleButton(leftPos + 154, topPos + 64, 14, 14,
                Component.literal(">"), button -> {
            page++;
            refresh();
        }));

        for (int row = 0; row < ROWS_PER_PAGE; row++) {
            final int rowIndex = row;
            int rowY = topPos + FIRST_ROW_Y + row * ROW_HEIGHT;

            for (int i = 0; i < MINUS_STEPS.length; i++) {
                final int step = MINUS_STEPS[i];
                minusButtons[row][i] = addRenderableWidget(new SimpleButton(
                        leftPos + 26 + i * STEP_W, rowY, STEP_W, 13,
                        Component.literal("-" + step),
                        button -> adjust(page * ROWS_PER_PAGE + rowIndex, -step)));
            }

            SimpleButton buy = addRenderableWidget(new SimpleButton(
                    leftPos + 148, rowY, 58, 13, Component.empty(),
                    button -> {
                        int index = page * ROWS_PER_PAGE + rowIndex;
                        ClientPlayNetworking.send(new ModPayloads.EmployerActionPayload(
                                ModPayloads.EmployerActionPayload.BUY, index, quantity(index), cardMode));
                    }));
            buyButtons.add(buy);

            for (int i = 0; i < PLUS_STEPS.length; i++) {
                final int step = PLUS_STEPS[i];
                plusButtons[row][i] = addRenderableWidget(new SimpleButton(
                        leftPos + 208 + i * STEP_W, rowY, STEP_W, 13,
                        Component.literal("+" + step),
                        button -> adjust(page * ROWS_PER_PAGE + rowIndex, step)));
            }
        }

        refresh();
    }

    private void onJobButton() {
        if (yourJob.isEmpty()) {
            ClientPlayNetworking.send(new ModPayloads.EmployerActionPayload(
                    ModPayloads.EmployerActionPayload.HIRE, 0, 0, false));
            yourJob = menu.professionId();
        } else if (yourJob.equals(menu.professionId())) {
            ClientPlayNetworking.send(new ModPayloads.EmployerActionPayload(
                    ModPayloads.EmployerActionPayload.QUIT, 0, 0, false));
            yourJob = "";
        }
        refresh();
    }

    private void refresh() {
        List<EmployerMenu.PriceEntry> shop = menu.data().shop();
        int entries = shop.size();
        int pages = Math.max(1, (entries + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        page = Math.max(0, Math.min(page, pages - 1));
        pagePrev.active = page > 0;
        pageNext.active = page < pages - 1;
        pagePrev.visible = pages > 1;
        pageNext.visible = pages > 1;

        for (int row = 0; row < ROWS_PER_PAGE; row++) {
            int index = page * ROWS_PER_PAGE + row;
            boolean visible = index < entries;
            buyButtons.get(row).visible = visible;
            for (int i = 0; i < MINUS_STEPS.length; i++) {
                minusButtons[row][i].visible = visible;
                plusButtons[row][i].visible = visible;
            }
            if (visible) {
                int qty = quantity(index);
                int max = maxStack(index);
                buyButtons.get(row).setMessage(Component.literal(priceOf(index, qty) + " ₴"));
                for (int i = 0; i < MINUS_STEPS.length; i++) {
                    minusButtons[row][i].active = qty - MINUS_STEPS[i] >= 1;
                    plusButtons[row][i].active = qty + PLUS_STEPS[i] <= max;
                }
            }
        }

        if (!menu.data().acceptsCard()) {
            cardMode = false;
            payButton.active = false;
            payButton.setMessage(Component.translatable("hryvnia.gui.only_cash"));
        } else {
            payButton.setMessage(Component.translatable(cardMode ? "hryvnia.gui.pay_card" : "hryvnia.gui.pay_cash"));
        }

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

    /** Live payout estimate for whatever is in the sell slots right now. */
    private long sellEstimate() {
        long total = 0;
        for (int i = 0; i < EmployerMenu.SELL_SLOTS; i++) {
            ItemStack stack = menu.slots.get(i).getItem();
            if (stack.isEmpty()) {
                continue;
            }
            Integer rate = rateByItem.get(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            if (rate != null) {
                total += (long) rate * stack.getCount();
            }
        }
        return total;
    }

    /** Cash the player is carrying, counted from the synced menu slots. */
    private long cashOnHand() {
        long total = 0;
        for (int i = EmployerMenu.SELL_SLOTS; i < menu.slots.size(); i++) {
            ItemStack stack = menu.slots.get(i).getItem();
            Integer value = ModItems.BANKNOTE_VALUES.get(stack.getItem());
            if (value != null) {
                total += (long) value * stack.getCount();
            }
        }
        return total;
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

        // Sell header with a live payout estimate.
        long estimate = sellEstimate();
        String sellHeader = estimate > 0
                ? Component.translatable("hryvnia.gui.sell_estimate", String.valueOf(estimate)).getString()
                : Component.translatable("hryvnia.gui.sell_hint").getString();
        graphics.text(font, sellHeader, x + 8, y + 18, estimate > 0 ? COLOR_ACCENT : COLOR_DARK, false);

        String jobLine = yourJob.isEmpty()
                ? Component.translatable("hryvnia.gui.no_job").getString()
                : Component.translatable("hryvnia.gui.your_job",
                        EmployerMenu.professionDisplayName(yourJob).getString()).getString();
        graphics.text(font, trim(jobLine, 98), x + 8, y + 50, COLOR_DARK, false);

        // Wallet tooltip on the payment toggle, updated live.
        payButton.setTooltip(Tooltip.create(
                Component.translatable("hryvnia.gui.wallet", String.valueOf(cashOnHand()))
                        .append(Component.literal("\n"))
                        .append(Component.translatable("hryvnia.gui.balance", String.valueOf(ClientEconomy.balance)))));

        // Pager: page number centered between the arrow buttons.
        List<EmployerMenu.PriceEntry> shop = menu.data().shop();
        int pages = Math.max(1, (shop.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        if (pages > 1) {
            String pageText = (page + 1) + "/" + pages;
            graphics.text(font, pageText, x + 133 - font.width(pageText) / 2, y + 67, COLOR_DARK, false);
        }

        // Shop rows: item icon with a quantity badge; steppers/buy are widgets.
        for (int row = 0; row < ROWS_PER_PAGE; row++) {
            int index = page * ROWS_PER_PAGE + row;
            if (index >= shop.size()) {
                break;
            }
            int qty = quantity(index);
            ItemStack display = new ItemStack(shopStacks.get(index).getItem(), qty);
            int iconX = x + 8;
            int iconY = y + FIRST_ROW_Y + row * ROW_HEIGHT - 1;
            graphics.item(display, iconX, iconY);
            graphics.itemDecorations(font, display, iconX, iconY);

            if (mouseX >= iconX && mouseX < iconX + 16 && mouseY >= iconY && mouseY < iconY + 16) {
                Component hover = itemName(shop.get(index).itemId()).copy()
                        .append(Component.literal("\n"))
                        .append(Component.translatable("hryvnia.gui.buy_tooltip",
                                String.valueOf(qty), String.valueOf(priceOf(index, qty))));
                graphics.setTooltipForNextFrame(font, hover, mouseX, mouseY);
            }
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
