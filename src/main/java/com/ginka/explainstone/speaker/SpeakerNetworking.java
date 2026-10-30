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
package com.ginka.explainstone.speaker;

import com.ginka.explainstone.SpeakerBlock;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * 服务端逻辑：处理界面操作、接收导入的 ogg（存进存档）、把存档里的音频发给客户端。
 * 音频库位置：<存档>/explainstone/audio/&lt;sha1&gt;.ogg 喵
 */
public final class SpeakerNetworking {
    /** 单个文件上限（16 MB），避免误传大文件把存档撑爆 喵 */
    private static final int MAX_FILE_BYTES = 6 * 1024 * 1024;
    /** 单曲时长上限（秒） 喵 */
    private static final int MAX_SECONDS = 5 * 60;
    /** 界面操作的最大有效距离（格） 喵 */
    private static final double MAX_INTERACT_DISTANCE_SQ = 64 * 64;

    private static final Map<String, ByteArrayOutputStream> UPLOADS = new HashMap<>();

    private SpeakerNetworking() {
    }

    /** 1.20.1 不需要显式注册 payload 类型（用 Identifier 通道） 喵 */
    public static void registerPayloadTypes() {
    }

    public static void registerServerHandlers() {
        ServerPlayNetworking.registerGlobalReceiver(SpeakerPayloads.CH_ACTION,
                (server, player, handler, buf, sender) -> {
                    SpeakerPayloads.Action payload = SpeakerPayloads.Action.read(buf);
                    server.execute(() -> handleAction(player, payload));
                });

        ServerPlayNetworking.registerGlobalReceiver(SpeakerPayloads.CH_IMPORT,
                (server, player, handler, buf, sender) -> {
                    SpeakerPayloads.ImportChunk payload = SpeakerPayloads.ImportChunk.read(buf);
                    server.execute(() -> handleImport(player, payload));
                });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                server.execute(() -> BroadcastRegistry.onJoin(handler.getPlayer())));

        ServerPlayNetworking.registerGlobalReceiver(SpeakerPayloads.CH_RESYNC,
                (server, player, handler, buf, sender) -> server.execute(() -> BroadcastRegistry.onJoin(player)));

