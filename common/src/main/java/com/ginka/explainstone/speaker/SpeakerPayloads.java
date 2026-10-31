/*
 * explainstone —— 讲解石 / 颂美铃 / 广播发信器
 *
 * 作者 / Author : GINKA_3L96WW1
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.ginka.explainstone.speaker;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 讲解石界面的所有自定义网络包。 */
public final class SpeakerPayloads {
    public static final int ACTION_PLAY = 0;
    public static final int ACTION_STOP = 1;
    public static final int ACTION_CYCLE_MODE = 2;
    public static final int ACTION_TOGGLE_REDSTONE = 3;
    public static final int ACTION_SELECT = 4;
    public static final int ACTION_REMOVE_TRACK = 5;
    public static final int ACTION_PLAY_INDEX = 6;
    public static final int ACTION_SET_CHANNEL = 7;
    public static final int ACTION_TOGGLE_RECEIVE = 8;
    public static final int ACTION_SET_TIMER = 9;
    public static final int ACTION_TOGGLE_ENABLED = 10;
    public static final int ACTION_SET_TRANSMIT_RANGE = 11;
    public static final int ACTION_SET_AUDIBLE_RANGE = 12;
    public static final int ACTION_SET_VOLUME = 13;

    public static final int CHUNK_SIZE = 8000;

    private SpeakerPayloads() {
    }

    public record Action(BlockPos pos, int action, int value) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Action> ID = new CustomPacketPayload.Type<>(
                Identifier.fromNamespaceAndPath("explainstone", "speaker_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Action::pos,
                ByteBufCodecs.VAR_INT, Action::action,
                ByteBufCodecs.VAR_INT, Action::value,
                Action::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record ImportChunk(BlockPos pos, String label, String sha1, int index, int total, byte[] data)
            implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ImportChunk> ID = new CustomPacketPayload.Type<>(
                Identifier.fromNamespaceAndPath("explainstone", "speaker_import"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ImportChunk> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, ImportChunk::pos,
                ByteBufCodecs.STRING_UTF8, ImportChunk::label,
                ByteBufCodecs.STRING_UTF8, ImportChunk::sha1,
                ByteBufCodecs.VAR_INT, ImportChunk::index,
                ByteBufCodecs.VAR_INT, ImportChunk::total,
                ByteBufCodecs.BYTE_ARRAY, ImportChunk::data,
                ImportChunk::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record RequestAudio(String sha1) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<RequestAudio> ID = new CustomPacketPayload.Type<>(
                Identifier.fromNamespaceAndPath("explainstone", "speaker_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestAudio> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, RequestAudio::sha1,
                RequestAudio::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record Resync() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Resync> ID = new CustomPacketPayload.Type<>(
                Identifier.fromNamespaceAndPath("explainstone", "speaker_resync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Resync> CODEC = StreamCodec.unit(new Resync());

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record Broadcast(int channel, int mode, int index, boolean active, String tracks,
                            long session, int elapsedSeconds)
            implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Broadcast> ID = new CustomPacketPayload.Type<>(
                Identifier.fromNamespaceAndPath("explainstone", "speaker_broadcast"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Broadcast> CODEC = StreamCodec.of(
                (buffer, value) -> {
                    buffer.writeVarInt(value.channel());
                    buffer.writeVarInt(value.mode());
                    buffer.writeVarInt(value.index());
                    buffer.writeBoolean(value.active());
                    buffer.writeUtf(value.tracks());
                    buffer.writeLong(value.session());
                    buffer.writeVarInt(value.elapsedSeconds());
                },
                buffer -> new Broadcast(
                        buffer.readVarInt(),
                        buffer.readVarInt(),
                        buffer.readVarInt(),
                        buffer.readBoolean(),
                        buffer.readUtf(),
                        buffer.readLong(),
                        buffer.readVarInt()));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record AudioChunk(String sha1, String label, int index, int total, byte[] data)
            implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<AudioChunk> ID = new CustomPacketPayload.Type<>(
                Identifier.fromNamespaceAndPath("explainstone", "speaker_audio"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AudioChunk> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, AudioChunk::sha1,
                ByteBufCodecs.STRING_UTF8, AudioChunk::label,
                ByteBufCodecs.VAR_INT, AudioChunk::index,
                ByteBufCodecs.VAR_INT, AudioChunk::total,
                ByteBufCodecs.BYTE_ARRAY, AudioChunk::data,
                AudioChunk::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }
}
