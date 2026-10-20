package com.s018d.client;

import com.s018d.entity.ServantEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Renders servants with the slim player model and the servant skins. */
public class ServantRenderer extends HumanoidMobRenderer<ServantEntity, PlayerModel<ServantEntity>> {
    private static final String[] SKINS = {
        "girl_0", "girl_1", "girl_2", "girl_3", "girl_4", "girl_5", "girl_6", "girl_7"
    };

    public ServantRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(ServantEntity servant) {
        int variant = Math.floorMod(servant.getVariant(), SKINS.length);
        return ResourceLocation.fromNamespaceAndPath("s018d",
            "textures/entity/worker/" + SKINS[variant] + ".png");
    }
}
