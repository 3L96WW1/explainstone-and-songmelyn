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

import com.ginka.explainstone.BroadcastTransmitterBlock;
import com.ginka.explainstone.SpeakerBlock;
import com.ginka.explainstone.speaker.PlayMode;
import com.ginka.explainstone.speaker.SpeakerBlockEntity;
import com.ginka.explainstone.speaker.SpeakerPayloads;
import com.ginka.explainstone.speaker.Track;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.OggAudioStream;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.ALC10;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 自建的 OpenAL 播放引擎（不受资源包限制，能直接播玩家导入的 ogg）。
 * 支持：本地播放列表 / 收信模式（听发信器广播）/ 发信器自身发声。 喵
 */
public final class SpeakerAudio {
    private static final Logger LOGGER = LoggerFactory.getLogger("explainstone-audio");
    private static final int MAX_VOICES = 3;
    /** 单个音源解码后允许占用的 PCM 上限（32MB ≈ 5 分钟单声道 / 2.5 分钟立体声） 喵 */
    private static final long MAX_PCM_BYTES = 32L * 1024 * 1024;
    /** 只提示一次"音频太大" 喵 */
    private static boolean warnedTooBig;
    private static final int SCAN_INTERVAL = 10;
    private static final Predicate<BlockState> HAS_TARGET =
            state -> state.getBlock() instanceof SpeakerBlock || state.getBlock() instanceof BroadcastTransmitterBlock;

    private static final Map<BlockPos, Voice> VOICES = new HashMap<>();
    /** 引擎自己的 tick 时钟（20 tick = 1 秒），用来对齐播放进度 喵 */
    private static long clock;
    /** 频道广播的起播时刻 {起点 tick, 当时的曲目序号}（所有收信讲解石都对齐到它） 喵 */
    private static final Map<Integer, long[]> BROADCAST_START = new HashMap<>();
    /** 本地曲目的起播时刻（同一首曲子的所有讲解石对齐到它） 喵 */
    private static final Map<String, Long> TRACK_START = new HashMap<>();
    /** 服务端推来的广播：频道 → (模式, 当前曲目序号, 曲目列表) 喵 */
    private static final Map<Integer, BroadcastInfo> BROADCASTS = new HashMap<>();

    private record BroadcastInfo(PlayMode mode, int index, java.util.List<Track> tracks) {
    }
    private static final Set<String> REQUESTED = new HashSet<>();
    private static final Random RANDOM = new Random();

    /** 上一轮扫描到的方块（每 10 tick 刷新一次） 喵 */
    private static List<BlockPos> speakers = List.of();
    private static List<BlockPos> transmitters = List.of();

    private static long device;
    private static long context;
    private static boolean available;
    /** 没设备时给玩家提示一次 喵 */
    private static boolean warnNoDevice;
    private static int tickCounter;

    private SpeakerAudio() {
    }

    public static void init() {
        try {
            device = ALC10.alcOpenDevice((ByteBuffer) null);
            if (device == 0L) {
                LOGGER.warn("打不开 OpenAL 设备，讲解石将不会发声");
                warnNoDevice = true;
                return;
            }
            context = ALC10.alcCreateContext(device, (int[]) null);
            if (context == 0L || !ALC10.alcMakeContextCurrent(context)) {
                LOGGER.warn("创建 OpenAL 上下文失败，讲解石将不会发声");
                warnNoDevice = true;
                return;
            }
            available = true;
        } catch (Throwable t) {
            LOGGER.warn("初始化 OpenAL 失败: {}", t.toString());
            available = false;
            warnNoDevice = true;
        }
    }

    public static void shutdown() {
        if (!available) {
            return;
        }
        try {
            for (Voice voice : VOICES.values()) {
                voice.dispose();
            }
            VOICES.clear();
            ALC10.alcMakeContextCurrent(0L);
            if (context != 0L) {
                ALC10.alcDestroyContext(context);
            }
            if (device != 0L) {
                ALC10.alcCloseDevice(device);
            }
        } catch (Throwable ignored) {
        }
        available = false;
    }

