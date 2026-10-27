/*
 * explainstone —— 讲解石 / 颂美铃 / 广播发信器（Minecraft Fabric 1.21.1 模组）
 *
 *  作者 / Author : GINKA_3L96WW1
 *  2011
 *
 * 本文件由作者手工编写；贴图与模型由 tools/ 下的脚本程序化生成。
 * 保留此注释即可随意修改、分发。 喵
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.ginka.explainstone.speaker;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** 讲解石界面的所有自定义网络包（1.21 的 CustomPayload 写法） 喵 */
public final class SpeakerPayloads {
    // 客户端 → 服务端：界面上的各种操作 喵
    public static final int ACTION_PLAY = 0;
    public static final int ACTION_STOP = 1;
    public static final int ACTION_CYCLE_MODE = 2;
    public static final int ACTION_TOGGLE_REDSTONE = 3;
    public static final int ACTION_SELECT = 4;
    public static final int ACTION_REMOVE_TRACK = 5;
    public static final int ACTION_PLAY_INDEX = 6;
    /** 设置频道（value = 0..999） 喵 */
    public static final int ACTION_SET_CHANNEL = 7;
    /** 切换收信模式 喵 */
    public static final int ACTION_TOGGLE_RECEIVE = 8;
    /** 设置定时播放秒数（value = 0/60/180/600） 喵 */
    public static final int ACTION_SET_TIMER = 9;
    /** 切换本机是否放音 喵 */
    public static final int ACTION_TOGGLE_ENABLED = 10;
    /** 设置发信器传播距离（value = 32..512） 喵 */
    public static final int ACTION_SET_TRANSMIT_RANGE = 11;
    /** 设置讲解石播放距离（value = 8..512） 喵 */
    public static final int ACTION_SET_AUDIBLE_RANGE = 12;
    /** 设置本机音量（value = 0..100） 喵 */
    public static final int ACTION_SET_VOLUME = 13;

    /** 单次传输的分块大小（远小于包上限） 喵 */
    public static final int CHUNK_SIZE = 8000;

    private SpeakerPayloads() {
    }

    /** 播放 / 停止 / 切模式 / 红石开关 / 选曲 / 删曲 喵 */
    public record Action(BlockPos pos, int action, int value) implements CustomPayload {
        public static final CustomPayload.Id<Action> ID =
                new CustomPayload.Id<>(Identifier.of("explainstone", "speaker_action"));
        public static final PacketCodec<RegistryByteBuf, Action> CODEC = PacketCodec.tuple(
                BlockPos.PACKET_CODEC, Action::pos,
                PacketCodecs.VAR_INT, Action::action,
                PacketCodecs.VAR_INT, Action::value,
                Action::new);

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** 客户端 → 服务端：导入一个音频文件（分块上传，存进存档） 喵 */
    public record ImportChunk(BlockPos pos, String label, String sha1, int index, int total, byte[] data)
            implements CustomPayload {
        public static final CustomPayload.Id<ImportChunk> ID =
                new CustomPayload.Id<>(Identifier.of("explainstone", "speaker_import"));
        public static final PacketCodec<RegistryByteBuf, ImportChunk> CODEC = PacketCodec.tuple(
                BlockPos.PACKET_CODEC, ImportChunk::pos,
                PacketCodecs.STRING, ImportChunk::label,
                PacketCodecs.STRING, ImportChunk::sha1,
                PacketCodecs.VAR_INT, ImportChunk::index,
                PacketCodecs.VAR_INT, ImportChunk::total,
                PacketCodecs.BYTE_ARRAY, ImportChunk::data,
                ImportChunk::new);

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** 客户端 → 服务端：本地没有这个音频，请求下载 喵 */
    public record RequestAudio(String sha1) implements CustomPayload {
        public static final CustomPayload.Id<RequestAudio> ID =
                new CustomPayload.Id<>(Identifier.of("explainstone", "speaker_request"));
        public static final PacketCodec<RegistryByteBuf, RequestAudio> CODEC = PacketCodec.tuple(
                PacketCodecs.STRING, RequestAudio::sha1,
                RequestAudio::new);

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** 客户端 → 服务端：我这边没收到广播，请重推一次 喵 */
    public record Resync() implements CustomPayload {
        public static final CustomPayload.Id<Resync> ID =
                new CustomPayload.Id<>(Identifier.of("explainstone", "speaker_resync"));
        public static final PacketCodec<RegistryByteBuf, Resync> CODEC = PacketCodec.unit(new Resync());

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** 服务端 → 客户端：某频道开始/停止广播（信号范围 512 格） 喵 */
    public record Broadcast(int channel, int mode, int index, boolean active, String tracks)
            implements CustomPayload {
        public static final CustomPayload.Id<Broadcast> ID =
                new CustomPayload.Id<>(Identifier.of("explainstone", "speaker_broadcast"));
        public static final PacketCodec<RegistryByteBuf, Broadcast> CODEC = PacketCodec.tuple(
                PacketCodecs.VAR_INT, Broadcast::channel,
                PacketCodecs.VAR_INT, Broadcast::mode,
                PacketCodecs.VAR_INT, Broadcast::index,
                PacketCodecs.BOOL, Broadcast::active,
                PacketCodecs.STRING, Broadcast::tracks,
                Broadcast::new);

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** 服务端 → 客户端：音频文件分块（label 只是给缓存起个可读的名字） 喵 */
    public record AudioChunk(String sha1, String label, int index, int total, byte[] data) implements CustomPayload {
        public static final CustomPayload.Id<AudioChunk> ID =
                new CustomPayload.Id<>(Identifier.of("explainstone", "speaker_audio"));
        public static final PacketCodec<RegistryByteBuf, AudioChunk> CODEC = PacketCodec.tuple(
                PacketCodecs.STRING, AudioChunk::sha1,
                PacketCodecs.STRING, AudioChunk::label,
                PacketCodecs.VAR_INT, AudioChunk::index,
                PacketCodecs.VAR_INT, AudioChunk::total,
                PacketCodecs.BYTE_ARRAY, AudioChunk::data,
                AudioChunk::new);

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
