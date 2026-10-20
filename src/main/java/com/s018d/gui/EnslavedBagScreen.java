package com.s018d.gui;

import com.s018d.enslavement.ServantBagMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** Chest-like screen for the servant's nine bag slots. */
public class EnslavedBagScreen extends AbstractContainerScreen<ServantBagMenu> {
    private static final ResourceLocation TEXTURE =
        ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final int ROWS = 3;

    public EnslavedBagScreen(ServantBagMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageHeight = 114 + ROWS * 18;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        graphics.blit(TEXTURE, x, y, 0, 0, this.imageWidth, ROWS * 18 + 17);
        graphics.blit(TEXTURE, x, y + ROWS * 18 + 17, 0, 126, this.imageWidth, 96);
    }
}
