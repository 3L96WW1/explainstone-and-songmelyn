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
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务端音频仓库：内容寻址（文件名就是 sha1），不放在存档里。
 *
 * <p>放在 config 下是关键：存档会整体复制、备份、上传，音频一旦混进去，
 * 每个存档副本都会背着一份几百兆的歌单。放在 config 下则所有存档共用，
 * 同一首歌只存一份。
 *
 * <p>另有一份 {@code index.json} 记录文件大小、时长和最后使用时间，
 * 用来在超出配额时按「最久没用到」回收。
 */
public final class ServerAudioStore {
    /** 配额：所有音频加起来的上限，超出按 LRU 回收。 */
    private static final long MAX_TOTAL_BYTES = 512L * 1024 * 1024;
    /** 配额：文件数上限，防止几万个短音频拖慢目录扫描。 */
    private static final int MAX_FILES = 1024;
    private static final String INDEX_NAME = "index.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Map<String, Entry> INDEX = new HashMap<>();
    private static Path resolvedDir;
    private static boolean loaded;
    private static boolean dirty;

    /** 索引里的一项。lastUsed 用 epoch millis，便于跨重启比较。 */
    public static final class Entry {
        public long size;
        public int seconds;
        public long lastUsed;

        Entry(long size, int seconds, long lastUsed) {
            this.size = size;
            this.seconds = seconds;
            this.lastUsed = lastUsed;
        }

        Entry() {
        }
    }

    private ServerAudioStore() {
    }

    /** 音频目录：config/explainstone/audio 喵 */
    public static Path dir() {
        if (resolvedDir == null) {
            resolvedDir = Explainstone.platform().configDir().resolve("explainstone").resolve("audio");
        }
        return resolvedDir;
    }

    public static Path file(String sha1) {
        return dir().resolve(sha1 + ".ogg");
    }

    public static boolean has(String sha1) {
        load();
        return sha1 != null && !sha1.isEmpty() && Files.isRegularFile(file(sha1));
    }

    public static int seconds(String sha1) {
        load();
        Entry entry = INDEX.get(sha1);
        return entry == null ? -1 : entry.seconds;
    }

    /** 记录一次使用，供 LRU 回收排序。不每次都落盘，避免频繁写。 */
    public static void touch(String sha1) {
        load();
        Entry entry = INDEX.get(sha1);
        if (entry == null) {
            return;
        }
        entry.lastUsed = System.currentTimeMillis();
        dirty = true;
    }

    /** 写入音频。已经存在则只更新索引，不重复写盘——这就是内容寻址的去重。 */
    public static void put(String sha1, byte[] bytes, int seconds) {
        load();
        Path target = file(sha1);
        try {
            Files.createDirectories(dir());
            if (!Files.isRegularFile(target)) {
                // 先写临时文件再改名，避免写到一半崩溃留下半截文件被当成完整曲目
                Path temp = dir().resolve(sha1 + ".ogg.tmp");
                Files.write(temp, bytes);
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            INDEX.put(sha1, new Entry(bytes.length, seconds, System.currentTimeMillis()));
            dirty = true;
            save();
            gc();
        } catch (IOException e) {
            Explainstone.LOGGER.warn("写入音频失败 {}", sha1, e);
        }
    }

    /**
     * 把旧版本放在存档里的音频搬到新目录。
     *
     * <p>老存档的音频在 {@code <world>/explainstone/audio/}，直接删掉会让玩家
     * 已有的曲目全部失效，所以首次启动时搬过来，搬完再把旧目录删掉。
     */
    public static void migrateLegacy(MinecraftServer server) {
        load();
        Path legacy = server.getWorldPath(LevelResource.ROOT).resolve("explainstone").resolve("audio");
        if (!Files.isDirectory(legacy)) {
            return;
        }
        int moved = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(legacy, "*.ogg")) {
            for (Path source : stream) {
                String name = source.getFileName().toString();
                String sha1 = name.substring(0, name.length() - 4);
                Path target = file(sha1);
                if (!Files.isRegularFile(target)) {
                    Files.createDirectories(dir());
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                    moved++;
                }
                if (!INDEX.containsKey(sha1)) {
                    INDEX.put(sha1, new Entry(Files.size(source), oggSeconds(source), System.currentTimeMillis()));
                    dirty = true;
                }
            }
        } catch (IOException e) {
            Explainstone.LOGGER.warn("迁移旧音频失败", e);
            return;
        }
        if (moved > 0) {
            Explainstone.LOGGER.info("从存档迁移了 {} 个音频文件到 {}", moved, dir());
        }
        save();
        // 搬完就把旧目录删掉，否则存档里那份白占空间
        deleteTree(legacy);
        Path parent = legacy.getParent();
        if (parent != null) {
            deleteTree(parent);
        }
    }

