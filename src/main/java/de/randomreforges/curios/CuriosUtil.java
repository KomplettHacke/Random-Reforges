package de.randomreforges.curios;

import net.minecraft.world.item.ItemStack;

public class CuriosUtil {
    public static boolean isCurioItem(ItemStack stack) {
        return stack.getTags().anyMatch(tag ->
            tag.location().getNamespace().equals("curios"));
    }
}