package com.s018d;

import com.s018d.enslavement.ServantBagMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
        DeferredRegister.create(Registries.MENU, Constants.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<ServantBagMenu>> SERVANT_BAG =
        MENUS.register("servant_bag", () -> IMenuTypeExtension.create(
            (containerId, inventory, data) -> new ServantBagMenu(containerId, inventory, data.readInt())));
}
