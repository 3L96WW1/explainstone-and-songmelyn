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

import com.ginka.explainstone.Explainstone;
import com.ginka.explainstone.client.audio.AudioSources;
import com.ginka.explainstone.client.audio.TrumpetAudio;
import com.ginka.explainstone.speaker.PlayMode;
import com.ginka.explainstone.speaker.Track;
import com.ginka.explainstone.trumpet.TrumpetPayloads;
import com.ginka.explainstone.trumpet.TrumpetState;
import com.ginka.explainstone.trumpet.TrumpetStates;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Path;
import java.util.List;

/** 手持特朗谱时的面板适配。状态来自物品组件，不是方块实体。 */
public final class TrumpetPanel implements SpeakerPanel {
    /** 界面打开时手上那格的槽位号；服务端据此定位物品。 */
    private final int slot;
    /** 最近一次从服务端同步回来的状态，避免每帧都读组件。 */
    private TrumpetState cached;

    public TrumpetPanel(int slot) {
        this.slot = slot;
        this.cached = currentState(slot);
    }

    static int heldSlot() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return -1;
        }
        return client.player.getInventory().getSelectedSlot();
    }

    private static TrumpetState currentState(int slot) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || slot < 0) {
            return TrumpetState.EMPTY;
        }
        ItemStack stack = client.player.getInventory().getItem(slot);
        return TrumpetStates.get(stack);
    }

    int slot() {
        return this.slot;
    }

    void update(TrumpetState state) {
        this.cached = state;
    }

    private TrumpetState state() {
        // 组件可能被别的途径改过（比如服务端同步），以物品上的为准
        TrumpetState live = currentState(this.slot);
        return live.equals(TrumpetState.EMPTY) ? this.cached : live;
    }

    @Override
    public Component title() {
        return Component.translatable("screen.explainstone.title.trumpet");
    }

    @Override
    public boolean transmitter() {
        return false;
    }

    @Override
    public List<Track> tracks() {
        return this.state().tracks();
    }

    @Override
    public int selected() {
        return this.state().resolvedSelected();
    }

    @Override
    public PlayMode mode() {
        return this.state().mode();
    }

    @Override
    public boolean receiveMode() {
        return this.state().receiveMode();
    }

    @Override
    public boolean speakerEnabled() {
        return this.state().speakerEnabled();
    }

    @Override
    public boolean respectRedstone() {
        // 手持物品没有红石概念，界面上这个按钮会被隐藏
        return false;
    }

    @Override
    public int channel() {
        return this.state().channel();
    }

    @Override
    public int volume() {
        return this.state().volume();
    }

    @Override
    public int audibleRange() {
        return this.state().audibleRange();
    }

    @Override
    public int transmitRange() {
        return this.state().transmitRange();
    }

    @Override
    public int timerSeconds() {
        return 0;
    }

    @Override
    public boolean playing() {
        return TrumpetAudio.isPlaying(this.slot);
    }

    @Override
    public float[] progress() {
        return TrumpetAudio.progress(this.slot);
    }

    @Override
    public void send(int action, int value) {
        if (Explainstone.network().canSendToServer(TrumpetPayloads.Action.ID)) {
            Explainstone.network().sendToServer(new TrumpetPayloads.Action(this.slot, action, value));
        }
        // 物品没有方块状态可读，播放/停止只能本地自己起停
        switch (action) {
            case com.ginka.explainstone.speaker.SpeakerPayloads.ACTION_PLAY ->
                    TrumpetAudio.play(this.slot, this.selected());
            case com.ginka.explainstone.speaker.SpeakerPayloads.ACTION_PLAY_INDEX ->
                    TrumpetAudio.play(this.slot, value);
            case com.ginka.explainstone.speaker.SpeakerPayloads.ACTION_STOP ->
                    TrumpetAudio.stop(this.slot);
            default -> {
            }
        }
    }

    @Override
    public void importFile(Path file) {
        TrumpetAudio.importFile(Minecraft.getInstance(), this.slot, file);
    }

    /** 服务端把改好的状态推回来。 */
    public static void onStateSync(TrumpetPayloads.StateSync payload) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }
        TrumpetPanel panel = ACTIVE;
        if (panel != null && panel.slot == payload.slot()) {
            panel.update(payload.state());
            return;
        }
        // 界面没开：直接把状态写回物品，让下一次打开就拿到最新的
        if (payload.slot() >= 0 && payload.slot() < client.player.getInventory().getContainerSize()) {
            ItemStack stack = client.player.getInventory().getItem(payload.slot());
            TrumpetStates.set(stack, payload.state());
        }
    }

    /** 当前打开的面板，服务端回包要靠它定位。 */
    private static TrumpetPanel ACTIVE;

    static void activate(TrumpetPanel panel) {
        ACTIVE = panel;
    }

    static void deactivate(TrumpetPanel panel) {
        if (ACTIVE == panel) {
            ACTIVE = null;
        }
    }

    /** 供界面显示「下载中」与时长用。 */
    static boolean hasLocal(Track track) {
        return AudioSources.hasLocal(track);
    }
}
