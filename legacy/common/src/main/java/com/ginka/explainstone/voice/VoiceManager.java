/*
 * explainstone —— 讲解石 / 颂美铃 / 广播发信器 / 特朗谱
 *
 * 作者 / Author : GINKA_3L96WW1
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.ginka.explainstone.voice;

import com.ginka.explainstone.Explainstone;
import com.ginka.explainstone.client.ExplainstoneClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import org.concentus.OpusEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 客户端语音协调器：按需开麦、编码、发包；同时管理收到的语音流。
 *
 * <p>设计取舍（用户已确认）：
 * <ul>
 *   <li>走 Minecraft 现有连接（TCP），不开 UDP 端口——不需要额外的端口映射与防火墙规则。</li>
 *   <li>默认所有玩家都能开麦，不做权限门槛。</li>
 *   <li>不做回声消除，只用音量门限压掉静音（见 {@link OpusCodec#isSilent}）。</li>
 * </ul>
 */
public final class VoiceManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("explainstone-voice");

    /** 平台实现：把 {@link VoiceStream} 接到各自的声音钩子上。 */
    public interface SoundFactory {
        VoiceSoundInstance create(UUID sender, Player player);
    }

    private static final Map<UUID, VoiceSoundInstance> RECEIVING = new HashMap<>();

    private static SoundFactory factory;
    private static Microphone microphone;
    private static OpusEncoder encoder;
    private static boolean talking;
    private static boolean warnedNoDevice;
    private static int sequence;

    private VoiceManager() {
    }

    public static void setSoundFactory(SoundFactory newFactory) {
        factory = newFactory;
    }

    public static boolean isTalking() {
        return talking;
    }

    /** 麦克风不可用时界面要能提示玩家。 */
    public static boolean microphoneAvailable() {
        return Microphone.available();
    }

    /** 开麦（按住说话）。已经在说就直接返回。 */
    public static void startTalking() {
        if (talking) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.getConnection() == null) {
            return;
        }
        if (!Explainstone.network().canSendToServer(VoicePayloads.Chunk.ID)) {
            // 服务器没装这个模组、或版本不匹配，走原版右上角通知
            toast(client, "message.explainstone.voice.unavailable");
            return;
        }
        if (!Microphone.available()) {
            if (!warnedNoDevice) {
                warnedNoDevice = true;
                toast(client, "message.explainstone.voice.no_device");
            }
            return;
        }
        if (encoder == null) {
            encoder = OpusCodec.newEncoder();
        }
        if (encoder == null) {
            toast(client, "message.explainstone.voice.encoder_failed");
            return;
        }
        microphone = new Microphone(VoiceManager::onFrame);
        if (!microphone.open()) {
            toast(client, "message.explainstone.voice.no_device");
            return;
        }
        talking = true;
        sequence = 0;
        LOGGER.info("开始说话");
    }

    /** 关麦。 */
    public static void stopTalking() {
        if (!talking) {
            return;
        }
        talking = false;
        if (microphone != null) {
            microphone.close();
            microphone = null;
        }
        LOGGER.info("停止说话");
    }

    private static void onFrame(short[] samples, int count) {
        if (!talking) {
            return;
        }
        // 静音帧直接丢：既省流量，也避免对面听到持续的底噪
        if (OpusCodec.isSilent(samples, count)) {
            return;
        }
        byte[] opus = OpusCodec.encode(encoder, samples, 0, count);
        if (opus == null) {
            return;
        }
        if (Explainstone.network().canSendToServer(VoicePayloads.Chunk.ID)) {
            Explainstone.network().sendToServer(new VoicePayloads.Chunk(sequence++, opus));
        }
    }

    /** 收到别人的一帧。 */
    public static void onFrame(VoicePayloads.Frame payload) {
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        Player player = level == null ? null : level.getPlayerByUUID(payload.sender());
        if (player == null) {
            return;
        }
        UUID sender = payload.sender();
        VoiceSoundInstance sound = RECEIVING.get(sender);
        if (sound == null || sound.isStopped()) {
            if (factory == null) {
                return;
            }
            sound = factory.create(sender, player);
            if (sound == null) {
                return;
            }
            RECEIVING.put(sender, sound);
            client.getSoundManager().play(sound);
        }
        sound.offer(payload.sequence(), payload.opus());
    }

    /** 收到「谁在说话」的状态包，用来画左下角的图标。 */
    public static void onSpeaking(VoicePayloads.Speaking payload) {
        SpeakerHud.setSpeaking(payload.who(), payload.speaking());
    }

    /** 每 tick：先处理按键，再做位置跟随与清理。 */
    public static void tick(Minecraft client) {
        VoiceKeybinds.tick(client);
        ClientLevel level = client.level;
        if (level == null) {
            stopAll();
            return;
        }
        for (UUID sender : RECEIVING.keySet().toArray(new UUID[0])) {
            VoiceSoundInstance sound = RECEIVING.get(sender);
            Player player = level.getPlayerByUUID(sender);
            if (sound == null || player == null || sound.idle()) {
                if (sound != null) {
                    sound.stop();
                    client.getSoundManager().stop(sound);
                }
                RECEIVING.remove(sender);
                SpeakerHud.setSpeaking(sender, false);
                continue;
            }
            sound.follow(player);
        }
        // 说话人离开视野或死了就自动松开
        if (talking && (client.player == null || !client.player.isAlive())) {
            stopTalking();
        }
    }

    public static void stopAll() {
        stopTalking();
        Minecraft client = Minecraft.getInstance();
        for (VoiceSoundInstance sound : RECEIVING.values()) {
            sound.stop();
            client.getSoundManager().stop(sound);
        }
        RECEIVING.clear();
        SpeakerHud.clear();
    }

    /** 走原版右上角的 toast，而不是聊天栏——开麦失败属于"系统通知"。 */
    private static void toast(Minecraft client, String key) {
        net.minecraft.client.gui.components.toasts.SystemToast.add(
                client.getToasts(),
                net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                net.minecraft.network.chat.Component.translatable("message.explainstone.voice.title"),
                net.minecraft.network.chat.Component.translatable(key));
    }
}
