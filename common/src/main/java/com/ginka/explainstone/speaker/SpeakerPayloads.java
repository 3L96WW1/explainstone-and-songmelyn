/*
 * explainstone —— 讲解石 / 颂美铃 / 广播发信器
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.ginka.explainstone.speaker;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Packet payloads shared by Fabric and Forge on 1.20.1. */
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

    public enum Channel {
        ACTION("speaker_action"),
        IMPORT("speaker_import"),
        REQUEST("speaker_request"),
        RESYNC("speaker_resync"),
        AUDIO("speaker_audio"),
        BROADCAST("speaker_broadcast");

        private final ResourceLocation id;

        Channel(String path) {
            this.id = new ResourceLocation("explainstone", path);
        }

        public ResourceLocation id() {
            return this.id;
        }
    }

    public interface Packet {
        Channel type();

        void write(FriendlyByteBuf buffer);
    }

    private SpeakerPayloads() {
    }

    public record Action(BlockPos pos, int action, int value) implements Packet {
        @Override
        public Channel type() {
            return Channel.ACTION;
        }

        @Override
        public void write(FriendlyByteBuf buffer) {
            buffer.writeBlockPos(this.pos);
            buffer.writeVarInt(this.action);
            buffer.writeVarInt(this.value);
        }

        public static Action read(FriendlyByteBuf buffer) {
            return new Action(buffer.readBlockPos(), buffer.readVarInt(), buffer.readVarInt());
        }
    }

    public record ImportChunk(BlockPos pos, String label, String sha1, int index, int total, byte[] data)
            implements Packet {
        @Override
        public Channel type() {
            return Channel.IMPORT;
        }

        @Override
        public void write(FriendlyByteBuf buffer) {
            buffer.writeBlockPos(this.pos);
            buffer.writeUtf(this.label);
            buffer.writeUtf(this.sha1);
            buffer.writeVarInt(this.index);
            buffer.writeVarInt(this.total);
            buffer.writeByteArray(this.data);
        }

        public static ImportChunk read(FriendlyByteBuf buffer) {
            return new ImportChunk(buffer.readBlockPos(), buffer.readUtf(), buffer.readUtf(),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readByteArray());
        }
    }

    public record RequestAudio(String sha1) implements Packet {
        @Override
        public Channel type() {
            return Channel.REQUEST;
        }

        @Override
        public void write(FriendlyByteBuf buffer) {
            buffer.writeUtf(this.sha1);
        }

        public static RequestAudio read(FriendlyByteBuf buffer) {
            return new RequestAudio(buffer.readUtf());
        }
    }

    public record Resync() implements Packet {
        @Override
        public Channel type() {
            return Channel.RESYNC;
        }

        @Override
        public void write(FriendlyByteBuf buffer) {
        }

        public static Resync read(FriendlyByteBuf buffer) {
            return new Resync();
        }
    }

    public record Broadcast(int channel, int mode, int index, boolean active, String tracks,
                            long session, int elapsedSeconds) implements Packet {
        @Override
        public Channel type() {
            return Channel.BROADCAST;
        }

        @Override
        public void write(FriendlyByteBuf buffer) {
            buffer.writeVarInt(this.channel);
            buffer.writeVarInt(this.mode);
            buffer.writeVarInt(this.index);
            buffer.writeBoolean(this.active);
            buffer.writeUtf(this.tracks);
            buffer.writeLong(this.session);
            buffer.writeVarInt(this.elapsedSeconds);
        }

        public static Broadcast read(FriendlyByteBuf buffer) {
            return new Broadcast(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readBoolean(), buffer.readUtf(), buffer.readLong(), buffer.readVarInt());
        }
    }

    public record AudioChunk(String sha1, String label, int index, int total, byte[] data)
            implements Packet {
        @Override
        public Channel type() {
            return Channel.AUDIO;
        }

        @Override
        public void write(FriendlyByteBuf buffer) {
            buffer.writeUtf(this.sha1);
            buffer.writeUtf(this.label);
            buffer.writeVarInt(this.index);
            buffer.writeVarInt(this.total);
            buffer.writeByteArray(this.data);
        }

        public static AudioChunk read(FriendlyByteBuf buffer) {
            return new AudioChunk(buffer.readUtf(), buffer.readUtf(), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readByteArray());
        }
    }
}
