/*
 * explainstone —— 讲解石 / 颂美铃 / 广播发信器（Minecraft 1.21.1 模组（Fabric + NeoForge））
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

import com.ginka.explainstone.Explainstone;
import com.ginka.explainstone.SpeakerBlock;
import com.ginka.explainstone.speaker.PlayMode;
import com.ginka.explainstone.speaker.SpeakerBlockEntity;
import com.ginka.explainstone.speaker.SpeakerPayloads;
import com.ginka.explainstone.speaker.Track;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Client-side playback coordinator. The actual OpenAL device/context belongs to
 * Minecraft's SoundEngine; each speaker is a streaming {@link SpeakerSoundInstance}.
 */
public final class SpeakerAudio {
    private static final Logger LOGGER = LoggerFactory.getLogger("explainstone-audio");
    private static final int SCAN_INTERVAL = 20;

    public interface SpeakerSoundFactory {
        SpeakerSoundInstance create(BlockPos pos, Track track, int audibleRange, int volume, double startOffsetSeconds);
    }

    private static final Map<BlockPos, Active> ACTIVE = new HashMap<>();
    private static final Map<Integer, BroadcastInfo> BROADCASTS = new HashMap<>();
    private static final Map<Integer, long[]> BROADCAST_START = new HashMap<>();
    private static final Set<String> REQUESTED = new HashSet<>();
    private static final Random RANDOM = new Random();

    private static SpeakerSoundFactory factory;
    private static List<BlockPos> speakers = List.of();
    private static int tickCounter;
    private static long clock;
    private static boolean initialised;
    private static boolean warnedMissingFactory;

    private record BroadcastInfo(PlayMode mode, int index, List<Track> tracks) {
    }

    private static final class Active {
        private final SpeakerSoundInstance sound;
        private final Track track;
        private final int index;
        private final int selected;
        private final PlayMode mode;
        private final boolean receive;

        private Active(SpeakerSoundInstance sound, Track track, int index, int selected, PlayMode mode, boolean receive) {
            this.sound = sound;
            this.track = track;
            this.index = index;
            this.selected = selected;
            this.mode = mode;
            this.receive = receive;
        }
    }

    private SpeakerAudio() {
    }

    public static void setSoundFactory(SpeakerSoundFactory newFactory) {
        factory = newFactory;
    }

    public static void init() {
        initialised = true;
    }

    public static void shutdown() {
        stopAll();
        BROADCASTS.clear();
        BROADCAST_START.clear();
        REQUESTED.clear();
        speakers = List.of();
        clock = 0L;
        tickCounter = 0;
        initialised = false;
    }

