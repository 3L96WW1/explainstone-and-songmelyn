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

    /**
     * 分片大小。8000 是原版包的安全上限，再大单包会被拆成多个 TCP 段，
     * 反而增加握手开销。这个值同时决定断点续传的粒度。
     */
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

    /** 导入前先问服务端有没有这份数据：有就整个上传都省了。 */
    public record ImportQuery(BlockPos pos, String label, String sha1, int total) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ImportQuery> ID = new CustomPacketPayload.Type<>(
                Identifier.fromNamespaceAndPath("explainstone", "speaker_import_query"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ImportQuery> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, ImportQuery::pos,
                ByteBufCodecs.STRING_UTF8, ImportQuery::label,
                ByteBufCodecs.STRING_UTF8, ImportQuery::sha1,
                ByteBufCodecs.VAR_INT, ImportQuery::total,
                ImportQuery::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /** 导入询问的答复。{@code fromChunk >= total} 表示服务端已有，无需上传。 */
    public record ImportReply(String sha1, int fromChunk) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ImportReply> ID = new CustomPacketPayload.Type<>(
                Identifier.fromNamespaceAndPath("explainstone", "speaker_import_reply"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ImportReply> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, ImportReply::sha1,
                ByteBufCodecs.VAR_INT, ImportReply::fromChunk,
                ImportReply::new);

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

    /**
     * 请求下发音频。{@code fromChunk} 是客户端已有的下一个分片号，用于断点续传：
     * 客户端重连或重播时不必从头再下一遍。
     */
    public record RequestAudio(String sha1, int fromChunk) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<RequestAudio> ID = new CustomPacketPayload.Type<>(
                Identifier.fromNamespaceAndPath("explainstone", "speaker_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestAudio> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, RequestAudio::sha1,
                ByteBufCodecs.VAR_INT, RequestAudio::fromChunk,
                RequestAudio::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /** 客户端不再需要这个文件：停播、切歌、离开范围。服务端据此立刻停发。 */
    public record CancelAudio(String sha1) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<CancelAudio> ID = new CustomPacketPayload.Type<>(
                Identifier.fromNamespaceAndPath("explainstone", "speaker_cancel"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CancelAudio> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, CancelAudio::sha1,
                CancelAudio::new);

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

    /** 广播状态。{@code tracks} 只在曲目表真的变化时才带内容，否则为空串。 */
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

    /**
     * 音频元信息：下发前先发一次，客户端据此分配缓冲、显示进度。
     *
     * <p>拆出这个包是为了让 {@link AudioChunk} 变瘦——每个分片只带 sha1 和序号，
     * 不再重复携带曲目标签和总分片数，750 个包能省下约 30 KB。
     */
    public record AudioMeta(String sha1, int total, int bytes, int seconds) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<AudioMeta> ID = new CustomPacketPayload.Type<>(
                Identifier.fromNamespaceAndPath("explainstone", "speaker_audio_meta"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AudioMeta> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, AudioMeta::sha1,
                ByteBufCodecs.VAR_INT, AudioMeta::total,
                ByteBufCodecs.VAR_INT, AudioMeta::bytes,
                ByteBufCodecs.VAR_INT, AudioMeta::seconds,
                AudioMeta::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record AudioChunk(String sha1, int index, byte[] data) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<AudioChunk> ID = new CustomPacketPayload.Type<>(
                Identifier.fromNamespaceAndPath("explainstone", "speaker_audio"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AudioChunk> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, AudioChunk::sha1,
                ByteBufCodecs.VAR_INT, AudioChunk::index,
                ByteBufCodecs.BYTE_ARRAY, AudioChunk::data,
                AudioChunk::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }
}
