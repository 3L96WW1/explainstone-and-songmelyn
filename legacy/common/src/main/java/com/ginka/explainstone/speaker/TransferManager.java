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

import com.ginka.explainstone.Explainstone;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 服务端音频下发队列。
 *
 * <p>老实现是一次性把整个文件的所有分片塞进网络层：6 MB 的曲目会在同一个
 * tick 里排 750 个包，服务器主线程被占住，网络层也会积压。
 *
 * <p>这里改成每 tick 只发固定的字节预算，超出的下一 tick 继续；客户端可以
 * 随时取消，登出自动清理。文件内容按 sha1 在内存里缓一份，多个玩家同时
 * 听同一首歌不会重复读盘。
 */
public final class TransferManager {
    /** 每 tick 每个玩家的下发预算。48 KB/tick ≈ 960 KB/s，一首 5 MB 的歌约 5 秒。 */
    private static final int BYTES_PER_TICK = 48 * 1024;
    /** 内存里最多缓存几个文件的内容，防止大服把内存吃满。 */
    private static final int CONTENT_CACHE = 8;

    private static final Map<UUID, Deque<Transfer>> QUEUES = new HashMap<>();
    private static final Map<String, byte[]> CONTENT = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
            return size() > CONTENT_CACHE;
        }
    };

    private TransferManager() {
    }

    private static final class Transfer {
        private final String sha1;
        private final int total;
        private int next;

        private Transfer(String sha1, int total, int next) {
            this.sha1 = sha1;
            this.total = total;
            this.next = next;
        }
    }

    /**
     * 客户端请求一首曲目。{@code fromChunk} 是它已经有的下一个分片号，
     * 用来实现断点续传——重连后不必从头再下一遍。
     */
    public static void request(ServerPlayer player, String sha1, int fromChunk) {
        if (sha1 == null || sha1.isEmpty() || sha1.length() > 64) {
            return;
        }
        if (!ServerAudioStore.has(sha1)) {
            return;
        }
        Deque<Transfer> queue = QUEUES.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>());
        // 已经在传这个文件就别重复入队，只调整起点
        for (Transfer existing : queue) {
            if (existing.sha1.equals(sha1)) {
                return;
            }
        }
        byte[] data = content(sha1);
        if (data == null) {
            return;
        }
        int total = (data.length + SpeakerPayloads.CHUNK_SIZE - 1) / SpeakerPayloads.CHUNK_SIZE;
        int start = Math.max(0, Math.min(total - 1, fromChunk));
        ServerAudioStore.touch(sha1);
        // 先把总大小和时长告诉客户端，让它能立刻显示进度并分配缓冲
        Explainstone.network().sendToPlayer(player,
                new SpeakerPayloads.AudioMeta(sha1, total, data.length, ServerAudioStore.seconds(sha1)));
        queue.addLast(new Transfer(sha1, total, start));
    }

    /** 客户端不再需要这个文件（停播、切歌、关界面）。 */
    public static void cancel(ServerPlayer player, String sha1) {
        Deque<Transfer> queue = QUEUES.get(player.getUUID());
        if (queue == null) {
            return;
        }
        queue.removeIf(transfer -> transfer.sha1.equals(sha1));
        if (queue.isEmpty()) {
            QUEUES.remove(player.getUUID());
        }
    }

    public static void forget(ServerPlayer player) {
        QUEUES.remove(player.getUUID());
    }

    /** 服务器每 tick 调一次：按预算推进所有队列。 */
    public static void tick(MinecraftServer server) {
        if (QUEUES.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Deque<Transfer>>> players = QUEUES.entrySet().iterator();
        while (players.hasNext()) {
            Map.Entry<UUID, Deque<Transfer>> entry = players.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || player.hasDisconnected()) {
                players.remove();
                continue;
            }
            Deque<Transfer> queue = entry.getValue();
            int budget = BYTES_PER_TICK;
            while (budget > 0 && !queue.isEmpty()) {
                Transfer transfer = queue.peekFirst();
                byte[] data = content(transfer.sha1);
                if (data == null || transfer.next >= transfer.total) {
                    queue.removeFirst();
                    continue;
                }
                if (!sendChunk(player, transfer, data)) {
                    queue.removeFirst();
                    continue;
                }
                budget -= SpeakerPayloads.CHUNK_SIZE;
                if (transfer.next >= transfer.total) {
                    queue.removeFirst();
                }
            }
            if (queue.isEmpty()) {
                players.remove();
            }
        }
    }

    private static boolean sendChunk(ServerPlayer player, Transfer transfer, byte[] data) {
        int from = transfer.next * SpeakerPayloads.CHUNK_SIZE;
        int length = Math.min(SpeakerPayloads.CHUNK_SIZE, data.length - from);
        if (length <= 0) {
            return false;
        }
        byte[] part = new byte[length];
        System.arraycopy(data, from, part, 0, length);
        Explainstone.network().sendToPlayer(player,
                new SpeakerPayloads.AudioChunk(transfer.sha1, transfer.next, part));
        transfer.next++;
        return true;
    }

    private static byte[] content(String sha1) {
        byte[] cached = CONTENT.get(sha1);
        if (cached != null) {
            return cached;
        }
        if (!ServerAudioStore.has(sha1)) {
            return null;
        }
        try {
            byte[] data = Files.readAllBytes(ServerAudioStore.file(sha1));
            CONTENT.put(sha1, data);
            return data;
        } catch (IOException e) {
            Explainstone.LOGGER.warn("读取音频失败 {}", sha1, e);
            return null;
        }
    }

    /** 清空所有状态（服务器关停时）。 */
    public static void reset() {
        QUEUES.clear();
        CONTENT.clear();
    }
}
