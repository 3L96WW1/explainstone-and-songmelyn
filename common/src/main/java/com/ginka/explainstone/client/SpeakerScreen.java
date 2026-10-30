/*
 * explainstone —— 讲解石 / 颂美铃 / 广播发信器（Minecraft 1.21.1 模组（Fabric + NeoForge））
 *
 *  作者 / Author : GINKA_3L96WW1
 *
 * 本文件由作者手工编写；贴图与模型由 tools/ 下的脚本程序化生成。
 * 保留此注释即可随意修改、分发。 喵
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.ginka.explainstone.client;

import com.ginka.explainstone.Explainstone;

import com.ginka.explainstone.BroadcastTransmitterBlock;
import com.ginka.explainstone.SongmelynBlock;
import com.ginka.explainstone.SpeakerBlock;
import com.ginka.explainstone.client.audio.AudioSources;
import com.ginka.explainstone.client.audio.SpeakerAudio;
import com.ginka.explainstone.client.audio.SpeakerAudioCache;
import com.ginka.explainstone.client.widget.IntSlider;
import com.ginka.explainstone.speaker.SpeakerBlockEntity;
import com.ginka.explainstone.speaker.SpeakerPayloads;
import com.ginka.explainstone.speaker.Track;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * 讲解石 / 颂美铃 / 广播发信器 的交互界面。
 * 布局按屏幕高度自适应（从底部往上排），小窗口/大 GUI 缩放时不会溢出。 喵
 */
public class SpeakerScreen extends Screen {
    private static final int PANEL_W = 300;
    private static final int ROW_H = 12;
    private static final int BTN_H = 20;
    private static final int GAP = 24;
    private static final String CHANNEL_PREFIX = "350.";
    private static final int DEFAULT_CHANNEL = 234;

    private final BlockPos pos;
    private int scroll;
    private boolean updatingField;

    // 自适应布局（init() 里算好） 喵
    private int panelW;
    private int left;
    private int titleY;
    private int listTop;
    private int listRows;
    private int hintY;
    private int row1;
    private int row2;
    private int row3;
    private int row4;
    private int channelY;
    private int sliderY;

    private Button tabSelf;
    private Button tabReceive;
    private Button playButton;
    private Button stopButton;
    private Button modeButton;
    private Button redstoneButton;
    private Button playModeButton;
    private Button enableButton;
    private Button importButton;
    private Button removeButton;
    private Button confirmButton;
    private Button resetButton;
    private EditBox channelField;
    private IntSlider rangeSlider;
    private IntSlider volumeSlider;
    private int pendingVolumeValue = -1;
    private long pendingVolumeAt;

    private int pendingRangeValue = -1;
    private boolean pendingRangeTransmit;
    private long pendingRangeAt;

    public SpeakerScreen(BlockPos pos) {
        super(Component.translatable("screen.explainstone.title.speaker"));
        this.pos = pos;
    }

