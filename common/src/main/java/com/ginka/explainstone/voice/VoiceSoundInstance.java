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

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 一个玩家说话的声源。
 *
 * <p>位置每 tick 跟着说话人走，所以听到的方向会随对方移动——比把语音固定在某个点自然。
 * 具体的 PCM 由 {@link VoiceStream} 从抖动缓冲里取。
 */
public abstract class VoiceSoundInstance extends AbstractSoundInstance implements TickableSoundInstance {
    private final VoiceStream stream;
    private boolean stopped;

    protected VoiceSoundInstance(UUID sender, Player player) {
        // 用「空」的标识符：真实音频由 getAudioStream/getStream 覆写提供，不查资源表
        super(Sounds.SILENT_ID, SoundSource.VOICE, SoundInstance.createUnseededRandom());
        this.stream = new VoiceStream(sender);
        follow(player);
        this.volume = 1.0f;
        this.pitch = 1.0f;
        this.looping = false;
        this.relative = false;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
    }

    public final void follow(Player player) {
        if (player != null) {
            this.x = player.getX();
            this.y = player.getY() + 1.5;
            this.z = player.getZ();
        }
    }

    public final void offer(int sequence, byte[] opus) {
        this.stream.offer(sequence, opus);
    }

    public final boolean idle() {
        return this.stream.idle();
    }

    public final UUID sender() {
        return this.stream.sender();
    }

    public final void stop() {
        this.stopped = true;
        this.looping = false;
    }

    @Override
    public final boolean isStopped() {
        return this.stopped;
    }

    @Override
    public final void tick() {
        // 位置在外部按玩家坐标更新，这里只管生命周期
    }

    /** 让平台子类把 {@link VoiceStream} 挂到自己的流式钩子上。 */
    protected final CompletableFuture<AudioStream> openStream(SoundBufferLibrary library, Object unused) {
        return CompletableFuture.completedFuture(this.stream);
    }

    /** 平台子类也可能需要这个重载（NeoForge 的签名带 Sound）。 */
    protected final CompletableFuture<AudioStream> openStreamFor(Sound sound) {
        return CompletableFuture.completedFuture(this.stream);
    }
}
