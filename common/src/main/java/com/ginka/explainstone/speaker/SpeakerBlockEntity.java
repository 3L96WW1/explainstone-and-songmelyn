/*
 * explainstone —— 讲解石 / 颂美铃 / 广播发信器
 *
 * 作者 / Author : GINKA_3L96WW1
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.ginka.explainstone.speaker;

import com.ginka.explainstone.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.List;

public class SpeakerBlockEntity extends BlockEntity {
    private final List<Track> tracks = new ArrayList<>();
    private int selected;
    private PlayMode mode = PlayMode.SEQUENCE;
    private boolean respectRedstone = true;
    private boolean lastPowered;
    private int channel = 234;
    private boolean receiveMode;
    private boolean speakerEnabled = true;
    private int transmitRange = 512;
    private int volume = 100;
    private int audibleRange = 40;
    private int timerSeconds;
    private int ticksLeft;

    public SpeakerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public SpeakerBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlocks.speakerBlockEntity, pos, state);
    }

    public List<Track> getTracks() {
        return List.copyOf(this.tracks);
    }

    public int getSelected() {
        return this.tracks.isEmpty() ? -1 : Math.floorMod(this.selected, this.tracks.size());
    }

    public Track getSelectedTrack() {
        int i = this.getSelected();
        return i < 0 ? null : this.tracks.get(i);
    }

    public void setSelected(int index) {
        if (index >= 0 && index < this.tracks.size()) {
            this.selected = index;
            this.sync();
        }
    }

    public int addTrack(Track track) {
        this.tracks.add(track);
        this.sync();
        return this.tracks.size() - 1;
    }

    public void removeTrack(int index) {
        if (index >= 0 && index < this.tracks.size()) {
            this.tracks.remove(index);
            if (this.selected >= this.tracks.size()) {
                this.selected = Math.max(0, this.tracks.size() - 1);
            }
            this.sync();
        }
    }

    public PlayMode getMode() {
        return this.mode;
    }

    public void setMode(PlayMode mode) {
        this.mode = mode;
        this.sync();
    }

    public boolean isRespectRedstone() {
        return this.respectRedstone;
    }

    public boolean isLastPowered() {
        return this.lastPowered;
    }

    public int getChannel() {
        return this.channel;
    }

    public void setChannel(int channel) {
        this.channel = Math.floorMod(channel, 1000);
        this.sync();
    }

    public boolean isReceiveMode() {
        return this.receiveMode;
    }

    public void setReceiveMode(boolean receiveMode) {
        this.receiveMode = receiveMode;
        this.sync();
    }

    public boolean isSpeakerEnabled() {
        return this.speakerEnabled;
    }

    public void setSpeakerEnabled(boolean enabled) {
        this.speakerEnabled = enabled;
        this.sync();
    }

    public int getTransmitRange() {
        return this.transmitRange;
    }

    public void setTransmitRange(int range) {
        this.transmitRange = Math.max(32, Math.min(512, range));
        this.sync();
    }

    public int getVolume() {
        return this.volume;
    }

    public void setVolume(int volume) {
        this.volume = Math.max(0, Math.min(100, volume));
        this.sync();
    }

    public int getAudibleRange() {
        return this.audibleRange;
    }

    public void setAudibleRange(int range) {
        this.audibleRange = Math.max(8, Math.min(48, range));
        this.sync();
    }

    public int getTimerSeconds() {
        return this.timerSeconds;
    }

    public void setTimerSeconds(int seconds) {
        this.timerSeconds = Math.max(0, seconds);
        this.sync();
    }

    public void startTimer() {
        this.ticksLeft = this.timerSeconds * 20;
    }

    public void stopTimer() {
        this.ticksLeft = 0;
    }

    public boolean hasTimerExpired() {
        return this.timerSeconds > 0 && this.ticksLeft <= 0;
    }

    public boolean tickTimer() {
        if (this.timerSeconds > 0) {
            if (this.ticksLeft > 0) {
                this.ticksLeft--;
            }
            return this.ticksLeft > 0;
        }
        return true;
    }

    public void setLastPowered(boolean powered) {
        if (this.lastPowered != powered) {
            this.lastPowered = powered;
            this.setChanged();
        }
    }

    public void setRespectRedstone(boolean respectRedstone) {
        this.respectRedstone = respectRedstone;
        this.sync();
    }

    public void sync() {
        this.setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            BlockState state = this.getBlockState();
            this.level.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_ALL);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ValueOutput.ValueOutputList list = out.childrenList("tracks");
        for (Track track : this.tracks) {
            track.toNbt(list.addChild());
        }
        out.putInt("selected", this.selected);
        out.putString("mode", this.mode.getSerializedName());
        out.putBoolean("respectRedstone", this.respectRedstone);
        out.putBoolean("lastPowered", this.lastPowered);
        out.putInt("channel", this.channel);
        out.putBoolean("receiveMode", this.receiveMode);
        out.putInt("timerSeconds", this.timerSeconds);
        out.putBoolean("speakerEnabled", this.speakerEnabled);
        out.putInt("transmitRange", this.transmitRange);
        out.putInt("audibleRange", this.audibleRange);
        out.putInt("volume", this.volume);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        this.tracks.clear();
        for (ValueInput child : in.childrenListOrEmpty("tracks")) {
            this.tracks.add(Track.fromNbt(child));
        }
        this.tracks.removeIf(track -> !resolvable(track));
        this.selected = in.getIntOr("selected", this.selected);
        String mode = in.getStringOr("mode", this.mode.getSerializedName());
        for (PlayMode value : PlayMode.values()) {
            if (value.getSerializedName().equals(mode)) {
                this.mode = value;
            }
        }
        this.respectRedstone = in.getBooleanOr("respectRedstone", this.respectRedstone);
        this.lastPowered = in.getBooleanOr("lastPowered", this.lastPowered);
        this.channel = Math.floorMod(in.getIntOr("channel", this.channel), 1000);
        this.receiveMode = in.getBooleanOr("receiveMode", this.receiveMode);
        this.timerSeconds = Math.max(0, in.getIntOr("timerSeconds", this.timerSeconds));
        this.speakerEnabled = in.getBooleanOr("speakerEnabled", this.speakerEnabled);
        this.transmitRange = Math.max(32, Math.min(512, in.getIntOr("transmitRange", this.transmitRange)));
        this.volume = Math.max(0, Math.min(100, in.getIntOr("volume", this.volume)));
        this.audibleRange = Math.max(8, Math.min(48, in.getIntOr("audibleRange", this.audibleRange)));
    }

    private static boolean resolvable(Track track) {
        if (track.isFile()) {
            return true;
        }
        String name = track.builtinName();
        if (!name.matches("[a-z0-9_\\-]+")) {
            return false;
        }
        return SpeakerBlockEntity.class.getResource("/assets/explainstone/sounds/" + name + ".ogg") != null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveWithFullMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
