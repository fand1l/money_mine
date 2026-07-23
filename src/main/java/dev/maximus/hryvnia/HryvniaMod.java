package dev.maximus.hryvnia;

import dev.maximus.hryvnia.command.HryvniaCommand;
import dev.maximus.hryvnia.economy.EconomyState;
import dev.maximus.hryvnia.economy.HryvniaConfig;
import dev.maximus.hryvnia.event.VillagerEvents;
import dev.maximus.hryvnia.network.ModPayloads;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HryvniaMod implements ModInitializer {
    public static final String MOD_ID = "hryvnia";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Save the economy at most every 5 minutes when dirty. */
    private static final int SAVE_INTERVAL_TICKS = 6000;
    private int saveCountdown = SAVE_INTERVAL_TICKS;

    @Override
    public void onInitialize() {
        HryvniaConfig.load();

        ModItems.register();
        ModProfessions.register();
        ModMenus.register();
        ModPayloads.register();
        VillagerEvents.register();
        HryvniaCommand.register();

        ServerLifecycleEvents.SERVER_STARTED.register(EconomyState::load);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> EconomyState.unload());

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (--saveCountdown <= 0) {
                saveCountdown = SAVE_INTERVAL_TICKS;
                EconomyState economy = EconomyState.get();
                if (economy != null) {
                    economy.saveIfDirty();
                }
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            EconomyState economy = EconomyState.get();
            if (economy != null) {
                economy.account(handler.player);
                economy.markDirty();
                economy.syncBalance(handler.player.getUUID());
            }
        });

        LOGGER.info("Hryvnia Economy loaded — emeralds are officially useless now");
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
