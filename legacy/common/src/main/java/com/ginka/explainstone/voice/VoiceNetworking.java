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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 服务端语音转发。
 *
 * <p>按距离转发：谁说话，就把这一帧发给「在传播距离内」的玩家。
 * 不做房间/频道概念——服务器里大家本来就站在同一片地方，按距离自然形成"附近能听见"。
 *
 * <p>说话状态靠「有没有收到声音」推断，客户端不另发状态包：
 * 收到第一帧就标记为在说，超过 {@link #SILENCE_MILLIS} 没有新帧就标记为停。
 * 这样既省一个往返，也不怕客户端异常退出把状态卡在"正在说"。
 */
public final class VoiceNetworking {
    /** 传播距离上限，与特朗谱物品上的 transmitRange 上限一致。 */
    private static final double MAX_RANGE = 512.0;
    /** 这么久没收到新帧就当他说完了。Opus 是 20ms 一帧，1 秒足够宽容。 */
    private static final long SILENCE_MILLIS = 1000L;

    /** 谁在说话 + 最后一次收到帧的时间。 */
    private static final Map<UUID, Long> SPEAKING = new HashMap<>();

    private VoiceNetworking() {
    }

    /** 客户端说了一帧：转发给传播距离内所有玩家。 */
    public static void onChunk(ServerPlayer sender, VoicePayloads.Chunk payload) {
        if (payload.opus().length == 0 || payload.opus().length > OpusCodec.MAX_PACKET) {
            return;
        }
        var level = sender.level();
        double rangeSq = MAX_RANGE * MAX_RANGE;
        for (Player listener : level.players()) {
            if (listener == sender) {
                continue;
            }
            if (listener.distanceToSqr(sender) > rangeSq) {
                continue;
            }
            Explainstone.network().sendToPlayer(listener,
                    new VoicePayloads.Frame(sender.getUUID(), payload.sequence(), payload.opus()));
        }
        if (SPEAKING.put(sender.getUUID(), System.currentTimeMillis()) == null) {
            broadcastState(sender, true);
        }
    }

    /**
     * 每 tick 检查谁已经安静了。
     * 不用客户端发"我说完了"，避免丢包导致图标一直亮着。
     */
    public static void tick(MinecraftServer server) {
        if (SPEAKING.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (UUID who : SPEAKING.keySet().toArray(new UUID[0])) {
            Long last = SPEAKING.get(who);
            if (last != null && now - last <= SILENCE_MILLIS) {
                continue;
            }
            SPEAKING.remove(who);
            ServerPlayer player = server.getPlayerList().getPlayer(who);
            if (player != null) {
                broadcastState(player, false);
            }
        }
    }

    private static void broadcastState(ServerPlayer speaker, boolean speaking) {
        var level = speaker.level();
        double rangeSq = MAX_RANGE * MAX_RANGE;
        for (Player listener : level.players()) {
            if (listener == speaker) {
                continue;
            }
            if (listener.distanceToSqr(speaker) > rangeSq) {
                continue;
            }
            Explainstone.network().sendToPlayer(listener,
                    new VoicePayloads.Speaking(speaker.getUUID(), speaking));
        }
    }

    /** 某个玩家断开时清掉状态，别让图标留在别人屏幕上。 */
    public static void onDisconnect(ServerPlayer player) {
        if (SPEAKING.remove(player.getUUID()) != null) {
            broadcastState(player, false);
        }
    }

    public static void reset() {
        SPEAKING.clear();
    }
}
