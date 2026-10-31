/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.ginka.explainstone.fabric;

import com.ginka.explainstone.client.audio.SpeakerSoundInstance;
import com.ginka.explainstone.speaker.Track;
import net.fabricmc.fabric.api.client.sound.v1.FabricSoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import java.util.concurrent.CompletableFuture;

/** Fabric's SoundEngine hook for custom streaming audio. */
public final class FabricSpeakerSound extends SpeakerSoundInstance implements FabricSoundInstance {
    public FabricSpeakerSound(BlockPos pos, Track track, int audibleRange, int volume, double startOffsetSeconds) {
        super(pos, track, audibleRange, volume, startOffsetSeconds);
    }

    @Override
    public CompletableFuture<AudioStream> getAudioStream(SoundBufferLibrary loader, Identifier id, boolean repeatInstantly) {
        return openStream();
    }
}
