/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.ginka.explainstone.fabric;

import com.ginka.explainstone.client.audio.SpeakerAudioCache;
import com.ginka.explainstone.platform.Network;
import com.ginka.explainstone.speaker.SpeakerPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public final class FabricClientNetwork implements Network {
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(SpeakerPayloads.AudioChunk.ID,
                (payload, context) -> context.client().execute(() -> SpeakerAudioCache.onAudioChunk(payload)));
        ClientPlayNetworking.registerGlobalReceiver(SpeakerPayloads.Broadcast.ID,
                (payload, context) -> context.client().execute(() -> SpeakerAudioCache.onBroadcast(payload)));
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        ClientPlayNetworking.send(payload);
    }

    @Override
    public boolean canSendToServer(CustomPacketPayload.Type<?> id) {
        return ClientPlayNetworking.canSend(id);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        throw new UnsupportedOperationException("Client cannot send to a server player directly");
    }
}
