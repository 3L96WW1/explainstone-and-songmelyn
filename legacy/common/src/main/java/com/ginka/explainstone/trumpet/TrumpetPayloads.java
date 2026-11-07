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

import com.ginka.explainstone.Explainstone;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 特朗谱的网络包。
 *
 * <p>物品不像方块有坐标可以定位，所以这里用「玩家背包槽位号」作标识：
 * 界面打开时记下手上那格的槽位，后续所有操作都带着它，服务端据此改对应格子的物品。
 * 槽位变了（玩家换手、丢出物品）服务端会直接忽略，不会改错东西。
 */
public final class TrumpetPayloads {
    private TrumpetPayloads() {
    }

    /** 界面里的一次操作，语义与方块版的 SpeakerPayloads.Action 一致。 */
    public record Action(int slot, int action, int value) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Action> ID = new CustomPacketPayload.Type<>(
                ResourceLocation.fromNamespaceAndPath(Explainstone.MOD_ID, "trumpet_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Action::slot,
                ByteBufCodecs.VAR_INT, Action::action,
                ByteBufCodecs.VAR_INT, Action::value,
                Action::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /** 导入前问服务端是否已有这份音频。 */
    public record ImportQuery(int slot, String label, String sha1, int total) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ImportQuery> ID = new CustomPacketPayload.Type<>(
                ResourceLocation.fromNamespaceAndPath(Explainstone.MOD_ID, "trumpet_import_query"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ImportQuery> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ImportQuery::slot,
                ByteBufCodecs.STRING_UTF8, ImportQuery::label,
                ByteBufCodecs.STRING_UTF8, ImportQuery::sha1,
                ByteBufCodecs.VAR_INT, ImportQuery::total,
                ImportQuery::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /** 分片上传（导入）。 */
    public record ImportChunk(int slot, String label, String sha1, int index, int total,
                              byte[] data) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ImportChunk> ID = new CustomPacketPayload.Type<>(
                ResourceLocation.fromNamespaceAndPath(Explainstone.MOD_ID, "trumpet_import_chunk"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ImportChunk> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ImportChunk::slot,
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

    /** 客户端告诉服务端「我手上这把要播放第几首」，服务端据此开播并把曲目推给附近玩家。 */
    public record Play(int slot, int index) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Play> ID = new CustomPacketPayload.Type<>(
                ResourceLocation.fromNamespaceAndPath(Explainstone.MOD_ID, "trumpet_play"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Play> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Play::slot,
                ByteBufCodecs.VAR_INT, Play::index,
                Play::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /** 让客户端刷新界面（服务端改完物品组件后回推）。 */
    public record StateSync(int slot, TrumpetState state) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<StateSync> ID = new CustomPacketPayload.Type<>(
                ResourceLocation.fromNamespaceAndPath(Explainstone.MOD_ID, "trumpet_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, StateSync> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, StateSync::slot,
                TrumpetState.NETWORK_CODEC, StateSync::state,
                StateSync::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }
}
