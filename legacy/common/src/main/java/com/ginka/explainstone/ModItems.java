/*
 * explainstone —— 讲解石 / 颂美铃 / 广播发信器 / 特朗谱
 *
 * 作者 / Author : GINKA_3L96WW1
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.ginka.explainstone;

import com.ginka.explainstone.trumpet.TrumpetComponents;
import com.ginka.explainstone.trumpet.TrumpetItem;
import com.ginka.explainstone.trumpet.TrumpetState;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** 特朗谱的物品注册。 */
public final class ModItems {
    public static Item trumpet;

    private ModItems() {
    }

    public static void register() {
        if (trumpet != null) {
            return;
        }
        TrumpetComponents.register();
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Explainstone.MOD_ID, "trumpet");
        trumpet = Registry.register(BuiltInRegistries.ITEM, id, new TrumpetItem(new Item.Properties()
                .stacksTo(1)
                .component(TrumpetComponents.TRUMPET_STATE, TrumpetState.EMPTY)));
    }

    public static ItemStack newTrumpet() {
        return new ItemStack(trumpet);
    }
}
