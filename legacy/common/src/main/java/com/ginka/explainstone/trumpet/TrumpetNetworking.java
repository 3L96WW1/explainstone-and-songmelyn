/*
 * explainstone —— 讲解石 / 颂美铃 / 广播发信器 / 特朗谱
 *
 * 作者 / Author : GINKA_3L96WW1
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.ginka.explainstone.trumpet;

import com.ginka.explainstone.Explainstone;
import com.ginka.explainstone.ExplainstoneConfig;
import com.ginka.explainstone.speaker.ServerAudioStore;
import com.ginka.explainstone.speaker.SpeakerPayloads;
import com.ginka.explainstone.speaker.Track;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * 特朗谱的服务端逻辑。
 *
 * <p>物品状态全在 {@link TrumpetState} 组件里，所以每个操作都是「读组件 → 改 → 写回」。
 * 槽位变了（玩家换手/丢出）就忽略整个包，避免改错格子。
 */
public final class TrumpetNetworking {
    /** 与方块版保持一致的导入上限。 */
    private static final long MAX_FILE_BYTES = 6L * 1024 * 1024;
    private static final int MAX_SECONDS = 5 * 60;
    /** 未完成的导入闲置这么久就丢弃，免得占着内存等一个再也不来的客户端。 */
    private static final long UPLOAD_TTL_MILLIS = 10L * 60 * 1000;

    /** key = 玩家 UUID + "/" + sha1。 */
    private static final Map<String, Upload> UPLOADS = new HashMap<>();

    private TrumpetNetworking() {
    }

    private static ItemStack stackAt(ServerPlayer player, int slot) {
        if (slot < 0 || slot >= player.getInventory().getContainerSize()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = player.getInventory().getItem(slot);
        return stack.getItem() instanceof TrumpetItem ? stack : ItemStack.EMPTY;
    }

    private static void write(ServerPlayer player, int slot, ItemStack stack, TrumpetState state) {
        TrumpetStates.set(stack, state);
        player.getInventory().setItem(slot, stack);
        // 组件变了要让客户端看到，否则界面读到的还是旧状态
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(
                player.inventoryMenu.containerId, player.inventoryMenu.incrementStateId(), slot, stack));
        Explainstone.network().sendToPlayer(player, new TrumpetPayloads.StateSync(slot, state));
    }

    public static void handleAction(ServerPlayer player, TrumpetPayloads.Action payload) {
        int slot = payload.slot();
        ItemStack stack = stackAt(player, slot);
        if (stack.isEmpty()) {
            return;
        }
        TrumpetState state = TrumpetStates.get(stack);
        TrumpetState updated;
        switch (payload.action()) {
            case SpeakerPayloads.ACTION_SET_CHANNEL -> updated = state.withChannel(payload.value());
            case SpeakerPayloads.ACTION_SELECT -> updated = state.withSelected(payload.value());
            case SpeakerPayloads.ACTION_PLAY_INDEX ->
                    updated = state.withSelected(payload.value());
            case SpeakerPayloads.ACTION_SET_VOLUME -> updated = state.withVolume(payload.value());
            case SpeakerPayloads.ACTION_SET_AUDIBLE_RANGE -> updated = state.withAudibleRange(payload.value());
            case SpeakerPayloads.ACTION_SET_TRANSMIT_RANGE -> updated = state.withTransmitRange(payload.value());
            case SpeakerPayloads.ACTION_TOGGLE_RECEIVE -> updated = state.withReceiveMode(!state.receiveMode());
            case SpeakerPayloads.ACTION_TOGGLE_ENABLED -> updated = state.withSpeakerEnabled(!state.speakerEnabled());
            case SpeakerPayloads.ACTION_CYCLE_MODE -> updated = state.withMode(state.mode().next());
            case SpeakerPayloads.ACTION_REMOVE_TRACK -> {
                if (payload.value() < 0 || payload.value() >= state.tracks().size()) {
                    return;
                }
                java.util.List<Track> tracks = new java.util.ArrayList<>(state.tracks());
                tracks.remove(payload.value());
                updated = state.withTracks(tracks);
            }
            case SpeakerPayloads.ACTION_PLAY, SpeakerPayloads.ACTION_STOP -> {
                if (payload.action() == SpeakerPayloads.ACTION_PLAY
                        && ExplainstoneConfig.broadcastRequiresOp()
                        && !player.hasPermissions(2)) {
                    player.displayClientMessage(Component.translatable("message.explainstone.broadcast.no_permission"), false);
                    return;
                }
                // 播放/停止本身不改物品数据，客户端根据 Play 包自行开播，这里只回同步
                Explainstone.network().sendToPlayer(player, new TrumpetPayloads.StateSync(slot, state));
                return;
            }
            default -> {
                return;
            }
        }
        write(player, slot, stack, updated);
    }

