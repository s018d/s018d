package com.s018d.gui;

import com.s018d.enslavement.EnslavedCommand;
import com.s018d.network.EnslaveActionPacket;
import com.s018d.network.ModPackets;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Button-only order menu, organised by category pages. Works for one
 * creature (entityId >= 0) or for all owned creatures at once (entityId -1).
 */
public class EnslavedScreen extends Screen {
    private static final String[] PAGE_TITLES = {
        "gui.s018d.page_follow",
        "gui.s018d.page_guard",
        "gui.s018d.page_gather",
        "gui.s018d.page_store",
        "gui.s018d.page_butler",
        "gui.s018d.page_settings"
    };

    private static final EnslavedCommand[][] PAGES = {
        {
            EnslavedCommand.FOLLOW,
            EnslavedCommand.STAY,
            EnslavedCommand.COME,
            EnslavedCommand.RETURN_HOME,
            EnslavedCommand.PATROL,
            EnslavedCommand.SIT,
            EnslavedCommand.DANCE,
            EnslavedCommand.STOP
        },
        {
            EnslavedCommand.PROTECT,
            EnslavedCommand.GUARD,
            EnslavedCommand.GUARD_RANGED,
            EnslavedCommand.GUARD_POST,
            EnslavedCommand.ATTACK,
            EnslavedCommand.HUNT,
            EnslavedCommand.SHOOT,
            EnslavedCommand.EXPLODE
        },
        {
            EnslavedCommand.GATHER_WOOD,
            EnslavedCommand.MINE,
            EnslavedCommand.DIG,
            EnslavedCommand.HARVEST,
            EnslavedCommand.PLANT,
            EnslavedCommand.TILL,
            EnslavedCommand.FERTILIZE,
            EnslavedCommand.FISH,
            EnslavedCommand.SHEAR,
            EnslavedCommand.GROW,
            EnslavedCommand.COLLECT,
            EnslavedCommand.BREED
        },
        {
            EnslavedCommand.SORT_CHESTS,
            EnslavedCommand.DEPOSIT,
            EnslavedCommand.TORCH,
            EnslavedCommand.HEAL
        },
        {
            EnslavedCommand.BUTLER
        }
    };

    private final int entityId;
    private String creatureName;
    private String currentCommand;
    private String tool;
    private int stashCount;
    private float health;
    private float maxHealth;
    private int blocks;
    private int items;
    private int kills;
    private int done;
    private int limit;
    private String chest;
    private final String selection;
    private int page = 0;

    public EnslavedScreen(int entityId, String creatureName, String currentCommand, String tool,
                          int stashCount, float health, float maxHealth,
                          int blocks, int items, int kills, int done, int limit, String chest) {
        this(entityId, creatureName, currentCommand, tool, stashCount, health, maxHealth,
            blocks, items, kills, done, limit, chest, "");
    }

    public EnslavedScreen(int entityId, String creatureName, String currentCommand, String tool,
                          int stashCount, float health, float maxHealth,
                          int blocks, int items, int kills, int done, int limit, String chest,
                          String selection) {
        super(Component.translatable(entityId == -1 ? "screen.s018d.enslaved_all"
            : entityId == -2 ? "screen.s018d.enslaved_selected" : "screen.s018d.enslaved"));
        this.entityId = entityId;
        this.creatureName = creatureName == null ? "" : creatureName;
        this.currentCommand = currentCommand == null ? "FOLLOW" : currentCommand;
        this.tool = tool == null ? "" : tool;
        this.stashCount = stashCount;
        this.health = health;
        this.maxHealth = maxHealth;
        this.blocks = blocks;
        this.items = items;
        this.kills = kills;
        this.done = done;
        this.limit = limit;
        this.chest = chest == null ? "" : chest;
        this.selection = selection == null ? "" : selection;
    }

    public int entityId() {
        return this.entityId;
    }

    /** Called from the server when the bag, order, health, stats or settings change. */
    public void updateData(String name, String command, String tool, int stashCount,
                           float health, float maxHealth, int blocks, int items, int kills,
                           int done, int limit, String chest) {
        if (name != null && !name.isEmpty()) {
            this.creatureName = name;
        }
        this.currentCommand = command == null ? this.currentCommand : command;
        this.tool = tool == null ? "" : tool;
        this.stashCount = stashCount;
        this.health = health;
        this.maxHealth = maxHealth;
        this.blocks = blocks;
        this.items = items;
        this.kills = kills;
        this.done = done;
        this.limit = limit;
        this.chest = chest == null ? "" : chest;
        this.rebuildWidgets();
    }

