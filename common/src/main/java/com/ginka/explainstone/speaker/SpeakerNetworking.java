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
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

/** 服务端逻辑：处理界面操作、接收导入的 ogg、把仓库里的音频发给客户端。 */
public final class SpeakerNetworking {
    private static final int MAX_FILE_BYTES = 6 * 1024 * 1024;
    private static final int MAX_SECONDS = 5 * 60;
    private static final double MAX_INTERACT_DISTANCE_SQ = 64 * 64;
    /** 上传缓冲的存活时间：玩家中途关掉客户端时不至于永久占内存。 */
    private static final long UPLOAD_TTL_MILLIS = 10L * 60 * 1000;

    private static final Map<String, Upload> UPLOADS = new HashMap<>();

    private SpeakerNetworking() {
    }

    /**
     * 音频目录。移到 config 下，不再跟着存档走——存档会被复制、备份、上传，
     * 音频混在里面每个副本都要背一份。详见 {@link ServerAudioStore}。
     */
    public static java.nio.file.Path audioDir(MinecraftServer server) {
        return ServerAudioStore.dir();
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
                && !player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            player.sendSystemMessage(Component.translatable("message.explainstone.broadcast.no_permission"));
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

    /**
     * 导入前先问一句「服务端有没有这份数据」。
     *
     * <p>内容寻址的好处就在这里：sha1 相同就是同一首歌，服务端已经有的话
     * 客户端一块都不用传，直接把曲目挂到方块上。这同时解决了重复上传
     * 覆盖写盘的问题。
     */
    public static void handleImportQuery(ServerPlayer player, SpeakerPayloads.ImportQuery payload) {
        String sha1 = payload.sha1();
        if (sha1.isEmpty() || sha1.length() > 64) {
            return;
        }
        if (ServerAudioStore.has(sha1)) {
            attachTrack(player, payload.pos(), payload.label(), sha1);
            Explainstone.network().sendToPlayer(player, new SpeakerPayloads.ImportReply(sha1, payload.total()));
            player.sendOverlayMessage(Component.translatable("message.explainstone.import.ok", payload.label()));
            return;
        }
        Explainstone.network().sendToPlayer(player, new SpeakerPayloads.ImportReply(sha1, 0));
    }

    public static void handleImport(ServerPlayer player, SpeakerPayloads.ImportChunk payload) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (payload.total() <= 0 || payload.index() < 0 || payload.index() >= payload.total()) {
            return;
        }
        expireUploads();
        String key = player.getStringUUID() + "/" + payload.sha1();
        Upload upload = UPLOADS.computeIfAbsent(key, k -> new Upload());
        upload.lastTouched = System.currentTimeMillis();
        upload.put(payload.index(), payload.data());
        if (upload.bytes > MAX_FILE_BYTES) {
            UPLOADS.remove(key);
            player.sendSystemMessage(Component.translatable("message.explainstone.import.too_large"));
            return;
        }
        if (!upload.isComplete(payload.total())) {
            return;
        }

        UPLOADS.remove(key);
        byte[] bytes = upload.assemble();
        if (!isOgg(bytes)) {
            player.sendSystemMessage(Component.translatable("message.explainstone.import.not_ogg"));
            return;
        }
        int seconds = ServerAudioStore.oggSeconds(bytes);
        if (seconds > MAX_SECONDS) {
            player.sendSystemMessage(Component.translatable("message.explainstone.import.too_long"));
            return;
        }
        ServerAudioStore.put(payload.sha1(), bytes, seconds);
        attachTrack(player, payload.pos(), payload.label(), payload.sha1());
        player.sendOverlayMessage(Component.translatable("message.explainstone.import.ok", payload.label()));
    }

    private static void attachTrack(ServerPlayer player, BlockPos pos, String label, String sha1) {
        if (!(player.level().getBlockEntity(pos) instanceof SpeakerBlockEntity be)) {
            return;
        }
        Track track = Track.file(label, sha1);
        if (!be.getTracks().contains(track)) {
            be.addTrack(track);
        }
    }

    private static void expireUploads() {
        long now = System.currentTimeMillis();
        UPLOADS.entrySet().removeIf(entry -> now - entry.getValue().lastTouched > UPLOAD_TTL_MILLIS);
    }

    private static boolean isOgg(byte[] bytes) {
        return bytes.length > 4 && bytes[0] == 'O' && bytes[1] == 'g' && bytes[2] == 'g' && bytes[3] == 'S';
    }

    public static void handleRequest(ServerPlayer player, SpeakerPayloads.RequestAudio payload) {
        TransferManager.request(player, payload.sha1(), payload.fromChunk());
    }

    public static void handleCancel(ServerPlayer player, SpeakerPayloads.CancelAudio payload) {
        TransferManager.cancel(player, payload.sha1());
    }

    public static void onJoin(ServerPlayer player) {
        ServerAudioStore.gc();
        BroadcastRegistry.onJoin(player);
    }

    public static void onDisconnect(ServerPlayer player) {
        TransferManager.forget(player);
        UPLOADS.keySet().removeIf(key -> key.startsWith(player.getStringUUID() + "/"));
    }

    /** 服务器每 tick 推进下发队列。 */
    public static void tick(MinecraftServer server) {
        TransferManager.tick(server);
    }

    /** 一个进行中的上传：按分片下标存，天然支持乱序到达与断点续传。 */
    private static final class Upload {
        private final Map<Integer, byte[]> parts = new HashMap<>();
        private int bytes;
        private long lastTouched = System.currentTimeMillis();

        private void put(int index, byte[] data) {
            if (this.parts.putIfAbsent(index, data) == null) {
                this.bytes += data.length;
            }
        }

        private boolean isComplete(int total) {
            if (this.parts.size() < total) {
                return false;
            }
            for (int i = 0; i < total; i++) {
                if (!this.parts.containsKey(i)) {
                    return false;
                }
            }
            return true;
        }

        private byte[] assemble() {
            ByteArrayOutputStream out = new ByteArrayOutputStream(this.bytes);
            for (int i = 0; ; i++) {
                byte[] part = this.parts.get(i);
                if (part == null) {
                    break;
                }
                out.writeBytes(part);
            }
            return out.toByteArray();
        }
    }
}