    /** 导入前查询：服务端已有就直接挂上曲目，不必上传。 */
    public static void handleImportQuery(ServerPlayer player, TrumpetPayloads.ImportQuery payload) {
        ItemStack stack = stackAt(player, payload.slot());
        if (stack.isEmpty()) {
            return;
        }
        String sha1 = payload.sha1();
        TrumpetState state = TrumpetStates.get(stack);
        int total = payload.total();
        if (ServerAudioStore.has(sha1)) {
            ServerAudioStore.touch(sha1);
            if (!state.tracks().stream().anyMatch(t -> t.isFile() && t.sha1().equals(sha1))) {
                write(player, payload.slot(), stack, state.withTracks(
                        append(state.tracks(), Track.file(payload.label(), sha1))));
            }
            player.displayClientMessage(Component.translatable("message.explainstone.import.ok", payload.label()), false);
            Explainstone.network().sendToPlayer(player,
                    new SpeakerPayloads.ImportReply(sha1, total));
            return;
        }
        expireUploads();
        Explainstone.network().sendToPlayer(player, new SpeakerPayloads.ImportReply(sha1, 0));
    }

    /** 分片上传。按 index 存，允许乱序与断点续传。 */
    public static void handleImport(ServerPlayer player, TrumpetPayloads.ImportChunk payload) {
        if (stackAt(player, payload.slot()).isEmpty()) {
            return;
        }
        String key = player.getStringUUID() + "/" + payload.sha1();
        Upload upload = UPLOADS.computeIfAbsent(key, k -> {
            Upload created = new Upload(payload.slot(), payload.label(), payload.sha1(), payload.total());
            return created;
        });
        upload.parts.put(payload.index(), payload.data());
        upload.received++;
        upload.lastTouched = System.currentTimeMillis();
        upload.size += payload.data().length;
        if (upload.parts.size() < upload.total) {
            return;
        }
        UPLOADS.remove(key);
        byte[] bytes = upload.assemble();
        if (bytes == null || bytes.length <= 0) {
            return;
        }
        if (bytes.length > MAX_FILE_BYTES) {
            player.displayClientMessage(Component.translatable("message.explainstone.import.too_large"), false);
            return;
        }
        int seconds = ServerAudioStore.oggSeconds(bytes);
        if (seconds <= 0 || seconds > MAX_SECONDS) {
            player.displayClientMessage(Component.translatable("message.explainstone.import.too_long"), false);
            return;
        }
        ServerAudioStore.put(payload.sha1(), bytes, seconds);

        ItemStack stack = stackAt(player, upload.slot);
        if (!stack.isEmpty()) {
            TrumpetState state = TrumpetStates.get(stack);
            if (state.tracks().stream().noneMatch(t -> t.isFile() && t.sha1().equals(payload.sha1()))) {
                write(player, upload.slot, stack, state.withTracks(
                        append(state.tracks(), Track.file(payload.label(), payload.sha1()))));
            }
        }
        player.displayClientMessage(Component.translatable("message.explainstone.import.ok", upload.label), false);
    }

    /** 玩家断开时清掉他的半截上传。 */
    public static void onDisconnect(ServerPlayer player) {
        String prefix = player.getStringUUID() + "/";
        UPLOADS.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private static java.util.List<Track> append(java.util.List<Track> tracks, Track track) {
        java.util.List<Track> out = new java.util.ArrayList<>(tracks);
        out.add(track);
        return out;
    }

    private static void expireUploads() {
        long now = System.currentTimeMillis();
        UPLOADS.entrySet().removeIf(entry -> now - entry.getValue().lastTouched > UPLOAD_TTL_MILLIS);
    }

    private static final class Upload {
        private final int slot;
        private final String label;
        private final String sha1;
        private final int total;
        private final Map<Integer, byte[]> parts = new HashMap<>();
        private int received;
        private int size;
        private long lastTouched = System.currentTimeMillis();

        private Upload(int slot, String label, String sha1, int total) {
            this.slot = slot;
            this.label = label;
            this.sha1 = sha1;
            this.total = Math.max(1, total);
        }

        private byte[] assemble() {
            byte[] out = new byte[this.size];
            int offset = 0;
            for (int i = 0; i < this.total; i++) {
                byte[] part = this.parts.get(i);
                if (part == null) {
                    return null;
                }
                System.arraycopy(part, 0, out, offset, part.length);
                offset += part.length;
            }
            return out;
        }
    }
}