    // ------------------------------------------------------------------ tick
    public static void tick(MinecraftClient client) {
        if (!available) {
            if (warnNoDevice && client.player != null) {
                warnNoDevice = false;
                client.player.sendMessage(net.minecraft.text.Text.translatable(
                        "message.explainstone.audio.unavailable"), false);
            }
            return;
        }
        ClientWorld world = client.world;
        ClientPlayerEntity player = client.player;
        if (world == null || player == null) {
            clear();
            return;
        }
        updateListener(client, player);
        clock++;

        // 扫描间隔随半径自适应：范围越大扫得越稀，避免大范围时卡顿 喵
        if (tickCounter++ % scanInterval() == 0) {
            scan(world, player.getBlockPos());
        }

        // 用 RECORDS 分类：MC 声音设置里有独立的"唱片机"滑块，可以直接控制这类音频 喵
        float master = client.options.getSoundVolume(SoundCategory.RECORDS);
        Vec3d ear = player.getEyePos();

        // 1) 广播表由服务端推送（信号范围 512 格），这里直接查 喵
        Map<Integer, BroadcastInfo> broadcasts = BROADCASTS;
        // 每个频道记住"什么时候开始播的"，后加入的收信音响会跳到同样的进度 喵
        BROADCAST_START.keySet().removeIf(ch -> !broadcasts.containsKey(ch));
        for (Map.Entry<Integer, BroadcastInfo> e : broadcasts.entrySet()) {
            long[] start = BROADCAST_START.get(e.getKey());
            if (start == null || (int) start[1] != e.getValue().index()) {
                BROADCAST_START.put(e.getKey(), new long[]{clock, e.getValue().index()});
            }
        }

        Set<BlockPos> active = new HashSet<>();

        // 2) 发信器只发信、不自己出声（声音由收信的讲解石发出） 喵

        // 3) 讲解石：本地播 or 收信 喵
        for (BlockPos pos : speakers) {
            if (!(world.getBlockEntity(pos) instanceof SpeakerBlockEntity be)) {
                continue;
            }
            if (!be.isSpeakerEnabled()) {
                continue;   // 这台讲解石被单独关掉了放音 喵
            }
            if (be.isReceiveMode()) {
                BroadcastInfo info = broadcasts.get(be.getChannel());
                if (info == null || info.tracks().isEmpty()) {
                    continue;
                }
                active.add(pos);
                if (!VOICES.containsKey(pos)) {
                    LOGGER.info("receive voice start ch={} mode={} index={} pos={}",
                            be.getChannel(), info.mode(), info.index(), pos.toShortString());
                }
                long[] start = BROADCAST_START.get(be.getChannel());
                long origin = start == null ? clock : start[0];
                voice(pos).update(info.tracks(), info.index(), info.mode(), ear, master, origin,
                        be.getAudibleRange(), be.getVolume());
                spawnNotes(world, pos);
            } else if (isPlaying(world, pos)) {
                active.add(pos);
                voice(pos).update(be.getTracks(), be.getSelected(), be.getMode(), ear, master, -1L,
                        be.getAudibleRange(), be.getVolume());
                spawnNotes(world, pos);
            }
        }

        // 3.5) 有讲解石在收信、但本端还没有该频道的广播 → 请求服务端重推 喵
        if (clock % 40 == 0) {
            for (BlockPos pos : speakers) {
                if (world.getBlockEntity(pos) instanceof SpeakerBlockEntity be
                        && be.isReceiveMode() && !BROADCASTS.containsKey(be.getChannel())
                        && ClientPlayNetworking.canSend(SpeakerPayloads.Resync.ID)) {
                    ClientPlayNetworking.send(new SpeakerPayloads.Resync());
                    LOGGER.info("request broadcast resync for ch={}", be.getChannel());
                    break;
                }
            }
        }

        // 4) 停掉不该响的音源 喵
        Iterator<Map.Entry<BlockPos, Voice>> it = VOICES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Voice> entry = it.next();
            if (!active.contains(entry.getKey())) {
                entry.getValue().dispose();
                it.remove();
            }
        }

