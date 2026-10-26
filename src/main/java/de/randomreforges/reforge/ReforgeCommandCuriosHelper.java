package de.randomreforges.reforge;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;


/*****************************************************************************************************************************************************************
Curios helper for ReforgeCommand – separated to prevent class-loading of Curios types
when Curios is not installed (same pattern as CuriosLoader / CurioAttributeEvents).
Only called from ReforgeCommand inside ModList.get().isLoaded("curios") guards.
*****************************************************************************************************************************************************************/
public class ReforgeCommandCuriosHelper {

    /*****************************************************************************************************************************************************************
    Returns all curio slot IDs registered on this player (used for tab completion).
    *****************************************************************************************************************************************************************/
    public static Set<String> getSlotIds(ServerPlayer player) {
        Set<String> result = new HashSet<>();
        CuriosApi.getCuriosHelper()
                .getCuriosHandler(player)
                .ifPresent(handler -> result.addAll(handler.getCurios().keySet()));
        return result;
    }

    /*****************************************************************************************************************************************************************
    Returns true if the player has a curio slot with this ID.
    *****************************************************************************************************************************************************************/
    public static boolean hasSlot(ServerPlayer player, String slotId) {
        boolean[] found = { false };
        CuriosApi.getCuriosHelper()
                .getCuriosHandler(player)
                .ifPresent(handler -> found[0] = handler.getCurios().containsKey(slotId));
        return found[0];
    }

    /*****************************************************************************************************************************************************************
    Returns all non-empty ItemStacks inside the given curio slot type.
    The returned stacks are direct references – modifying their NBT persists in the inventory.
    *****************************************************************************************************************************************************************/
    public static List<ItemStack> getStacksInSlot(ServerPlayer player, String slotId) {
        List<ItemStack> result = new ArrayList<>();
        CuriosApi.getCuriosHelper()
                .getCuriosHandler(player)
                .ifPresent(handler -> {
                    var stacksHandler = handler.getCurios().get(slotId);
                    if (stacksHandler == null) return;
                    var stacks = stacksHandler.getStacks();
                    for (int i = 0; i < stacks.getSlots(); i++) {
                        ItemStack s = stacks.getStackInSlot(i);
                        if (!s.isEmpty()) result.add(s);
                    }
                });
        return result;
    }
}