/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.ginka.explainstone.neoforge;

import com.ginka.explainstone.client.audio.SpeakerSoundInstance;
import com.ginka.explainstone.speaker.Track;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.core.BlockPos;

import java.util.concurrent.CompletableFuture;

/** NeoForge's SoundInstance streaming hook. */
public final class NeoForgeSpeakerSound extends SpeakerSoundInstance {
    public NeoForgeSpeakerSound(BlockPos pos, Track track, int audibleRange, int volume, double startOffsetSeconds) {
        super(pos, track, audibleRange, volume, startOffsetSeconds);
    }

    @Override
    public CompletableFuture<AudioStream> getStream(SoundBufferLibrary soundBuffers, Sound sound, boolean looping) {
        return openStream();
    }
}
