/*
 * explainstone —— 讲解石 / 颂美铃 / 广播发信器（Minecraft Fabric 1.21.1 模组）
 *
 *  作者 / Author : GINKA_3L96WW1
 *  2011
 *
 * 本文件由作者手工编写；贴图与模型由 tools/ 下的脚本程序化生成。
 * 保留此注释即可随意修改、分发。 喵
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.ginka.explainstone.speaker;

import net.minecraft.util.StringIdentifiable;

/** 播放模式 喵 */
public enum PlayMode implements StringIdentifiable {
    /** 列表顺序播放（播完自动下一首，到末尾回到第一首） 喵 */
    SEQUENCE("sequence", "顺序"),
    /** 单曲循环 喵 */
    REPEAT_ONE("repeat_one", "单曲"),
    /** 随机播放 喵 */
    SHUFFLE("shuffle", "随机");

    private final String id;
    private final String label;

    PlayMode(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String getLabel() {
        return this.label;
    }

    public PlayMode next() {
        PlayMode[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }

    public static PlayMode byOrdinal(int ordinal) {
        PlayMode[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : SEQUENCE;
    }

    @Override
    public String asString() {
        return this.id;
    }
}
