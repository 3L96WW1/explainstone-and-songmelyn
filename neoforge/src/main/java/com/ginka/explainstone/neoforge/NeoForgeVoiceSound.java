/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.ginka.explainstone.neoforge;

import com.ginka.explainstone.voice.VoiceSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** NeoForge 的实时语音声源：把 VoiceStream 接到 NeoForge 的流式钩子上。 */
public final class NeoForgeVoiceSound extends VoiceSoundInstance {
    public NeoForgeVoiceSound(UUID sender, Player player) {
        super(sender, player);
    }

    @Override
    public CompletableFuture<AudioStream> getStream(SoundBufferLibrary soundBuffers, Sound sound, boolean looping) {
        return openStreamFor(sound);
    }
}
