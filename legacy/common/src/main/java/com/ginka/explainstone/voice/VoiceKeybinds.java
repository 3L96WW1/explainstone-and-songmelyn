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

import com.ginka.explainstone.Explainstone;
import com.ginka.explainstone.ModItems;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * 「按住说话」键位。
 *
 * <p>默认绑 CapsLock（用户指定）：按住开麦、松开闭麦，不需要额外开关。
 * 键位注册由两个加载器各自调用 {@link #register()} 完成。
 *
 * <p>只有手持特朗谱时才响应，避免聊天/查地图时误开麦。
 */
public final class VoiceKeybinds {
    private static KeyMapping talk;

    private VoiceKeybinds() {
    }

    /** 建键位。必须在客户端初始化阶段调用。 */
    public static KeyMapping register() {
        if (talk != null) {
            return talk;
        }
        // 1.21 的分类只是字符串，跟原版一样用 "key.categories.<name>" 作键
        talk = new KeyMapping(
                "key.explainstone.talk",
                com.mojang.blaze3d.platform.InputConstants.KEY_CAPSLOCK,
                "key.category.explainstone.main");
        return talk;
    }

    public static KeyMapping mapping() {
        return talk;
    }

    /**
     * 每 tick 检查按键状态。按住就开麦，松开就闭麦。
     * 键位可能被玩家改掉，所以每次都用 {@code isDown()} 而不是记状态。
     */
    public static void tick(Minecraft client) {
        if (talk == null) {
            return;
        }
        boolean holding = client.screen == null
                && client.player != null
                && (client.player.getMainHandItem().is(ModItems.trumpet)
                    || client.player.getOffhandItem().is(ModItems.trumpet));
        boolean wantTalk = holding && isDown(client);
        if (wantTalk && !VoiceManager.isTalking()) {
            VoiceManager.startTalking();
        } else if (!wantTalk && VoiceManager.isTalking()) {
            VoiceManager.stopTalking();
        }
    }

    private static boolean isDown(Minecraft client) {
        // 界面打开时按键会被输入框吃掉，这里也统一不算
        return talk.isDown();
    }
}
