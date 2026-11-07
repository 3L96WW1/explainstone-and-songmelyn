/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.ginka.explainstone.neoforge;

import com.ginka.explainstone.Explainstone;
import com.ginka.explainstone.voice.SpeakerHud;
import com.ginka.explainstone.voice.VoiceKeybinds;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/** NeoForge 侧的键位与 HUD 注册（都走 MOD 总线，属于初始化期事件）。 */
@EventBusSubscriber(modid = Explainstone.MOD_ID, value = Dist.CLIENT)
public final class NeoForgeVoiceClient {
    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(VoiceKeybinds.register());
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                Identifier.fromNamespaceAndPath(Explainstone.MOD_ID, "voice_indicator"),
                SpeakerHud::extract);
    }
}
