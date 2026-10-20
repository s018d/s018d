package com.s018d.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

import java.util.List;

/**
 * Opens client screens through reflection, so common classes (items and
 * packets) never link against client-only Minecraft classes on a dedicated
 * server. Without this the server crashes while registering items.
 */
public final class ClientScreens {
    private ClientScreens() {
    }

    public static boolean isClient() {
        return FMLEnvironment.dist == Dist.CLIENT;
    }

    public static Entity clientEntity(int entityId) {
        if (!isClient()) {
            return null;
        }
        try {
            Object mc = minecraft();
            Object level = mc.getClass().getField("level").get(mc);
            if (level == null) {
                return null;
            }
            Object entity = level.getClass().getMethod("getEntity", int.class).invoke(level, entityId);
            return entity instanceof Entity e ? e : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    public static void openEnslaved(int entityId, String name, String command, String tool, int stashCount,
                                    float health, float maxHealth, int blocks, int items, int kills,
                                    int done, int limit, String chest) {
        open("com.s018d.gui.EnslavedScreen",
                new Class<?>[]{int.class, String.class, String.class, String.class, int.class,
                    float.class, float.class, int.class, int.class, int.class, int.class, int.class, String.class},
                entityId, name, command, tool, stashCount, health, maxHealth, blocks, items, kills,
                done, limit, chest);
    }

    /** Updates the open order menu in place, keeping the current page. */
    public static void refreshEnslaved(int entityId, String name, String command, String tool, int stashCount,
                                       float health, float maxHealth, int blocks, int items, int kills,
                                       int done, int limit, String chest) {
        if (!isClient()) {
            return;
        }
        try {
            Object mc = minecraft();
            Object screen = mc.getClass().getField("screen").get(mc);
            if (screen == null || !screen.getClass().getName().equals("com.s018d.gui.EnslavedScreen")) {
                return;
            }
            Object id = screen.getClass().getMethod("entityId").invoke(screen);
            if (!(id instanceof Integer current) || current != entityId) {
                return;
            }
            screen.getClass().getMethod("updateData",
                    String.class, String.class, String.class, int.class, float.class, float.class,
                    int.class, int.class, int.class, int.class, int.class, String.class)
                .invoke(screen, name, command, tool, stashCount, health, maxHealth,
                    blocks, items, kills, done, limit, chest);
        } catch (ReflectiveOperationException ignored) {
            // No menu open: nothing to refresh.
        }
    }

    public static void openServantList(List<?> servants) {
        open("com.s018d.gui.ServantListScreen", new Class<?>[]{List.class}, servants);
    }

    public static void openServant(int servantId, List<String> tasks, List<String> items) {
        open("com.s018d.gui.ServantScreen",
                new Class<?>[]{int.class, List.class, List.class}, servantId, tasks, items);
    }

    public static void openChestFilter(BlockPos pos) {
        open("com.s018d.gui.ChestFilterScreen", new Class<?>[]{BlockPos.class}, pos);
    }

    private static void open(String className, Class<?>[] parameterTypes, Object... args) {
        if (!isClient()) {
            return;
        }
        try {
            Object screen = Class.forName(className).getConstructor(parameterTypes).newInstance(args);
            Object mc = minecraft();
            Class<?> screenClass = Class.forName("net.minecraft.client.gui.screens.Screen");
            mc.getClass().getMethod("setScreen", screenClass).invoke(mc, screen);
        } catch (ReflectiveOperationException ignored) {
            // Client class missing: nothing to do on a dedicated server.
        }
    }

    private static Object minecraft() throws ReflectiveOperationException {
        Class<?> minecraftClass = Class.forName("net.minecraft.client.Minecraft");
        return minecraftClass.getMethod("getInstance").invoke(null);
    }
}
