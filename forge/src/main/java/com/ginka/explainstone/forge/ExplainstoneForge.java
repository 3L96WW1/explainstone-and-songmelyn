package com.ginka.explainstone.forge;

import com.ginka.explainstone.Explainstone;
import com.ginka.explainstone.ModBlocks;
import com.ginka.explainstone.speaker.SpeakerBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.RegisterEvent;

@Mod(Explainstone.MOD_ID)
public final class ExplainstoneForge {
    public ExplainstoneForge() {
        Explainstone.init(new ForgePlatform(), new ForgeNetwork());
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ForgeNetwork.register(bus);
    }

    @Mod.EventBusSubscriber(modid = Explainstone.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
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
                        new ResourceLocation(Explainstone.MOD_ID, "speaker_block"),
                        () -> ModBlocks.speakerBlockEntity);
            }
        }

        @SubscribeEvent
        public static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
            if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) {
                ModBlocks.addCreativeTabEntries(entry -> event.accept(new ItemStack(entry)));
            }
        }
    }
}
