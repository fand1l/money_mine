package dev.maximus.hryvnia.event;

import dev.maximus.hryvnia.HryvniaMod;
import dev.maximus.hryvnia.ModProfessions;
import dev.maximus.hryvnia.economy.HryvniaConfig;
import dev.maximus.hryvnia.menu.BankMenu;
import dev.maximus.hryvnia.menu.EmployerMenu;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

import java.util.Set;

/**
 * Two responsibilities:
 * 1. Naturally spawned jobless adult villagers have a configurable chance to
 *    become bankers. Only first-time spawns with a natural spawn reason roll,
 *    so bred or summoned villagers never become bankers — bankers stay scarce.
 * 2. Right-clicking villagers opens our own economy menus instead of vanilla
 *    trading, which is what makes emeralds worthless.
 */
public final class VillagerEvents {
    /** Spawn reasons that count as "natural" for banker conversion. */
    private static final Set<String> NATURAL_REASONS = Set.of("NATURAL", "CHUNK_GENERATION", "STRUCTURE", "EVENT");

    private VillagerEvents() {
    }

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (!(entity instanceof Villager villager)) {
                return;
            }
            // A fresh spawn fires ENTITY_LOAD exactly once, so no extra
            // once-per-villager bookkeeping is needed.
            if (villager.isLoadedFromDisk()) {
                return;
            }
            if (villager.isBaby() || !professionId(villager).equals("minecraft:none")) {
                return;
            }
            EntitySpawnReason reason = villager.spawnReason();
            if (reason == null || !NATURAL_REASONS.contains(reason.name())) {
                return;
            }
            if (level.getRandom().nextDouble() < HryvniaConfig.INSTANCE.bankerSpawnChance) {
                makeBanker(villager);
            }
        });

        UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (hand != InteractionHand.MAIN_HAND || player.isSpectator()) {
                return InteractionResult.PASS;
            }
            if (entity instanceof Villager villager) {
                if (villager.isBaby() || !villager.isAlive()) {
                    return InteractionResult.PASS;
                }
                // Only a ServerPlayer means we are on the logical server.
                if (player instanceof ServerPlayer serverPlayer) {
                    open(serverPlayer, villager);
                }
                return InteractionResult.SUCCESS;
            }
            if (BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().equals("minecraft:wandering_trader")) {
                if (player instanceof ServerPlayer serverPlayer) {
                    serverPlayer.sendSystemMessage(Component.translatable("hryvnia.msg.wandering"));
                }
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
    }

    private static void open(ServerPlayer player, Villager villager) {
        Holder<VillagerProfession> profession = villager.getVillagerData().profession();
        if (profession.is(ModProfessions.BANKER_KEY)) {
            BankMenu.open(player, villager);
            return;
        }
        String professionId = professionId(villager);
        if (HryvniaConfig.INSTANCE.professionEconomy(professionId) != null) {
            EmployerMenu.open(player, villager, professionId);
        } else {
            player.sendSystemMessage(Component.translatable("hryvnia.msg.jobless_villager"));
        }
    }

    private static String professionId(Villager villager) {
        return villager.getVillagerData().profession().unwrapKey()
                .map(key -> key.identifier().toString())
                .orElse("minecraft:none");
    }

    public static void makeBanker(Villager villager) {
        villager.setVillagerData(villager.getVillagerData().withProfession(ModProfessions.bankerHolder()));
        // Villagers with trade XP never lose their profession, locking the banker in.
        villager.setVillagerXp(10);
        villager.setCustomName(Component.translatable("entity.hryvnia.banker"));
        villager.setCustomNameVisible(false);
        HryvniaMod.LOGGER.debug("Villager at {} became a banker", villager.blockPosition());
    }
}
