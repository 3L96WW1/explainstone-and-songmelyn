/*
 * explainstone —— 讲解石 / 颂美铃 / 广播发信器
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.ginka.explainstone.client.audio;

import com.ginka.explainstone.speaker.Track;
import net.minecraft.Util;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.AudioStream;
import com.mojang.blaze3d.audio.OggAudioStream;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Common streaming sound implementation. The loader-specific subclasses only bridge
 * the platform's custom-stream hook to {@link #openStream()}.
 */
public abstract class SpeakerSoundInstance extends AbstractSoundInstance implements TickableSoundInstance {
    public static final ResourceLocation SOUND_ID = new ResourceLocation("explainstone", "speaker_music");

    private final Track track;
    private final double startOffsetSeconds;
    private volatile double durationSeconds = -1.0;
    private volatile boolean started;
    private boolean stopped;
    private int ticks;

    protected SpeakerSoundInstance(BlockPos pos, Track track, int audibleRange, int volumePercent,
                                   double startOffsetSeconds) {
        super(SOUND_ID, SoundSource.RECORDS, SoundInstance.createUnseededRandom());
        this.track = track;
        this.startOffsetSeconds = Math.max(0.0, startOffsetSeconds);
        this.x = pos.getX() + 0.5;
        this.y = pos.getY() + 0.5;
        this.z = pos.getZ() + 0.5;
        update(audibleRange, volumePercent);
    }

    public final void update(int audibleRange, int volumePercent) {
        float range = Math.max(8, Math.min(48, audibleRange));
        this.volume = (range / 16.0f) * (Math.max(0, Math.min(100, volumePercent)) / 100.0f);
    }

    public final Track track() {
        return this.track;
    }

    public final boolean isStarted() {
        return this.started;
    }

    public final float[] progress() {
        double duration = this.durationSeconds;
        if (duration <= 0.0) {
            return null;
        }
        double elapsed = this.startOffsetSeconds + this.ticks / 20.0;
        elapsed %= duration;
        return new float[]{(float) elapsed, (float) duration};
    }

    @Override
    public final void tick() {
        this.ticks++;
        if (this.durationSeconds > 0.0 && this.ticks > this.durationSeconds * 20.0 + 20.0) {
            this.stop();
        }
    }

    @Override
    public final boolean isStopped() {
        return this.stopped;
    }

    public final void stop() {
        this.stopped = true;
        this.looping = false;
    }

    /** Runs on the platform's background executor; never on the render thread. */
    protected final CompletableFuture<AudioStream> openStream() {
        return CompletableFuture.supplyAsync(() -> {
            this.durationSeconds = AudioSources.durationSeconds(this.track);
            InputStream stream = AudioSources.open(this.track);
            if (stream == null) {
                throw new CompletionException(new IOException("Missing audio: " + this.track.id()));
            }
            try {
                AudioStream audio = new OggAudioStream(stream);
                this.started = true;
                if (this.startOffsetSeconds > 0.0 && audio.getFormat().getFrameSize() > 0) {
                    double offset = this.startOffsetSeconds;
                    if (this.durationSeconds > 0.0) {
                        offset %= this.durationSeconds;
                    }
                    long bytes = (long) (offset * audio.getFormat().getSampleRate()) * audio.getFormat().getFrameSize();
                    audio = new SkippingAudioStream(audio, bytes);
                }
                return audio;
            } catch (IOException e) {
                this.stopped = true;
                try {
                    stream.close();
                } catch (IOException ignored) {
                }
                throw new CompletionException(e);
            }
        }, Util.backgroundExecutor());
    }

    private static final class SkippingAudioStream implements AudioStream {
        private final AudioStream delegate;
        private long bytesToSkip;

        private SkippingAudioStream(AudioStream delegate, long bytesToSkip) {
            this.delegate = delegate;
            this.bytesToSkip = bytesToSkip;
        }

        @Override
        public AudioFormat getFormat() {
            return this.delegate.getFormat();
        }

        @Override
        public ByteBuffer read(int size) throws IOException {
            while (this.bytesToSkip > 0L) {
                ByteBuffer buffer = this.delegate.read((int) Math.min(Integer.MAX_VALUE, Math.max(1L, this.bytesToSkip)));
                if (buffer == null) {
                    return null;
                }
                int skip = (int) Math.min(buffer.remaining(), this.bytesToSkip);
                buffer.position(buffer.position() + skip);
                this.bytesToSkip -= skip;
                if (buffer.hasRemaining()) {
                    return buffer;
                }
            }
            return this.delegate.read(size);
        }

        @Override
        public void close() throws IOException {
            this.delegate.close();
        }
    }
}
