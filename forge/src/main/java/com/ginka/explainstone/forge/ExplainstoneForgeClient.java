package com.ginka.explainstone.forge;

import com.ginka.explainstone.Explainstone;
import com.ginka.explainstone.client.ExplainstoneClient;
import com.ginka.explainstone.client.audio.SpeakerAudio;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;

public final class ExplainstoneForgeClient {
    @Mod.EventBusSubscriber(modid = Explainstone.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            SpeakerAudio.setSoundFactory(ForgeSpeakerSound::new);
            ExplainstoneClient.init();
        }
    }

    @Mod.EventBusSubscriber(modid = Explainstone.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class GameEvents {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                ExplainstoneClient.tick(Minecraft.getInstance());
            }
        }

        @SubscribeEvent
        public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            ExplainstoneClient.stopping(Minecraft.getInstance());
        }

        @SubscribeEvent
        public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
            if (ExplainstoneClient.openSpeakerScreen(event.getLevel(), event.getPos())) {
                event.setCanceled(true);
                event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
            }
        }
    }
}
