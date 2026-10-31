/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.ginka.explainstone.fabric;

import com.ginka.explainstone.Explainstone;
import com.ginka.explainstone.ModBlocks;
import com.ginka.explainstone.speaker.SpeakerBlockEntity;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public final class ExplainstoneFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Explainstone.init(new FabricPlatform(), new FabricNetwork());

        ModBlocks.registerBlocks();
        ModBlocks.registerItems();
        ModBlocks.speakerBlockEntity = FabricBlockEntityTypeBuilder.create(
                SpeakerBlockEntity::new, ModBlocks.allBlocks().toArray(Block[]::new)).build();
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(Explainstone.MOD_ID, "speaker_block"),
                ModBlocks.speakerBlockEntity);

        FabricNetwork.registerServer();
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.REDSTONE_BLOCKS)
                .register(output -> ModBlocks.allBlocks().forEach(block -> output.accept(new ItemStack(block))));
    }
}
