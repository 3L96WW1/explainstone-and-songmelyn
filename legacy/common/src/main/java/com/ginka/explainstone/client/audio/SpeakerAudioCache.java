/*
 * explainstone —— 讲解石 / 颂美铃 / 广播发信器
 *
 * 作者 / Author : GINKA_3L96WW1
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.ginka.explainstone.client.audio;

import com.ginka.explainstone.Explainstone;
import com.ginka.explainstone.speaker.SpeakerPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/** 客户端音频缓存：接收服务端下发的 ogg，并负责把本地文件上传到服务端仓库。 */
public final class SpeakerAudioCache {
    private static final Logger LOGGER = LoggerFactory.getLogger("explainstone-audio");
    private static final Map<String, Pending> PENDING = new HashMap<>();
    /** 等待服务端答复的导入请求，key 是 sha1。 */
    private static final Map<String, PendingImport> IMPORTS = new HashMap<>();

    private SpeakerAudioCache() {
    }

    /** 服务端先发元信息：拿到总分片数才能分配缓冲，也顺便知道该不该再要。 */
    public static void onAudioMeta(SpeakerPayloads.AudioMeta payload) {
        if (payload.total() <= 0) {
            return;
        }
        Pending existing = PENDING.get(payload.sha1());
        if (existing == null) {
            PENDING.put(payload.sha1(), new Pending(payload.total()));
            return;
        }
        // 之前因为元信息丢了先建了个「总数未知」的，现在补上真实总数，
        // 已经收到的分片要带过来，否则白下一遍
        if (existing.total == Integer.MAX_VALUE) {
            Pending rebuilt = new Pending(payload.total());
            rebuilt.parts.putAll(existing.parts);
            rebuilt.received = existing.received;
            rebuilt.size = existing.size;
            PENDING.put(payload.sha1(), rebuilt);
            if (rebuilt.isComplete()) {
                finish(payload.sha1(), rebuilt);
            }
        }
    }

    public static void onAudioChunk(SpeakerPayloads.AudioChunk payload) {
        Pending pending = PENDING.get(payload.sha1());
        if (pending == null) {
            // 元信息丢了（比如重连时只收到尾巴），先按「未知总数」收着，等 meta 到了再校验
            pending = new Pending(Integer.MAX_VALUE);
            PENDING.put(payload.sha1(), pending);
        }
        pending.put(payload.index(), payload.data());
        if (pending.isComplete()) {
            finish(payload.sha1(), pending);
        }
    }

    private static void finish(String sha1, Pending pending) {
        PENDING.remove(sha1);
        AudioSources.saveLocal(sha1, pending.assemble());
        SpeakerAudio.onAudioReceived(sha1);
    }

    public static void onBroadcast(SpeakerPayloads.Broadcast payload) {
        SpeakerAudio.onBroadcast(payload);
    }

    /** 断点续传用：已经连续收到多少个分片。 */
    public static int receivedChunks(String sha1) {
        Pending pending = PENDING.get(sha1);
        return pending == null ? 0 : pending.received;
    }

    public static void onImportReply(SpeakerPayloads.ImportReply payload) {
        PendingImport pending = IMPORTS.remove(payload.sha1());
        if (pending == null) {
            return;
        }
        if (payload.fromChunk() >= pending.total) {
            // 服务端已有这份数据，一块都不用传
            SpeakerAudio.onImportDone(pending.sha1, pending.label);
            return;
        }
        upload(pending, payload.fromChunk());
    }

