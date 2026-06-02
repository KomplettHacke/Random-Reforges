package de.randomreforges.curios;

import net.minecraftforge.common.MinecraftForge;


//just a fallback to make sure that curio doesnt lead to crashes if its not loaded
public class CuriosLoader {
    public static void register() {
        MinecraftForge.EVENT_BUS.register(CurioAttributeEvents.class);
        MinecraftForge.EVENT_BUS.register(CurioEquipEvents.class);
    }
}