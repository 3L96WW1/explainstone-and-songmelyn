/*
 * explainstone —— 讲解石 / 颂美铃 / 广播发信器 / 特朗谱
 *
 * 作者 / Author : GINKA_3L96WW1
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.ginka.explainstone.trumpet;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * 特朗谱：手持版讲解石，外加麦克风。
 *
 * <p>右键打开和讲解石一样的界面（播放列表、频道、音量都在里面），
 * 手持时按住开麦键说话，声音会发给附近玩家。
 */
public class TrumpetItem extends Item {
    /** 客户端入口装进来的开界面回调；专用服务器上是 null。 */
    private static Runnable openScreen = () -> {
    };

    public TrumpetItem(Properties properties) {
        super(properties);
    }

    public static void setOpenScreen(Runnable hook) {
        openScreen = hook == null ? () -> {
        } : hook;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!TrumpetStates.available()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            openScreen.run();
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        TrumpetState state = TrumpetStates.get(stack);
        tooltip.accept(Component.translatable("tooltip.explainstone.trumpet.channel", state.channel()));
        tooltip.accept(Component.translatable("tooltip.explainstone.trumpet.volume", state.volume()));
        tooltip.accept(Component.translatable("tooltip.explainstone.trumpet.hold_to_talk"));
    }
}