    /**
     * 导入一个本地文件。
     *
     * <p>先算 sha1 问服务端有没有；有就直接挂到方块上，省掉整份上传。
     * 这是内容寻址最直接的收益：同一首歌导入十次，只有第一次真的走网络。
     */
    public static void importFile(Minecraft client, BlockPos pos, Path path) {
        try {
            long size = Files.size(path);
            if (size <= 0 || size > 6L * 1024 * 1024) {
                message(client, Component.translatable("message.explainstone.import.too_large"));
                return;
            }
            byte[] bytes = Files.readAllBytes(path);
            if (!isOgg(bytes)) {
                message(client, Component.translatable("message.explainstone.import.not_ogg"));
                return;
            }
            String sha1 = sha1(bytes);
            String label = path.getFileName().toString();
            if (label.toLowerCase().endsWith(".ogg")) {
                label = label.substring(0, label.length() - 4);
            }
            // 本地已经有就不用再存一遍
            if (!AudioSources.hasLocal(sha1)) {
                AudioSources.saveLocal(sha1, bytes);
            }
            int total = (bytes.length + SpeakerPayloads.CHUNK_SIZE - 1) / SpeakerPayloads.CHUNK_SIZE;
            IMPORTS.put(sha1, new PendingImport(pos, label, sha1, bytes, total));
            Explainstone.network().sendToServer(new SpeakerPayloads.ImportQuery(pos, label, sha1, total));
        } catch (IOException e) {
            message(client, Component.translatable("message.explainstone.import.read_failed", String.valueOf(e.getMessage())));
            LOGGER.warn("导入音频失败", e);
        }
    }

    private static void upload(PendingImport pending, int fromChunk) {
        int start = Math.max(0, Math.min(pending.total - 1, fromChunk));
        for (int i = start; i < pending.total; i++) {
            int from = i * SpeakerPayloads.CHUNK_SIZE;
            int len = Math.min(SpeakerPayloads.CHUNK_SIZE, pending.bytes.length - from);
            byte[] part = new byte[len];
            System.arraycopy(pending.bytes, from, part, 0, len);
            Explainstone.network().sendToServer(
                    new SpeakerPayloads.ImportChunk(pending.pos, pending.label, pending.sha1, i, pending.total, part));
        }
    }

    /** 服务端确认导入成功（或无需上传）后由 SpeakerAudio 回调，用来提示玩家。 */
    static void notifyImportOk(String label) {
        Minecraft client = Minecraft.getInstance();
        message(client, Component.translatable("message.explainstone.import.ok", label));
    }

    private static boolean isOgg(byte[] bytes) {
        return bytes.length > 4 && bytes[0] == 'O' && bytes[1] == 'g' && bytes[2] == 'g' && bytes[3] == 'S';
    }

    private static String sha1(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(bytes);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(bytes.length);
        }
    }

    private static void message(Minecraft client, Component text) {
        if (client.player != null) {
            client.player.displayClientMessage(text, false);
        }
    }

    /** 一个还没上传完的导入。 */
    private static final class PendingImport {
        private final BlockPos pos;
        private final String label;
        private final String sha1;
        private final byte[] bytes;
        private final int total;

        private PendingImport(BlockPos pos, String label, String sha1, byte[] bytes, int total) {
            this.pos = pos;
            this.label = label;
            this.sha1 = sha1;
            this.bytes = bytes;
            this.total = total;
        }
    }

    /** 正在接收的下载。分片按 index 存放，支持乱序与断点续传。 */
    private static final class Pending {
        private final Map<Integer, byte[]> parts = new HashMap<>();
        private final int total;
        private int received;
        private int size;

        private Pending(int total) {
            this.total = total;
        }

        private void put(int index, byte[] data) {
            if (index < 0 || this.parts.putIfAbsent(index, data) != null) {
                return;
            }
            this.received++;
            this.size += data.length;
        }

        private boolean isComplete() {
            return this.total != Integer.MAX_VALUE && this.received >= this.total;
        }

        private byte[] assemble() {
            byte[] out = new byte[this.size];
            int offset = 0;
            for (int i = 0; ; i++) {
                byte[] part = this.parts.get(i);
                if (part == null) {
                    break;
                }
                System.arraycopy(part, 0, out, offset, part.length);
                offset += part.length;
            }
            return out;
        }
    }
}
