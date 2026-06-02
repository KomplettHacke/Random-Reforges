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




/* TO DO
- Reforge Cage Block fixen                                                  - Erledigt (2.0)
- Reforge GUI: Suchleiste                                                   - Erledigt (2.0)
- Add Attribute-Button Scroll-Dings hinzufügen                              - Erledigt (2.0)
- Random UUID entfernen -> Stabilität                                       - Erledigt (2.0)
- Logik-Duplikate in Events.java und CurioEquipEvents.java entfernen        - Erledigt (2.0)
- Shield Reforges                                                           - Erledigt (2.0)
- Item Descriptions                                                         - Erledigt 2.1.1
- Fehlermeldung beim Reforge Cage Block: "You have less than <required levels> levels", "You have less than <required item amount> of <item>"   - 2.1.1
- Scaling Reforges (x <Attribute> pro y <Attribute>) hinzufügen             - Erledigt (3.0)
    - Scrollcontainer in Editor GUI fixen                                   - 3.0
- Item Attribute neu berechnen bei Reforgeänderung                          - Erledigt (3.0)
    - funktioniert im Spielerinventar                                       - Erledigt (3.0)
    - funktioniert in Kisten                                                - nicht machbar, Ersatz hinzugefügt (3.0)
- GUI-Scale Fix                                                             - Erledigt (3.0)
- HelpGUI Formattierungen fixen                                             - Erledigt (3.0)
- Advancement "Gonna catch 'em all" (Soulless Core gecraftet)               - Kleines Update 3.1
- Advancement "Another 'ethical' villager prison..."(Reforge Cage erzeugt)  - Kleines Update 3.1
- "Perfect"-Reforge (configurable, SettingsGUI)                             - Kleines Update 3.2
- Reforge Blacklist                                                         - Kleines Update 3.3
- Vollständige Servercompat                                                 - Großes Update 4.0

*/