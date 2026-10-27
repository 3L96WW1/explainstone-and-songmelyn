/*
 * explainstone —— 讲解石 / 颂美铃 / 广播发信器（Minecraft Fabric 1.21.1 模组）
 *
 *  作者 / Author : GINKA_3L96WW1
 *
 * 本文件由作者手工编写；贴图与模型由 tools/ 下的脚本程序化生成。
 * 保留此注释即可随意修改、分发。 喵
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.ginka.explainstone;

import com.ginka.explainstone.speaker.SpeakerBlockEntity;
import com.ginka.explainstone.speaker.SpeakerNetworking;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * 讲解石的所有变体：外壳纹理不同，方块行为完全一样（共用同一个 BlockEntity 类型）。 喵
 */
public class ModBlocks {
    public static final Block SPEAKER_BLOCK = register("speaker_block");
    public static final Block STONE_SPEAKER_BLOCK = register("stone_speaker_block");
    public static final Block COBBLESTONE_SPEAKER_BLOCK = register("cobblestone_speaker_block");
    public static final Block GRANITE_SPEAKER_BLOCK = register("granite_speaker_block");
    public static final Block DIORITE_SPEAKER_BLOCK = register("diorite_speaker_block");
    public static final Block ANDESITE_SPEAKER_BLOCK = register("andesite_speaker_block");
    /** 颂美铃：和讲解石功能完全一样，只是外形是壁挂式教室广播音响（碰撞箱按模型收窄） 喵 */
    public static final Block SONGMELYN_BLOCK = registerSongmelyn();

    public static final Block BROADCAST_TRANSMITTER = Registry.register(
            Registries.BLOCK,
            Identifier.of("explainstone", "broadcast_transmitter"),
            new BroadcastTransmitterBlock(AbstractBlock.Settings.create().strength(3.0f).requiresTool())
    );

    static {
        Registry.register(Registries.ITEM, Identifier.of("explainstone", "broadcast_transmitter"),
                new BlockItem(BROADCAST_TRANSMITTER, new Item.Settings()));
    }

    public static final BlockEntityType<SpeakerBlockEntity> SPEAKER_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of("explainstone", "speaker_block"),
            BlockEntityType.Builder.create(SpeakerBlockEntity::new,
                    SPEAKER_BLOCK,
                    STONE_SPEAKER_BLOCK,
                    COBBLESTONE_SPEAKER_BLOCK,
                    GRANITE_SPEAKER_BLOCK,
                    DIORITE_SPEAKER_BLOCK,
                    ANDESITE_SPEAKER_BLOCK,
                    SONGMELYN_BLOCK,
                    BROADCAST_TRANSMITTER
            ).build(null)
    );

    private static Block registerSongmelyn() {
        Identifier id = Identifier.of("explainstone", "songmelyn_block");
        Block block = Registry.register(Registries.BLOCK, id,
                new SongmelynBlock(AbstractBlock.Settings.create().strength(2.0f).requiresTool().nonOpaque()));
        Registry.register(Registries.ITEM, id, new BlockItem(block, new Item.Settings()));
        return block;
    }

    private static Block register(String name) {
        Identifier id = Identifier.of("explainstone", name);
        Block block = Registry.register(Registries.BLOCK, id,
                new SpeakerBlock(AbstractBlock.Settings.create().strength(2.0f).requiresTool().nonOpaque()));
        Registry.register(Registries.ITEM, id, new BlockItem(block, new Item.Settings()));
        return block;
    }

    public static void initialize() {
        // 网络包 + 服务端处理器（界面操作、音频导入/下发） 喵
        SpeakerNetworking.registerPayloadTypes();
        SpeakerNetworking.registerServerHandlers();

        // 所有变体都放进创造模式物品栏的「红石」栏 喵
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.REDSTONE).register(entries -> {
            entries.add(SPEAKER_BLOCK);
            entries.add(STONE_SPEAKER_BLOCK);
            entries.add(COBBLESTONE_SPEAKER_BLOCK);
            entries.add(GRANITE_SPEAKER_BLOCK);
            entries.add(DIORITE_SPEAKER_BLOCK);
            entries.add(ANDESITE_SPEAKER_BLOCK);
            entries.add(SONGMELYN_BLOCK);
            entries.add(BROADCAST_TRANSMITTER);
        });
    }
}
