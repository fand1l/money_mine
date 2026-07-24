package dev.maximus.hryvnia.event;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.npc.villager.Villager;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Keeps villagers still while a player has their shop/bank menu open, so they
 * cannot wander off mid-interaction. Every server tick the villager's path is
 * cancelled and it is pinned to the horizontal spot it stood on when the menu
 * opened (vertical motion is left to gravity). Nothing is written to the
 * entity's NBT, so nothing stays stuck if the game closes with a menu open.
 * Reference-counted so two players browsing the same villager both hold it.
 */
public final class VillagerFreeze {
    private record Anchor(double x, double z, int count) {
    }

    private static final Map<Villager, Anchor> BUSY = new HashMap<>();

    private VillagerFreeze() {
    }

    public static void begin(Villager villager) {
        if (villager == null) {
            return;
        }
        Anchor existing = BUSY.get(villager);
        if (existing == null) {
            BUSY.put(villager, new Anchor(villager.getX(), villager.getZ(), 1));
        } else {
            BUSY.put(villager, new Anchor(existing.x(), existing.z(), existing.count() + 1));
        }
    }

    public static void end(Villager villager) {
        if (villager == null) {
            return;
        }
        Anchor anchor = BUSY.get(villager);
        if (anchor == null) {
            return;
        }
        if (anchor.count() <= 1) {
            BUSY.remove(villager);
        } else {
            BUSY.put(villager, new Anchor(anchor.x(), anchor.z(), anchor.count() - 1));
        }
    }

    public static void tick(MinecraftServer server) {
        if (BUSY.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<Villager, Anchor>> it = BUSY.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Villager, Anchor> entry = it.next();
            Villager villager = entry.getKey();
            if (villager.isRemoved() || !villager.isAlive()) {
                it.remove();
                continue;
            }
            Anchor anchor = entry.getValue();
            villager.getNavigation().stop();
            villager.setDeltaMovement(0, villager.getDeltaMovement().y, 0);
            villager.setJumping(false);
            villager.setPos(anchor.x(), villager.getY(), anchor.z());
        }
    }
}
