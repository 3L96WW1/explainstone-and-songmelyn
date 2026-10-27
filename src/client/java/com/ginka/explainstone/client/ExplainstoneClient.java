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
package com.ginka.explainstone.client;

import com.ginka.explainstone.BroadcastTransmitterBlock;
import com.ginka.explainstone.SpeakerBlock;
import com.ginka.explainstone.client.audio.SpeakerAudio;
import com.ginka.explainstone.client.audio.SpeakerAudioCache;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.ActionResult;

public class ExplainstoneClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        SpeakerAudio.init();
        SpeakerAudioCache.registerReceivers();

        // 每 tick 更新音源（距离衰减、切换曲目、按模式续播） 喵
        ClientTickEvents.END_CLIENT_TICK.register(SpeakerAudio::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> SpeakerAudio.shutdown());

        // 右键石头打开界面 喵
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!world.isClient) {
                return ActionResult.PASS;
            }
            if (world.getBlockState(hitResult.getBlockPos()).getBlock() instanceof SpeakerBlock
                    || world.getBlockState(hitResult.getBlockPos()).getBlock() instanceof BroadcastTransmitterBlock) {
                MinecraftClient client = MinecraftClient.getInstance();
                client.setScreen(new SpeakerScreen(hitResult.getBlockPos()));
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });
    }
}
