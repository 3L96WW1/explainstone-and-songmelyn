package com.ginka.explainstone.forge;

import com.ginka.explainstone.client.audio.SpeakerSoundInstance;
import com.ginka.explainstone.speaker.Track;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.core.BlockPos;

import java.util.concurrent.CompletableFuture;

public final class ForgeSpeakerSound extends SpeakerSoundInstance {
    public ForgeSpeakerSound(BlockPos pos, Track track, int audibleRange, int volume, double startOffsetSeconds) {
        super(pos, track, audibleRange, volume, startOffsetSeconds);
    }

    @Override
    public CompletableFuture<AudioStream> getStream(SoundBufferLibrary soundBuffers, Sound sound, boolean looping) {
        return openStream();
    }
}
