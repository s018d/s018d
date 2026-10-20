package com.s018d.gui;

import com.s018d.network.ModPackets;
import com.s018d.network.SetChestFilterPacket;
import com.s018d.util.ChestFilterManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class ChestFilterScreen extends Screen {
    private final BlockPos chestPos;
    private int filterType = 0;

    private static final String[] FILTER_KEYS = {
        "gui.s018d.chest_filter_all",
        "gui.s018d.chest_filter_specific",
        "gui.s018d.chest_filter_output",
        "gui.s018d.chest_filter_blacklist"
    };

    public ChestFilterScreen(BlockPos pos) {
        super(Component.translatable("screen.s018d.chest_filter_gui"));
        this.chestPos = pos;
    }

    private static String heldItemId() {
        if (Minecraft.getInstance().player == null) {
            return "";
        }
        ItemStack held = Minecraft.getInstance().player.getMainHandItem();
        if (held.isEmpty()) {
            return "";
        }
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(held.getItem());
        return key == null ? "" : key.toString();
    }

    @Override
    protected void init() {
        super.init();
        this.filterType = ChestFilterManager.getClientFilter(chestPos).filterType;
        int x = (this.width - 200) / 2;
        int y = (this.height - 210) / 2;

        for (int i = 0; i < FILTER_KEYS.length; i++) {
            final int type = i;
            Component label = Component.translatable(FILTER_KEYS[i]);
            if (type == filterType) {
                label = Component.translatable(FILTER_KEYS[i]).append(" ✓");
            }
            this.addRenderableWidget(Button.builder(label, button -> setFilter(type, ""))
                .bounds(x + 10, y + 10 + i * 24, 180, 20).build());
        }

        this.addRenderableWidget(Button.builder(Component.translatable("gui.s018d.from_hand"), button -> {
                String id = heldItemId();
                if (!id.isEmpty()) {
                    setFilter(filterType == 3 ? 3 : 1, id);
                }
            })
            .bounds(x + 10, y + 110, 180, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.s018d.done"), button -> this.onClose())
            .bounds(x + 10, y + 140, 180, 20).build());
    }

    private void setFilter(int type, String itemId) {
        this.filterType = type;
        ChestFilterManager.setClientFilter(chestPos, type, itemId);
        ModPackets.sendToServer(new SetChestFilterPacket(chestPos, type, itemId));
        this.rebuildWidgets();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
