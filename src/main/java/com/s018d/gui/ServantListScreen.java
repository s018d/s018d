package com.s018d.gui;

import com.s018d.enslavement.EnslavedCommand;
import com.s018d.network.EnslaveActionPacket;
import com.s018d.network.ModPackets;
import com.s018d.network.RequestServantsPacket;
import com.s018d.network.ServantsListPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

/** Sorted roster of every owned creature; click a row to open its orders. */
public class ServantListScreen extends Screen {
    private static final int ROWS = 8;

    private final List<ServantsListPacket.Entry> servants;
    private final Set<Integer> selected = new LinkedHashSet<>();
    private int page = 0;

    public ServantListScreen(List<ServantsListPacket.Entry> servants) {
        super(Component.translatable("screen.s018d.servants"));
        this.servants = servants;
    }

    @Override
    protected void init() {
        super.init();
        int x = (this.width - 340) / 2;
        int y = (this.height - 310) / 2;
        int pages = Math.max(1, (servants.size() + ROWS - 1) / ROWS);
        if (page >= pages) {
            page = pages - 1;
        }

        int first = page * ROWS;
        for (int i = 0; i < ROWS && first + i < servants.size(); i++) {
            final ServantsListPacket.Entry entry = servants.get(first + i);
            String job = EnslavedCommand.byName(entry.job()).label().getString();
            String mood = Component.translatable("gui.s018d.mood_" + entry.mood().toLowerCase()).getString();
            String label = entry.name() + " | " + job
                + " | " + (int) entry.health() + "/" + (int) entry.maxHealth()
                + " | " + mood + " " + entry.affection() + "%"
                + " | " + entry.x() + " " + entry.y() + " " + entry.z();
            String dimension = shortDimension(entry.dimension());
            if (!"overworld".equals(dimension)) {
                label = label + " | " + dimension;
            }
            this.addRenderableWidget(Button.builder(
                    Component.literal(selected.contains(entry.id()) ? "[x]" : "[ ]"),
                    button -> toggleSelection(entry.id()))
                .bounds(x + 8, y + 44 + i * 22, 22, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal(label), button ->
                    ModPackets.sendToServer(new EnslaveActionPacket(entry.id(), "SYNC", "")))
                .bounds(x + 32, y + 44 + i * 22, 246, 20).build());
            this.addRenderableWidget(Button.builder(Component.translatable("gui.s018d.bag_short"),
                    button -> ModPackets.sendToServer(new EnslaveActionPacket(entry.id(), "OPENBAG", "")))
                .bounds(x + 282, y + 44 + i * 22, 50, 20).build());
        }

        int bottom = y + 44 + ROWS * 22 + 6;
        this.addRenderableWidget(Button.builder(Component.literal("<"), button -> changePage(-1))
            .bounds(x + 8, bottom, 40, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal(">"), button -> changePage(1))
            .bounds(x + 52, bottom, 40, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.s018d.refresh"), button ->
                ModPackets.sendToServer(new RequestServantsPacket()))
            .bounds(x + 96, bottom, 100, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.s018d.all_orders"), button ->
                this.minecraft.setScreen(new EnslavedScreen(-1, "", "FOLLOW", "", -1, 0.0f, 0.0f, 0, 0, 0, 0, 0, "")))
            .bounds(x + 200, bottom, 112, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.s018d.ens_track"), button ->
                ModPackets.sendToServer(new EnslaveActionPacket(-1, "TRACK", "")))
            .bounds(x + 58, bottom + 24, 108, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.s018d.done"), button -> this.onClose())
            .bounds(x + 174, bottom + 24, 88, 20).build());
        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.s018d.selected_orders", selected.size()),
                button -> openSelectedOrders())
            .bounds(x + 8, bottom + 48, 150, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.s018d.select_all"),
                button -> selectAll())
            .bounds(x + 164, bottom + 48, 80, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.s018d.select_clear"),
                button -> clearSelection())
            .bounds(x + 250, bottom + 48, 82, 20).build());
    }

    private void toggleSelection(int id) {
        if (!selected.remove(id)) {
            selected.add(id);
        }
        this.rebuildWidgets();
    }

    private void selectAll() {
        selected.clear();
        for (ServantsListPacket.Entry entry : servants) {
            selected.add(entry.id());
        }
        this.rebuildWidgets();
    }

    private void clearSelection() {
        selected.clear();
        this.rebuildWidgets();
    }

    private void openSelectedOrders() {
        if (selected.isEmpty()) {
            return;
        }
        StringJoiner ids = new StringJoiner(",");
        for (int id : selected) {
            ids.add(Integer.toString(id));
        }
        this.minecraft.setScreen(new EnslavedScreen(-2, "", "FOLLOW", "", 0, 0.0f, 0.0f,
            0, 0, 0, 0, 0, "", ids.toString()));
    }

    private static String shortDimension(String dimension) {
        int split = dimension.lastIndexOf(':');
        return split < 0 ? dimension : dimension.substring(split + 1);
    }

    private void changePage(int delta) {
        int pages = Math.max(1, (servants.size() + ROWS - 1) / ROWS);
        int next = this.page + delta;
        if (next < 0 || next >= pages) {
            return;
        }
        this.page = next;
        this.rebuildWidgets();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        int center = this.width / 2;
        int top = (this.height - 310) / 2;
        graphics.drawCenteredString(this.font, this.title, center, top + 10, 0xFFFFFF);
        graphics.drawCenteredString(this.font,
            Component.translatable("gui.s018d.servant_count", servants.size()), center, top + 28, 0xB8B8FF);
    }
}
