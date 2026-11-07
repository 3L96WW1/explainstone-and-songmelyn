/*
 * explainstone —— 讲解石 / 颂美铃 / 广播发信器 / 特朗谱
 *
 * 作者 / Author : GINKA_3L96WW1
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.ginka.explainstone.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.screens.Screen;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;

/**
 * 26.1 与 26.2 之间「读当前界面」和「拿 Toast 管理器」的落点变了，这里做一层兼容。
 *
 * <ul>
 *   <li>26.1.2：{@code Minecraft.screen} 字段 + {@code Minecraft.getToastManager()}</li>
 *   <li>26.2：{@code Gui.screen()} 方法 + {@code Gui.toastManager()} 方法</li>
 * </ul>
 *
 * <p>同一份源码要编译这两个版本，直接写任何一种都会在另一边编译失败，
 * 所以按名字反射一次并把 handle 缓存下来。查找失败只影响对应功能，不影响主流程。
 */
public final class ClientCompat {
    private static final MethodHandle GUI_SCREEN;
    private static final MethodHandle GUI_TOASTS;
    private static final Field MINECRAFT_SCREEN;
    private static final MethodHandle MINECRAFT_TOASTS;

    static {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        GUI_SCREEN = findMethod(lookup, "net.minecraft.client.gui.Gui", "screen", Screen.class);
        GUI_TOASTS = findMethod(lookup, "net.minecraft.client.gui.Gui", "toastManager", ToastManager.class);
        MINECRAFT_SCREEN = findField("net.minecraft.client.Minecraft", "screen");
        MethodHandle toasts = null;
        try {
            toasts = lookup.findVirtual(Minecraft.class, "getToastManager",
                    MethodType.methodType(ToastManager.class));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // 26.2 已经没有这个方法，走 Gui.toastManager() 那条路
        }
        MINECRAFT_TOASTS = toasts;
    }

    private ClientCompat() {
    }

    private static MethodHandle findMethod(MethodHandles.Lookup lookup, String owner, String name,
                                           Class<?> returnType) {
        try {
            Class<?> type = Class.forName(owner);
            return lookup.findVirtual(type, name, MethodType.methodType(returnType));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Field findField(String owner, String name) {
        try {
            Field field = Class.forName(owner).getField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    /** 当前打开的界面；没有界面或查不到时返回 null。 */
    public static Screen currentScreen(Minecraft client) {
        if (GUI_SCREEN != null) {
            try {
                return (Screen) GUI_SCREEN.invoke(client.gui);
            } catch (Throwable ignored) {
                // 落到下面的字段读取
            }
        }
        if (MINECRAFT_SCREEN != null) {
            try {
                return (Screen) MINECRAFT_SCREEN.get(client);
            } catch (Throwable ignored) {
                // 都没有就返回 null
            }
        }
        return null;
    }

    /** Toast 管理器；查不到时返回 null，调用方跳过提示即可。 */
    public static ToastManager toastManager(Minecraft client) {
        if (GUI_TOASTS != null) {
            try {
                return (ToastManager) GUI_TOASTS.invoke(client.gui);
            } catch (Throwable ignored) {
                // 落到下面的方法调用
            }
        }
        if (MINECRAFT_TOASTS != null) {
            try {
                return (ToastManager) MINECRAFT_TOASTS.invoke(client);
            } catch (Throwable ignored) {
                // 都没有就返回 null
            }
        }
        return null;
    }
}
