/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.ginka.explainstone.client.audio;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import net.minecraft.client.sounds.JOrbisAudioStream;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Decodes an Ogg Vorbis stream into interleaved signed 16-bit PCM. */
public final class OggDecoder {
    public record Decoded(AudioFormat format, ByteBuffer pcm) {
    }

    private OggDecoder() {
    }

    public static Decoded decode(InputStream stream) throws IOException {
        try (JOrbisAudioStream ogg = new JOrbisAudioStream(stream)) {
            FloatArrayList samples = new FloatArrayList();
            while (ogg.readChunk(samples::add)) {
                // Decode until the stream ends.
            }
            ByteBuffer pcm = ByteBuffer.allocateDirect(samples.size() * 2).order(ByteOrder.nativeOrder());
            for (int i = 0; i < samples.size(); i++) {
                float value = Math.max(-1.0f, Math.min(1.0f, samples.getFloat(i)));
                pcm.putShort((short) (value * 32767.0f));
            }
            pcm.flip();
            return new Decoded(ogg.getFormat(), pcm);
        }
    }
}
