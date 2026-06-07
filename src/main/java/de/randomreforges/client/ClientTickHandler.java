package de.randomreforges.client;

import de.randomreforges.RandomReforges;
import de.randomreforges.gui.ReforgeListGUI;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;


/*****************************************************************************************************************************************************************
This is mainly used for Scaling Reforges (like spellbooks_cursed) and to open the GUI
*****************************************************************************************************************************************************************/

@Mod.EventBusSubscriber(modid = RandomReforges.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientTickHandler {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;

        while (KeyBindings.OPEN_REFORGE_GUI.consumeClick()) {
            mc.setScreen(new ReforgeListGUI());
        }
    }
}
