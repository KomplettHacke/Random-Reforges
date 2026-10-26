package de.randomreforges.curios;

import java.util.List;
import java.util.UUID;

import de.randomreforges.reforge.AttributeUtil;
import de.randomreforges.reforge.Reforge;
import de.randomreforges.reforge.ReforgeInstance;
import de.randomreforges.reforge.ReforgeManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import top.theillusivec4.curios.api.event.CurioAttributeModifierEvent;
import net.minecraft.world.entity.ai.attributes.Attribute;


/*****************************************************************************************************************************************************************
Task: Reforge CuriosAPI items
Copies the "normal" reforge logic but uses another attribute appliance method as CuriosAPI comes with an integrated one.
*****************************************************************************************************************************************************************/

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
        String slotId    = event.getSlotContext().identifier();
        net.minecraft.world.entity.LivingEntity entity = event.getSlotContext().entity();

        List<Reforge.AttributeEntry> attributes = inst.getReforge().getAttributes();
        for (int i = 0; i < attributes.size(); i++) {
            Reforge.AttributeEntry entry = attributes.get(i);

            Attribute attr = entry.getAttribute();
            if (attr == null) continue;

            UUID uuid = UUID.nameUUIDFromBytes(
                ("RandomReforges:" + reforgeId + ":" + entry.getAttributeId() + ":" + slotId)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)
            );

            AttributeModifier.Operation op = AttributeUtil.fromString(entry.operation());
            // Use computeAmount so scaled reforges work on curio items too
            double amount = entry.computeAmount(entity);

            //*** Hardmode: apply per-attribute debuff multiplier to negative amounts ******************************************************
            if (amount < 0) {
                amount *= inst.getDebuffMultiplier(i);
            }

            //*** Perfect reforge: skip negatives (unless hardmode), boost positives ******************************************************
            if (inst.isPerfect()) {
                if (amount < 0 && !ReforgeManager.isHardmodeEnabled()) continue;
                if (amount >= 0) {
                    int bonus = ReforgeManager.getPerfectBonusPercent();
                    if (bonus > 0) amount *= (1.0 + bonus / 100.0);
                }
            }

            AttributeModifier mod = new AttributeModifier(
                uuid,
                "Reforge_" + reforgeId,
                amount,
                op
            );
            event.addModifier(attr, mod);
        }
    }
}
