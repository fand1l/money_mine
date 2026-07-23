package dev.maximus.hryvnia;

import dev.maximus.hryvnia.menu.BankMenu;
import dev.maximus.hryvnia.menu.EmployerMenu;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

public final class ModMenus {
    public static ExtendedMenuType<EmployerMenu, EmployerMenu.EmployerData> EMPLOYER;
    public static ExtendedMenuType<BankMenu, BankMenu.BankData> BANK;

    private ModMenus() {
    }

    public static void register() {
        EMPLOYER = new ExtendedMenuType<>(
                (containerId, inventory, data) -> new EmployerMenu(containerId, inventory, data),
                EmployerMenu.EmployerData.STREAM_CODEC);
        BANK = new ExtendedMenuType<>(
                (containerId, inventory, data) -> new BankMenu(containerId, inventory, data),
                BankMenu.BankData.STREAM_CODEC);
        Registry.register(BuiltInRegistries.MENU, HryvniaMod.id("employer"), EMPLOYER);
        Registry.register(BuiltInRegistries.MENU, HryvniaMod.id("bank"), BANK);
    }
}
