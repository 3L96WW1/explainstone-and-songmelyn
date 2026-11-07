/*
 * explainstone —— 讲解石 / 颂美铃 / 广播发信器 / 特朗谱
 *
 * 作者 / Author : GINKA_3L96WW1
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.ginka.explainstone.trumpet;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.ginka.explainstone.speaker.PlayMode;
import com.ginka.explainstone.speaker.Track;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * 一把特朗谱的全部状态。
 *
 * <p>存在物品的 {@link com.ginka.explainstone.trumpet.TrumpetComponents} 上，所以每把物品
 * 都有一套独立的播放列表和频道设置——把两把特朗谱分别配成不同频道互不干扰。
 *
 * <p>这个 record 同时给 Codec（存档）和 StreamCodec（同步）用，两者必须保持一致，
 * 否则服务端改完客户端看到的还是旧值。
 */
public record TrumpetState(
        List<Track> tracks,
        int selected,
        PlayMode mode,
        int channel,
        boolean receiveMode,
        int volume,
        int audibleRange,
        int transmitRange,
        boolean speakerEnabled) {

    public static final TrumpetState EMPTY = new TrumpetState(
            List.of(), 0, PlayMode.SEQUENCE, 234, false, 100, 40, 512, true);

    private static final Codec<Track> TRACK_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("label").forGetter(Track::label),
            Codec.STRING.fieldOf("id").forGetter(Track::id)
    ).apply(instance, Track::new));

    public static final Codec<TrumpetState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            TRACK_CODEC.listOf().fieldOf("tracks").forGetter(TrumpetState::tracks),
            Codec.INT.fieldOf("selected").forGetter(TrumpetState::selected),
            PlayMode.CODEC.fieldOf("mode").forGetter(TrumpetState::mode),
            Codec.INT.fieldOf("channel").forGetter(TrumpetState::channel),
            Codec.BOOL.fieldOf("receiveMode").forGetter(TrumpetState::receiveMode),
            Codec.INT.fieldOf("volume").forGetter(TrumpetState::volume),
            Codec.INT.fieldOf("audibleRange").forGetter(TrumpetState::audibleRange),
            Codec.INT.fieldOf("transmitRange").forGetter(TrumpetState::transmitRange),
            Codec.BOOL.fieldOf("speakerEnabled").forGetter(TrumpetState::speakerEnabled)
    ).apply(instance, TrumpetState::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, TrumpetState> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public TrumpetState decode(RegistryFriendlyByteBuf buffer) {
            int count = buffer.readVarInt();
            List<Track> tracks = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                tracks.add(new Track(buffer.readUtf(), buffer.readUtf()));
            }
            return new TrumpetState(
                    tracks,
                    buffer.readVarInt(),
                    buffer.readEnum(PlayMode.class),
                    buffer.readVarInt(),
                    buffer.readBoolean(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readBoolean());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, TrumpetState value) {
            buffer.writeVarInt(value.tracks().size());
            for (Track track : value.tracks()) {
                buffer.writeUtf(track.label());
                buffer.writeUtf(track.id());
            }
            buffer.writeVarInt(value.selected());
            buffer.writeEnum(value.mode());
            buffer.writeVarInt(value.channel());
            buffer.writeBoolean(value.receiveMode());
            buffer.writeVarInt(value.volume());
            buffer.writeVarInt(value.audibleRange());
            buffer.writeVarInt(value.transmitRange());
            buffer.writeBoolean(value.speakerEnabled());
        }
    };

    /** 用同一个 StreamCodec 类型参数，避免调用点到处写泛型。 */
    public static final StreamCodec<RegistryFriendlyByteBuf, TrumpetState> NETWORK_CODEC =
            StreamCodec.of(TrumpetState.STREAM_CODEC::encode, TrumpetState.STREAM_CODEC::decode);

    public TrumpetState {
        tracks = List.copyOf(tracks);
        selected = Math.floorMod(selected, 1000);
        channel = Math.floorMod(channel, 1000);
        volume = Math.max(0, Math.min(100, volume));
        audibleRange = Math.max(8, Math.min(48, audibleRange));
        transmitRange = Math.max(32, Math.min(512, transmitRange));
    }

    public TrumpetState withTracks(List<Track> value) {
        return new TrumpetState(value, this.selected, this.mode, this.channel, this.receiveMode,
                this.volume, this.audibleRange, this.transmitRange, this.speakerEnabled);
    }

    public TrumpetState withSelected(int value) {
        return new TrumpetState(this.tracks, value, this.mode, this.channel, this.receiveMode,
                this.volume, this.audibleRange, this.transmitRange, this.speakerEnabled);
    }

    public TrumpetState withMode(PlayMode value) {
        return new TrumpetState(this.tracks, this.selected, value, this.channel, this.receiveMode,
                this.volume, this.audibleRange, this.transmitRange, this.speakerEnabled);
    }

    public TrumpetState withChannel(int value) {
        return new TrumpetState(this.tracks, this.selected, this.mode, value, this.receiveMode,
                this.volume, this.audibleRange, this.transmitRange, this.speakerEnabled);
    }

    public TrumpetState withReceiveMode(boolean value) {
        return new TrumpetState(this.tracks, this.selected, this.mode, this.channel, value,
                this.volume, this.audibleRange, this.transmitRange, this.speakerEnabled);
    }

    public TrumpetState withVolume(int value) {
        return new TrumpetState(this.tracks, this.selected, this.mode, this.channel, this.receiveMode,
                value, this.audibleRange, this.transmitRange, this.speakerEnabled);
    }

    public TrumpetState withAudibleRange(int value) {
        return new TrumpetState(this.tracks, this.selected, this.mode, this.channel, this.receiveMode,
                this.volume, value, this.transmitRange, this.speakerEnabled);
    }

    public TrumpetState withTransmitRange(int value) {
        return new TrumpetState(this.tracks, this.selected, this.mode, this.channel, this.receiveMode,
                this.volume, this.audibleRange, value, this.speakerEnabled);
    }

    public TrumpetState withSpeakerEnabled(boolean value) {
        return new TrumpetState(this.tracks, this.selected, this.mode, this.channel, this.receiveMode,
                this.volume, this.audibleRange, this.transmitRange, value);
    }

    /** 与讲解石一致：选中项在越界时回落到列表末尾，空列表返回 -1。 */
    public int resolvedSelected() {
        return this.tracks.isEmpty() ? -1 : Math.floorMod(this.selected, this.tracks.size());
    }

    public Track selectedTrack() {
        int i = this.resolvedSelected();
        return i < 0 ? null : this.tracks.get(i);
    }
}
