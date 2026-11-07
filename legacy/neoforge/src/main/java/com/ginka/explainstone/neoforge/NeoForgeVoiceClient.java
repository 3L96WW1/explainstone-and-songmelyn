/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.ginka.explainstone.neoforge;

import com.ginka.explainstone.Explainstone;
import com.ginka.explainstone.voice.SpeakerHud;
import com.ginka.explainstone.voice.VoiceKeybinds;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiOverlaysEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/**
 * 1.21 的 HUD 与键位接入。
 *
 * <p>1.21 用 {@code RegisterGuiOverlaysEvent}（不是 26.x 的 {@code RegisterGuiLayersEvent}），
 * 回调签名是 {@code render(GuiGraphics, DeltaTracker)}。
 */
@EventBusSubscriber(modid = Explainstone.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class NeoForgeVoiceClient {
    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(VoiceKeybinds.register());
    }

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("voice_indicator", SpeakerHud::render);
    }
}
