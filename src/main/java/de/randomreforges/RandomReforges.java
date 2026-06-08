package de.randomreforges;

import org.slf4j.Logger;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;

import de.randomreforges.curios.CuriosLoader;
import de.randomreforges.reforge.ReforgeCommand;
import de.randomreforges.reforge.ReforgeManager;
import de.randomreforges.registry.BlockEntityRegistry;
import de.randomreforges.registry.BlockRegistry;
import de.randomreforges.registry.CreativeTabRegistry;
import de.randomreforges.registry.ItemRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;


@Mod(RandomReforges.MODID)
public class RandomReforges {
    
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final String MODID = "randomreforges";


    public RandomReforges() {
        var modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ItemRegistry.ITEMS.register(modBus);
        BlockRegistry.BLOCKS.register(modBus);
        BlockRegistry.BLOCK_ITEMS.register(modBus);
        CreativeTabRegistry.TABS.register(modBus);
        BlockEntityRegistry.BLOCK_ENTITIES.register(modBus);

        MinecraftForge.EVENT_BUS.register(this);

        if (ModList.get().isLoaded("curios")) {
            LOGGER.info("[RandomReforges] Curios detected – enabling Curios attribute support.");
            CuriosLoader.register();
        } else {
            LOGGER.info("[RandomReforges] Curios not installed – skipping Curios integration.");
        }
    }



    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModLifecycle {
        @SubscribeEvent
        public static void onLoadComplete(FMLLoadCompleteEvent event) {
            ReforgeManager.load();
        }
    }

    @Mod.EventBusSubscriber(modid = MODID)
    public static class CommandRegistration {
        @SubscribeEvent
        public static void onRegisterCommands(RegisterCommandsEvent event) {
            CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
            dispatcher.register(ReforgeCommand.register());
        }
    }
}
