/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.ginka.explainstone.neoforge;

import com.ginka.explainstone.Explainstone;
import com.ginka.explainstone.client.audio.SpeakerAudioCache;
import com.ginka.explainstone.speaker.SpeakerPayloads;
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
        registrar.playToClient(SpeakerPayloads.AudioChunk.ID, SpeakerPayloads.AudioChunk.CODEC,
                (payload, context) -> context.enqueueWork(() -> SpeakerAudioCache.onAudioChunk(payload)));
        registrar.playToClient(SpeakerPayloads.Broadcast.ID, SpeakerPayloads.Broadcast.CODEC,
                (payload, context) -> context.enqueueWork(() -> SpeakerAudioCache.onBroadcast(payload)));
    }
}
