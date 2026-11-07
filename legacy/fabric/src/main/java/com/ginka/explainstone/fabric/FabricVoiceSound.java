/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.ginka.explainstone.fabric;

import com.ginka.explainstone.voice.VoiceSoundInstance;
import net.fabricmc.fabric.api.client.sound.v1.FabricSoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Fabric 的流式声音注入点。 */
public final class FabricVoiceSound extends VoiceSoundInstance implements FabricSoundInstance {
    public FabricVoiceSound(UUID sender, Player player) {
        super(sender, player);
    }

    @Override
    public CompletableFuture<AudioStream> getAudioStream(SoundBufferLibrary library, ResourceLocation id, boolean repeatInstantly) {
        return openStream(library, id);
    }
}
