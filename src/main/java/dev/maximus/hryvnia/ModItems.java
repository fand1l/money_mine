package dev.maximus.hryvnia;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ModItems {
    /** denomination -> banknote item, in declaration order (1 .. 2000). */
    public static final Map<Integer, Item> BANKNOTES = new LinkedHashMap<>();
    /** item -> denomination, for fast wallet scans. */
    public static final Map<Item, Integer> BANKNOTE_VALUES = new LinkedHashMap<>();

    public static Item BANK_CARD;

    public static final ResourceKey<CreativeModeTab> TAB_KEY =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, HryvniaMod.id("hryvnia"));

    private ModItems() {
    }

    public static Item banknote(int denomination) {
        return BANKNOTES.get(denomination);
    }

    public static void register() {
        for (int denomination : new int[]{1, 2, 5, 10, 20, 50, 100, 200, 500, 1000, 2000}) {
            Item note = registerItem("banknote_" + denomination, new Item.Properties().stacksTo(64));
            BANKNOTES.put(denomination, note);
            BANKNOTE_VALUES.put(note, denomination);
        }
        BANK_CARD = registerItem("bank_card", new Item.Properties().stacksTo(1));

        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB_KEY, FabricCreativeModeTab.builder()
                .title(Component.translatable("itemGroup.hryvnia"))
                .icon(() -> new ItemStack(banknote(100)))
                .displayItems((context, output) -> {
                    for (Item note : BANKNOTES.values()) {
                        output.accept(note);
                    }
                    output.accept(BANK_CARD);
                })
                .build());
    }

    private static Item registerItem(String name, Item.Properties properties) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, HryvniaMod.id(name));
        Item item = new Item(properties.setId(key));
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }
}
