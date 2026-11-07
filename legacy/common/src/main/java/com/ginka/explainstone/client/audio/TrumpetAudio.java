/*
 * explainstone —— 讲解石 / 颂美铃 / 广播发信器 / 特朗谱
 *
 * 作者 / Author : GINKA_3L96WW1
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.ginka.explainstone.client.audio;

import com.ginka.explainstone.Explainstone;
import com.ginka.explainstone.speaker.PlayMode;
import com.ginka.explainstone.speaker.Track;
import com.ginka.explainstone.trumpet.TrumpetPayloads;
import com.ginka.explainstone.trumpet.TrumpetState;
import com.ginka.explainstone.trumpet.TrumpetStates;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * 特朗谱的播放协调器。
 *
 * <p>和讲解石最大的不同：讲解石钉在方块坐标上，特朗谱是拿在手里的，声源位置要每帧跟着玩家走。
 * 复用同一套 {@link SpeakerSoundInstance}（流式 ogg + 起点偏移），只是发声点从方块换成玩家。
 *
 * <p>每个槽位同时最多一首：手上那格换了物品、或者玩家停播，就立刻停掉。
 */
public final class TrumpetAudio {
    private static final Logger LOGGER = LoggerFactory.getLogger("explainstone-trumpet");

    /** key 是背包槽位号。 */
    private static final Map<Integer, Playing> ACTIVE = new HashMap<>();
    private static boolean listenerInstalled;

    private static final class Playing {
        private final SpeakerSoundInstance sound;
        private final Track track;
        private final int slot;

        private Playing(SpeakerSoundInstance sound, Track track, int slot) {
            this.sound = sound;
            this.track = track;
            this.slot = slot;
        }
    }

    private TrumpetAudio() {
    }

    /** 音频下载完成后，把因为缺文件没起来的声音重新拉起来。 */
    public static void init() {
        if (listenerInstalled) {
            return;
        }
        listenerInstalled = true;
        SpeakerAudio.addAudioListener(sha1 -> Minecraft.getInstance().execute(() -> restartWaiting(sha1)));
    }

    public static void shutdown() {
        stopAll();
    }

    public static boolean isPlaying(int slot) {
        Playing playing = ACTIVE.get(slot);
        return playing != null && !playing.sound.isStopped();
    }

    public static float[] progress(int slot) {
        Playing playing = ACTIVE.get(slot);
        return playing == null ? null : playing.sound.progress();
    }

    /**
     * 开始播放某个槽位上的第 index 首。
     *
     * <p>只在该槽位状态真的变了时才重开：界面每帧都会调到这里。
     */
    public static void play(int slot, int index) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }
        ItemStack stack = client.player.getInventory().getItem(slot);
        TrumpetState state = TrumpetStates.get(stack);
        if (state.tracks().isEmpty()) {
            stop(slot);
            return;
        }
        int resolved = Math.max(0, Math.min(state.tracks().size() - 1, index));
        Track track = state.tracks().get(resolved);

        Playing current = ACTIVE.get(slot);
        if (current != null && current.track.equals(track) && !current.sound.isStopped()) {
            // 同一首已经在放，只更新音量与可听范围，避免每帧重开
            current.sound.update(state.audibleRange(), state.volume());
            followPlayer(current);
            return;
        }
        stop(slot);

        SpeakerAudio.requestAudio(track);
        // 声音以玩家位置为中心；方块坐标这里只当「声源锚点」用
        BlockPos anchor = client.player.blockPosition();
        SpeakerSoundInstance sound = SpeakerAudio.createSound(anchor, track,
                state.audibleRange(), state.volume(), 0.0);
        if (sound == null) {
            return;
        }
        followPlayer(sound, client.player);
        client.getSoundManager().play(sound);
        ACTIVE.put(slot, new Playing(sound, track, slot));
        LOGGER.info("trumpet play slot={} track={}", slot, track.id());
    }

    public static void stop(int slot) {
        Playing playing = ACTIVE.remove(slot);
        if (playing == null) {
            return;
        }
        playing.sound.stop();
        Minecraft.getInstance().getSoundManager().stop(playing.sound);
        if (!AudioSources.hasLocal(playing.track)) {
            // 还没下完就停了，别再要剩下的分片
            SpeakerAudio.cancelAudio(playing.track);
        }
    }

    public static void stopAll() {
        for (Integer slot : ACTIVE.keySet().toArray(new Integer[0])) {
            stop(slot);
        }
        ACTIVE.clear();
    }

    /** 每 tick 让声音跟着玩家走；玩家换物品就停掉。 */
    public static void tick(Minecraft client) {
        Player player = client.player;
        if (player == null) {
            stopAll();
            return;
        }
        for (Integer slot : ACTIVE.keySet().toArray(new Integer[0])) {
            Playing playing = ACTIVE.get(slot);
            if (playing == null) {
                continue;
            }
            ItemStack stack = player.getInventory().getItem(slot);
            if (!(stack.getItem() instanceof com.ginka.explainstone.trumpet.TrumpetItem)) {
                // 这一格已经不是特朗谱了
                stop(slot);
                continue;
            }
            if (playing.sound.isStopped()) {
                ACTIVE.remove(slot);
                continue;
            }
            followPlayer(playing);
            TrumpetState state = TrumpetStates.get(stack);
            playing.sound.update(state.audibleRange(), state.volume());
        }
    }

    private static void followPlayer(Playing playing) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            followPlayer(playing.sound, client.player);
        }
    }

    private static void followPlayer(SpeakerSoundInstance sound, Player player) {
        sound.moveTo(player.getX(), player.getY() + 1.2, player.getZ());
    }

    /** 音频到了：把还在等文件的声音重开一次。 */
    private static void restartWaiting(String sha1) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }
        for (Map.Entry<Integer, Playing> entry : new ArrayList<>(ACTIVE.entrySet())) {
            Playing playing = entry.getValue();
            if (playing.track.isFile() && playing.track.sha1().equals(sha1) && !playing.sound.isStarted()) {
                int slot = entry.getKey();
                stop(slot);
                TrumpetState state = TrumpetStates.get(client.player.getInventory().getItem(slot));
                play(slot, state.resolvedSelected());
            }
        }
    }

    /** 界面里导入音频。 */
    public static void importFile(Minecraft client, int slot, Path path) {
        SpeakerAudioCache.importFile(client, new SlotImportTarget(slot), path);
    }

    /** 特朗谱的导入目标：包都带槽位号。 */
    private record SlotImportTarget(int slot) implements SpeakerAudioCache.ImportTarget {
        @Override
        public void sendQuery(String label, String sha1, int total) {
            Explainstone.network().sendToServer(new TrumpetPayloads.ImportQuery(this.slot, label, sha1, total));
        }

        @Override
        public void sendChunk(String label, String sha1, int index, int total, byte[] data) {
            Explainstone.network().sendToServer(
                    new TrumpetPayloads.ImportChunk(this.slot, label, sha1, index, total, data));
        }

        @Override
        public void onDone(String sha1, String label) {
            Minecraft client = Minecraft.getInstance();
            if (client.player != null) {
                client.player.displayClientMessage(
                        Component.translatable("message.explainstone.import.ok", label), false);
            }
        }
    }
}
