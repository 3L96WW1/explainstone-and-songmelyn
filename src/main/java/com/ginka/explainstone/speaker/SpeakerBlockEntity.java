/*
 * explainstone —— 讲解石 / 颂美铃 / 广播发信器（Minecraft Fabric 1.21.1 模组）
 *
 *  作者 / Author : GINKA_3L96WW1
 *  2011
 *
 * 本文件由作者手工编写；贴图与模型由 tools/ 下的脚本程序化生成。
 * 保留此注释即可随意修改、分发。 喵
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.ginka.explainstone.speaker;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * 讲解石方块的数据：播放列表 + 播放模式 + 是否接受红石触发 + 当前选中的曲目。
 * 这些数据存在存档里（NBT），并会同步给打开界面的客户端。 喵
 */
public class SpeakerBlockEntity extends BlockEntity {
    private final List<Track> tracks = new ArrayList<>();
    private int selected;
    private PlayMode mode = PlayMode.SEQUENCE;
    private boolean respectRedstone = true;
    /** 上一次看到的红石状态，用来只响应"边沿"（防止旁边方块被破坏时误停播放） 喵 */
    private boolean lastPowered;
    /** 频道号：0-999，界面上显示成 350.xxx 喵 */
    private int channel = 234;
    /** 收信模式：只听广播，不放自己的列表 喵 */
    private boolean receiveMode;
    /** 本机是否放音（关掉后即使收信成功也不出声） 喵 */
    private boolean speakerEnabled = true;
    /** 发信器信号传播距离（格），32-512 喵 */
    private int transmitRange = 512;
    /** 本机音量百分比 0-100 喵 */
    private int volume = 100;
    /** 讲解石播放音频的可闻距离（格），8-48 喵 */
    private int audibleRange = 40;
    /** 定时播放秒数（0 = 不定时），只对发信器有效 喵 */
    private int timerSeconds;
    /** 定时倒计时（tick），运行时用 喵 */
    private int ticksLeft;

    public SpeakerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        // 包内不再自带音频，列表从空开始，玩家自己导入 ogg 喵
    }

    /** 供 BlockEntityType.Builder 使用的构造器 喵 */
    public SpeakerBlockEntity(BlockPos pos, BlockState state) {
        this(com.ginka.explainstone.ModBlocks.SPEAKER_BLOCK_ENTITY, pos, state);
    }

    // ------------------------------------------------------------ 播放列表 喵
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

    public int indexOf(Track track) {
        return this.tracks.indexOf(track);
    }

    // ------------------------------------------------------------ 模式 / 红石 喵
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

    /** 发信器开始广播时调用：启动定时倒计时 喵 */
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

    /** 记录红石状态（不触发客户端同步，避免邻居更新时刷包） 喵 */
    public void setLastPowered(boolean powered) {
        if (this.lastPowered != powered) {
            this.lastPowered = powered;
            this.markDirty();
        }
    }

    public void setRespectRedstone(boolean respectRedstone) {
        this.respectRedstone = respectRedstone;
        this.sync();
    }

    /** 把改动同步到客户端（界面显示 + 音效引擎都要用） 喵 */
    public void sync() {
        this.markDirty();
        if (this.world != null && !this.world.isClient) {
            BlockState state = this.getCachedState();
            this.world.updateListeners(this.pos, state, state, Block.NOTIFY_ALL);
        }
    }

    // ------------------------------------------------------------------ NBT
    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        NbtList list = new NbtList();
        for (Track track : this.tracks) {
            list.add(track.toNbt());
        }
        nbt.put("tracks", list);
        nbt.putInt("selected", this.selected);
        nbt.putString("mode", this.mode.asString());
        nbt.putBoolean("respectRedstone", this.respectRedstone);
        nbt.putBoolean("lastPowered", this.lastPowered);
        nbt.putInt("channel", this.channel);
        nbt.putBoolean("receiveMode", this.receiveMode);
        nbt.putInt("timerSeconds", this.timerSeconds);
        nbt.putBoolean("speakerEnabled", this.speakerEnabled);
        nbt.putInt("transmitRange", this.transmitRange);
        nbt.putInt("audibleRange", this.audibleRange);
        nbt.putInt("volume", this.volume);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        this.tracks.clear();
        NbtList list = nbt.getList("tracks", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            this.tracks.add(Track.fromNbt(list.getCompound(i)));
        }
        // 包内已不再附带音频：把解析不到的 builtin 曲目剔掉，避免"列表里有、点了不出声" 喵
        this.tracks.removeIf(track -> !resolvable(track));
        this.selected = nbt.getInt("selected");
        for (PlayMode value : PlayMode.values()) {
            if (value.asString().equals(nbt.getString("mode"))) {
                this.mode = value;
            }
        }
        if (nbt.contains("respectRedstone")) {
            this.respectRedstone = nbt.getBoolean("respectRedstone");
        }
        if (nbt.contains("lastPowered")) {
            this.lastPowered = nbt.getBoolean("lastPowered");
        }
        if (nbt.contains("channel")) {
            this.channel = Math.floorMod(nbt.getInt("channel"), 1000);
        }
        if (nbt.contains("receiveMode")) {
            this.receiveMode = nbt.getBoolean("receiveMode");
        }
        if (nbt.contains("timerSeconds")) {
            this.timerSeconds = Math.max(0, nbt.getInt("timerSeconds"));
        }
        if (nbt.contains("speakerEnabled")) {
            this.speakerEnabled = nbt.getBoolean("speakerEnabled");
        }
        if (nbt.contains("transmitRange")) {
            this.transmitRange = Math.max(32, Math.min(512, nbt.getInt("transmitRange")));
        }
        if (nbt.contains("volume")) {
            this.volume = Math.max(0, Math.min(100, nbt.getInt("volume")));
        }
        if (nbt.contains("audibleRange")) {
            this.audibleRange = Math.max(8, Math.min(48, nbt.getInt("audibleRange")));
        }
    }

    /** 这条曲目还能不能放：导入曲目看文件，内置曲目看 jar 里有没有对应 ogg 喵 */
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
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        return this.createNbt(registries);
    }

    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }
}
