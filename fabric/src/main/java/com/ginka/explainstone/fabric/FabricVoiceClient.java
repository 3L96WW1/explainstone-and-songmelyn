/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.ginka.explainstone.fabric;

import com.ginka.explainstone.Explainstone;
import com.ginka.explainstone.voice.SpeakerHud;
import com.ginka.explainstone.voice.VoiceKeybinds;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;

/** Fabric 侧的键位与 HUD 注册。 */
public final class FabricVoiceClient {
    private FabricVoiceClient() {
    }

    public static void register() {
        KeyMappingHelper.registerKeyMapping(VoiceKeybinds.register());
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(Explainstone.MOD_ID, "voice_indicator"),
                SpeakerHud::extract);
    }
}
