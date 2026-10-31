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

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 播放列表里的一条曲目。
 * id 形如 {@code builtin:speaker_music}（模组自带，打在 jar 里）或 {@code file:<sha1>}（玩家导入，存在存档里）。
 */
public record Track(String label, String id) {
    public static final String BUILTIN_PREFIX = "builtin:";
    public static final String FILE_PREFIX = "file:";
    public static final String BUILTIN_NAME = "speaker_music";

    public static Track builtin(String label, String name) {
        return new Track(label, BUILTIN_PREFIX + name);
    }

    public static Track file(String label, String sha1) {
        return new Track(label, FILE_PREFIX + sha1);
    }

    public boolean isFile() {
        return this.id.startsWith(FILE_PREFIX);
    }

    public String sha1() {
        int i = this.id.indexOf(':');
        return i < 0 ? "" : this.id.substring(i + 1);
    }

    public String builtinName() {
        int i = this.id.indexOf(':');
        return i < 0 ? this.id : this.id.substring(i + 1);
    }

    public void toNbt(ValueOutput out) {
        out.putString("label", this.label);
        out.putString("id", this.id);
    }

    public static Track fromNbt(ValueInput in) {
        return new Track(in.getStringOr("label", ""), in.getStringOr("id", ""));
    }
}
