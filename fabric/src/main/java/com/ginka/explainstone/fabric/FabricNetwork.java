/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.ginka.explainstone.fabric;

import com.ginka.explainstone.platform.Network;
import com.ginka.explainstone.speaker.SpeakerNetworking;
import com.ginka.explainstone.speaker.SpeakerPayloads;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public final class FabricNetwork implements Network {
    private static Network clientDelegate;

    public static void setClientDelegate(Network delegate) {
        clientDelegate = delegate;
    }

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(SpeakerPayloads.Channel.ACTION.id(),
                (server, player, handler, buffer, sender) -> {
                    SpeakerPayloads.Action payload = SpeakerPayloads.Action.read(buffer);
                    server.execute(() -> SpeakerNetworking.handleAction(player, payload));
                });
        ServerPlayNetworking.registerGlobalReceiver(SpeakerPayloads.Channel.IMPORT.id(),
                (server, player, handler, buffer, sender) -> {
                    SpeakerPayloads.ImportChunk payload = SpeakerPayloads.ImportChunk.read(buffer);
                    server.execute(() -> SpeakerNetworking.handleImport(player, payload));
                });
        ServerPlayNetworking.registerGlobalReceiver(SpeakerPayloads.Channel.REQUEST.id(),
                (server, player, handler, buffer, sender) -> {
                    SpeakerPayloads.RequestAudio payload = SpeakerPayloads.RequestAudio.read(buffer);
                    server.execute(() -> SpeakerNetworking.handleRequest(player, payload));
                });
        ServerPlayNetworking.registerGlobalReceiver(SpeakerPayloads.Channel.RESYNC.id(),
                (server, player, handler, buffer, sender) -> {
                    SpeakerPayloads.Resync.read(buffer);
                    server.execute(() -> SpeakerNetworking.onJoin(player));
                });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                server.execute(() -> SpeakerNetworking.onJoin(handler.getPlayer())));
    }

    @Override
    public void sendToServer(SpeakerPayloads.Packet payload) {
        if (clientDelegate == null) {
            throw new UnsupportedOperationException("Client networking is not available on this side");
        }
        clientDelegate.sendToServer(payload);
    }

    @Override
    public boolean canSendToServer(SpeakerPayloads.Channel channel) {
        return clientDelegate != null && clientDelegate.canSendToServer(channel);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, SpeakerPayloads.Packet payload) {
        FriendlyByteBuf buffer = PacketByteBufs.create();
        payload.write(buffer);
        ServerPlayNetworking.send(player, payload.type().id(), buffer);
    }
}
