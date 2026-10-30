/*
 * explainstone —— 讲解石 / 颂美铃 / 广播发信器（Minecraft Fabric 1.21.1 模组）
 *
 *  作者 / Author : GINKA_3L96WW1
 *
 * 本文件由作者手工编写；贴图与模型由 tools/ 下的脚本程序化生成。
 * 保留此注释即可随意修改、分发。 喵
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.ginka.explainstone.client.audio;

import com.ginka.explainstone.speaker.SpeakerPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/** 客户端音频缓存：接收服务端下发的 ogg，并负责把本地文件上传到存档 喵 */
public final class SpeakerAudioCache {
    private static final Logger LOGGER = LoggerFactory.getLogger("explainstone-audio");
    private static final Map<String, Pending> PENDING = new HashMap<>();

    private SpeakerAudioCache() {
    }

    public static void registerReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(SpeakerPayloads.CH_AUDIO, (client, handler, buf, sender) -> {
            SpeakerPayloads.AudioChunk payload = SpeakerPayloads.AudioChunk.read(buf);
            client.execute(() -> onChunk(payload));
        });
        ClientPlayNetworking.registerGlobalReceiver(SpeakerPayloads.CH_BROADCAST, (client, handler, buf, sender) -> {
            SpeakerPayloads.Broadcast payload = SpeakerPayloads.Broadcast.read(buf);
            client.execute(() -> SpeakerAudio.onBroadcast(payload));
        });
    }

    private static void onChunk(SpeakerPayloads.AudioChunk payload) {
        Pending pending = PENDING.computeIfAbsent(payload.sha1(), k -> new Pending(payload.total()));
        pending.put(payload.index(), payload.data());
        if (pending.isComplete()) {
            PENDING.remove(payload.sha1());
            AudioSources.saveLocal(payload.sha1(), pending.bytes());
            SpeakerAudio.onAudioReceived(payload.sha1());
        }
    }

    /** 读取本地 ogg 并上传到服务端（服务端存进存档并加入播放列表） 喵 */
    public static void importFile(MinecraftClient client, BlockPos pos, Path path) {
        try {
            long size = Files.size(path);
            if (size <= 0 || size > 6L * 1024 * 1024) {
                message(client, Text.translatable("message.explainstone.import.too_large"));
                return;
            }
            byte[] bytes = Files.readAllBytes(path);
            if (!isOgg(bytes)) {
                message(client, Text.translatable("message.explainstone.import.not_ogg"));
                return;
            }
            String sha1 = sha1(bytes);
            String label = path.getFileName().toString();
            if (label.toLowerCase().endsWith(".ogg")) {
                label = label.substring(0, label.length() - 4);
            }
            // 先放本地缓存，自己立刻能听 喵
            AudioSources.saveLocal(sha1, bytes);

            int total = (bytes.length + SpeakerPayloads.CHUNK_SIZE - 1) / SpeakerPayloads.CHUNK_SIZE;
            for (int i = 0; i < total; i++) {
                int from = i * SpeakerPayloads.CHUNK_SIZE;
                int len = Math.min(SpeakerPayloads.CHUNK_SIZE, bytes.length - from);
                byte[] part = new byte[len];
                System.arraycopy(bytes, from, part, 0, len);
                var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
                new SpeakerPayloads.ImportChunk(pos, label, sha1, i, total, part).write(buf);
                ClientPlayNetworking.send(SpeakerPayloads.CH_IMPORT, buf);
            }
            message(client, Text.translatable("message.explainstone.import.ok", label));
        } catch (IOException e) {
            message(client, Text.translatable("message.explainstone.import.read_failed", String.valueOf(e.getMessage())));
            LOGGER.warn("导入音频失败", e);
        }
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

    private static void message(MinecraftClient client, net.minecraft.text.Text text) {
        if (client.player != null) {
            client.player.sendMessage(text, false);
        }
    }

    private static final class Pending {
        private final byte[][] chunks;
        private int received;

        private Pending(int total) {
            this.chunks = new byte[Math.max(1, total)][];
        }

        private void put(int index, byte[] data) {
            if (index >= 0 && index < this.chunks.length && this.chunks[index] == null) {
                this.chunks[index] = data;
                this.received++;
            }
        }

        private boolean isComplete() {
            return this.received >= this.chunks.length;
        }

        private byte[] bytes() {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (byte[] chunk : this.chunks) {
                if (chunk != null) {
                    out.writeBytes(chunk);
                }
            }
            return out.toByteArray();
        }
    }
}
