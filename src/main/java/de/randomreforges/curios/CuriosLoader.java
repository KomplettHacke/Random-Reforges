package de.randomreforges.curios;

import net.minecraftforge.common.MinecraftForge;


/*****************************************************************************************************************************************************************
Not sure what exactly changed, but creating an own file just for CuriosAPI loading helped with some crashes if CuriosAPI was not installed
*****************************************************************************************************************************************************************/
public class CuriosLoader {
    public static void register() {
        MinecraftForge.EVENT_BUS.register(CurioAttributeEvents.class);
        MinecraftForge.EVENT_BUS.register(CurioEquipEvents.class);
    }
}
