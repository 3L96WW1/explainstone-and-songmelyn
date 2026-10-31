/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.ginka.explainstone.fabric;

import com.ginka.explainstone.platform.Network;
import com.ginka.explainstone.speaker.SpeakerNetworking;
import com.ginka.explainstone.speaker.SpeakerPayloads;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public final class FabricNetwork implements Network {
    private static Network clientDelegate;

    public static void setClientDelegate(Network delegate) {
        clientDelegate = delegate;
    }

    public static void registerServer() {
        PayloadTypeRegistry.serverboundPlay().register(SpeakerPayloads.Action.ID, SpeakerPayloads.Action.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SpeakerPayloads.ImportChunk.ID, SpeakerPayloads.ImportChunk.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SpeakerPayloads.RequestAudio.ID, SpeakerPayloads.RequestAudio.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SpeakerPayloads.Resync.ID, SpeakerPayloads.Resync.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SpeakerPayloads.AudioChunk.ID, SpeakerPayloads.AudioChunk.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SpeakerPayloads.Broadcast.ID, SpeakerPayloads.Broadcast.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(SpeakerPayloads.Action.ID,
                (payload, context) -> context.server().execute(() ->
                        SpeakerNetworking.handleAction(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(SpeakerPayloads.ImportChunk.ID,
                (payload, context) -> context.server().execute(() ->
                        SpeakerNetworking.handleImport(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(SpeakerPayloads.RequestAudio.ID,
                (payload, context) -> context.server().execute(() ->
                        SpeakerNetworking.handleRequest(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(SpeakerPayloads.Resync.ID,
                (payload, context) -> context.server().execute(() ->
                        SpeakerNetworking.onJoin(context.player())));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                server.execute(() -> SpeakerNetworking.onJoin(handler.getPlayer())));
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        if (clientDelegate == null) {
            throw new UnsupportedOperationException("Client networking is not available on this side");
        }
        clientDelegate.sendToServer(payload);
    }

    @Override
    public boolean canSendToServer(CustomPacketPayload.Type<?> id) {
        return clientDelegate != null && clientDelegate.canSendToServer(id);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }
}
