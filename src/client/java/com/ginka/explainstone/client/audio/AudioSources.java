/*
 * explainstone —— 讲解石 / 颂美铃 / 广播发信器（Minecraft Fabric 1.21.1 模组）
 *
 *  作者 / Author : GINKA_3L96WW1
 *  2011
 *
 * 本文件由作者手工编写；贴图与模型由 tools/ 下的脚本程序化生成。
 * 保留此注释即可随意修改、分发。 喵
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.ginka.explainstone.client.audio;

import com.ginka.explainstone.speaker.Track;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** 音频数据来源：模组自带（jar 内）或玩家导入（本地缓存 / 存档） 喵 */
public final class AudioSources {
    /** 曲目时长缓存（秒），-1 表示拿不到 喵 */
    private static final Map<String, Integer> DURATIONS = new HashMap<>();

    private AudioSources() {
    }

    /** 直接解析 ogg 头算时长：采样率来自 Vorbis 识别头，总采样数来自最后一个页的 granule 喵 */
    public static int durationSeconds(Track track) {
        if (track.isFile() && !hasLocal(track)) {
            return -1;   // 还没下载，先不缓存，等拿到文件再算 喵
        }
        Integer cached = DURATIONS.get(track.id());
        if (cached != null) {
            return cached;
        }
        int seconds = -1;
        try (InputStream in = open(track)) {
            if (in != null) {
                byte[] data = in.readAllBytes();
                int rate = sampleRate(data);
                int last = lastIndexOfOggS(data);
                if (rate > 0 && last >= 0 && last + 14 <= data.length) {
                    long granule = 0L;
                    for (int i = 7; i >= 0; i--) {
                        granule = (granule << 8) | (data[last + 6 + i] & 0xFF);
                    }
                    if (granule > 0) {
                        seconds = (int) (granule / rate);
                    }
                }
            }
        } catch (IOException ignored) {
        }
        if (seconds > 0) {
            DURATIONS.put(track.id(), seconds);
        }
        return seconds;
    }

    private static int lastIndexOfOggS(byte[] data) {
        for (int i = data.length - 4; i >= 0; i--) {
            if (data[i] == 'O' && data[i + 1] == 'g' && data[i + 2] == 'g' && data[i + 3] == 'S') {
                return i;
            }
        }
        return -1;
    }

    private static int sampleRate(byte[] data) {
        int first = -1;
        for (int i = 0; i + 4 <= data.length; i++) {
            if (data[i] == 'O' && data[i + 1] == 'g' && data[i + 2] == 'g' && data[i + 3] == 'S') {
                first = i;
                break;
            }
        }
        if (first < 0 || first + 27 >= data.length) {
            return -1;
        }
        int segments = data[first + 26] & 0xFF;
        int pkt = first + 27 + segments;
        if (pkt + 16 > data.length || data[pkt] != 1) {
            return -1;
        }
        int rate = (data[pkt + 12] & 0xFF) | ((data[pkt + 13] & 0xFF) << 8)
                | ((data[pkt + 14] & 0xFF) << 16) | ((data[pkt + 15] & 0xFF) << 24);
        return rate > 0 ? rate : -1;
    }

    /** 客户端缓存目录：config/explainstone/cache 喵 */
    public static Path cacheDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("explainstone").resolve("cache");
    }

    public static Path cacheFile(String sha1) {
        return cacheDir().resolve(sha1 + ".ogg");
    }

    public static boolean hasLocal(Track track) {
        return track.isFile() && Files.isRegularFile(cacheFile(track.sha1()));
    }

    /** 打开曲目的 ogg 数据流；拿不到返回 null 喵 */
    public static InputStream open(Track track) {
        try {
            if (track.isFile()) {
                Path file = cacheFile(track.sha1());
                return Files.isRegularFile(file) ? Files.newInputStream(file) : null;
            }
            String name = track.builtinName();
            // 只允许简单文件名，避免路径穿越 喵
            if (!name.matches("[a-z0-9_\\-]+")) {
                return null;
            }
            return AudioSources.class.getResourceAsStream("/assets/explainstone/sounds/" + name + ".ogg");
        } catch (IOException e) {
            return null;
        }
    }

    public static void saveLocal(String sha1, byte[] bytes) {
        try {
            Path dir = cacheDir();
            Files.createDirectories(dir);
            Files.write(cacheFile(sha1), bytes);
        } catch (IOException ignored) {
        }
    }

    public static Identifier builtinId(String name) {
        return Identifier.of("explainstone", name);
    }
}
