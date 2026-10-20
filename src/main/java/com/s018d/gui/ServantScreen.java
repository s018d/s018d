package com.s018d.gui;

import com.s018d.entity.ServantEntity;
import com.s018d.network.ModPackets;
import com.s018d.network.ServantActionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Super servant menu: live task queue, inventory contents,
 * button-only task orders and servant management.
 */
public class ServantScreen extends Screen {
    private static final ServantEntity.TaskType[] ORDER = {
        ServantEntity.TaskType.FOLLOW,
        ServantEntity.TaskType.STAY,
        ServantEntity.TaskType.GOTO,
        ServantEntity.TaskType.BREAK,
        ServantEntity.TaskType.FETCH,
        ServantEntity.TaskType.DELIVER,
        ServantEntity.TaskType.ATTACK,
        ServantEntity.TaskType.HARVEST,
        ServantEntity.TaskType.PLANT
    };
    private static final String[] ORDER_KEYS = {
        "task_follow",
        "task_stay",
        "task_goto",
        "task_break",
        "task_fetch",
        "task_deliver",
        "task_attack",
        "task_harvest",
        "task_plant"
    };

    private final int servantId;
    private final List<String> tasks;
    private final List<String> items;

    public ServantScreen(int servantId, List<String> tasks, List<String> items) {
        super(Component.translatable("screen.s018d.servant"));
        this.servantId = servantId;
        this.tasks = tasks;
        this.items = items;
    }

    private void order(ServantEntity.TaskType type, String params) {
        ModPackets.sendToServer(new ServantActionPacket(servantId, "addtask", type.ordinal() + "|" + params));
    }

    private String heldBlockId() {
        if (Minecraft.getInstance().player == null) {
            return "";
        }
        ItemStack held = Minecraft.getInstance().player.getMainHandItem();
        if (held.isEmpty() || !(held.getItem() instanceof net.minecraft.world.item.BlockItem)) {
            return "";
        }
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(held.getItem());
        ResourceLocation blockKey = BuiltInRegistries.BLOCK.getKey(
            ((net.minecraft.world.item.BlockItem) held.getItem()).getBlock());
        return blockKey == null ? "" : blockKey.toString();
    }

    private String heldItemId() {
        if (Minecraft.getInstance().player == null) {
            return "";
        }
        ItemStack held = Minecraft.getInstance().player.getMainHandItem();
        if (held.isEmpty()) {
            return "";
        }
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(held.getItem());
        return key == null ? "" : key.toString() + ";count=" + held.getCount();
    }

    private String playerPos() {
        if (Minecraft.getInstance().player == null) {
            return "";
        }
        net.minecraft.core.BlockPos pos = Minecraft.getInstance().player.blockPosition();
        return "x=" + pos.getX() + ";y=" + pos.getY() + ";z=" + pos.getZ();
    }

    @Override
    protected void init() {
        super.init();
        int x = (this.width - 240) / 2;
        int y = (this.height - 300) / 2;

        for (int i = 0; i < ORDER.length; i++) {
            final ServantEntity.TaskType type = ORDER[i];
            final String key = ORDER_KEYS[i];
            int col = i % 3;
            int row = i / 3;
            this.addRenderableWidget(Button.builder(Component.translatable(key), button -> {
                    switch (type) {
                        case GOTO -> order(type, playerPos());
                        case BREAK -> {
                            String id = heldBlockId();
                            if (!id.isEmpty()) {
                                order(type, "block=" + id + ";count=16;radius=16");
                            }
                        }
                        case FETCH -> {
                            String id = heldItemId();
                            if (!id.isEmpty()) {
                                order(type, "item=" + id);
                            }
                        }
                        case STAY -> order(type, "ticks=200");
                        case HARVEST, PLANT -> order(type, "count=16");
                        default -> order(type, "");
                    }
                })
                .bounds(x + 8 + col * 76, y + 64 + row * 22, 74, 20).build());
        }

        int below = y + 64 + 3 * 22 + 6;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.s018d.servant_withdraw"), button ->
                ModPackets.sendToServer(new ServantActionPacket(servantId, "withdraw", "")))
            .bounds(x + 8, below, 108, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.s018d.home_set"), button ->
                ModPackets.sendToServer(new ServantActionPacket(servantId, "home", "")))
            .bounds(x + 118, below, 112, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.s018d.servant_clear"), button ->
                ModPackets.sendToServer(new ServantActionPacket(servantId, "clear", "all")))
            .bounds(x + 8, below + 24, 108, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.s018d.release"), button -> {
                ModPackets.sendToServer(new ServantActionPacket(servantId, "release", ""));
                this.onClose();
            })
            .bounds(x + 118, below + 24, 112, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        int cx = this.width / 2;
        graphics.drawCenteredString(this.font, this.title, cx, (this.height - 300) / 2 + 8, 0xFFFFFF);
        int lineY = (this.height - 300) / 2 + 22;
        if (tasks.isEmpty()) {
            graphics.drawCenteredString(this.font,
                Component.translatable("gui.s018d.servant_empty"), cx, lineY, 0xAAAAAA);
        } else {
            int shown = Math.min(tasks.size(), 2);
            for (int i = 0; i < shown; i++) {
                String[] parts = tasks.get(i).split("\\|");
                String label = (i + 1) + ". " + parts[0] + " [" + (parts.length > 1 ? parts[1] : "?") + "]";
                graphics.drawCenteredString(this.font, label, cx, lineY + i * 10, 0xFFFFAA);
            }
            if (tasks.size() > 2) {
                graphics.drawCenteredString(this.font, "+" + (tasks.size() - 2), cx, lineY + 20, 0xAAAAAA);
            }
        }
        if (!items.isEmpty()) {
            graphics.drawCenteredString(this.font,
                Component.translatable("gui.s018d.servant_inv").getString() + ": " + String.join(", ", items.subList(0, Math.min(3, items.size()))),
                cx, (this.height - 300) / 2 + 52, 0xAAAAAA);
        }
    }
}
