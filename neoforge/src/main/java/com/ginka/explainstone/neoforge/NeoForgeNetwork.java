/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.ginka.explainstone.neoforge;

import com.ginka.explainstone.platform.Network;
import com.ginka.explainstone.speaker.SpeakerNetworking;
import com.ginka.explainstone.speaker.SpeakerPayloads;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class NeoForgeNetwork implements Network {
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(SpeakerPayloads.Action.ID, SpeakerPayloads.Action.CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        SpeakerNetworking.handleAction((ServerPlayer) context.player(), payload)));
        registrar.playToServer(SpeakerPayloads.ImportChunk.ID, SpeakerPayloads.ImportChunk.CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        SpeakerNetworking.handleImport((ServerPlayer) context.player(), payload)));
        registrar.playToServer(SpeakerPayloads.RequestAudio.ID, SpeakerPayloads.RequestAudio.CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        SpeakerNetworking.handleRequest((ServerPlayer) context.player(), payload)));
        registrar.playToServer(SpeakerPayloads.Resync.ID, SpeakerPayloads.Resync.CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        SpeakerNetworking.onJoin((ServerPlayer) context.player())));

    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    @Override
    public boolean canSendToServer(CustomPacketPayload.Type<?> id) {
        return true;
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }
}
