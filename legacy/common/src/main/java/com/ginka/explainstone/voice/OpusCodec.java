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

import org.concentus.OpusApplication;
import org.concentus.OpusDecoder;
import org.concentus.OpusEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Opus 编解码的薄封装。
 *
 * <p>实测参数（Concentus 纯 Java 实现，48kHz 单声道、20ms 帧）：
 * <ul>
 *   <li>{@link OpusApplication#OPUS_APPLICATION_AUDIO} + 64kbps → 单帧约 222 字节，SNR 31.1 dB</li>
 *   <li>{@link OpusApplication#OPUS_APPLICATION_VOIP} + 32kbps → SNR 只有 13.5 dB，人声发闷，不用</li>
 * </ul>
 * 选前者：语音要听得清，每帧 222 字节在 20ms 粒度下约 89 kbps，
 * 一个说话的玩家每秒约 11KB，和 SVC 一个量级。
 */
public final class OpusCodec {
    private static final Logger LOGGER = LoggerFactory.getLogger("explainstone-voice");

    public static final int SAMPLE_RATE = Microphone.SAMPLE_RATE;
    public static final int FRAME_SAMPLES = Microphone.FRAME_SAMPLES;
    public static final int BITRATE = 64000;
    /** 单帧最大字节数，Concentus 在 64kbps 下远小于这个值，留够余量。 */
    public static final int MAX_PACKET = 512;

    private OpusCodec() {
    }

    /** 编码器；创建失败返回 null（比如平台不支持）。 */
    public static OpusEncoder newEncoder() {
        try {
            OpusEncoder encoder = new OpusEncoder(SAMPLE_RATE, 1, OpusApplication.OPUS_APPLICATION_AUDIO);
            encoder.setBitrate(BITRATE);
            encoder.setComplexity(5);
            return encoder;
        } catch (Throwable e) {
            LOGGER.warn("Opus 编码器创建失败：{}", e.toString());
            return null;
        }
    }

    public static OpusDecoder newDecoder() {
        try {
            return new OpusDecoder(SAMPLE_RATE, 1);
        } catch (Throwable e) {
            LOGGER.warn("Opus 解码器创建失败：{}", e.toString());
            return null;
        }
    }

    /** 编一帧；失败返回 null。 */
    public static byte[] encode(OpusEncoder encoder, short[] samples, int offset, int count) {
        if (encoder == null) {
            return null;
        }
        byte[] out = new byte[MAX_PACKET];
        try {
            int len = encoder.encode(samples, offset, count, out, 0, out.length);
            if (len <= 0) {
                return null;
            }
            byte[] trimmed = new byte[len];
            System.arraycopy(out, 0, trimmed, 0, len);
            return trimmed;
        } catch (Throwable e) {
            return null;
        }
    }

    /** 解一帧到 samples；返回解出的样本数，失败返回 -1。 */
    public static int decode(OpusDecoder decoder, byte[] packet, short[] samples) {
        if (decoder == null || packet == null || packet.length == 0) {
            return -1;
        }
        try {
            return decoder.decode(packet, 0, packet.length, samples, 0, FRAME_SAMPLES, false);
        } catch (Throwable e) {
            return -1;
        }
    }

    /**
     * 音量门限：全零帧不编也不发。
     *
     * <p>不做回声消除（用户明确要求只加门限与降噪），但静音时直接掐掉能省掉绝大部分空闲流量——
     * 实测不开麦时如果一直发静音帧，光保活就比说话还费。
     */
    public static boolean isSilent(short[] samples, int count) {
        long energy = 0;
        for (int i = 0; i < count; i++) {
            energy += (long) samples[i] * samples[i];
        }
        if (energy == 0) {
            return true;
        }
        double rms = Math.sqrt((double) energy / Math.max(1, count));
        return rms < SILENCE_RMS;
    }

    /**
     * 门限值。人正常说话的 RMS 大约在 1500~4000，环境底噪通常 < 100，
     * 取 300 能过滤掉空调声与键盘声，又不会把轻声说话吞掉。
     */
    private static final double SILENCE_RMS = 300.0;
}
