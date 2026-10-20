package com.s018d.gui;

import com.s018d.network.EnslaveActionPacket;
import com.s018d.network.ModPackets;
import com.s018d.util.CraftBook;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Recipe picker for a craftsman servant. Every click appends one request to
 * that servant's queue; the servant collects materials and crafts when idle.
 */
public class EnslavedCraftScreen extends Screen {
    private static final int PER_PAGE = 15;
    private static final int COLUMNS = 3;
    private final int entityId;
    private final String creatureName;
    private int page;

    public EnslavedCraftScreen(int entityId, String creatureName) {
        super(Component.translatable("screen.s018d.craft"));
        this.entityId = entityId;
        this.creatureName = creatureName == null ? "" : creatureName;
    }

    @Override
    protected void init() {
        super.init();
        List<CraftBook.Entry> recipes = CraftBook.all();
        int pages = Math.max(1, (recipes.size() + PER_PAGE - 1) / PER_PAGE);
        this.page = Math.max(0, Math.min(this.page, pages - 1));
        int x = (this.width - 300) / 2;
        int y = (this.height - 250) / 2;
        int start = this.page * PER_PAGE;
        int end = Math.min(recipes.size(), start + PER_PAGE);
        for (int i = start; i < end; i++) {
            CraftBook.Entry entry = recipes.get(i);
            int index = i - start;
            int col = index % COLUMNS;
            int row = index / COLUMNS;
            String label = entry.result().getHoverName().getString();
            if (entry.result().getCount() > 1) {
                label += " x" + entry.result().getCount();
            }
            this.addRenderableWidget(Button.builder(Component.literal(label),
                    button -> request(entry))
                .bounds(x + 8 + col * 94, y + 42 + row * 22, 88, 20).build());
        }
        int bottom = y + 42 + 5 * 22 + 8;
        this.addRenderableWidget(Button.builder(Component.literal("<"), button -> changePage(-1))
            .bounds(x + 8, bottom, 40, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal(">"), button -> changePage(1))
            .bounds(x + 52, bottom, 40, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.s018d.craft_clear"),
                button -> ModPackets.sendToServer(new EnslaveActionPacket(this.entityId, "CRAFT_CLEAR", "")))
            .bounds(x + 100, bottom, 100, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.s018d.done"),
                button -> this.onClose())
            .bounds(x + 208, bottom, 84, 20).build());
    }

    private void request(CraftBook.Entry entry) {
        ModPackets.sendToServer(new EnslaveActionPacket(this.entityId, "CRAFT_REQ", entry.id()));
    }

    private void changePage(int delta) {
        int pages = Math.max(1, (CraftBook.all().size() + PER_PAGE - 1) / PER_PAGE);
        this.page = Math.floorMod(this.page + delta, pages);
        this.rebuildWidgets();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        int center = this.width / 2;
        int top = (this.height - 250) / 2;
        graphics.drawCenteredString(this.font, this.title, center, top + 10, 0xFFFFFF);
        graphics.drawCenteredString(this.font,
            Component.translatable("gui.s018d.craft_for", this.creatureName),
            center, top + 26, 0xFFD966);
        graphics.drawCenteredString(this.font,
            Component.translatable("gui.s018d.craft_hint"),
            center, this.height / 2 + 62, 0x8FA3BF);
    }
}