    /** 按配额回收：文件数或总字节超限时，从最久没用到的开始删。 */
    public static void gc() {
        load();
        try {
            Files.createDirectories(dir());
            long total = 0L;
            int count = 0;
            Map<String, Long> onDisk = new HashMap<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir(), "*.ogg")) {
                for (Path path : stream) {
                    String name = path.getFileName().toString();
                    String sha1 = name.substring(0, name.length() - 4);
                    long size;
                    try {
                        size = Files.size(path);
                    } catch (IOException ignored) {
                        continue;
                    }
                    onDisk.put(sha1, size);
                    total += size;
                    count++;
                }
            }
            if (total <= MAX_TOTAL_BYTES && count <= MAX_FILES) {
                return;
            }
            List<Map.Entry<String, Long>> candidates = new ArrayList<>(onDisk.entrySet());
            // 索引里没有记录的当 0，最优先删
            candidates.sort(Comparator.comparingLong(e -> {
                Entry entry = INDEX.get(e.getKey());
                return entry == null ? 0L : entry.lastUsed;
            }));
            for (Map.Entry<String, Long> candidate : candidates) {
                if (total <= MAX_TOTAL_BYTES && count <= MAX_FILES) {
                    break;
                }
                String sha1 = candidate.getKey();
                try {
                    Files.deleteIfExists(file(sha1));
                    total -= candidate.getValue();
                    count--;
                    INDEX.remove(sha1);
                    dirty = true;
                    Explainstone.LOGGER.info("回收音频 {}", sha1);
                } catch (IOException ignored) {
                }
            }
            save();
        } catch (IOException ignored) {
        }
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path index = dir().resolve(INDEX_NAME);
        if (!Files.isRegularFile(index)) {
            return;
        }
        try {
            JsonObject root = GSON.fromJson(Files.readString(index), JsonObject.class);
            if (root == null) {
                return;
            }
            for (String key : root.keySet()) {
                JsonObject value = root.getAsJsonObject(key);
                Entry entry = new Entry();
                entry.size = value.has("size") ? value.get("size").getAsLong() : 0L;
                entry.seconds = value.has("seconds") ? value.get("seconds").getAsInt() : -1;
                entry.lastUsed = value.has("lastUsed") ? value.get("lastUsed").getAsLong() : 0L;
                INDEX.put(key, entry);
            }
        } catch (IOException | RuntimeException e) {
            Explainstone.LOGGER.warn("读取音频索引失败，将重建", e);
        }
    }

    private static void save() {
        if (!dirty) {
            return;
        }
        dirty = false;
        JsonObject root = new JsonObject();
        for (Map.Entry<String, Entry> entry : INDEX.entrySet()) {
            JsonObject value = new JsonObject();
            value.addProperty("size", entry.getValue().size);
            value.addProperty("seconds", entry.getValue().seconds);
            value.addProperty("lastUsed", entry.getValue().lastUsed);
            root.add(entry.getKey(), value);
        }
        try {
            Files.createDirectories(dir());
            Path index = dir().resolve(INDEX_NAME);
            Path temp = dir().resolve(INDEX_NAME + ".tmp");
            Files.writeString(temp, GSON.toJson(root));
            Files.move(temp, index, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
        }
    }

    /** 解析 ogg 时长；失败返回 -1（此时不限制播放，只是进度条不显示）。 */
    private static int oggSeconds(Path path) {
        try {
            return oggSeconds(Files.readAllBytes(path));
        } catch (IOException e) {
            return -1;
        }
    }

    static int oggSeconds(byte[] data) {
        int rate = sampleRate(data);
        if (rate <= 0) {
            return -1;
        }
        for (int i = data.length - 4; i >= 0; i--) {
            if (data[i] == 'O' && data[i + 1] == 'g' && data[i + 2] == 'g' && data[i + 3] == 'S'
                    && i + 14 <= data.length) {
                long granule = 0L;
                for (int b = 7; b >= 0; b--) {
                    granule = (granule << 8) | (data[i + 6 + b] & 0xFF);
                }
                return granule > 0 ? (int) (granule / rate) : -1;
            }
        }
        return -1;
    }

    private static int sampleRate(byte[] data) {
        for (int i = 0; i + 4 <= data.length; i++) {
            if (data[i] == 'O' && data[i + 1] == 'g' && data[i + 2] == 'g' && data[i + 3] == 'S') {
                int segments = data[i + 26] & 0xFF;
                int packet = i + 27 + segments;
                if (packet + 16 <= data.length && data[packet] == 1) {
                    int rate = (data[packet + 12] & 0xFF) | ((data[packet + 13] & 0xFF) << 8)
                            | ((data[packet + 14] & 0xFF) << 16) | ((data[packet + 15] & 0xFF) << 24);
                    return rate > 0 ? rate : -1;
                }
                return -1;
            }
        }
        return -1;
    }

    private static void deleteTree(Path path) {
        try {
            if (Files.isDirectory(path)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                    for (Path child : stream) {
                        Files.deleteIfExists(child);
                    }
                }
            }
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }
}