    public static void tick(Minecraft client) {
        if (!initialised) {
            init();
        }
        if (factory == null) {
            if (!warnedMissingFactory) {
                warnedMissingFactory = true;
                LOGGER.warn("Sound factory is not installed; speaker playback disabled");
            }
            return;
        }

        ClientLevel world = client.level;
        LocalPlayer player = client.player;
        if (world == null || player == null) {
            stopAll();
            BROADCASTS.clear();
            BROADCAST_START.clear();
            speakers = List.of();
            return;
        }

        clock++;
        if (tickCounter++ % SCAN_INTERVAL == 0) {
            scan(world, player.blockPosition());
        }

        BROADCAST_START.keySet().removeIf(channel -> !BROADCASTS.containsKey(channel));
        for (Map.Entry<Integer, BroadcastInfo> entry : BROADCASTS.entrySet()) {
            long[] start = BROADCAST_START.get(entry.getKey());
            if (start == null || (int) start[1] != entry.getValue().index()) {
                BROADCAST_START.put(entry.getKey(), new long[]{clock, entry.getValue().index()});
            }
        }

        SoundManager soundManager = client.getSoundManager();
        Set<BlockPos> seen = new HashSet<>();

        for (BlockPos pos : speakers) {
            if (!(world.getBlockEntity(pos) instanceof SpeakerBlockEntity be) || !be.isSpeakerEnabled()) {
                continue;
            }
            seen.add(pos);

            if (be.isReceiveMode()) {
                BroadcastInfo info = BROADCASTS.get(be.getChannel());
                if (info == null || info.tracks().isEmpty()) {
                    stop(pos);
                    continue;
                }
                int index = Math.max(0, Math.min(info.tracks().size() - 1, info.index()));
                Track track = info.tracks().get(index);
                long[] broadcastStart = BROADCAST_START.get(be.getChannel());
                double offset = broadcastStart == null ? 0.0
                        : Math.max(0L, clock - broadcastStart[0]) / 20.0;
                Active active = ACTIVE.get(pos);
                if (active == null || !active.receive || !active.track.equals(track)
                        || active.index != index || !isActive(active)) {
                    stop(pos);
                    play(soundManager, pos, track, index, index, PlayMode.SEQUENCE, true,
                            be.getAudibleRange(), be.getVolume(), offset);
                } else {
                    active.sound.update(be.getAudibleRange(), be.getVolume());
                }
                spawnNotes(world, pos);
            } else if (isPlaying(world, pos)) {
                List<Track> tracks = List.copyOf(be.getTracks());
                if (tracks.isEmpty()) {
                    stop(pos);
                    continue;
                }
                int selected = Math.max(0, Math.min(tracks.size() - 1, be.getSelected()));
                Active active = ACTIVE.get(pos);
                if (active == null || active.receive || active.selected != selected) {
                    stop(pos);
                    play(soundManager, pos, tracks.get(selected), selected, selected, be.getMode(), false,
                            be.getAudibleRange(), be.getVolume(), 0.0);
                } else if (!isActive(active)) {
                    int next = switch (active.mode) {
                        case REPEAT_ONE -> active.index;
                        case SHUFFLE -> {
                            if (tracks.size() <= 1) {
                                yield 0;
                            }
                            int candidate = RANDOM.nextInt(tracks.size() - 1);
                            yield candidate >= active.index ? candidate + 1 : candidate;
                        }
                        case SEQUENCE -> (active.index + 1) % tracks.size();
                    };
                    stop(pos);
                    play(soundManager, pos, tracks.get(next), next, selected, active.mode, false,
                            be.getAudibleRange(), be.getVolume(), 0.0);
                } else {
                    active.sound.update(be.getAudibleRange(), be.getVolume());
                }
                spawnNotes(world, pos);
            } else {
                stop(pos);
            }
        }

        Iterator<Map.Entry<BlockPos, Active>> iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, Active> entry = iterator.next();
            if (!seen.contains(entry.getKey())) {
                entry.getValue().sound.stop();
                soundManager.stop(entry.getValue().sound);
                iterator.remove();
            }
        }

