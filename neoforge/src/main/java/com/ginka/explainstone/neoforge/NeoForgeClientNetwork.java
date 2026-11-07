/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.ginka.explainstone.neoforge;

import com.ginka.explainstone.Explainstone;
import com.ginka.explainstone.client.audio.SpeakerAudioCache;
import com.ginka.explainstone.client.TrumpetPanel;
import com.ginka.explainstone.speaker.SpeakerPayloads;
import com.ginka.explainstone.trumpet.TrumpetPayloads;
import com.ginka.explainstone.voice.VoiceManager;
import com.ginka.explainstone.voice.VoicePayloads;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = Explainstone.MOD_ID, value = Dist.CLIENT)
public final class NeoForgeClientNetwork {
    private NeoForgeClientNetwork() {
    }

    @SubscribeEvent
    public static void onPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(SpeakerPayloads.AudioMeta.ID, SpeakerPayloads.AudioMeta.CODEC,
                (payload, context) -> context.enqueueWork(() -> SpeakerAudioCache.onAudioMeta(payload)));
        registrar.playToClient(SpeakerPayloads.AudioChunk.ID, SpeakerPayloads.AudioChunk.CODEC,
                (payload, context) -> context.enqueueWork(() -> SpeakerAudioCache.onAudioChunk(payload)));
        registrar.playToClient(SpeakerPayloads.ImportReply.ID, SpeakerPayloads.ImportReply.CODEC,
                (payload, context) -> context.enqueueWork(() -> SpeakerAudioCache.onImportReply(payload)));
        registrar.playToClient(SpeakerPayloads.Broadcast.ID, SpeakerPayloads.Broadcast.CODEC,
                (payload, context) -> context.enqueueWork(() -> SpeakerAudioCache.onBroadcast(payload)));
        registrar.playToClient(TrumpetPayloads.StateSync.ID, TrumpetPayloads.StateSync.CODEC,
                (payload, context) -> context.enqueueWork(() -> TrumpetPanel.onStateSync(payload)));
        registrar.playToClient(VoicePayloads.Frame.ID, VoicePayloads.Frame.CODEC,
                (payload, context) -> context.enqueueWork(() -> VoiceManager.onFrame(payload)));
        registrar.playToClient(VoicePayloads.Speaking.ID, VoicePayloads.Speaking.CODEC,
                (payload, context) -> context.enqueueWork(() -> VoiceManager.onSpeaking(payload)));
    }
}
