/*
 * explainstone —— 讲解石 / 颂美铃 / 广播发信器
 *
 * 作者 / Author : GINKA_3L96WW1
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.ginka.explainstone;

import com.ginka.explainstone.speaker.SpeakerBlockEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** 讲解石的所有变体：外壳纹理不同，方块行为完全一样（共用同一个 BlockEntity 类型）。 */
public final class ModBlocks {
    private static final List<Block> ALL = new ArrayList<>();

    public static Block speakerBlock;
    public static Block stoneSpeakerBlock;
    public static Block cobblestoneSpeakerBlock;
    public static Block graniteSpeakerBlock;
    public static Block dioriteSpeakerBlock;
    public static Block andesiteSpeakerBlock;
    public static Block songmelynBlock;
    public static Block broadcastTransmitter;
    public static BlockEntityType<SpeakerBlockEntity> speakerBlockEntity;

    private ModBlocks() {
    }

    public static void registerBlocks() {
        if (!ALL.isEmpty()) {
            return;
        }
        speakerBlock = registerSpeaker("speaker_block");
        stoneSpeakerBlock = registerSpeaker("stone_speaker_block");
        cobblestoneSpeakerBlock = registerSpeaker("cobblestone_speaker_block");
        graniteSpeakerBlock = registerSpeaker("granite_speaker_block");
        dioriteSpeakerBlock = registerSpeaker("diorite_speaker_block");
        andesiteSpeakerBlock = registerSpeaker("andesite_speaker_block");

        songmelynBlock = Registry.register(BuiltInRegistries.BLOCK, id("songmelyn_block"),
                new SongmelynBlock(BlockBehaviour.Properties.of().strength(1.05f).noOcclusion()));
        ALL.add(songmelynBlock);

        broadcastTransmitter = Registry.register(BuiltInRegistries.BLOCK, id("broadcast_transmitter"),
                new BroadcastTransmitterBlock(BlockBehaviour.Properties.of().strength(1.05f)));
        ALL.add(broadcastTransmitter);
    }

    public static void registerItems() {
        if (ALL.isEmpty()) {
            registerBlocks();
        }
        for (Block block : ALL) {
            Registry.register(BuiltInRegistries.ITEM,
                    BuiltInRegistries.BLOCK.getKey(block),
                    new BlockItem(block, new Item.Properties()));
        }
    }

    public static void addCreativeTabEntries(Consumer<ItemLike> consumer) {
        for (Block block : ALL) {
            consumer.accept(block);
        }
    }

    public static List<Block> allBlocks() {
        return List.copyOf(ALL);
    }

    private static Block registerSpeaker(String name) {
        Block block = Registry.register(BuiltInRegistries.BLOCK, id(name),
                new SpeakerBlock(BlockBehaviour.Properties.of().strength(1.05f).noOcclusion()));
        ALL.add(block);
        return block;
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Explainstone.MOD_ID, path);
    }
}