        ServerPlayNetworking.registerGlobalReceiver(SpeakerPayloads.CH_REQUEST,
                (server, player, handler, buf, sender) -> {
                    SpeakerPayloads.RequestAudio payload = SpeakerPayloads.RequestAudio.read(buf);
                    server.execute(() -> handleRequest(player, payload));
                });
    }

    // ------------------------------------------------------------ 音频库路径 喵
    public static Path audioDir(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("explainstone").resolve("audio");
    }

    // ------------------------------------------------------------ 界面操作 喵
    private static void handleAction(ServerPlayerEntity player, SpeakerPayloads.Action payload) {
        BlockPos pos = payload.pos();
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        if (player.getBlockPos().getSquaredDistance(pos) > MAX_INTERACT_DISTANCE_SQ) {
            return;
        }
        if (!(world.getBlockEntity(pos) instanceof SpeakerBlockEntity be)) {
            return;
        }

        boolean isTransmitter = world.getBlockState(pos).getBlock()
                instanceof com.ginka.explainstone.BroadcastTransmitterBlock;
        boolean broadcasting = payload.action() == SpeakerPayloads.ACTION_PLAY
                || payload.action() == SpeakerPayloads.ACTION_PLAY_INDEX;
        if (isTransmitter && broadcasting
                && com.ginka.explainstone.ExplainstoneConfig.broadcastRequiresOp()
                && !player.hasPermissionLevel(2)) {
            player.sendMessage(Text.translatable("message.explainstone.broadcast.no_permission"), false);
            return;
        }

        switch (payload.action()) {
            case SpeakerPayloads.ACTION_PLAY -> {
                setPlaying(world, pos, true);
                if (be.getTimerSeconds() > 0) {
                    be.startTimer();
                }
            }
            case SpeakerPayloads.ACTION_STOP -> {
                setPlaying(world, pos, false);
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
                setPlaying(world, pos, true);
            }
            case SpeakerPayloads.ACTION_CYCLE_MODE -> be.setMode(be.getMode().next());
            case SpeakerPayloads.ACTION_TOGGLE_REDSTONE -> be.setRespectRedstone(!be.isRespectRedstone());
            case SpeakerPayloads.ACTION_SELECT -> be.setSelected(payload.value());
            case SpeakerPayloads.ACTION_REMOVE_TRACK -> be.removeTrack(payload.value());
            default -> {
            }
        }

        // 发信器的状态变了 → 重新登记并推给 512 格内的玩家 喵
        if (world.getBlockState(pos).getBlock() instanceof com.ginka.explainstone.BroadcastTransmitterBlock) {
            BroadcastRegistry.refresh(world, pos, be);
        }
    }

    private static void setPlaying(ServerWorld world, BlockPos pos, boolean playing) {
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof com.ginka.explainstone.BroadcastTransmitterBlock
                && state.get(com.ginka.explainstone.BroadcastTransmitterBlock.PLAYING) != playing) {
            world.setBlockState(pos, state.with(com.ginka.explainstone.BroadcastTransmitterBlock.PLAYING, playing),
                    Block.NOTIFY_ALL);
        } else if (state.getBlock() instanceof SpeakerBlock && state.get(SpeakerBlock.PLAYING) != playing) {
            world.setBlockState(pos, state.with(SpeakerBlock.PLAYING, playing), Block.NOTIFY_ALL);
        }
    }

    // ------------------------------------------------------------ 导入文件 喵
    private static void handleImport(ServerPlayerEntity player, SpeakerPayloads.ImportChunk payload) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        if (payload.total() <= 0 || payload.index() < 0 || payload.index() >= payload.total()) {
            return;
        }
        String key = player.getUuidAsString() + "/" + payload.sha1();
        ByteArrayOutputStream buffer = UPLOADS.computeIfAbsent(key, k -> new ByteArrayOutputStream());
        buffer.writeBytes(payload.data());
        if (buffer.size() > MAX_FILE_BYTES) {
            UPLOADS.remove(key);
            player.sendMessage(Text.translatable("message.explainstone.import.too_large"), false);
            return;
        }
        if (payload.index() + 1 < payload.total()) {
            return;
        }

        UPLOADS.remove(key);
        byte[] bytes = buffer.toByteArray();
        if (!isOgg(bytes)) {
            player.sendMessage(Text.translatable("message.explainstone.import.not_ogg"), false);
            return;
        }
        int seconds = oggSeconds(bytes);
        if (seconds > MAX_SECONDS) {
            player.sendMessage(Text.translatable("message.explainstone.import.too_long"), false);
            return;
        }
        try {
            Path dir = audioDir(world.getServer());
            Files.createDirectories(dir);
            Files.write(dir.resolve(payload.sha1() + ".ogg"), bytes);
        } catch (IOException e) {
            player.sendMessage(Text.translatable("message.explainstone.import.failed"), false);
            return;
        }

        if (world.getBlockEntity(payload.pos()) instanceof SpeakerBlockEntity be) {
            Track track = Track.file(payload.label(), payload.sha1());
            if (!be.getTracks().contains(track)) {
                be.addTrack(track);
            }
        }
        player.sendMessage(Text.translatable("message.explainstone.import.ok", payload.label()), true);
    }

    /** 直接解析 ogg 头算时长（采样率 + 末尾页 granule） 喵 */
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
        // Ogg 容器固定以 "OggS" 开头 喵
        return bytes.length > 4 && bytes[0] == 'O' && bytes[1] == 'g' && bytes[2] == 'g' && bytes[3] == 'S';
    }

    // ------------------------------------------------------------ 下发文件 喵
    private static void handleRequest(ServerPlayerEntity player, SpeakerPayloads.RequestAudio payload) {
        String sha1 = payload.sha1();
        if (sha1.isEmpty() || sha1.length() > 64) {
            return;
        }
        Path file = audioDir(player.getServer()).resolve(sha1 + ".ogg");
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
                net.minecraft.network.PacketByteBuf out = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
                new SpeakerPayloads.AudioChunk(sha1, sha1, i, total, part).write(out);
                ServerPlayNetworking.send(player, SpeakerPayloads.CH_AUDIO, out);
            }
        } catch (IOException ignored) {
        }
    }
}
