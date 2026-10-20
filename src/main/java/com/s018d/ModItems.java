package com.s018d;

import com.s018d.item.BandageItem;
import com.s018d.item.ChestFilterItem;
import com.s018d.item.RingItem;
import com.s018d.item.ServantControllerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Constants.MODID);

    public static final DeferredHolder<Item, Item> SERVANT_CONTROLLER = ITEMS.register("servant_controller",
        () -> new ServantControllerItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<Item, Item> CHEST_FILTER = ITEMS.register("chest_filter",
        () -> new ChestFilterItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<Item, Item> BANDAGE = ITEMS.register("bandage",
        () -> new BandageItem(new Item.Properties().stacksTo(16)));

    public static final DeferredHolder<Item, Item> ENGAGEMENT_RING = ITEMS.register("engagement_ring",
        () -> new RingItem(RingItem.Type.ENGAGEMENT, new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<Item, Item> WEDDING_RING = ITEMS.register("wedding_ring",
        () -> new RingItem(RingItem.Type.WEDDING, new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<Item, Item> BONDING_RING = ITEMS.register("bonding_ring",
        () -> new RingItem(RingItem.Type.BONDING, new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<Item, SpawnEggItem> SERVANT_SPAWN_EGG = ITEMS.register("servant_spawn_egg",
        () -> new SpawnEggItem(ModEntityTypes.SERVANT.get(), 0x3B3B8A, 0xFFD700, new Item.Properties()));

    public static final DeferredHolder<Item, SpawnEggItem> HYBRID_SERVANT_SPAWN_EGG = ITEMS.register("hybrid_servant_spawn_egg",
        () -> new SpawnEggItem(ModEntityTypes.HYBRID_SERVANT.get(), 0x6A3B8A, 0x8AE0FF, new Item.Properties()));
}