        if (clock % 40 == 0) {
            for (BlockPos pos : speakers) {
                if (world.getBlockEntity(pos) instanceof SpeakerBlockEntity be
                        && be.isReceiveMode() && !BROADCASTS.containsKey(be.getChannel())
                        && Explainstone.network().canSendToServer(SpeakerPayloads.Resync.ID)) {
                    Explainstone.network().sendToServer(new SpeakerPayloads.Resync());
                    LOGGER.info("request broadcast resync for ch={}", be.getChannel());
                    break;
                }
            }
        }
    }

    private static boolean isActive(Active active) {
        // Do not poll SoundManager.isActive here: a streaming sound is not active
        // until its asynchronous stream has been attached. Restarting on that gap
        // cancels every stream before it can play.
        return !active.sound.isStopped();
    }

    private static void play(SoundManager soundManager, BlockPos pos, Track track, int index, int selected,
                             PlayMode mode, boolean receive, int range, int volume, double startOffsetSeconds) {
        requestAudio(track);
        SpeakerSoundInstance sound = factory.create(pos, track, range, volume, startOffsetSeconds);
        ACTIVE.put(pos, new Active(sound, track, index, selected, mode, receive));
        soundManager.play(sound);
        if (receive) {
            LOGGER.info("receive voice start pos={} track={}", pos.toShortString(), track.id());
        } else {
            LOGGER.info("local voice start pos={} track={}", pos.toShortString(), track);
        }
    }

    private static void stop(BlockPos pos) {
        Active active = ACTIVE.remove(pos);
        if (active != null) {
            active.sound.stop();
            Minecraft.getInstance().getSoundManager().stop(active.sound);
        }
    }

    private static void stopAll() {
        SoundManager soundManager = Minecraft.getInstance().getSoundManager();
        for (Active active : ACTIVE.values()) {
            active.sound.stop();
            soundManager.stop(active.sound);
        }
        ACTIVE.clear();
    }

    private static void spawnNotes(ClientLevel world, BlockPos pos) {
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

    // ------------------------------------------------------------------ 扫描
    private static void scan(ClientLevel world, BlockPos center) {
        int radius = 64;
        int chunkRadius = (radius >> 4) + 1;
        int centerChunkX = center.getX() >> 4;
        int centerChunkZ = center.getZ() >> 4;
        int radiusSq = radius * radius;

        List<BlockPos> found = new ArrayList<>();
        for (int chunkX = centerChunkX - chunkRadius; chunkX <= centerChunkX + chunkRadius; chunkX++) {
            for (int chunkZ = centerChunkZ - chunkRadius; chunkZ <= centerChunkZ + chunkRadius; chunkZ++) {
                if (!world.hasChunk(chunkX, chunkZ)) {
                    continue;
                }
                LevelChunk chunk = world.getChunk(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (blockEntity instanceof SpeakerBlockEntity speaker) {
                        addSpeaker(world, center, radiusSq, speaker.getBlockPos().immutable(), found);
                    }
                }
            }
        }
        found.sort((a, b) -> Double.compare(a.distSqr(center), b.distSqr(center)));
        speakers = found;
    }

    private static void addSpeaker(ClientLevel world, BlockPos center, int radiusSq, BlockPos pos, List<BlockPos> out) {
        if (pos.distSqr(center) > radiusSq || out.contains(pos)
                || !(world.getBlockEntity(pos) instanceof SpeakerBlockEntity speaker)
                || !speaker.isSpeakerEnabled()) {
            return;
        }
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof SpeakerBlock)) {
            return;
        }
        if (!speaker.isReceiveMode() && !state.getValue(SpeakerBlock.PLAYING)) {
            return;
        }
        double range = Math.max(8, Math.min(48, speaker.getAudibleRange()));
        if (pos.distSqr(center) <= range * range) {
            out.add(pos);
        }
    }

    private static boolean isPlaying(ClientLevel world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.getBlock() instanceof SpeakerBlock && state.getValue(SpeakerBlock.PLAYING);
    }

    private static void requestAudio(Track track) {
        if (!track.isFile()) {
            return;
        }
        String sha1 = track.sha1();
        if (!REQUESTED.add(sha1)) {
            return;
        }
        if (Explainstone.network().canSendToServer(SpeakerPayloads.RequestAudio.ID)) {
            Explainstone.network().sendToServer(new SpeakerPayloads.RequestAudio(sha1));
        }
    }

    public static float[] progress(BlockPos pos) {
        Active active = ACTIVE.get(pos);
        return active == null ? null : active.sound.progress();
    }

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

    public static void onBroadcast(SpeakerPayloads.Broadcast payload) {
        if (!payload.active()) {
            BROADCASTS.remove(payload.channel());
            BROADCAST_START.remove(payload.channel());
            LOGGER.info("broadcast ch={} stopped", payload.channel());
            return;
        }
        List<Track> tracks = new ArrayList<>();
        for (String part : payload.tracks().split("\u0002")) {
            int sep = part.indexOf('\u0001');
            if (sep > 0) {
                tracks.add(new Track(part.substring(0, sep), part.substring(sep + 1)));
            }
        }
        BROADCASTS.put(payload.channel(),
                new BroadcastInfo(PlayMode.byOrdinal(payload.mode()), payload.index(), List.copyOf(tracks)));
        LOGGER.info("broadcast ch={} mode={} index={} tracks={}",
                payload.channel(), payload.mode(), payload.index(), tracks.size());
    }

    public static void onAudioReceived(String sha1) {
        // The stream itself is already responsible for retrying a missing file;
        // do not tear down a live instance when duplicate chunks arrive.
        REQUESTED.remove(sha1);
    }
}
