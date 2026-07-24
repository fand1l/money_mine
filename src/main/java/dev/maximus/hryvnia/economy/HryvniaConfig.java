package dev.maximus.hryvnia.economy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.maximus.hryvnia.HryvniaMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON config stored at config/hryvnia.json. Everything the server owner may
 * want to tune lives here: banker spawn chance, emerald scrap value, and the
 * per-profession job rates (what villagers pay you per item) and shop offers
 * (what you can buy from them).
 */
public class HryvniaConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static volatile HryvniaConfig INSTANCE = new HryvniaConfig();

    /** Chance that a freshly spawned jobless adult villager becomes a banker. */
    public double bankerSpawnChance = 0.15;
    /** How many hryvnias a banker pays for one emerald handed in as scrap. */
    public int emeraldScrapValue = 5;
    /** Percent of working villagers that accept card payments (the rest dodge taxes). */
    public int cardAcceptancePercent = 90;
    /** Exponential price bias k: higher = expensive end of the range even more likely. */
    public double priceBias = 2.0;
    /** One-time price of a bank card, paid in cash. */
    public long cardPrice = 10000;
    /** Maximum balance a card can hold. 0 disables the limit. */
    public long cardBalanceLimit = 100000;
    /** Max hryvnias one player can wire to another per real-world day. 0 disables. */
    public long transferDailyLimit = 40000;
    /** Fee percentages for banker operations. */
    public double withdrawFeePercent = 1.0;
    public double depositFeePercent = 0.0;
    public double transferFeePercent = 2.0;

    /** profession id (e.g. "minecraft:butcher") -> economy for that profession. */
    public Map<String, ProfessionEconomy> professions = new LinkedHashMap<>();

    public static class ProfessionEconomy {
        /** item id -> hryvnias paid to the player per item handed in. */
        public Map<String, Integer> jobRates = new LinkedHashMap<>();
        /** offers the player can buy from this villager. */
        public List<ShopEntry> shop = new ArrayList<>();
    }

    public static class ShopEntry {
        public String item;
        public int count;
        /** Flat price, used when no valid priceMin/priceMax range is set. */
        public int price;
        /** Optional daily price range; cheap prices are exponentially rarer. */
        public int priceMin;
        public int priceMax;

        public ShopEntry() {
        }

        public ShopEntry(String item, int count, int price) {
            this.item = item;
            this.count = count;
            this.price = price;
        }
    }

    public ProfessionEconomy professionEconomy(String professionId) {
        return professions.get(professionId);
    }

    public static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("hryvnia.json");
    }

    public static void load() {
        Path path = configPath();
        if (Files.exists(path)) {
            try {
                String json = Files.readString(path);
                HryvniaConfig loaded = GSON.fromJson(json, HryvniaConfig.class);
                if (loaded != null) {
                    if (loaded.professions == null || loaded.professions.isEmpty()) {
                        loaded.professions = createDefaultProfessions();
                    }
                    INSTANCE = loaded;
                    HryvniaMod.LOGGER.info("Loaded hryvnia config ({} professions)", loaded.professions.size());
                    return;
                }
            } catch (Exception e) {
                HryvniaMod.LOGGER.error("Failed to read config/hryvnia.json, using defaults", e);
            }
        }
        HryvniaConfig fresh = new HryvniaConfig();
        fresh.professions = createDefaultProfessions();
        INSTANCE = fresh;
        save();
    }

    public static void save() {
        try {
            Path path = configPath();
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(INSTANCE));
        } catch (IOException e) {
            HryvniaMod.LOGGER.error("Failed to write config/hryvnia.json", e);
        }
    }

    private static void rate(ProfessionEconomy e, String item, int price) {
        e.jobRates.put(item, price);
    }

    private static void offer(ProfessionEconomy e, String item, int count, int price) {
        ShopEntry entry = new ShopEntry(item, count, price);
        // Default shops get a ±20-25% daily range around the base price.
        entry.priceMin = Math.max(1, (int) Math.round(price * 0.8));
        entry.priceMax = Math.max(entry.priceMin, (int) Math.round(price * 1.25));
        e.shop.add(entry);
    }

    private static Map<String, ProfessionEconomy> createDefaultProfessions() {
        Map<String, ProfessionEconomy> map = new LinkedHashMap<>();

        ProfessionEconomy farmer = new ProfessionEconomy();
        rate(farmer, "minecraft:wheat", 2);
        rate(farmer, "minecraft:potato", 2);
        rate(farmer, "minecraft:carrot", 2);
        rate(farmer, "minecraft:beetroot", 2);
        rate(farmer, "minecraft:bread", 5);
        rate(farmer, "minecraft:pumpkin", 4);
        rate(farmer, "minecraft:melon_slice", 1);
        rate(farmer, "minecraft:sugar_cane", 1);
        rate(farmer, "minecraft:egg", 1);
        offer(farmer, "minecraft:bread", 1, 10);
        offer(farmer, "minecraft:apple", 1, 8);
        offer(farmer, "minecraft:cookie", 4, 6);
        offer(farmer, "minecraft:pumpkin_pie", 1, 25);
        offer(farmer, "minecraft:cake", 1, 60);
        offer(farmer, "minecraft:golden_carrot", 1, 40);
        offer(farmer, "minecraft:honey_bottle", 1, 30);
        offer(farmer, "minecraft:hay_block", 1, 20);
        offer(farmer, "minecraft:bone_meal", 4, 10);
        offer(farmer, "minecraft:wheat_seeds", 8, 5);
        offer(farmer, "minecraft:golden_apple", 1, 300);
        map.put("minecraft:farmer", farmer);

        ProfessionEconomy butcher = new ProfessionEconomy();
        rate(butcher, "minecraft:porkchop", 3);
        rate(butcher, "minecraft:beef", 3);
        rate(butcher, "minecraft:chicken", 2);
        rate(butcher, "minecraft:mutton", 3);
        rate(butcher, "minecraft:rabbit", 3);
        rate(butcher, "minecraft:cooked_porkchop", 5);
        rate(butcher, "minecraft:cooked_beef", 5);
        rate(butcher, "minecraft:cooked_chicken", 4);
        rate(butcher, "minecraft:cooked_mutton", 5);
        rate(butcher, "minecraft:cooked_rabbit", 5);
        offer(butcher, "minecraft:cooked_beef", 1, 12);
        offer(butcher, "minecraft:cooked_porkchop", 1, 12);
        offer(butcher, "minecraft:cooked_chicken", 1, 9);
        offer(butcher, "minecraft:cooked_mutton", 1, 10);
        offer(butcher, "minecraft:rabbit_stew", 1, 25);
        offer(butcher, "minecraft:beef", 1, 7);
        offer(butcher, "minecraft:porkchop", 1, 7);
        offer(butcher, "minecraft:leather", 1, 15);
        map.put("minecraft:butcher", butcher);

        ProfessionEconomy fisherman = new ProfessionEconomy();
        rate(fisherman, "minecraft:cod", 3);
        rate(fisherman, "minecraft:salmon", 4);
        rate(fisherman, "minecraft:tropical_fish", 6);
        rate(fisherman, "minecraft:pufferfish", 4);
        rate(fisherman, "minecraft:cooked_cod", 5);
        rate(fisherman, "minecraft:cooked_salmon", 6);
        rate(fisherman, "minecraft:ink_sac", 2);
        offer(fisherman, "minecraft:cooked_cod", 1, 10);
        offer(fisherman, "minecraft:cooked_salmon", 1, 12);
        offer(fisherman, "minecraft:tropical_fish", 1, 15);
        offer(fisherman, "minecraft:fishing_rod", 1, 60);
        offer(fisherman, "minecraft:bucket", 1, 40);
        offer(fisherman, "minecraft:water_bucket", 1, 45);
        offer(fisherman, "minecraft:oak_boat", 1, 35);
        offer(fisherman, "minecraft:campfire", 1, 30);
        map.put("minecraft:fisherman", fisherman);

        ProfessionEconomy shepherd = new ProfessionEconomy();
        rate(shepherd, "minecraft:white_wool", 3);
        rate(shepherd, "minecraft:black_wool", 3);
        rate(shepherd, "minecraft:gray_wool", 3);
        rate(shepherd, "minecraft:brown_wool", 3);
        rate(shepherd, "minecraft:mutton", 2);
        rate(shepherd, "minecraft:string", 2);
        offer(shepherd, "minecraft:white_wool", 1, 8);
        offer(shepherd, "minecraft:red_wool", 1, 8);
        offer(shepherd, "minecraft:blue_wool", 1, 8);
        offer(shepherd, "minecraft:green_wool", 1, 8);
        offer(shepherd, "minecraft:yellow_wool", 1, 8);
        offer(shepherd, "minecraft:black_wool", 1, 8);
        offer(shepherd, "minecraft:white_bed", 1, 45);
        offer(shepherd, "minecraft:white_carpet", 2, 6);
        offer(shepherd, "minecraft:shears", 1, 30);
        offer(shepherd, "minecraft:painting", 1, 20);
        offer(shepherd, "minecraft:string", 4, 10);
        map.put("minecraft:shepherd", shepherd);

        ProfessionEconomy fletcher = new ProfessionEconomy();
        rate(fletcher, "minecraft:feather", 2);
        rate(fletcher, "minecraft:flint", 2);
        rate(fletcher, "minecraft:stick", 1);
        rate(fletcher, "minecraft:string", 3);
        offer(fletcher, "minecraft:arrow", 8, 12);
        offer(fletcher, "minecraft:bow", 1, 80);
        offer(fletcher, "minecraft:crossbow", 1, 90);
        offer(fletcher, "minecraft:spectral_arrow", 4, 20);
        offer(fletcher, "minecraft:target", 1, 25);
        offer(fletcher, "minecraft:flint", 4, 10);
        offer(fletcher, "minecraft:feather", 4, 10);
        map.put("minecraft:fletcher", fletcher);

        ProfessionEconomy librarian = new ProfessionEconomy();
        rate(librarian, "minecraft:paper", 1);
        rate(librarian, "minecraft:book", 6);
        rate(librarian, "minecraft:ink_sac", 3);
        offer(librarian, "minecraft:book", 1, 15);
        offer(librarian, "minecraft:bookshelf", 1, 40);
        offer(librarian, "minecraft:writable_book", 1, 25);
        offer(librarian, "minecraft:paper", 4, 8);
        offer(librarian, "minecraft:name_tag", 1, 120);
        offer(librarian, "minecraft:compass", 1, 50);
        offer(librarian, "minecraft:clock", 1, 50);
        offer(librarian, "minecraft:glass", 4, 12);
        offer(librarian, "minecraft:lantern", 1, 15);
        offer(librarian, "minecraft:experience_bottle", 1, 35);
        map.put("minecraft:librarian", librarian);

        ProfessionEconomy cleric = new ProfessionEconomy();
        rate(cleric, "minecraft:rotten_flesh", 1);
        rate(cleric, "minecraft:redstone", 3);
        rate(cleric, "minecraft:lapis_lazuli", 3);
        rate(cleric, "minecraft:glowstone_dust", 4);
        rate(cleric, "minecraft:ender_pearl", 15);
        rate(cleric, "minecraft:nether_wart", 3);
        offer(cleric, "minecraft:redstone", 4, 20);
        offer(cleric, "minecraft:lapis_lazuli", 4, 20);
        offer(cleric, "minecraft:glowstone", 1, 25);
        offer(cleric, "minecraft:ender_pearl", 1, 60);
        offer(cleric, "minecraft:experience_bottle", 1, 35);
        offer(cleric, "minecraft:blaze_powder", 1, 40);
        offer(cleric, "minecraft:ghast_tear", 1, 150);
        offer(cleric, "minecraft:nether_wart", 1, 15);
        map.put("minecraft:cleric", cleric);

        ProfessionEconomy armorer = new ProfessionEconomy();
        rate(armorer, "minecraft:coal", 2);
        rate(armorer, "minecraft:iron_ingot", 8);
        rate(armorer, "minecraft:gold_ingot", 10);
        rate(armorer, "minecraft:diamond", 40);
        offer(armorer, "minecraft:iron_helmet", 1, 130);
        offer(armorer, "minecraft:iron_chestplate", 1, 200);
        offer(armorer, "minecraft:iron_leggings", 1, 180);
        offer(armorer, "minecraft:iron_boots", 1, 110);
        offer(armorer, "minecraft:shield", 1, 90);
        offer(armorer, "minecraft:diamond_chestplate", 1, 700);
        offer(armorer, "minecraft:bell", 1, 80);
        offer(armorer, "minecraft:iron_ingot", 1, 15);
        map.put("minecraft:armorer", armorer);

        ProfessionEconomy weaponsmith = new ProfessionEconomy();
        rate(weaponsmith, "minecraft:coal", 2);
        rate(weaponsmith, "minecraft:iron_ingot", 8);
        rate(weaponsmith, "minecraft:gold_ingot", 10);
        rate(weaponsmith, "minecraft:diamond", 40);
        rate(weaponsmith, "minecraft:flint", 2);
        offer(weaponsmith, "minecraft:iron_sword", 1, 110);
        offer(weaponsmith, "minecraft:iron_axe", 1, 100);
        offer(weaponsmith, "minecraft:diamond_sword", 1, 500);
        offer(weaponsmith, "minecraft:diamond_axe", 1, 480);
        offer(weaponsmith, "minecraft:shield", 1, 90);
        offer(weaponsmith, "minecraft:iron_ingot", 1, 15);
        map.put("minecraft:weaponsmith", weaponsmith);

        ProfessionEconomy toolsmith = new ProfessionEconomy();
        rate(toolsmith, "minecraft:coal", 2);
        rate(toolsmith, "minecraft:iron_ingot", 8);
        rate(toolsmith, "minecraft:gold_ingot", 10);
        rate(toolsmith, "minecraft:diamond", 40);
        rate(toolsmith, "minecraft:stick", 1);
        offer(toolsmith, "minecraft:stone_pickaxe", 1, 25);
        offer(toolsmith, "minecraft:iron_pickaxe", 1, 120);
        offer(toolsmith, "minecraft:iron_shovel", 1, 70);
        offer(toolsmith, "minecraft:iron_hoe", 1, 60);
        offer(toolsmith, "minecraft:iron_axe", 1, 100);
        offer(toolsmith, "minecraft:diamond_pickaxe", 1, 550);
        offer(toolsmith, "minecraft:diamond_shovel", 1, 350);
        offer(toolsmith, "minecraft:iron_ingot", 1, 15);
        map.put("minecraft:toolsmith", toolsmith);

        ProfessionEconomy mason = new ProfessionEconomy();
        rate(mason, "minecraft:stone", 1);
        rate(mason, "minecraft:clay_ball", 2);
        rate(mason, "minecraft:brick", 2);
        rate(mason, "minecraft:andesite", 1);
        rate(mason, "minecraft:granite", 1);
        rate(mason, "minecraft:diorite", 1);
        rate(mason, "minecraft:quartz", 4);
        offer(mason, "minecraft:brick", 4, 15);
        offer(mason, "minecraft:bricks", 1, 18);
        offer(mason, "minecraft:stone_bricks", 4, 20);
        offer(mason, "minecraft:chiseled_stone_bricks", 1, 10);
        offer(mason, "minecraft:terracotta", 1, 8);
        offer(mason, "minecraft:white_glazed_terracotta", 1, 15);
        offer(mason, "minecraft:quartz_block", 1, 45);
        offer(mason, "minecraft:polished_granite", 4, 20);
        map.put("minecraft:mason", mason);

        ProfessionEconomy leatherworker = new ProfessionEconomy();
        rate(leatherworker, "minecraft:leather", 4);
        rate(leatherworker, "minecraft:rabbit_hide", 2);
        rate(leatherworker, "minecraft:flint", 1);
        offer(leatherworker, "minecraft:leather_helmet", 1, 40);
        offer(leatherworker, "minecraft:leather_chestplate", 1, 60);
        offer(leatherworker, "minecraft:leather_leggings", 1, 50);
        offer(leatherworker, "minecraft:leather_boots", 1, 35);
        offer(leatherworker, "minecraft:saddle", 1, 250);
        offer(leatherworker, "minecraft:leather_horse_armor", 1, 120);
        offer(leatherworker, "minecraft:item_frame", 1, 20);
        map.put("minecraft:leatherworker", leatherworker);

        ProfessionEconomy cartographer = new ProfessionEconomy();
        rate(cartographer, "minecraft:paper", 1);
        rate(cartographer, "minecraft:glass_pane", 1);
        offer(cartographer, "minecraft:map", 1, 30);
        offer(cartographer, "minecraft:compass", 1, 50);
        offer(cartographer, "minecraft:item_frame", 1, 20);
        offer(cartographer, "minecraft:white_banner", 1, 25);
        offer(cartographer, "minecraft:glass_pane", 4, 10);
        offer(cartographer, "minecraft:spyglass", 1, 150);
        map.put("minecraft:cartographer", cartographer);

        return map;
    }
}
