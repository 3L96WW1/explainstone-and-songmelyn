/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.ginka.explainstone.neoforge;

import com.ginka.explainstone.Explainstone;
import com.ginka.explainstone.ModBlocks;
import com.ginka.explainstone.speaker.BroadcastRegistry;
import com.ginka.explainstone.speaker.ServerAudioStore;
import com.ginka.explainstone.speaker.SpeakerBlockEntity;
import com.ginka.explainstone.speaker.SpeakerNetworking;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

@Mod(Explainstone.MOD_ID)
public final class ExplainstoneNeoForge {
    public ExplainstoneNeoForge() {
        Explainstone.init(new NeoForgePlatform(), new NeoForgeNetwork());
    }

    @EventBusSubscriber(modid = Explainstone.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        @SubscribeEvent
        public static void onRegister(RegisterEvent event) {
            if (event.getRegistryKey().equals(Registries.BLOCK)) {
                ModBlocks.registerBlocks();
            } else if (event.getRegistryKey().equals(Registries.ITEM)) {
                ModBlocks.registerItems();
            } else if (event.getRegistryKey().equals(Registries.BLOCK_ENTITY_TYPE)) {
                ModBlocks.speakerBlockEntity = BlockEntityType.Builder.of(
                        SpeakerBlockEntity::new, ModBlocks.allBlocks().toArray(Block[]::new)).build(null);
                event.register(Registries.BLOCK_ENTITY_TYPE,
                        ResourceLocation.fromNamespaceAndPath(Explainstone.MOD_ID, "speaker_block"),
                        () -> ModBlocks.speakerBlockEntity);
            }
        }

        @SubscribeEvent
        public static void onPayloads(RegisterPayloadHandlersEvent event) {
            NeoForgeNetwork.register(event);
        }

        @SubscribeEvent
        public static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
            if (event.getTabKey().equals(CreativeModeTabs.REDSTONE_BLOCKS)) {
                ModBlocks.allBlocks().forEach(block -> event.accept(new ItemStack(block)));
            }
        }
    }

    /**
     * 服务器运行期的事件。这几个都在 GAME 总线上，不能和上面 MOD 总线的
     * 注册事件混在同一个 {@code @EventBusSubscriber} 里，否则一个都不会被调用。喵
     */
    @EventBusSubscriber(modid = Explainstone.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
    public static final class GameEvents {
        /** 服务器启动后把旧存档里的音频搬到 config 下，搬完删旧目录。 */
        @SubscribeEvent
        public static void onServerStarted(ServerStartedEvent event) {
            ServerAudioStore.migrateLegacy(event.getServer());
        }

        /** 每 tick 按预算推进下发队列，避免整首歌压在同一 tick 里。 */
        @SubscribeEvent
        public static void onServerTick(ServerTickEvent.Post event) {
            SpeakerNetworking.tick(event.getServer());
        }

        @SubscribeEvent
        public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                SpeakerNetworking.onDisconnect(player);
                BroadcastRegistry.forgetPlayer(player);
            }
        }
    }
}
