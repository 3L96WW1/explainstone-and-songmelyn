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

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 抖动缓冲。
 *
 * <p>网络包到达不均匀，直接播会一顿一顿。这里按序号排队，丢包就补一段静音
 * （而不是让整条流错位），延迟攒到 {@link #TARGET_FRAMES} 帧才开始出声。
 *
 * <p>参考 Simple Voice Chat 的做法，但简化成单声道定点长度帧：
 * 每帧固定 20ms、解码后固定 {@link OpusCodec#FRAME_SAMPLES} 样本，不需要处理变长帧。
 */
final class JitterBuffer {
    /** 攒 3 帧（60ms）再开播：低于这个值网络抖一下就会断音。 */
    private static final int TARGET_FRAMES = 3;
    /** 允许积压的上限；超过说明客户端播不动，丢掉最老的追上去。 */
    private static final int MAX_FRAMES = 12;

    private final Deque<byte[]> packets = new ArrayDeque<>();
    private int nextSequence = -1;
    private boolean started;

    /** 收到一帧。序号倒退的（重发包）直接丢。 */
    synchronized void offer(int sequence, byte[] opus) {
        if (this.nextSequence >= 0 && sequence < this.nextSequence) {
            return;
        }
        this.packets.addLast(opus);
        while (this.packets.size() > MAX_FRAMES) {
            this.packets.removeFirst();
        }
        if (this.nextSequence < 0) {
            this.nextSequence = sequence;
        }
    }

    /**
     * 取下一帧；返回 null 表示暂时没数据（调用方补静音）。
     * 只有攒够目标帧数才会开始吐数据。
     */
    synchronized byte[] poll() {
        if (!this.started) {
            if (this.packets.size() < TARGET_FRAMES) {
                return null;
            }
            this.started = true;
        }
        byte[] next = this.packets.pollFirst();
        if (next != null) {
            this.nextSequence++;
        }
        return next;
    }

    synchronized boolean isEmpty() {
        return this.packets.isEmpty();
    }

    synchronized void clear() {
        this.packets.clear();
        this.started = false;
        this.nextSequence = -1;
    }
}
