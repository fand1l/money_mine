package dev.maximus.hryvnia;

import com.google.common.collect.ImmutableSet;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

public final class ModProfessions {
    public static final ResourceKey<VillagerProfession> BANKER_KEY =
            ResourceKey.create(Registries.VILLAGER_PROFESSION, HryvniaMod.id("banker"));

    private ModProfessions() {
    }

    public static void register() {
        // Both POI predicates always fail: bankers can never be created by
        // placing a workstation, so the only bankers are the naturally
        // spawned ones (see VillagerEvents). They keep the profession because
        // they are given villager XP on conversion.
        Registry.register(BuiltInRegistries.VILLAGER_PROFESSION, BANKER_KEY, new VillagerProfession(
                Component.translatable("entity.hryvnia.banker"),
                holder -> false,
                holder -> false,
                ImmutableSet.of(),
                ImmutableSet.of(),
                null));
    }

    public static Holder<VillagerProfession> bankerHolder() {
        return BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(BANKER_KEY);
    }
}
