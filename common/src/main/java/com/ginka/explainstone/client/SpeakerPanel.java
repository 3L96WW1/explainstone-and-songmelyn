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

import com.ginka.explainstone.speaker.PlayMode;
import com.ginka.explainstone.speaker.Track;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 界面看到的一份「扬声器状态」。
 *
 * <p>讲解石的状态在方块实体里，特朗谱的状态在物品组件里——两者的字段几乎一样，
 * 界面只需要读，所以抽这一层出来让同一个 {@link SpeakerScreen} 服务两边。
 */
public interface SpeakerPanel {

    /** 面板标题（讲解石 / 颂美铃 / 广播发信器 / 特朗谱）。 */
    Component title();

    /** 是否是广播发信器（决定按钮布局）。 */
    boolean transmitter();

    List<Track> tracks();

    int selected();

    PlayMode mode();

    boolean receiveMode();

    boolean speakerEnabled();

    boolean respectRedstone();

    int channel();

    int volume();

    int audibleRange();

    int transmitRange();

    int timerSeconds();

    /** 当前是否正在放音（决定标题后面加不加「播放中」）。 */
    boolean playing();

    /** 播放进度 [已播秒数, 总秒数]；没有在播返回 null。 */
    float[] progress();

    /** 把界面上的按钮操作发给服务端。 */
    void send(int action, int value);

    /** 导入音频（客户端本地算 sha1 后上传）。 */
    void importFile(java.nio.file.Path file);
}
