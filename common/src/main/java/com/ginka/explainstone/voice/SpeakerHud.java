/*
 * explainstone —— 讲解石 / 颂美铃 / 广播发信器 / 特朗谱
 *
 * 作者 / Author : GINKA_3L96WW1
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.ginka.explainstone.voice;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 左下角的麦克风图标：自己开麦时亮，别人说话时也亮（按说话人排序显示）。
 *
 * <p>不画贴图，直接用几何图形拼一个小麦克风——省一张贴图，也不受资源包影响。
 * 两个加载器各自把 {@link #extract} 注册到自己的 HUD 层里。
 */
public final class SpeakerHud {
    private static final Map<UUID, Boolean> SPEAKING = new HashMap<>();
    /** 说话图标从响起时开始计时，用来做呼吸动画。 */
    private static final Map<UUID, Long> SINCE = new HashMap<>();
    private static final int SIZE = 10;
    private static final int MARGIN = 6;

    private SpeakerHud() {
    }

    public static void setSpeaking(UUID who, boolean speaking) {
        if (speaking) {
            if (!SPEAKING.containsKey(who) || !SPEAKING.get(who)) {
                SINCE.put(who, System.currentTimeMillis());
            }
            SPEAKING.put(who, Boolean.TRUE);
        } else {
            SPEAKING.remove(who);
            SINCE.remove(who);
        }
    }

    public static void clear() {
        SPEAKING.clear();
        SINCE.clear();
    }

    /** 有谁在说话（含自己）。 */
    public static boolean anySpeaking() {
        return !SPEAKING.isEmpty();
    }

    /**
     * 画在左下角。{@code mine} 是当前玩家自己的 UUID，用于区分「我」和「别人」的图标。
     */
    public static void extract(GuiGraphicsExtractor context, DeltaTracker delta) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer self = client.player;
        if (self == null) {
            return;
        }
        int height = context.guiHeight();
        int x = MARGIN;
        int y = height - MARGIN - SIZE;
        // 自己先画，然后是别人
        UUID selfId = self.getUUID();
        if (Boolean.TRUE.equals(SPEAKING.get(selfId))) {
            drawMic(context, x, y, true);
            x += SIZE + 3;
        }
        for (Map.Entry<UUID, Boolean> entry : SPEAKING.entrySet()) {
            if (entry.getKey().equals(selfId) || !Boolean.TRUE.equals(entry.getValue())) {
                continue;
            }
            drawMic(context, x, y, false);
            x += SIZE + 3;
            if (x > context.guiWidth() / 2) {
                break;
            }
        }
    }

    /**
     * 一个 10x10 的小麦克风：竖着的话筒头 + 底座 + 支架。
     * 开麦时用亮绿，别人说话用淡蓝，都不带边框，避免遮挡视野。
     */
    private static void drawMic(GuiGraphicsExtractor context, int x, int y, boolean mine) {
        int color = mine ? 0xFF55FF77 : 0xFF77BBFF;
        // 话筒头（3 宽 5 高的圆角块）
        context.fill(x + 3, y, x + 7, y + 6, color);
        // 支架弧线（两侧竖线 + 底部横线）
        context.fill(x + 1, y + 6, x + 2, y + 8, color);
        context.fill(x + 8, y + 6, x + 9, y + 8, color);
        context.fill(x + 2, y + 8, x + 8, y + 9, color);
        // 杆
        context.fill(x + 4, y + 9, x + 6, y + SIZE, color);
    }
}
