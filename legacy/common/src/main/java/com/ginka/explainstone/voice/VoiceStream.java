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

import net.minecraft.client.sounds.AudioStream;
import org.concentus.OpusDecoder;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

/**
 * 一个说话人的实时音频流。
 *
 * <p>OpenAL 每要一批样本就调 {@link #read(int)}：这里从抖动缓冲取 Opus 帧解码成 PCM。
 * 没有帧可播时输出静音而不是返回 null——返回 null 会让 SoundEngine 判定流结束，
 * 于是玩家一停顿就再也听不到后续的话。
 */
final class VoiceStream implements AudioStream {
    static final AudioFormat FORMAT =
            new AudioFormat(OpusCodec.SAMPLE_RATE, 16, 1, true, false);

    private final UUID sender;
    private final JitterBuffer buffer = new JitterBuffer();
    private final OpusDecoder decoder;
    private final short[] frame = new short[OpusCodec.FRAME_SAMPLES];
    private final byte[] pcm = new byte[OpusCodec.FRAME_SAMPLES * 2];
    private ByteBuffer pending;
    private boolean silent;
    private long lastPacketAt = System.currentTimeMillis();

    VoiceStream(UUID sender) {
        this.sender = sender;
        this.decoder = OpusCodec.newDecoder();
    }

    UUID sender() {
        return this.sender;
    }

    void offer(int sequence, byte[] opus) {
        this.buffer.offer(sequence, opus);
        this.lastPacketAt = System.currentTimeMillis();
    }

    /** 超过这么久没包，认为对方已经说完/走了。 */
    boolean idle() {
        return System.currentTimeMillis() - this.lastPacketAt > 2000L;
    }

    @Override
    public AudioFormat getFormat() {
        return FORMAT;
    }

    @Override
    public ByteBuffer read(int size) throws IOException {
        ByteBuffer out = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        while (out.hasRemaining()) {
            if (this.pending == null || !this.pending.hasRemaining()) {
                this.pending = nextFrame();
            }
            if (this.pending == null) {
                break;
            }
            int copy = Math.min(out.remaining(), this.pending.remaining());
            out.put(this.pending.array(), this.pending.position(), copy);
            this.pending.position(this.pending.position() + copy);
        }
        if (out.position() == 0) {
            // 一块都没填上：给一段静音，让声音保持"在播"的状态
            return ByteBuffer.allocate(size);
        }
        out.flip();
        return out;
    }

    private ByteBuffer nextFrame() {
        byte[] opus = this.buffer.poll();
        if (opus == null) {
            if (!this.silent) {
                this.silent = true;
                return ByteBuffer.wrap(new byte[this.pcm.length]);
            }
            return ByteBuffer.wrap(new byte[this.pcm.length]);
        }
        this.silent = false;
        int samples = OpusCodec.decode(this.decoder, opus, this.frame);
        int count = samples > 0 ? Math.min(samples, OpusCodec.FRAME_SAMPLES) : OpusCodec.FRAME_SAMPLES;
        for (int i = 0; i < count; i++) {
            short value = samples > 0 ? this.frame[i] : 0;
            this.pcm[i * 2] = (byte) (value & 0xFF);
            this.pcm[i * 2 + 1] = (byte) ((value >> 8) & 0xFF);
        }
        return ByteBuffer.wrap(this.pcm, 0, count * 2);
    }

    @Override
    public void close() {
        this.buffer.clear();
    }
}
