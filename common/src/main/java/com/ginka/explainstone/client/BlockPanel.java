/*
 * explainstone —— 讲解石 / 颂美铃 / 广播发信器 / 特朗谱
 *
 * 作者 / Author : GINKA_3L96WW1
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.ginka.explainstone.client;

import com.ginka.explainstone.BroadcastTransmitterBlock;
import com.ginka.explainstone.Explainstone;
import com.ginka.explainstone.SongmelynBlock;
import com.ginka.explainstone.SpeakerBlock;
import com.ginka.explainstone.client.audio.SpeakerAudio;
import com.ginka.explainstone.client.audio.SpeakerAudioCache;
import com.ginka.explainstone.speaker.PlayMode;
import com.ginka.explainstone.speaker.SpeakerBlockEntity;
import com.ginka.explainstone.speaker.SpeakerPayloads;
import com.ginka.explainstone.speaker.Track;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.List;

/** 方块形式（讲解石 / 颂美铃 / 广播发信器）的面板适配。 */
public final class BlockPanel implements SpeakerPanel {
    private final BlockPos pos;

    BlockPanel(BlockPos pos) {
        this.pos = pos;
    }

    private SpeakerBlockEntity entity() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return null;
        }
        return client.level.getBlockEntity(this.pos) instanceof SpeakerBlockEntity be ? be : null;
    }

    @Override
    public Component title() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return Component.translatable("screen.explainstone.title.speaker");
        }
        net.minecraft.world.level.block.Block block = client.level.getBlockState(this.pos).getBlock();
        if (block instanceof BroadcastTransmitterBlock) {
            return Component.translatable("screen.explainstone.title.transmitter");
        }
        if (block instanceof SongmelynBlock) {
            return Component.translatable("screen.explainstone.title.songmelyn");
        }
        return Component.translatable("screen.explainstone.title.speaker");
    }

    @Override
    public boolean transmitter() {
        Minecraft client = Minecraft.getInstance();
        return client.level != null
                && client.level.getBlockState(this.pos).getBlock() instanceof BroadcastTransmitterBlock;
    }

    @Override
    public List<Track> tracks() {
        SpeakerBlockEntity be = this.entity();
        return be == null ? List.of() : be.getTracks();
    }

    @Override
    public int selected() {
        SpeakerBlockEntity be = this.entity();
        return be == null ? -1 : be.getSelected();
    }

    @Override
    public PlayMode mode() {
        SpeakerBlockEntity be = this.entity();
        return be == null ? PlayMode.SEQUENCE : be.getMode();
    }

    @Override
    public boolean receiveMode() {
        SpeakerBlockEntity be = this.entity();
        return be != null && be.isReceiveMode();
    }

    @Override
    public boolean speakerEnabled() {
        SpeakerBlockEntity be = this.entity();
        return be == null || be.isSpeakerEnabled();
    }

    @Override
    public boolean respectRedstone() {
        SpeakerBlockEntity be = this.entity();
        return be == null || be.isRespectRedstone();
    }

    @Override
    public int channel() {
        SpeakerBlockEntity be = this.entity();
        return be == null ? 234 : be.getChannel();
    }

    @Override
    public int volume() {
        SpeakerBlockEntity be = this.entity();
        return be == null ? 100 : be.getVolume();
    }

    @Override
    public int audibleRange() {
        SpeakerBlockEntity be = this.entity();
        return be == null ? 40 : be.getAudibleRange();
    }

    @Override
    public int transmitRange() {
        SpeakerBlockEntity be = this.entity();
        return be == null ? 512 : be.getTransmitRange();
    }

    @Override
    public int timerSeconds() {
        SpeakerBlockEntity be = this.entity();
        return be == null ? 0 : be.getTimerSeconds();
    }

    @Override
    public boolean playing() {
        SpeakerBlockEntity be = this.entity();
        if (be == null) {
            return false;
        }
        return this.transmitter()
                ? be.getBlockState().getValue(BroadcastTransmitterBlock.PLAYING)
                : be.getBlockState().getValue(SpeakerBlock.PLAYING);
    }

    @Override
    public float[] progress() {
        return this.transmitter()
                ? SpeakerAudio.broadcastProgress(this.channel())
                : SpeakerAudio.progress(this.pos);
    }

    @Override
    public void send(int action, int value) {
        if (Explainstone.network().canSendToServer(SpeakerPayloads.Action.ID)) {
            Explainstone.network().sendToServer(new SpeakerPayloads.Action(this.pos, action, value));
        }
    }

    @Override
    public void importFile(Path file) {
        SpeakerAudioCache.importFile(Minecraft.getInstance(), this.pos, file);
    }
}
