/*
 * explainstone —— 讲解石 / 颂美铃 / 广播发信器（Minecraft Fabric 1.21.1 模组）
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
package com.ginka.explainstone.client.widget;

import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.function.IntConsumer;

/** 整数滑块：拖动时把 int 值回调出去（屏幕那边自己做防抖） 喵 */
public class IntSlider extends SliderWidget {
    private final String label;
    private final int min;
    private final int max;
    private final IntConsumer consumer;

    public IntSlider(int x, int y, int width, int height, String label, int min, int max, int value,
                     IntConsumer consumer) {
        super(x, y, width, height, Text.literal(label + "：" + value),
                (value - min) / (double) Math.max(1, max - min));
        this.label = label;
        this.min = min;
        this.max = max;
        this.consumer = consumer;
        this.updateMessage();
    }

    public int getIntValue() {
        return this.min + (int) Math.round(this.value * (this.max - this.min));
    }

    public void setIntValue(int value) {
        this.value = (Math.max(this.min, Math.min(this.max, value)) - this.min)
                / (double) Math.max(1, this.max - this.min);
        this.updateMessage();
    }

    @Override
    protected void updateMessage() {
        this.setMessage(Text.literal(this.label + "：" + this.getIntValue()));
    }

    @Override
    protected void applyValue() {
        this.consumer.accept(this.getIntValue());
    }
}
