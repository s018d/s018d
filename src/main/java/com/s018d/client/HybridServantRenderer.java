package com.s018d.client;

import com.s018d.entity.HybridServantEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

public class HybridServantRenderer extends HumanoidMobRenderer<HybridServantEntity, PlayerModel<HybridServantEntity>> {
    private static final String[] SKINS = {
        "girl_0", "girl_1", "girl_2", "girl_3", "girl_4", "girl_5", "girl_6", "girl_7"
    };

    public HybridServantRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(HybridServantEntity entity) {
        int variant = entity.getVariant();
        return ResourceLocation.fromNamespaceAndPath("s018d",
            "textures/entity/worker/" + SKINS[variant] + ".png");
    }
}
