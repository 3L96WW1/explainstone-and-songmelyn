/*
 * explainstone —— 讲解石 / 颂美铃 / 广播发信器 / 特朗谱
 *
 * 作者 / Author : GINKA_3L96WW1
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.ginka.explainstone.voice;

import com.ginka.explainstone.Explainstone;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/**
 * 语音包。
 *
 * <p>每包就是一段 20ms 的 Opus 数据（约 200 字节），固定头只有序号与发送者，
 * 没有音频数据的冗余。参考 Simple Voice Chat 的 PlayerSoundPacket，但省掉了
 * UDP 那套——我们走 Minecraft 自己的连接，不需要再实现一遍重传。
 */
public final class VoicePayloads {
    /** ByteBufCodecs 里没有 UUID，手写一个。 */
    private static final StreamCodec<FriendlyByteBuf, UUID> UUID_CODEC =
            StreamCodec.of(
                    (buf, id) -> buf.writeUUID(id),
                    buf -> buf.readUUID());

    private VoicePayloads() {
    }

    /** 客户端 → 服务端：我这一帧的声音。 */
    public record Chunk(int sequence, byte[] opus) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Chunk> ID = new CustomPacketPayload.Type<>(
                Identifier.fromNamespaceAndPath(Explainstone.MOD_ID, "voice_chunk"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Chunk> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Chunk::sequence,
                ByteBufCodecs.BYTE_ARRAY, Chunk::opus,
                Chunk::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /** 服务端 → 客户端：谁的这一帧。 */
    public record Frame(UUID sender, int sequence, byte[] opus) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Frame> ID = new CustomPacketPayload.Type<>(
                Identifier.fromNamespaceAndPath(Explainstone.MOD_ID, "voice_frame"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Frame> CODEC = StreamCodec.composite(
                UUID_CODEC, Frame::sender,
                ByteBufCodecs.VAR_INT, Frame::sequence,
                ByteBufCodecs.BYTE_ARRAY, Frame::opus,
                Frame::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /** 服务端 → 客户端：开麦状态变了（有人开始/停止说话），用来画左下角的麦克风图标。
     *  <p>单向：客户端不发这个包，服务端从是否收到 Chunk 自行判断，省一半流量。 */
    public record Speaking(UUID who, boolean speaking) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Speaking> ID = new CustomPacketPayload.Type<>(
                Identifier.fromNamespaceAndPath(Explainstone.MOD_ID, "voice_speaking"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Speaking> CODEC = StreamCodec.composite(
                UUID_CODEC, Speaking::who,
                ByteBufCodecs.BOOL, Speaking::speaking,
                Speaking::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }
}
