/*
 * explainstone —— 讲解石 / 颂美铃 / 广播发信器
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
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** Common client hooks; loader entrypoints wire them to their own events. */
public final class ExplainstoneClient {
    private ExplainstoneClient() {
    }

    public static void init() {
        SpeakerAudio.init();
    }

    public static void tick(Minecraft client) {
        SpeakerAudio.tick(client);
    }

    public static void stopping(Minecraft client) {
        SpeakerAudio.shutdown();
    }

    public static boolean openSpeakerScreen(Level level, BlockPos pos) {
        if (!level.isClientSide) {
            return false;
        }
        Block block = level.getBlockState(pos).getBlock();
        if (block instanceof SpeakerBlock || block instanceof BroadcastTransmitterBlock) {
            Minecraft.getInstance().setScreen(new SpeakerScreen(pos));
            return true;
        }
        return false;
    }
}
