/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.ginka.explainstone.fabric;

import com.ginka.explainstone.voice.SpeakerHud;
import com.ginka.explainstone.voice.VoiceKeybinds;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

/**
 * 1.21 的 HUD 与键位接入。
 *
 * <p>1.21 还没有 {@code HudElementRegistry}，用的是 {@code HudRenderCallback}；
 * 键位用 {@code KeyBindingHelper.registerKeyBinding}。
 */
public final class FabricVoiceClient {
    private FabricVoiceClient() {
    }

    public static void register() {
        KeyBindingHelper.registerKeyBinding(VoiceKeybinds.register());
        HudRenderCallback.EVENT.register((context, delta) -> SpeakerHud.render(context, delta));
    }
}
