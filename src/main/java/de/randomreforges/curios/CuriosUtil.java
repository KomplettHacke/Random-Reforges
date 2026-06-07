package de.randomreforges.curios;

import net.minecraft.world.item.ItemStack;

public class CuriosUtil {
    /*****************************************************************************************************************************************************************
    Helper function to check if an item is a CuriosAPI item

    Needed to apply reforge attributes manually only to non-curio items and let CuriosAPI apply the reforges
    *****************************************************************************************************************************************************************/
    public static boolean isCurioItem(ItemStack stack) {
        return stack.getTags().anyMatch(tag ->
            tag.location().getNamespace().equals("curios"));
    }
}
