/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.ginka.explainstone.fabric;

import com.ginka.explainstone.platform.Network;
import com.ginka.explainstone.speaker.BroadcastRegistry;
import com.ginka.explainstone.speaker.ServerAudioStore;
import com.ginka.explainstone.speaker.SpeakerNetworking;
import com.ginka.explainstone.speaker.SpeakerPayloads;
import com.ginka.explainstone.trumpet.TrumpetNetworking;
import com.ginka.explainstone.trumpet.TrumpetPayloads;
import com.ginka.explainstone.voice.VoiceNetworking;
import com.ginka.explainstone.voice.VoicePayloads;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
        PayloadTypeRegistry.playC2S().register(SpeakerPayloads.Action.ID, SpeakerPayloads.Action.CODEC);
        PayloadTypeRegistry.playC2S().register(SpeakerPayloads.ImportQuery.ID, SpeakerPayloads.ImportQuery.CODEC);
        PayloadTypeRegistry.playC2S().register(SpeakerPayloads.ImportChunk.ID, SpeakerPayloads.ImportChunk.CODEC);
        PayloadTypeRegistry.playC2S().register(SpeakerPayloads.RequestAudio.ID, SpeakerPayloads.RequestAudio.CODEC);
        PayloadTypeRegistry.playC2S().register(SpeakerPayloads.CancelAudio.ID, SpeakerPayloads.CancelAudio.CODEC);
        PayloadTypeRegistry.playC2S().register(SpeakerPayloads.Resync.ID, SpeakerPayloads.Resync.CODEC);
        PayloadTypeRegistry.playS2C().register(SpeakerPayloads.AudioMeta.ID, SpeakerPayloads.AudioMeta.CODEC);
        PayloadTypeRegistry.playS2C().register(SpeakerPayloads.AudioChunk.ID, SpeakerPayloads.AudioChunk.CODEC);
        PayloadTypeRegistry.playS2C().register(SpeakerPayloads.ImportReply.ID, SpeakerPayloads.ImportReply.CODEC);
        PayloadTypeRegistry.playS2C().register(SpeakerPayloads.Broadcast.ID, SpeakerPayloads.Broadcast.CODEC);
        PayloadTypeRegistry.playC2S().register(TrumpetPayloads.Action.ID, TrumpetPayloads.Action.CODEC);
        PayloadTypeRegistry.playC2S().register(TrumpetPayloads.ImportQuery.ID, TrumpetPayloads.ImportQuery.CODEC);
        PayloadTypeRegistry.playC2S().register(TrumpetPayloads.ImportChunk.ID, TrumpetPayloads.ImportChunk.CODEC);
        PayloadTypeRegistry.playC2S().register(TrumpetPayloads.Play.ID, TrumpetPayloads.Play.CODEC);
        PayloadTypeRegistry.playS2C().register(TrumpetPayloads.StateSync.ID, TrumpetPayloads.StateSync.CODEC);
        PayloadTypeRegistry.playC2S().register(VoicePayloads.Chunk.ID, VoicePayloads.Chunk.CODEC);
        PayloadTypeRegistry.playS2C().register(VoicePayloads.Frame.ID, VoicePayloads.Frame.CODEC);
        PayloadTypeRegistry.playS2C().register(VoicePayloads.Speaking.ID, VoicePayloads.Speaking.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(SpeakerPayloads.Action.ID,
                (payload, context) -> context.server().execute(() ->
                        SpeakerNetworking.handleAction(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(SpeakerPayloads.ImportQuery.ID,
                (payload, context) -> context.server().execute(() ->
                        SpeakerNetworking.handleImportQuery(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(SpeakerPayloads.ImportChunk.ID,
                (payload, context) -> context.server().execute(() ->
                        SpeakerNetworking.handleImport(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(SpeakerPayloads.RequestAudio.ID,
                (payload, context) -> context.server().execute(() ->
                        SpeakerNetworking.handleRequest(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(SpeakerPayloads.CancelAudio.ID,
                (payload, context) -> context.server().execute(() ->
                        SpeakerNetworking.handleCancel(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(SpeakerPayloads.Resync.ID,
                (payload, context) -> context.server().execute(() ->
                        SpeakerNetworking.onJoin(context.player())));
        ServerPlayNetworking.registerGlobalReceiver(TrumpetPayloads.Action.ID,
                (payload, context) -> context.server().execute(() ->
                        TrumpetNetworking.handleAction(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(TrumpetPayloads.ImportQuery.ID,
                (payload, context) -> context.server().execute(() ->
                        TrumpetNetworking.handleImportQuery(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(TrumpetPayloads.ImportChunk.ID,
                (payload, context) -> context.server().execute(() ->
                        TrumpetNetworking.handleImport(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(TrumpetPayloads.Play.ID,
                (payload, context) -> context.server().execute(() ->
                        TrumpetNetworking.handleAction(context.player(),
                                new TrumpetPayloads.Action(payload.slot(), 2, payload.index()))));
        ServerPlayNetworking.registerGlobalReceiver(VoicePayloads.Chunk.ID,
                (payload, context) -> context.server().execute(() ->
                        VoiceNetworking.onChunk(context.player(), payload)));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                server.execute(() -> SpeakerNetworking.onJoin(handler.getPlayer())));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                server.execute(() -> {
                    SpeakerNetworking.onDisconnect(handler.getPlayer());
                    BroadcastRegistry.forgetPlayer(handler.getPlayer());
                    TrumpetNetworking.onDisconnect(handler.getPlayer());
                    VoiceNetworking.onDisconnect(handler.getPlayer());
                }));
        // 服务器每 tick 推进下发队列：老实现是一次性把整首歌塞进网络层，
        // 几百个包压在同 tick 里会拖慢主线程，也顶不住网络层积压
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            SpeakerNetworking.tick(server);
            VoiceNetworking.tick(server);
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> ServerAudioStore.migrateLegacy(server));
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
