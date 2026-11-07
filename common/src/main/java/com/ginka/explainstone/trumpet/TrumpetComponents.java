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

import com.ginka.explainstone.Explainstone;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/** 特朗谱的物品组件。每把物品自带一份 {@link TrumpetState}。 */
public final class TrumpetComponents {
    public static DataComponentType<TrumpetState> TRUMPET_STATE;

    private TrumpetComponents() {
    }

    public static void register() {
        if (TRUMPET_STATE != null) {
            return;
        }
        TRUMPET_STATE = Registry.register(
                BuiltInRegistries.DATA_COMPONENT_TYPE,
                Identifier.fromNamespaceAndPath(Explainstone.MOD_ID, "trumpet_state"),
                DataComponentType.<TrumpetState>builder()
                        .persistent(TrumpetState.CODEC)
                        .networkSynchronized(TrumpetState.NETWORK_CODEC)
                        .build());
    }
}
