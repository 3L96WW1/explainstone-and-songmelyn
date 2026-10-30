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
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public final class FabricClientNetwork implements Network {
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(SpeakerPayloads.Channel.AUDIO.id(),
                (client, handler, buffer, sender) -> {
                    SpeakerPayloads.AudioChunk payload = SpeakerPayloads.AudioChunk.read(buffer);
                    client.execute(() -> SpeakerAudioCache.onAudioChunk(payload));
                });
        ClientPlayNetworking.registerGlobalReceiver(SpeakerPayloads.Channel.BROADCAST.id(),
                (client, handler, buffer, sender) -> {
                    SpeakerPayloads.Broadcast payload = SpeakerPayloads.Broadcast.read(buffer);
                    client.execute(() -> SpeakerAudioCache.onBroadcast(payload));
                });
    }

    @Override
    public void sendToServer(SpeakerPayloads.Packet payload) {
        FriendlyByteBuf buffer = PacketByteBufs.create();
        payload.write(buffer);
        ClientPlayNetworking.send(payload.type().id(), buffer);
    }

    @Override
    public boolean canSendToServer(SpeakerPayloads.Channel channel) {
        return ClientPlayNetworking.canSend(channel.id());
    }

    @Override
    public void sendToPlayer(ServerPlayer player, SpeakerPayloads.Packet payload) {
        throw new UnsupportedOperationException("Client facade cannot send to a server player");
    }
}
