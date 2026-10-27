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

import net.minecraft.nbt.NbtCompound;

/**
 * 播放列表里的一条曲目。
 * id 形如 {@code builtin:speaker_music}（模组自带，打在 jar 里）或 {@code file:<sha1>}（玩家导入，存在存档里）。 喵
 */
public record Track(String label, String id) {
    public static final String BUILTIN_PREFIX = "builtin:";
    public static final String FILE_PREFIX = "file:";

    /** 内置曲目名（包内已不带音频；保留给想自己塞资源包/加曲的人用） 喵 */
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

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("label", this.label);
        nbt.putString("id", this.id);
        return nbt;
    }

    public static Track fromNbt(NbtCompound nbt) {
        return new Track(nbt.getString("label"), nbt.getString("id"));
    }
}
