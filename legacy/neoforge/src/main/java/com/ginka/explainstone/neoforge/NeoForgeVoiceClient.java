/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.ginka.explainstone.neoforge;

import com.ginka.explainstone.Explainstone;
import com.ginka.explainstone.voice.SpeakerHud;
import com.ginka.explainstone.voice.VoiceKeybinds;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/**
 * 1.21 的 HUD 与键位接入。
 *
 * <p>1.21 是 {@code RegisterGuiLayersEvent}，参数要 {@code ResourceLocation} +
 * {@code LayeredDraw.Layer}；26.x 才把它改名为 Layers 并收 {@code Identifier}。
 */
@EventBusSubscriber(modid = Explainstone.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class NeoForgeVoiceClient {
    private static final ResourceLocation VOICE_INDICATOR =
            ResourceLocation.fromNamespaceAndPath(Explainstone.MOD_ID, "voice_indicator");

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(VoiceKeybinds.register());
    }

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiLayersEvent event) {
        event.registerAboveAll(VOICE_INDICATOR, SpeakerHud::render);
    }
}
