package de.randomreforges.curios;

import de.randomreforges.reforge.Events;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import top.theillusivec4.curios.api.event.CurioChangeEvent;

public class CurioEquipEvents {

    @SubscribeEvent
    public static void onCurioEquip(CurioChangeEvent event) {
        if (!event.getFrom().isEmpty()) return;

        ItemStack stack = event.getTo();
        if (stack.isEmpty()) return;

        net.minecraft.world.entity.LivingEntity entity = event.getEntity();
        Events.applyInitialReforge(stack, EquipmentSlot.MAINHAND, true, entity);
    }
}