        // 某首本地曲子已经没有任何讲解石在放 → 忘掉它的起点 喵
        TRACK_START.keySet().removeIf(id -> VOICES.values().stream()
                .noneMatch(v -> v.loadedTrack != null && v.loadedTrack.id().equals(id)));
    }

    /** 正在出声的讲解石头顶冒音符（本地播放和收信模式都有） 喵 */
    private static void spawnNotes(ClientWorld world, BlockPos pos) {
        var random = world.random;
        if (random.nextInt(5) != 0) {
            return;
        }
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 1.05;
        double z = pos.getZ() + 0.5;
        for (int i = 0; i < 2; i++) {
            world.addParticle(ParticleTypes.NOTE,
                    x + (random.nextDouble() - 0.5) * 0.6,
                    y,
                    z + (random.nextDouble() - 0.5) * 0.6,
                    random.nextDouble(), 0.0, 0.0);
        }
    }

    private static Voice voice(BlockPos pos) {
        return VOICES.computeIfAbsent(pos, Voice::new);
    }

    private static void updateListener(MinecraftClient client, ClientPlayerEntity player) {
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVec(1.0f);
        AL10.alListener3f(AL10.AL_POSITION, (float) eye.x, (float) eye.y, (float) eye.z);
        AL10.alListenerfv(AL10.AL_ORIENTATION, new float[]{
                (float) look.x, (float) look.y, (float) look.z, 0.0f, 1.0f, 0.0f});
        AL10.alListenerf(AL10.AL_GAIN, 1.0f);   // 主音量由 source 端的 master 承担，避免重复相乘 喵
    }

    private static void clear() {
        if (!available) {
            return;
        }
        for (Voice voice : VOICES.values()) {
            voice.dispose();
        }
        VOICES.clear();
        speakers = List.of();
        transmitters = List.of();
        BROADCASTS.clear();
    }

    // ------------------------------------------------------------------ 扫描 喵
    private static int scanInterval() {
        int radius = 64;
        for (Voice voice : VOICES.values()) {
            radius = Math.max(radius, voice.rangeHint + 16);
        }
        if (radius > 320) {
            return 60;
        }
        return radius > 192 ? 40 : (radius > 96 ? 20 : SCAN_INTERVAL);
    }

    private static void scan(ClientWorld world, BlockPos center) {
        int radius = 64;
        for (Voice voice : VOICES.values()) {
            radius = Math.max(radius, voice.rangeHint + 16);
        }
        radius = Math.min(512, radius);
        int chunkRadius = (radius >> 4) + 1;
        int centerChunkX = center.getX() >> 4;
        int centerChunkZ = center.getZ() >> 4;
        int radiusSq = radius * radius;

        List<BlockPos> foundSpeakers = new ArrayList<>();
        List<BlockPos> foundTransmitters = new ArrayList<>();
        for (int chunkX = centerChunkX - chunkRadius; chunkX <= centerChunkX + chunkRadius; chunkX++) {
            for (int chunkZ = centerChunkZ - chunkRadius; chunkZ <= centerChunkZ + chunkRadius; chunkZ++) {
                if (!world.isChunkLoaded(chunkX, chunkZ)) {
                    continue;
                }
                WorldChunk chunk = world.getChunk(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                collect(world, chunk, center, radiusSq, foundSpeakers, foundTransmitters);
            }
        }
        foundSpeakers.sort((a, b) -> Double.compare(a.getSquaredDistance(center), b.getSquaredDistance(center)));
        foundTransmitters.sort((a, b) -> Double.compare(a.getSquaredDistance(center), b.getSquaredDistance(center)));
        speakers = foundSpeakers;
        transmitters = foundTransmitters;
    }

    private static void collect(ClientWorld world, WorldChunk chunk, BlockPos center, int radiusSq,
                                List<BlockPos> outSpeakers, List<BlockPos> outTransmitters) {
        ChunkSection[] sections = chunk.getSectionArray();
        int startX = chunk.getPos().getStartX();
        int startZ = chunk.getPos().getStartZ();
        for (int index = 0; index < sections.length; index++) {
            ChunkSection section = sections[index];
            if (section == null || section.isEmpty() || !section.hasAny(HAS_TARGET)) {
                continue;
            }
            int baseY = world.sectionIndexToCoord(index) << 4;
            for (int localX = 0; localX < 16; localX++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    for (int localY = 0; localY < 16; localY++) {
                        BlockState state = section.getBlockState(localX, localY, localZ);
                        if (!HAS_TARGET.test(state)) {
                            continue;
                        }
                        BlockPos pos = new BlockPos(startX + localX, baseY + localY, startZ + localZ);
                        if (pos.getSquaredDistance(center) > radiusSq) {
                            continue;
                        }
                        if (state.getBlock() instanceof BroadcastTransmitterBlock) {
                            // 发信器的广播状态由服务端推送，不用在这里扫 喵
                            continue;
                        } else if (world.getBlockEntity(pos) instanceof SpeakerBlockEntity be) {
                            boolean receive = be.isReceiveMode();
                            if (!receive && !state.get(SpeakerBlock.PLAYING)) {
                                continue;
                            }
                            double range = Math.max(8, Math.min(48, be.getAudibleRange()));
                            if (pos.getSquaredDistance(center) <= range * range) {
                                outSpeakers.add(pos);
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean isPlaying(ClientWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.getBlock() instanceof SpeakerBlock && state.get(SpeakerBlock.PLAYING);
    }

    // ------------------------------------------------------------------ 音源 喵
    private static final class Voice {
        private final BlockPos pos;
        private int index = -1;
        private int baseIndex = -1;
        private Track loadedTrack;
        private long syncOrigin = -1L;
        private int rangeHint = 40;
        private int volumePercent = 100;
        private double durationSeconds;
        private int source;
        private int buffer = -1;

        private Voice(BlockPos pos) {
            this.pos = pos;
            this.source = AL10.alGenSources();
            AL10.alSourcef(this.source, AL10.AL_ROLLOFF_FACTOR, 0.0f);
            AL10.alSource3f(this.source, AL10.AL_POSITION,
                    pos.getX() + 0.5f, pos.getY() + 0.5f, pos.getZ() + 0.5f);
            AL10.alSourcef(this.source, AL10.AL_PITCH, 1.0f);
        }

        private void update(List<Track> tracks, int selected, PlayMode mode, Vec3d ear, float categoryGain,
                            long syncOrigin, int audibleRange, int volumePercent) {
            this.syncOrigin = syncOrigin;
            this.volumePercent = Math.max(0, Math.min(100, volumePercent));
            if (tracks.isEmpty()) {
                return;
            }
            if (this.baseIndex != selected) {
                this.baseIndex = selected;
                this.index = selected;
            }
            if (this.index < 0 || this.index >= tracks.size()) {
                this.index = 0;
            }
            Track wanted = tracks.get(this.index);
            if (!wanted.equals(this.loadedTrack)) {
                this.load(wanted);
            }

            double distance = Math.sqrt(this.pos.getSquaredDistance(ear));
            float range = Math.max(8, Math.min(48, audibleRange));
            this.rangeHint = (int) range;
            float distanceGain = distance >= range ? 0.0f : (float) (1.0 - distance / range);
            float volumeScale = this.volumePercent / 100.0f;
            AL10.alSourcef(this.source, AL10.AL_GAIN, Math.max(0.0f, categoryGain * distanceGain * volumeScale));

            if (this.buffer < 0) {
                requestAudio(wanted);
                return;
            }
            if (AL10.alGetSourcei(this.source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) {
                switch (mode) {
                    case REPEAT_ONE -> this.restartAligned();
                    case SHUFFLE -> {
                        if (tracks.size() > 1) {
                            int next = RANDOM.nextInt(tracks.size() - 1);
                            this.index = next >= this.index ? next + 1 : next;
                        }
                        this.load(tracks.get(this.index));
                    }
                    case SEQUENCE -> {
                        this.index = (this.index + 1) % tracks.size();
                        this.load(tracks.get(this.index));
                    }
                }
            }
        }

        private void load(Track track) {
            this.loadedTrack = track;
            if (this.buffer >= 0) {
                AL10.alSourceStop(this.source);
                AL10.alSourcei(this.source, AL10.AL_BUFFER, 0);
                AL10.alDeleteBuffers(this.buffer);
                this.buffer = -1;
            }
            if (track.isFile() && !AudioSources.hasLocal(track)) {
                requestAudio(track);
                return;
            }
            InputStream stream = AudioSources.open(track);
            if (stream == null) {
                return;
            }
            try (OggAudioStream ogg = new OggAudioStream(stream)) {
                javax.sound.sampled.AudioFormat format = ogg.getFormat();
                ByteBuffer pcm = ogg.readAll();
                if (pcm == null || !pcm.hasRemaining()) {
                    return;
                }
                if (!pcm.isDirect()) {
                    ByteBuffer direct = ByteBuffer.allocateDirect(pcm.remaining());
                    direct.put(pcm.duplicate());
                    direct.flip();
                    pcm = direct;
                }
                if (pcm.remaining() > MAX_PCM_BYTES) {
                    LOGGER.warn("音频解码后过大({} MB)，跳过播放: {}",
                            pcm.remaining() / (1024 * 1024), track.id());
                    if (!warnedTooBig) {
                        warnedTooBig = true;
                        MinecraftClient client = MinecraftClient.getInstance();
                        if (client.player != null) {
                            client.player.sendMessage(net.minecraft.text.Text.translatable(
                                    "message.explainstone.audio.too_big"), false);
                        }
                    }
                    return;
                }
                int alFormat = format.getChannels() > 1 ? AL10.AL_FORMAT_STEREO16 : AL10.AL_FORMAT_MONO16;
                int channels = Math.max(1, format.getChannels());
                int sampleRate = (int) format.getSampleRate();
                this.durationSeconds = pcm.remaining() / (2.0 * channels * sampleRate);
                this.buffer = AL10.alGenBuffers();
                AL10.alBufferData(this.buffer, alFormat, pcm, sampleRate);
                AL10.alSourcei(this.source, AL10.AL_BUFFER, this.buffer);
                AL10.alSourcei(this.source, AL10.AL_LOOPING, AL10.AL_FALSE);
                this.seekToOrigin();
                AL10.alSourcePlay(this.source);
            } catch (Exception e) {
                LOGGER.warn("解码音频失败 {}: {}", track.id(), e.toString());
            }
        }

        /** 起点时刻（本次这个音源该从第几秒开始放） 喵 */
        private long origin() {
            if (this.syncOrigin >= 0L) {
                return this.syncOrigin;
            }
            if (this.loadedTrack == null) {
                return clock;
            }
            return TRACK_START.computeIfAbsent(this.loadedTrack.id(), k -> clock);
        }

        private void seekToOrigin() {
            if (this.durationSeconds <= 0.0) {
                return;
            }
            double elapsed = Math.max(0L, clock - this.origin()) / 20.0;
            float offset = (float) (elapsed % this.durationSeconds);
            AL10.alSourcef(this.source, AL11.AL_SEC_OFFSET, offset);
        }

        /** 单曲循环时不要回到 0，而是回到"按公共时间轴该到的位置" 喵 */
        private void restartAligned() {
            if (this.durationSeconds <= 0.0) {
                AL10.alSourceRewind(this.source);
                AL10.alSourcePlay(this.source);
                return;
            }
            this.seekToOrigin();
            AL10.alSourcePlay(this.source);
        }

        private void dispose() {
            if (this.source >= 0) {
                AL10.alSourceStop(this.source);
                AL10.alSourcei(this.source, AL10.AL_BUFFER, 0);
                AL10.alDeleteSources(this.source);
                this.source = -1;
            }
            if (this.buffer >= 0) {
                AL10.alDeleteBuffers(this.buffer);
                this.buffer = -1;
            }
        }
    }

    // ---------------------------------------------------------------- 音频下载 喵
    private static void requestAudio(Track track) {
        if (!track.isFile()) {
            return;
        }
        String sha1 = track.sha1();
        if (!REQUESTED.add(sha1)) {
            return;
        }
        if (ClientPlayNetworking.canSend(SpeakerPayloads.RequestAudio.ID)) {
            ClientPlayNetworking.send(new SpeakerPayloads.RequestAudio(sha1));
        }
    }

    /** 播放进度：{已播放秒, 总秒}；没有在播返回 null 喵 */
    public static float[] progress(BlockPos pos) {
        Voice voice = VOICES.get(pos);
        if (voice == null || voice.buffer < 0 || voice.durationSeconds <= 0.0) {
            return null;
        }
        float offset = AL10.alGetSourcef(voice.source, AL11.AL_SEC_OFFSET);
        return new float[]{(float) Math.max(0.0, Math.min(voice.durationSeconds, offset)),
                (float) voice.durationSeconds};
    }

    /** 广播进度（发信器自己不发声，按公共时间轴算）：{已播秒, 总秒}；没在播返回 null 喵 */
    public static float[] broadcastProgress(int channel) {
        BroadcastInfo info = BROADCASTS.get(channel);
        long[] start = BROADCAST_START.get(channel);
        if (info == null || start == null || info.tracks().isEmpty()) {
            return null;
        }
        int index = Math.max(0, Math.min(info.tracks().size() - 1, info.index()));
        int duration = AudioSources.durationSeconds(info.tracks().get(index));
        if (duration <= 0) {
            return null;
        }
        double elapsed = Math.max(0L, clock - start[0]) / 20.0;
        return new float[]{(float) (elapsed % duration), (float) duration};
    }

    /** 服务端推送：某频道开始/停止广播（带曲目列表、当前序号和播放模式） 喵 */
    public static void onBroadcast(SpeakerPayloads.Broadcast payload) {
        if (!payload.active()) {
            BROADCASTS.remove(payload.channel());
            BROADCAST_START.remove(payload.channel());
            LOGGER.info("broadcast ch={} stopped", payload.channel());
            return;
        }
        java.util.List<Track> tracks = new java.util.ArrayList<>();
        for (String part : payload.tracks().split("\u0002")) {
            int sep = part.indexOf('\u0001');
            if (sep > 0) {
                tracks.add(new Track(part.substring(0, sep), part.substring(sep + 1)));
            }
        }
        BROADCASTS.put(payload.channel(),
                new BroadcastInfo(PlayMode.byOrdinal(payload.mode()), payload.index(), tracks));
        LOGGER.info("broadcast ch={} mode={} index={} tracks={}",
                payload.channel(), payload.mode(), payload.index(), tracks.size());
    }

    public static void onAudioReceived(String sha1) {
        REQUESTED.remove(sha1);
        for (Voice voice : VOICES.values()) {
            if (voice.loadedTrack != null && voice.loadedTrack.isFile() && voice.loadedTrack.sha1().equals(sha1)) {
                voice.loadedTrack = null;
            }
        }
    }
}
