/*
 * explainstone —— 讲解石 / 颂美铃 / 广播发信器（Minecraft Fabric 1.20.1 版）
 *
 *  作者 / Author : GINKA_3L96WW1
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0.
 */
package com.ginka.explainstone.speaker;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** 讲解石的所有网络包（1.20.1 的 PacketByteBuf 写法）喵 */
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

    public static final Identifier CH_ACTION = new Identifier("explainstone", "speaker_action");
    public static final Identifier CH_IMPORT = new Identifier("explainstone", "speaker_import");
    public static final Identifier CH_REQUEST = new Identifier("explainstone", "speaker_request");
    public static final Identifier CH_RESYNC = new Identifier("explainstone", "speaker_resync");
    public static final Identifier CH_AUDIO = new Identifier("explainstone", "speaker_audio");
    public static final Identifier CH_BROADCAST = new Identifier("explainstone", "speaker_broadcast");

    private SpeakerPayloads() {
    }

    /** 播放 / 停止 / 切模式 / 红石 / 选曲 / 删曲 / 频道 / 音量… 喵 */
    public record Action(BlockPos pos, int action, int value) {
        public void write(PacketByteBuf buf) {
            buf.writeBlockPos(this.pos);
            buf.writeVarInt(this.action);
            buf.writeVarInt(this.value);
        }

        public static Action read(PacketByteBuf buf) {
            return new Action(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt());
        }
    }

    /** 导入音频（分块）喵 */
    public record ImportChunk(BlockPos pos, String label, String sha1, int index, int total, byte[] data) {
        public void write(PacketByteBuf buf) {
            buf.writeBlockPos(this.pos);
            buf.writeString(this.label);
            buf.writeString(this.sha1);
            buf.writeVarInt(this.index);
            buf.writeVarInt(this.total);
            buf.writeByteArray(this.data);
        }

        public static ImportChunk read(PacketByteBuf buf) {
            return new ImportChunk(buf.readBlockPos(), buf.readString(), buf.readString(),
                    buf.readVarInt(), buf.readVarInt(), buf.readByteArray());
        }
    }

    /** 客户端请求某首音频 喵 */
    public record RequestAudio(String sha1) {
        public void write(PacketByteBuf buf) {
            buf.writeString(this.sha1);
        }

        public static RequestAudio read(PacketByteBuf buf) {
            return new RequestAudio(buf.readString());
        }
    }

    /** 客户端请求重推广播状态 喵 */
    public record Resync() {
        public void write(PacketByteBuf buf) {
        }

        public static Resync read(PacketByteBuf buf) {
            return new Resync();
        }
    }

    /** 服务端下发广播状态 喵 */
    public record Broadcast(int channel, int mode, int index, boolean active, String tracks) {
        public void write(PacketByteBuf buf) {
            buf.writeVarInt(this.channel);
            buf.writeVarInt(this.mode);
            buf.writeVarInt(this.index);
            buf.writeBoolean(this.active);
            buf.writeString(this.tracks);
        }

        public static Broadcast read(PacketByteBuf buf) {
            return new Broadcast(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readBoolean(), buf.readString());
        }
    }

    /** 服务端下发音频分块 喵 */
    public record AudioChunk(String sha1, String label, int index, int total, byte[] data) {
        public void write(PacketByteBuf buf) {
            buf.writeString(this.sha1);
            buf.writeString(this.label);
            buf.writeVarInt(this.index);
            buf.writeVarInt(this.total);
            buf.writeByteArray(this.data);
        }

        public static AudioChunk read(PacketByteBuf buf) {
            return new AudioChunk(buf.readString(), buf.readString(), buf.readVarInt(),
                    buf.readVarInt(), buf.readByteArray());
        }
    }
}
