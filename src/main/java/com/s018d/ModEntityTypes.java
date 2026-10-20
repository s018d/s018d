package com.s018d;

import com.s018d.entity.HybridServantEntity;
import com.s018d.entity.ServantEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModEntityTypes {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
        DeferredRegister.create(Registries.ENTITY_TYPE, Constants.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<ServantEntity>> SERVANT =
        ENTITY_TYPES.register("servant",
            () -> EntityType.Builder.of(ServantEntity::new, MobCategory.MISC)
                .sized(0.6f, 1.95f)
                .clientTrackingRange(10)
                .updateInterval(3)
                .build("servant"));

    public static final DeferredHolder<EntityType<?>, EntityType<HybridServantEntity>> HYBRID_SERVANT =
        ENTITY_TYPES.register("hybrid_servant",
            () -> EntityType.Builder.of(HybridServantEntity::new, MobCategory.MISC)
                .sized(0.6f, 1.95f)
                .clientTrackingRange(10)
                .updateInterval(3)
                .build("hybrid_servant"));
}
