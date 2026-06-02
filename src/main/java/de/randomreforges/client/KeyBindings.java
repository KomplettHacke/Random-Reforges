package de.randomreforges.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;
import de.randomreforges.RandomReforges;

@Mod.EventBusSubscriber(modid = RandomReforges.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class KeyBindings {

    public static final KeyMapping OPEN_REFORGE_GUI = new KeyMapping(
            "key.randomreforges.open_reforge_gui",      // lang key
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_R,                        // default: R
            "key.categories.randomreforges"             // category
    );

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_REFORGE_GUI);
    }
}