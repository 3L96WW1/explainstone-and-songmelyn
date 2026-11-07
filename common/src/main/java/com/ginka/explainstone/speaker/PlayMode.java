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

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum PlayMode implements StringRepresentable {
    SEQUENCE("sequence", "顺序"),
    REPEAT_ONE("repeat_one", "单曲"),
    SHUFFLE("shuffle", "随机");

    /** 存档与网络同步共用，字符串形式与 {@link #getSerializedName()} 一致。 */
    public static final Codec<PlayMode> CODEC = StringRepresentable.fromValues(PlayMode::values);

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
    public String getSerializedName() {
        return this.id;
    }
}
