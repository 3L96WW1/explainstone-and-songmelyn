/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.ginka.explainstone.fabric;

import com.ginka.explainstone.Explainstone;
import com.ginka.explainstone.client.ExplainstoneClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.world.InteractionResult;

public final class ExplainstoneFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Explainstone.setNetwork(new FabricClientNetwork());
        ExplainstoneClient.init();
        FabricClientNetwork.register();

        ClientTickEvents.END_CLIENT_TICK.register(ExplainstoneClient::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(ExplainstoneClient::stopping);

        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (ExplainstoneClient.openSpeakerScreen(level, hitResult.getBlockPos())) {
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
    }
}