    /** 当前方块的中文名（讲解石 / 颂美铃 / 广播发信器） 喵 */
    /** 当前方块的名字翻译键 喵 */
    private String blockNameKey() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return "screen.explainstone.title.speaker";
        }
        net.minecraft.world.level.block.Block block = client.level.getBlockState(this.pos).getBlock();
        if (block instanceof BroadcastTransmitterBlock) {
            return "screen.explainstone.title.transmitter";
        }
        if (block instanceof SongmelynBlock) {
            return "screen.explainstone.title.songmelyn";
        }
        return "screen.explainstone.title.speaker";
    }

    private Component blockName() {
        return Component.translatable(this.blockNameKey());
    }

    private boolean isTransmitter() {
        Minecraft client = Minecraft.getInstance();
        return client.level != null
                && client.level.getBlockState(this.pos).getBlock() instanceof BroadcastTransmitterBlock;
    }

    @Override
    public Component getTitle() {
        return this.blockName();
    }

    private SpeakerBlockEntity speaker() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return null;
        }
        return client.level.getBlockEntity(this.pos) instanceof SpeakerBlockEntity be ? be : null;
    }

    @Override
    protected void init() {
        this.panelW = Math.min(PANEL_W, Math.max(220, this.width - 16));
        this.left = (this.width - this.panelW) / 2;
        int half = this.panelW / 2 - 2;

        // ---- 从底部往上排，保证永远放得下（窗口很矮时自动压缩行距）---- 喵
        int gap = this.height < 260 ? 20 : GAP;
        int bottom = this.height - 6;
        this.sliderY = bottom - BTN_H;
        this.channelY = this.sliderY - gap;
        this.row4 = this.channelY - gap;
        this.row3 = this.row4 - gap;
        this.row2 = this.row3 - gap;
        this.row1 = this.row2 - gap;
        this.hintY = this.row1 - 12;

        // ---- 顶部固定 ---- 喵
        this.titleY = 8;
        int tabY = this.titleY + 14;
        this.listTop = tabY + 20 + 6;
        this.listRows = Math.max(1, (this.hintY - this.listTop - 2) / ROW_H);

        // 模式标签页 喵
        this.tabSelf = this.addRenderableWidget(Button.builder(Component.translatable("screen.explainstone.tab.self"), b -> {
            SpeakerBlockEntity be = this.speaker();
            if (be != null && be.isReceiveMode()) {
                send(SpeakerPayloads.ACTION_TOGGLE_RECEIVE, 0);
            }
            this.refreshLabels();
        }).bounds(this.left, tabY, half, 18).build());
        this.tabReceive = this.addRenderableWidget(Button.builder(Component.translatable("screen.explainstone.tab.receive"), b -> {
            SpeakerBlockEntity be = this.speaker();
            if (be != null && !be.isReceiveMode()) {
                send(SpeakerPayloads.ACTION_TOGGLE_RECEIVE, 0);
            }
            this.refreshLabels();
        }).bounds(this.left + half + 4, tabY, half, 18).build());

        // 播放 / 停止 喵
        this.playButton = this.addRenderableWidget(Button.builder(Component.translatable("screen.explainstone.button.play"),
                        b -> send(SpeakerPayloads.ACTION_PLAY, 0))
                .bounds(this.left, this.row1, half, BTN_H).build());
        this.stopButton = this.addRenderableWidget(Button.builder(Component.translatable("screen.explainstone.button.stop"),
                        b -> send(SpeakerPayloads.ACTION_STOP, 0))
                .bounds(this.left + half + 4, this.row1, half, BTN_H).build());

        // 收信开关（发信器则是定时） 喵
        this.modeButton = this.addRenderableWidget(Button.builder(Component.translatable("screen.explainstone.button.receive"), b -> {
            if (this.isTransmitter()) {
                int[] steps = {0, 60, 180, 600};
                SpeakerBlockEntity be = this.speaker();
                int current = be == null ? 0 : be.getTimerSeconds();
                int next = steps[0];
                for (int i = 0; i < steps.length; i++) {
                    if (steps[i] == current) {
                        next = steps[(i + 1) % steps.length];
                        break;
                    }
                }
                send(SpeakerPayloads.ACTION_SET_TIMER, next);
            } else {
                send(SpeakerPayloads.ACTION_TOGGLE_RECEIVE, 0);
            }
            this.refreshLabels();
        }).bounds(this.left, this.row2, half, BTN_H).build());

        this.redstoneButton = this.addRenderableWidget(Button.builder(Component.translatable("screen.explainstone.button.redstone"), b -> {
            send(SpeakerPayloads.ACTION_TOGGLE_REDSTONE, 0);
            this.refreshLabels();
        }).bounds(this.left + half + 4, this.row2, half, BTN_H).build());

        // 播放模式 喵
        this.playModeButton = this.addRenderableWidget(Button.builder(Component.translatable("screen.explainstone.button.mode"), b -> {
            send(SpeakerPayloads.ACTION_CYCLE_MODE, 0);
            this.refreshLabels();
        }).bounds(this.left, this.row3, half, BTN_H).build());

        // 放音开关（只有讲解石/颂美铃） 喵
        this.enableButton = this.addRenderableWidget(Button.builder(Component.translatable("screen.explainstone.button.enable"),
                        b -> send(SpeakerPayloads.ACTION_TOGGLE_ENABLED, 0))
                .bounds(this.left + half + 4, this.row3, half, BTN_H).build());

        // 导入 / 删除 喵
        this.importButton = this.addRenderableWidget(Button.builder(Component.translatable("screen.explainstone.button.import"),
                        b -> this.onImportClicked())
                .bounds(this.left, this.row4, half, BTN_H).build());
        this.removeButton = this.addRenderableWidget(Button.builder(Component.translatable("screen.explainstone.button.remove"), b -> {
            SpeakerBlockEntity be = this.speaker();
            if (be != null && be.getSelected() >= 0) {
                send(SpeakerPayloads.ACTION_REMOVE_TRACK, be.getSelected());
            }
        }).bounds(this.left + half + 4, this.row4, half, BTN_H).build());

        // 频率：350.xxx 喵
        SpeakerBlockEntity be = this.speaker();
        int channel = be == null ? DEFAULT_CHANNEL : be.getChannel();
        this.channelField = new EditBox(this.font, this.left + 34, this.channelY, 62, 18,
                Component.translatable("screen.explainstone.label.frequency"));
        this.channelField.setMaxLength(CHANNEL_PREFIX.length() + 3);
        this.channelField.setValue(CHANNEL_PREFIX + String.format("%03d", channel));
        this.channelField.setResponder(text -> {
            if (this.updatingField) {
                return;
            }
            String fixed = sanitizeChannel(text);
            if (!fixed.equals(text)) {
                this.updatingField = true;
                this.channelField.setValue(fixed);
                this.updatingField = false;
            }
        });
        this.addRenderableWidget(this.channelField);

        this.confirmButton = this.addRenderableWidget(Button.builder(Component.translatable("screen.explainstone.button.confirm"), b -> {
            int value = parseChannel(this.channelField == null ? "" : this.channelField.getValue());
            this.setChannelField(value);
            send(SpeakerPayloads.ACTION_SET_CHANNEL, value);
        }).bounds(this.left + 102, this.channelY - 1, 56, BTN_H).build());

        this.resetButton = this.addRenderableWidget(Button.builder(Component.translatable("screen.explainstone.button.reset"), b -> {
            this.setChannelField(DEFAULT_CHANNEL);
            send(SpeakerPayloads.ACTION_SET_CHANNEL, DEFAULT_CHANNEL);
        }).bounds(this.left + 162, this.channelY - 1, 56, BTN_H).build());

        // 距离滑块 喵
        if (this.isTransmitter()) {
            int value = be == null ? 512 : be.getTransmitRange();
            this.rangeSlider = this.addRenderableWidget(new IntSlider(this.left, this.sliderY, this.panelW, BTN_H,
                    Component.translatable("screen.explainstone.slider.range").getString(), 32, 512, value, v -> this.queueRange(v, true)));
        } else {
            int distance = be == null ? 40 : be.getAudibleRange();
            int volume = be == null ? 100 : be.getVolume();
            this.rangeSlider = this.addRenderableWidget(new IntSlider(this.left, this.sliderY, half, BTN_H,
                    Component.translatable("screen.explainstone.slider.distance").getString(), 8, 48, distance, v -> this.queueRange(v, false)));
            this.volumeSlider = this.addRenderableWidget(new IntSlider(this.left + half + 4, this.sliderY, half, BTN_H,
                    Component.translatable("screen.explainstone.slider.volume").getString(), 0, 100, volume, v -> this.queueVolume(v)));
        }

        this.refreshLabels();
    }

    private void queueRange(int value, boolean transmit) {
        this.pendingRangeValue = value;
        this.pendingRangeTransmit = transmit;
        this.pendingRangeAt = System.currentTimeMillis() + 150;
    }

    private void queueVolume(int value) {
        this.pendingVolumeValue = value;
        this.pendingVolumeAt = System.currentTimeMillis() + 150;
    }

    private void flushPendingVolume() {
        if (this.pendingVolumeValue < 0 || System.currentTimeMillis() < this.pendingVolumeAt) {
            return;
        }
        send(SpeakerPayloads.ACTION_SET_VOLUME, this.pendingVolumeValue);
        this.pendingVolumeValue = -1;
    }

    private void flushPendingRange() {
        if (this.pendingRangeValue < 0 || System.currentTimeMillis() < this.pendingRangeAt) {
            return;
        }
        send(this.pendingRangeTransmit ? SpeakerPayloads.ACTION_SET_TRANSMIT_RANGE
                : SpeakerPayloads.ACTION_SET_AUDIBLE_RANGE, this.pendingRangeValue);
        this.pendingRangeValue = -1;
    }

    /** 强制保留 "350." 前缀，后面最多 3 位数字 喵 */
    private static String sanitizeChannel(String text) {
        String digits;
        if (text.startsWith(CHANNEL_PREFIX)) {
            digits = text.substring(CHANNEL_PREFIX.length());
        } else {
            digits = text.replaceAll("\\D", "");
            if (digits.startsWith("350")) {
                digits = digits.substring(3);
            }
        }
        digits = digits.replaceAll("\\D", "");
        if (digits.length() > 3) {
            digits = digits.substring(0, 3);
        }
        return CHANNEL_PREFIX + digits;
    }

    private static int parseChannel(String text) {
        String digits = text.startsWith(CHANNEL_PREFIX) ? text.substring(CHANNEL_PREFIX.length()) : text;
        digits = digits.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return DEFAULT_CHANNEL;
        }
        return Math.max(0, Math.min(999, Integer.parseInt(digits)));
    }

    private void setChannelField(int channel) {
        if (this.channelField == null) {
            return;
        }
        this.updatingField = true;
        this.channelField.setValue(CHANNEL_PREFIX + String.format("%03d", Math.floorMod(channel, 1000)));
        this.updatingField = false;
    }

    private void refreshLabels() {
        SpeakerBlockEntity be = this.speaker();
        boolean transmitter = this.isTransmitter();
        boolean receive = !transmitter && be != null && be.isReceiveMode();

        if (this.tabSelf != null) {
            this.tabSelf.visible = !transmitter;
            this.tabSelf.active = true;
            this.tabSelf.setMessage(receive
                    ? Component.translatable("screen.explainstone.tab.self")
                    : Component.translatable("screen.explainstone.tab.self.active"));
        }
        if (this.tabReceive != null) {
            this.tabReceive.visible = !transmitter;
            this.tabReceive.active = true;
            this.tabReceive.setMessage(receive
                    ? Component.translatable("screen.explainstone.tab.receive.active")
                    : Component.translatable("screen.explainstone.tab.receive"));
        }
        if (this.modeButton != null) {
            if (transmitter) {
                int timer = be == null ? 0 : be.getTimerSeconds();
                this.modeButton.setMessage(timer <= 0
                        ? Component.translatable("screen.explainstone.timer.off")
                        : Component.translatable("screen.explainstone.timer.minutes", timer / 60));
            } else {
                this.modeButton.setMessage(receive
                    ? Component.translatable("screen.explainstone.receive.on")
                    : Component.translatable("screen.explainstone.receive.off"));
            }
        }
        if (this.redstoneButton != null) {
            boolean respect = be == null || be.isRespectRedstone();
            this.redstoneButton.setMessage(respect
                    ? Component.translatable("screen.explainstone.redstone.detect")
                    : Component.translatable("screen.explainstone.redstone.ignore"));
            this.redstoneButton.visible = !receive;   // 收信模式没有红石 喵
        }
        if (this.playModeButton != null) {
            this.playModeButton.setMessage(Component.translatable("screen.explainstone.mode.label",
                    be == null ? Component.translatable("screen.explainstone.mode.sequence")
                            : Component.translatable("screen.explainstone.mode." + be.getMode().getSerializedName())));
            this.playModeButton.visible = !receive;
        }
        if (this.enableButton != null) {
            boolean enabled = be == null || be.isSpeakerEnabled();
            this.enableButton.setMessage(enabled
                    ? Component.translatable("screen.explainstone.enable.on")
                    : Component.translatable("screen.explainstone.enable.off"));
            this.enableButton.visible = !transmitter;
        }
        if (this.playButton != null) {
            this.playButton.visible = !receive;
        }
        if (this.stopButton != null) {
            this.stopButton.visible = !receive;
        }
        if (this.importButton != null) {
            this.importButton.visible = !receive;
        }
        if (this.removeButton != null) {
            this.removeButton.visible = !receive;
        }
        boolean showChannel = transmitter || receive;
        if (this.channelField != null) {
            this.channelField.visible = showChannel;
            if (!this.channelField.isFocused()) {
                this.setChannelField(be == null ? DEFAULT_CHANNEL : be.getChannel());
            }
        }
        if (this.confirmButton != null) {
            this.confirmButton.visible = showChannel;
        }
        if (this.resetButton != null) {
            this.resetButton.visible = showChannel;
        }
        this.flushPendingRange();
        this.flushPendingVolume();
        if (this.volumeSlider != null && this.pendingVolumeValue < 0) {
            int actual = be == null ? 100 : be.getVolume();
            if (this.volumeSlider.getIntValue() != actual) {
                this.volumeSlider.setIntValue(actual);
            }
        }
        if (this.rangeSlider != null && this.pendingRangeValue < 0) {
            int actual = transmitter ? (be == null ? 512 : be.getTransmitRange())
                    : (be == null ? 40 : be.getAudibleRange());
            if (this.rangeSlider.getIntValue() != actual) {
                this.rangeSlider.setIntValue(actual);
            }
        }
    }

    private void send(int action, int value) {
        if (Explainstone.network().canSendToServer(SpeakerPayloads.Channel.ACTION)) {
            Explainstone.network().sendToServer(new SpeakerPayloads.Action(this.pos, action, value));
        }
    }

    // ---------------------------------------------------------------- 渲染 喵
    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        super.render(context, mouseX, mouseY, delta);

        SpeakerBlockEntity be = this.speaker();
        boolean transmitter = this.isTransmitter();
        List<Track> tracks = be == null ? List.of() : be.getTracks();
        int selected = be == null ? -1 : be.getSelected();
        boolean playing = be != null && (transmitter
                ? be.getBlockState().getValue(BroadcastTransmitterBlock.PLAYING)
                : be.getBlockState().getValue(SpeakerBlock.PLAYING));
        boolean receive = !transmitter && be != null && be.isReceiveMode();

        Component title = this.blockName();
        if (playing) {
            title = title.copy().append(Component.translatable(
                    transmitter ? "screen.explainstone.sending" : "screen.explainstone.playing"));
        }
        context.drawCenteredString(this.font, title, this.width / 2, this.titleY, 0xFFFFFF);

        if (receive) {
            context.drawCenteredString(this.font,
                    Component.translatable("screen.explainstone.receive.title"),
                    this.width / 2, this.listTop + 10, 0x9BE8C8);
            context.drawCenteredString(this.font,
                    Component.translatable("screen.explainstone.receive.hint"),
                    this.width / 2, this.listTop + 26, 0x909090);
        } else {
            int maxScroll = Math.max(0, tracks.size() - this.listRows);
            this.scroll = Math.min(this.scroll, maxScroll);
            if (tracks.isEmpty()) {
                context.drawString(this.font, Component.translatable("screen.explainstone.list.empty"),
                        this.left + 8, this.listTop + 2, 0xA0A0A0);
            }
            for (int row = 0; row < this.listRows; row++) {
                int index = this.scroll + row;
                if (index >= tracks.size()) {
                    break;
                }
                Track track = tracks.get(index);
                int y = this.listTop + row * ROW_H;
                if (index == selected) {
                    context.fill(this.left, y - 1, this.left + this.panelW, y + ROW_H - 2, 0x602E6BE6);
                }
                String name = (index + 1) + ". " + (index == selected ? "▶ " : "") + track.label();
                if (track.isFile() && !AudioSources.hasLocal(track)) {
                    name = name + "  " + Component.translatable("screen.explainstone.list.downloading").getString();
                }
                context.drawString(this.font, name, this.left + 4, y, 0xFFFFFF);
                String length = formatTime(AudioSources.durationSeconds(track));
                if (length != null) {
                    context.drawString(this.font, length,
                            this.left + this.panelW - 4 - this.font.width(length), y, 0xB0C8FF);
                }
            }
            float[] progress = transmitter
                    ? SpeakerAudio.broadcastProgress(be == null ? 0 : be.getChannel())
                    : SpeakerAudio.progress(this.pos);
            if (progress != null) {
                int barTop = this.hintY - 4;
                int barBottom = this.hintY + 8;
                context.fill(this.left, barTop, this.left + this.panelW, barBottom, 0x60FFFFFF);
                int filled = Math.round(this.panelW * Math.max(0f, Math.min(1f, progress[0] / progress[1])));
                context.fill(this.left, barTop, this.left + filled, barBottom, 0xC06BCB77);
                context.drawCenteredString(this.font,
                        timeOrZero((int) progress[0]) + " / " + timeOrZero((int) progress[1]),
                        this.width / 2, barTop - 10, 0xFFFFFF);
            } else if (this.hintY >= this.listTop + ROW_H) {
                context.drawString(this.font,
                        Component.translatable("screen.explainstone.list.hint", tracks.size()), this.left, this.hintY, 0x909090);
            }
        }

        if (transmitter || receive) {
            context.drawString(this.font, Component.translatable("screen.explainstone.label.frequency"),
                    this.left + 6, this.channelY + 5, 0xFFFFFF);
        }

        this.refreshLabels();
    }

    private static String timeOrZero(int seconds) {
        int s = Math.max(0, seconds);
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    /** 秒 → m:ss；拿不到时长返回 null 喵 */
    private static String formatTime(int seconds) {
        if (seconds <= 0) {
            return null;
        }
        return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        SpeakerBlockEntity be = this.speaker();
        boolean receive = !this.isTransmitter() && be != null && be.isReceiveMode();
        if (!receive && button == 0 && mouseX >= this.left && mouseX <= this.left + this.panelW
                && mouseY >= this.listTop - 1 && mouseY < this.listTop + this.listRows * ROW_H) {
            int index = this.scroll + (int) ((mouseY - this.listTop + 1) / ROW_H);
            if (be != null && index >= 0 && index < be.getTracks().size()) {
                send(SpeakerPayloads.ACTION_SELECT, index);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
        SpeakerBlockEntity be = this.speaker();
        int maxScroll = be == null ? 0 : Math.max(0, be.getTracks().size() - this.listRows);
        this.scroll = Math.max(0, Math.min(maxScroll, this.scroll - (int) Math.signum(verticalAmount)));
        return true;
    }

    /** 导入按钮：先开系统文件选择框，选不了再退回 inbox 目录里已有的 ogg 喵 */
    private void onImportClicked() {
        Path inbox = Explainstone.platform().configDir().resolve("explainstone").resolve("inbox");
        try {
            Files.createDirectories(inbox);
        } catch (IOException ignored) {
        }
        this.openFileDialog(inbox);
    }

    private void openFileDialog(Path inbox) {
        Minecraft client = Minecraft.getInstance();
        Thread thread = new Thread(() -> {
            String chosen = null;
            boolean failed = false;
            try {
                java.awt.FileDialog dialog = new java.awt.FileDialog((java.awt.Frame) null, "选择 ogg 音频",
                        java.awt.FileDialog.LOAD);
                dialog.setDirectory(inbox.toString());
                dialog.setFilenameFilter((dir, name) -> name.toLowerCase().endsWith(".ogg"));
                dialog.setMultipleMode(false);
                dialog.setVisible(true);
                if (dialog.getFile() != null) {
                    chosen = new File(dialog.getDirectory(), dialog.getFile()).getAbsolutePath();
                }
                dialog.dispose();
            } catch (Throwable t) {
                failed = true;
            }
            if (chosen == null && failed) {
                try {
                    final String[] picked = {null};
                    javax.swing.SwingUtilities.invokeAndWait(() -> {
                        javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
                        chooser.setDialogTitle("选择 ogg 音频");
                        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Ogg 音频 (*.ogg)", "ogg"));
                        chooser.setCurrentDirectory(inbox.toFile());
                        if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION
                                && chooser.getSelectedFile() != null) {
                            picked[0] = chooser.getSelectedFile().getAbsolutePath();
                        }
                    });
                    chosen = picked[0];
                } catch (Throwable ignored) {
                }
            }
            final String result = chosen;
            client.execute(() -> {
                if (result != null) {
                    SpeakerAudioCache.importFile(client, this.pos, Path.of(result));
                    return;
                }
                List<Path> files = listInbox(inbox);
                if (!files.isEmpty()) {
                    for (Path file : files) {
                        SpeakerAudioCache.importFile(client, this.pos, file);
                    }
                    info(client, Component.translatable("message.explainstone.import.inbox", files.size()));
                } else {
                    info(client, Component.translatable("message.explainstone.import.none", inbox.toString()));
                }
            });
        }, "explainstone-file-dialog");
        thread.setDaemon(true);
        thread.start();
    }

    private static List<Path> listInbox(Path inbox) {
        try (Stream<Path> stream = Files.list(inbox)) {
            return stream.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".ogg"))
                    .limit(8).toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private void info(Minecraft client, Component text) {
        if (client.player != null) {
            client.player.displayClientMessage(Component.translatable("message.explainstone.prefix", this.blockName(), text), false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
