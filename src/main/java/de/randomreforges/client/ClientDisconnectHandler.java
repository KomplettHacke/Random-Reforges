package de.randomreforges.client;

import de.randomreforges.RandomReforges;
import de.randomreforges.reforge.ReforgeManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/*****************************************************************************************************************************************************************
reload config upon player leaves a server/sp world
*****************************************************************************************************************************************************************/
@Mod.EventBusSubscriber(modid = RandomReforges.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientDisconnectHandler {

    @SubscribeEvent
    public static void onDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        ReforgeManager.load();
    }
}
