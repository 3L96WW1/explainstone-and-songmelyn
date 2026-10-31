/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.ginka.explainstone.platform;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Loader-neutral packet sending. Registration stays in the loader modules. */
public interface Network {
    void sendToServer(CustomPacketPayload payload);

    boolean canSendToServer(CustomPacketPayload.Type<?> id);

    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);
}
