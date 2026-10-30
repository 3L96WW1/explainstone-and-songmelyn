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

import com.ginka.explainstone.BroadcastTransmitterBlock;
import com.ginka.explainstone.Explainstone;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 服务端广播登记表：记录每个世界里正在广播的发信器，并把频道与曲目推给附近客户端。 */
public final class BroadcastRegistry {
    public static final double SIGNAL_RANGE = 512.0;

    private static final Map<ResourceKey<Level>, Map<BlockPos, Entry>> ACTIVE = new HashMap<>();

    private BroadcastRegistry() {
    }

    private record Entry(int channel, List<Track> tracks, int index, int mode, int range,
                         long session, long startGameTime) {
        Track current() {
            return this.index >= 0 && this.index < this.tracks.size() ? this.tracks.get(this.index) : null;
        }

        int elapsedSeconds(ServerLevel level) {
            return (int) Math.max(0L, (level.getGameTime() - this.startGameTime) / 20L);
        }
    }

    private static String encode(List<Track> tracks) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tracks.size(); i++) {
            if (i > 0) {
                sb.append('\u0002');
            }
            sb.append(tracks.get(i).label()).append('\u0001').append(tracks.get(i).id());
        }
        return sb.toString();
    }

    public static void refresh(ServerLevel level, BlockPos pos, SpeakerBlockEntity be) {
        BlockState state = level.getBlockState(pos);
        boolean active = state.getBlock() instanceof BroadcastTransmitterBlock
                && state.getValue(BroadcastTransmitterBlock.PLAYING)
                && be.getSelectedTrack() != null;

        Map<BlockPos, Entry> map = ACTIVE.computeIfAbsent(level.dimension(), k -> new HashMap<>());
        int range = Math.max(32, Math.min(512, be.getTransmitRange()));
        Entry old = map.get(pos);
        long session = old != null && old.channel() == be.getChannel()
                && old.index() == be.getSelected() && old.tracks().equals(be.getTracks())
                ? old.session() : level.getGameTime();
        long startGameTime = old != null && old.session() == session ? old.startGameTime() : level.getGameTime();
        Entry entry = active
                ? new Entry(be.getChannel(), be.getTracks(), be.getSelected(), be.getMode().ordinal(), range,
                        session, startGameTime)
                : null;
        if (entry == null) {
            map.remove(pos);
        } else {
            map.put(pos.immutable(), entry);
        }

        for (ServerPlayer player : level.players()) {
            if (player.blockPosition().closerThan(pos, range)) {
                send(player, be.getChannel(), entry, level);
            }
        }
    }

    public static void forget(ServerLevel level, BlockPos pos) {
        Map<BlockPos, Entry> map = ACTIVE.get(level.dimension());
        if (map == null) {
            return;
        }
        Entry removed = map.remove(pos);
        if (removed == null) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            send(player, removed.channel(), null, level);
        }
    }

    public static void onJoin(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Map<BlockPos, Entry> map = ACTIVE.get(level.dimension());
        if (map == null) {
            return;
        }
        for (Map.Entry<BlockPos, Entry> e : map.entrySet()) {
            if (player.blockPosition().closerThan(e.getKey(), e.getValue().range())) {
                send(player, e.getValue().channel(), e.getValue(), level);
            }
        }
    }

    public static void stopAll(ServerLevel level) {
        Map<BlockPos, Entry> map = ACTIVE.get(level.dimension());
        if (map == null) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            for (Entry entry : map.values()) {
                send(player, entry.channel(), null, level);
            }
        }
        map.clear();
    }

    private static void send(ServerPlayer player, int channel, Entry entry, ServerLevel level) {
        if (entry == null) {
            Explainstone.network().sendToPlayer(player,
                    new SpeakerPayloads.Broadcast(channel, 0, 0, false, "", 0L, 0));
        } else {
            Explainstone.network().sendToPlayer(player, new SpeakerPayloads.Broadcast(
                    entry.channel(), entry.mode(), entry.index(), true, encode(entry.tracks()),
                    entry.session(), entry.elapsedSeconds(level)));
        }
    }
}
