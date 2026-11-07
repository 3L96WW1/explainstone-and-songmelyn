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
import com.ginka.explainstone.SpeakerBlock;
import com.ginka.explainstone.client.audio.SpeakerAudio;
import com.ginka.explainstone.client.audio.TrumpetAudio;
import com.ginka.explainstone.trumpet.TrumpetItem;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** Common client hooks; loader entrypoints wire them to their own events. */
public final class ExplainstoneClient {
    private ExplainstoneClient() {
    }

    public static void init() {
        SpeakerAudio.init();
        TrumpetAudio.init();
        // 右键特朗谱时打开和讲解石一样的界面
        TrumpetItem.setOpenScreen(() -> {
            int slot = TrumpetPanel.heldSlot();
            if (slot < 0) {
                return;
            }
            TrumpetPanel panel = new TrumpetPanel(slot);
            TrumpetPanel.activate(panel);
            Minecraft.getInstance().setScreen(new SpeakerScreen(panel));
        });
    }

    public static void tick(Minecraft client) {
        SpeakerAudio.tick(client);
        TrumpetAudio.tick(client);
    }

    public static void stopping(Minecraft client) {
        SpeakerAudio.shutdown();
        TrumpetAudio.shutdown();
    }

    public static boolean openSpeakerScreen(Level level, BlockPos pos) {
        if (!level.isClientSide()) {
            return false;
        }
        Block block = level.getBlockState(pos).getBlock();
        if (block instanceof SpeakerBlock || block instanceof BroadcastTransmitterBlock) {
            Minecraft.getInstance().setScreen(new SpeakerScreen(new BlockPanel(pos)));
            return true;
        }
        return false;
    }

    /** 手上这把是不是特朗谱（开麦时要用）。 */
    public static boolean holdingTrumpet(Minecraft client) {
        if (client.player == null) {
            return false;
        }
        ItemStack stack = client.player.getMainHandItem();
        return stack.getItem() instanceof TrumpetItem;
    }
}