    private int pageCount() {
        return entityId == -1 || entityId == -2 ? PAGES.length : PAGES.length + 1;
    }

    @Override
    protected void init() {
        super.init();
        int x = (this.width - 300) / 2;
        int y = (this.height - 340) / 2;

        boolean all = entityId == -1 || entityId == -2;
        int gridTop = all ? 54 : 90;
        int rows;
        if (!all && this.page == PAGES.length) {
            rows = 3;
            int w = 284;
            this.addRenderableWidget(Button.builder(Component.translatable("gui.s018d.ens_open_bag"),
                    button -> action("OPENBAG"))
                .bounds(x + 8, y + gridTop, w, 20).build());
            Component chestValue = this.chest.isEmpty()
                ? Component.translatable("gui.s018d.ens_chest_none")
                : Component.literal(this.chest);
            this.addRenderableWidget(Button.builder(
                    Component.translatable("gui.s018d.ens_chest_btn", chestValue),
                    button -> action("SETCHEST"))
                .bounds(x + 8, y + gridTop + 22, w, 20).build());
            Component limitValue = this.limit <= 0
                ? Component.translatable("gui.s018d.ens_limit_off")
                : Component.literal(String.valueOf(this.limit));
            this.addRenderableWidget(Button.builder(
                    Component.translatable("gui.s018d.ens_limit_btn", limitValue),
                    button -> action("LIMIT"))
                .bounds(x + 8, y + gridTop + 44, w, 20).build());
            this.addRenderableWidget(Button.builder(
                    Component.translatable("gui.s018d.ens_autopickup"),
                    button -> action("AUTOPICKUP"))
                .bounds(x + 8, y + gridTop + 66, w, 20).build());
            this.addRenderableWidget(Button.builder(
                    Component.translatable("gui.s018d.ens_autodeposit"),
                    button -> action("AUTODEPOSIT"))
                .bounds(x + 8, y + gridTop + 88, w, 20).build());
            this.addRenderableWidget(Button.builder(
                    Component.translatable("gui.s018d.ens_queue_next"),
                    button -> action("QUEUE_NEXT"))
                .bounds(x + 8, y + gridTop + 110, 138, 20).build());
            this.addRenderableWidget(Button.builder(
                    Component.translatable("gui.s018d.ens_queue_clear"),
                    button -> action("QUEUE_CLEAR"))
                .bounds(x + 154, y + gridTop + 110, 138, 20).build());
            rows = 7;
            if (!this.chest.isEmpty()) {
                this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.s018d.ens_chest_clear"),
                        button -> action("CLEARCHEST"))
                    .bounds(x + 8, y + gridTop + 132, w, 20).build());
                rows = 8;
            }
        } else {
            EnslavedCommand[] orders = PAGES[this.page];
            for (int i = 0; i < orders.length; i++) {
                final EnslavedCommand order = orders[i];
                int col = i % 2;
                int row = i / 2;
                Component label = order.label();
                if (!all && order.name().equals(this.currentCommand)) {
                    label = label.copy().withStyle(ChatFormatting.GOLD);
                }
                this.addRenderableWidget(Button.builder(label, button -> send(order))
                    .bounds(x + 8 + col * 146, y + gridTop + row * 22, 138, 20).build());
            }
            rows = (orders.length + 1) / 2;
        }

        int bottom = y + gridTop + rows * 22 + 6;
        this.addRenderableWidget(Button.builder(Component.literal("<"), button -> changePage(-1))
            .bounds(x + 8, bottom, 46, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal(">"), button -> changePage(1))
            .bounds(x + 60, bottom, 46, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.s018d.home_here"),
                button -> sendHome())
            .bounds(x + 112, bottom, 180, 20).build());

        int bottom2 = bottom + 26;
        if (all) {
            this.addRenderableWidget(Button.builder(Component.translatable("gui.s018d.done"),
                    button -> this.onClose())
                .bounds(x + 112, bottom2, 180, 20).build());
            return;
        }

        this.addRenderableWidget(Button.builder(EnslavedCommand.RELEASE.label(), button -> {
                send(EnslavedCommand.RELEASE);
                this.onClose();
            })
            .bounds(x + 8, bottom2, 68, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.s018d.ens_equip"),
                button -> bag("EQUIP"))
            .bounds(x + 82, bottom2, 68, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.s018d.ens_withdraw"),
                button -> bag("WITHDRAW"))
            .bounds(x + 156, bottom2, 68, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.s018d.done"),
                button -> this.onClose())
            .bounds(x + 230, bottom2, 68, 20).build());
    }

    private void changePage(int delta) {
        int next = this.page + delta;
        if (next < 0 || next >= pageCount()) {
            return;
        }
        this.page = next;
        this.rebuildWidgets();
    }

    private void action(String command) {
        ModPackets.sendToServer(new EnslaveActionPacket(this.entityId, command, ""));
    }

    private void send(EnslavedCommand order) {
        if (entityId == -2) {
            ModPackets.sendToServer(new EnslaveActionPacket(-2, "MULTI",
                order.name() + "|" + selection));
            this.onClose();
            return;
        }
        if (entityId >= 0 && Screen.hasShiftDown() && order != EnslavedCommand.RELEASE) {
            ModPackets.sendToServer(new EnslaveActionPacket(entityId, "QUEUE_ADD", order.name()));
            return;
        }
        ModPackets.sendToServer(new EnslaveActionPacket(this.entityId, order.name(), ""));
        if (order != EnslavedCommand.RELEASE) {
            this.onClose();
        }
    }

    private void bag(String action) {
        ModPackets.sendToServer(new EnslaveActionPacket(this.entityId, action, ""));
        this.onClose();
    }

    private void sendHome() {
        if (entityId == -2) {
            ModPackets.sendToServer(new EnslaveActionPacket(-2, "MULTI_HOME", selection));
            this.onClose();
            return;
        }
        ModPackets.sendToServer(new EnslaveActionPacket(this.entityId, "HOME", ""));
        this.onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        int center = this.width / 2;
        int top = (this.height - 340) / 2;
        graphics.drawCenteredString(this.font, this.title, center, top + 10, 0xFFFFFF);
        if (entityId == -1) {
            graphics.drawCenteredString(this.font, Component.translatable("gui.s018d.enslaved_all_hint"),
                center, top + 26, 0xFFD966);
        } else if (entityId == -2) {
            int count = selection.isBlank() ? 0 : selection.split(",").length;
            graphics.drawCenteredString(this.font,
                Component.translatable("gui.s018d.enslaved_selected_hint", count),
                center, top + 26, 0xFFD966);
        } else {
            graphics.drawCenteredString(this.font,
                Component.literal(this.creatureName).append("  —  ")
                    .append(Component.translatable("gui.s018d.enslaved_current",
                        EnslavedCommand.byName(this.currentCommand).label())),
                center, top + 26, 0xFFD966);
            Component toolLine = this.tool.isEmpty()
                ? Component.translatable("gui.s018d.ens_no_tool")
                : Component.translatable("gui.s018d.ens_tool", this.tool);
            MutableComponent stats = toolLine.copy();
            if (this.stashCount >= 0) {
                stats.append(Component.literal("  "))
                    .append(Component.translatable("gui.s018d.ens_stash", this.stashCount));
            }
            stats.append(Component.literal("  HP " + (int) this.health + "/" + (int) this.maxHealth));
            if (this.limit > 0) {
                stats.append(Component.literal("  "))
                    .append(Component.translatable("gui.s018d.ens_work_progress", this.done, this.limit));
            }
            graphics.drawCenteredString(this.font, stats, center, top + 40, 0x9FE8A0);
            graphics.drawCenteredString(this.font,
                Component.translatable("gui.s018d.ens_stats", this.blocks, this.items, this.kills),
                center, top + 52, 0x8FA3BF);
        }
        int categoryY = entityId == -1 ? top + 40 : top + 64;
        graphics.drawCenteredString(this.font, Component.translatable("gui.s018d.page_category",
                Component.translatable(PAGE_TITLES[this.page]), this.page + 1, pageCount()),
            center, categoryY, 0xB8B8FF);
        if (entityId >= 0) {
            graphics.drawCenteredString(this.font, Component.translatable("gui.s018d.ens_queue_hint"),
                center, top + 76, 0x8FA3BF);
        }
    }
}

