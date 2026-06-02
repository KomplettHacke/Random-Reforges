package de.randomreforges.curios;

import java.util.UUID;

import de.randomreforges.reforge.AttributeUtil;
import de.randomreforges.reforge.ReforgeInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import top.theillusivec4.curios.api.event.CurioAttributeModifierEvent;
import net.minecraft.world.entity.ai.attributes.Attribute;


public class CurioAttributeEvents {

    @SubscribeEvent
    public static void onCurioAttributes(CurioAttributeModifierEvent event) {

        ItemStack stack = event.getItemStack();

        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains("RandomReforges")) {
            return;
        }

        ReforgeInstance inst = ReforgeInstance.fromNBT(tag.getCompound("RandomReforges"));
        if (inst == null) {
            return;
        }

        String reforgeId = inst.getReforge().getId();
        String slotId = event.getSlotContext().identifier();

        inst.getReforge().getAttributes().forEach(entry -> {
            Attribute attr = entry.getAttribute();
            if (attr == null) return;

            //removed random UUID, deterministic UUID as fix for permanent stats
            UUID uuid = UUID.nameUUIDFromBytes(
                ("RandomReforges:" + reforgeId + ":" + entry.getAttributeId() + ":" + slotId)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)
            );

            AttributeModifier.Operation op = AttributeUtil.fromString(entry.operation());
            // Use computeAmount so scaled reforges work on curio items too
            net.minecraft.world.entity.LivingEntity entity = event.getSlotContext().entity();
            double amount = entry.computeAmount(entity);
            AttributeModifier mod = new AttributeModifier(
                uuid,
                "Reforge_" + reforgeId,
                amount,
                op
            );
            event.addModifier(attr, mod);
        });
    }
}