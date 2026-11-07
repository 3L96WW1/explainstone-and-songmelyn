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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

/**
 * 麦克风采集。
 *
 * <p>开一个独立的采集线程，按 20ms 一帧（48kHz 单声道 16bit = 960 样本）读出来交给编码器。
 * 关麦时整条线路都释放掉——蓝牙耳机这类设备占着麦会持续耗电且可能影响其他程序录音。
 *
 * <p>任何一步失败（没麦、被占用、驱动不支持 48kHz）都只记一条日志并把自己停掉，
 * 不让它把游戏搞崩。
 */
final class Microphone {
    private static final Logger LOGGER = LoggerFactory.getLogger("explainstone-voice");

    static final int SAMPLE_RATE = 48000;
    static final int FRAME_SAMPLES = 960;   // 20ms
    static final AudioFormat FORMAT = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);

    private final Listener listener;
    private TargetDataLine line;
    private Thread thread;
    private volatile boolean running;
    private volatile boolean failed;

    interface Listener {
        void onFrame(short[] samples, int count);
    }

    Microphone(Listener listener) {
        this.listener = listener;
    }

    /** 设备能不能用（有些环境压根没有录音线）。 */
    static boolean available() {
        try {
            return AudioSystem.isLineSupported(new DataLine.Info(TargetDataLine.class, FORMAT));
        } catch (Throwable t) {
            return false;
        }
    }

    boolean isRunning() {
        return this.running;
    }

    boolean isFailed() {
        return this.failed;
    }

    /** 打开麦克风；失败返回 false，调用方据此提示玩家。 */
    boolean open() {
        if (this.running) {
            return true;
        }
        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
            if (!AudioSystem.isLineSupported(info)) {
                LOGGER.warn("麦克风不支持 48kHz 单声道录音，语音功能关闭");
                this.failed = true;
                return false;
            }
            this.line = (TargetDataLine) AudioSystem.getLine(info);
            this.line.open(FORMAT, FRAME_SAMPLES * 2 * 4);
            this.line.start();
            this.running = true;
            this.failed = false;
            this.thread = new Thread(this::loop, "explainstone-mic");
            this.thread.setDaemon(true);
            this.thread.start();
            return true;
        } catch (LineUnavailableException | IllegalArgumentException | SecurityException e) {
            LOGGER.warn("打不开麦克风：{}", e.toString());
            this.failed = true;
            close();
            return false;
        }
    }

    private void loop() {
        short[] buffer = new short[FRAME_SAMPLES];
        byte[] bytes = new byte[FRAME_SAMPLES * 2];
        while (this.running) {
            int read = this.line.read(bytes, 0, bytes.length);
            if (read <= 0) {
                continue;
            }
            int samples = read / 2;
            for (int i = 0; i < samples; i++) {
                // 小端 16bit
                buffer[i] = (short) ((bytes[i * 2] & 0xFF) | (bytes[i * 2 + 1] << 8));
            }
            if (samples > 0) {
                try {
                    this.listener.onFrame(buffer, samples);
                } catch (Throwable t) {
                    LOGGER.warn("语音帧处理失败", t);
                }
            }
        }
    }

    void close() {
        this.running = false;
        TargetDataLine current = this.line;
        this.line = null;
        if (current != null) {
            try {
                current.stop();
                current.close();
            } catch (Throwable ignored) {
            }
        }
    }
}
