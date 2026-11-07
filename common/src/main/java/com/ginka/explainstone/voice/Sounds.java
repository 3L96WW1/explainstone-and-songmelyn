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

import net.minecraft.resources.Identifier;

/** 语音用到的声音资源标识。 */
public final class Sounds {
    /**
     * 语音不查资源表，但 {@code AbstractSoundInstance} 必须要一个标识符。
     * 复用模组自己的 speaker_music（它在 sounds.json 里是 stream 型），
     * 真正的字节由子类覆写流式钩子直接给出。
     */
    public static final Identifier SILENT_ID =
            Identifier.fromNamespaceAndPath("explainstone", "speaker_music");

    private Sounds() {
    }
}
