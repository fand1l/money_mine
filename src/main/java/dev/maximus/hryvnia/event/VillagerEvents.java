package dev.maximus.hryvnia.event;

import dev.maximus.hryvnia.HryvniaMod;
import dev.maximus.hryvnia.ModProfessions;
import dev.maximus.hryvnia.economy.HryvniaConfig;
import dev.maximus.hryvnia.menu.BankMenu;
import dev.maximus.hryvnia.menu.EmployerMenu;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

import java.util.Set;

/**
 * Two responsibilities:
 * 1. Naturally spawned jobless adult villagers have a configurable chance to
 *    become bankers, exactly once per villager (tracked with a command tag).
 *    Bred villagers never become bankers, keeping bankers scarce.
 * 2. Right-clicking villagers opens our own economy menus instead of vanilla
 *    trading, which is what makes emeralds worthless.
 */
public final class VillagerEvents {
    private static final String CHECKED_TAG = "hryvnia_checked";
    /** Spawn reasons that count as "natural" for banker conversion. */
    private static final Set<String> NATURAL_REASONS = Set.of("NATURAL", "CHUNK_GENERATION", "STRUCTURE", "EVENT");

    private VillagerEvents() {
    }

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (!(entity instanceof Villager villager)) {
                return;
            }
            if (villager.getTags().contains(CHECKED_TAG)) {
                return;
            }
            villager.addTag(CHECKED_TAG);
            if (villager.isBaby() || !professionId(villager).equals("minecraft:none")) {
                return;
            }
            if (!villager.isLoadedFromDisk()) {
                EntitySpawnReason reason = villager.spawnReason();
                if (reason == null || !NATURAL_REASONS.contains(reason.name())) {
                    return;
                }
            }
            // Villagers loaded from disk without our tag come from worlds that
            // existed before the mod was installed; give them the same one-time
            // roll so old villages can have bankers too.
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
                if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                    open(serverPlayer, villager);
                }
                return InteractionResult.SUCCESS;
            }
            if (entity.getType() == EntityType.WANDERING_TRADER) {
                if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                    serverPlayer.displayClientMessage(Component.translatable("hryvnia.msg.wandering"), false);
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
            player.displayClientMessage(Component.translatable("hryvnia.msg.jobless_villager"), true);
        }
    }

    private static String professionId(Villager villager) {
        return villager.getVillagerData().profession().unwrapKey()
                .map(key -> key.identifier().toString())
                .orElse("minecraft:none");
    }

    private static void makeBanker(Villager villager) {
        villager.setVillagerData(villager.getVillagerData().withProfession(ModProfessions.bankerHolder()));
        // Villagers with trade XP never lose their profession, locking the banker in.
        villager.setVillagerXp(10);
        villager.setCustomName(Component.translatable("entity.hryvnia.banker"));
        villager.setCustomNameVisible(false);
        HryvniaMod.LOGGER.debug("Villager at {} became a banker", villager.blockPosition());
    }
}
