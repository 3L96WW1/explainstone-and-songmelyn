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
import com.ginka.explainstone.ExplainstoneConfig;
import com.ginka.explainstone.SpeakerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** 服务端逻辑：处理界面操作、接收导入的 ogg、把存档里的音频发给客户端。 */
public final class SpeakerNetworking {
    private static final int MAX_FILE_BYTES = 6 * 1024 * 1024;
    private static final int MAX_SECONDS = 5 * 60;
    private static final double MAX_INTERACT_DISTANCE_SQ = 64 * 64;

    private static final Map<String, ByteArrayOutputStream> UPLOADS = new HashMap<>();

    private SpeakerNetworking() {
    }

    public static Path audioDir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("explainstone").resolve("audio");
    }

    public static void handleAction(ServerPlayer player, SpeakerPayloads.Action payload) {
        BlockPos pos = payload.pos();
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (player.blockPosition().distSqr(pos) > MAX_INTERACT_DISTANCE_SQ) {
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof SpeakerBlockEntity be)) {
            return;
        }

        boolean isTransmitter = level.getBlockState(pos).getBlock() instanceof BroadcastTransmitterBlock;
        boolean broadcasting = payload.action() == SpeakerPayloads.ACTION_PLAY
                || payload.action() == SpeakerPayloads.ACTION_PLAY_INDEX;
        if (isTransmitter && broadcasting && ExplainstoneConfig.broadcastRequiresOp()
                && !player.hasPermissions(2)) {
            player.displayClientMessage(Component.translatable("message.explainstone.broadcast.no_permission"), false);
            return;
        }

        switch (payload.action()) {
            case SpeakerPayloads.ACTION_PLAY -> {
                setPlaying(level, pos, true);
                if (be.getTimerSeconds() > 0) {
                    be.startTimer();
                }
            }
            case SpeakerPayloads.ACTION_STOP -> {
                setPlaying(level, pos, false);
                be.stopTimer();
            }
            case SpeakerPayloads.ACTION_SET_CHANNEL -> be.setChannel(payload.value());
            case SpeakerPayloads.ACTION_TOGGLE_RECEIVE -> be.setReceiveMode(!be.isReceiveMode());
            case SpeakerPayloads.ACTION_SET_TIMER -> be.setTimerSeconds(payload.value());
            case SpeakerPayloads.ACTION_TOGGLE_ENABLED -> be.setSpeakerEnabled(!be.isSpeakerEnabled());
            case SpeakerPayloads.ACTION_SET_TRANSMIT_RANGE -> be.setTransmitRange(payload.value());
            case SpeakerPayloads.ACTION_SET_AUDIBLE_RANGE -> be.setAudibleRange(payload.value());
            case SpeakerPayloads.ACTION_SET_VOLUME -> be.setVolume(payload.value());
            case SpeakerPayloads.ACTION_PLAY_INDEX -> {
                be.setSelected(payload.value());
                setPlaying(level, pos, true);
            }
            case SpeakerPayloads.ACTION_CYCLE_MODE -> be.setMode(be.getMode().next());
            case SpeakerPayloads.ACTION_TOGGLE_REDSTONE -> be.setRespectRedstone(!be.isRespectRedstone());
            case SpeakerPayloads.ACTION_SELECT -> be.setSelected(payload.value());
            case SpeakerPayloads.ACTION_REMOVE_TRACK -> be.removeTrack(payload.value());
            default -> {
            }
        }

        if (level.getBlockState(pos).getBlock() instanceof BroadcastTransmitterBlock) {
            BroadcastRegistry.refresh(level, pos, be);
        }
    }

    private static void setPlaying(ServerLevel level, BlockPos pos, boolean playing) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof BroadcastTransmitterBlock
                && state.getValue(BroadcastTransmitterBlock.PLAYING) != playing) {
            level.setBlock(pos, state.setValue(BroadcastTransmitterBlock.PLAYING, playing), Block.UPDATE_ALL);
        } else if (state.getBlock() instanceof SpeakerBlock && state.getValue(SpeakerBlock.PLAYING) != playing) {
            level.setBlock(pos, state.setValue(SpeakerBlock.PLAYING, playing), Block.UPDATE_ALL);
        }
    }

    public static void handleImport(ServerPlayer player, SpeakerPayloads.ImportChunk payload) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (payload.total() <= 0 || payload.index() < 0 || payload.index() >= payload.total()) {
            return;
        }
        String key = player.getStringUUID() + "/" + payload.sha1();
        ByteArrayOutputStream buffer = UPLOADS.computeIfAbsent(key, k -> new ByteArrayOutputStream());
        buffer.writeBytes(payload.data());
        if (buffer.size() > MAX_FILE_BYTES) {
            UPLOADS.remove(key);
            player.displayClientMessage(Component.translatable("message.explainstone.import.too_large"), false);
            return;
        }
        if (payload.index() + 1 < payload.total()) {
            return;
        }

        UPLOADS.remove(key);
        byte[] bytes = buffer.toByteArray();
        if (!isOgg(bytes)) {
            player.displayClientMessage(Component.translatable("message.explainstone.import.not_ogg"), false);
            return;
        }
        int seconds = oggSeconds(bytes);
        if (seconds > MAX_SECONDS) {
            player.displayClientMessage(Component.translatable("message.explainstone.import.too_long"), false);
            return;
        }
        try {
            Path dir = audioDir(level.getServer());
            Files.createDirectories(dir);
            Files.write(dir.resolve(payload.sha1() + ".ogg"), bytes);
        } catch (IOException e) {
            player.displayClientMessage(Component.translatable("message.explainstone.import.failed"), false);
            return;
        }

        if (level.getBlockEntity(payload.pos()) instanceof SpeakerBlockEntity be) {
            Track track = Track.file(payload.label(), payload.sha1());
            if (!be.getTracks().contains(track)) {
                be.addTrack(track);
            }
        }
        player.displayClientMessage(Component.translatable("message.explainstone.import.ok", payload.label()), true);
    }

    private static int oggSeconds(byte[] data) {
        int rate = -1;
        for (int i = 0; i + 4 <= data.length; i++) {
            if (data[i] == 'O' && data[i + 1] == 'g' && data[i + 2] == 'g' && data[i + 3] == 'S') {
                int segments = data[i + 26] & 0xFF;
                int pkt = i + 27 + segments;
                if (pkt + 16 <= data.length && data[pkt] == 1) {
                    rate = (data[pkt + 12] & 0xFF) | ((data[pkt + 13] & 0xFF) << 8)
                            | ((data[pkt + 14] & 0xFF) << 16) | ((data[pkt + 15] & 0xFF) << 24);
                }
                break;
            }
        }
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

    private static boolean isOgg(byte[] bytes) {
        return bytes.length > 4 && bytes[0] == 'O' && bytes[1] == 'g' && bytes[2] == 'g' && bytes[3] == 'S';
    }

    public static void handleRequest(ServerPlayer player, SpeakerPayloads.RequestAudio payload) {
        String sha1 = payload.sha1();
        if (sha1.isEmpty() || sha1.length() > 64) {
            return;
        }
        Path file = audioDir(player.serverLevel().getServer()).resolve(sha1 + ".ogg");
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            byte[] bytes = Files.readAllBytes(file);
            int total = (bytes.length + SpeakerPayloads.CHUNK_SIZE - 1) / SpeakerPayloads.CHUNK_SIZE;
            for (int i = 0; i < total; i++) {
                int from = i * SpeakerPayloads.CHUNK_SIZE;
                int len = Math.min(SpeakerPayloads.CHUNK_SIZE, bytes.length - from);
                byte[] part = new byte[len];
                System.arraycopy(bytes, from, part, 0, len);
                Explainstone.network().sendToPlayer(player,
                        new SpeakerPayloads.AudioChunk(sha1, sha1, i, total, part));
            }
        } catch (IOException ignored) {
        }
    }

    public static void onJoin(ServerPlayer player) {
        BroadcastRegistry.onJoin(player);
    }
}
