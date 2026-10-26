package de.randomreforges.reforge;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import de.randomreforges.RandomReforges;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;


public class AttributeUtil {
    /*****************************************************************************************************************************************************************
    Utilities often used in the mod:
    1. removeAttributes (CuriosAPI and normal items)
    2. applyAttributes (normal items only)
    2.1 Requires "stack", "instance"
    2.2 Requires "stack", "instance", "entity"    -> If specific entity is needed
    3. detectSlot                                 -> Needed to define the groups in SettingsGUI and to apply the attributes
    *****************************************************************************************************************************************************************/

    public static void removeAttributes(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();

        if (!tag.contains("AttributeModifiers")) return;

        ListTag list = tag.getList("AttributeModifiers", Tag.TAG_COMPOUND);
        ListTag newList = new ListTag();

        for (Tag t : list) {
            CompoundTag entry = (CompoundTag) t;

            //remove only reforge modifiers
            if (entry.getString("Name").startsWith("Reforge_")) {
                continue;
            }

            newList.add(entry);
        }

        tag.put("AttributeModifiers", newList);
        stack.setTag(tag);
    }


    public static void applyAttributes(ItemStack stack, ReforgeInstance instance) {
        applyAttributesCore(stack, instance, null);
    }

    /*****************************************************************************************************************************************************************
    Applies reforge attributes to item NBT, computing scaled amounts from the given entity.

    Call this instead of applyAttributes(stack, instance) when a player/entity is available!
    *****************************************************************************************************************************************************************/
    public static void applyAttributes(ItemStack stack, ReforgeInstance instance,
                                       net.minecraft.world.entity.LivingEntity entity) {
        applyAttributesCore(stack, instance, entity);
    }

    /*****************************************************************************************************************************************************************
    Core implementation shared by both applyAttributes overloads.
    When entity is null, uses the static entry.amount() (no scaling).
    When entity is non-null, uses entry.computeAmount(entity) (scaling supported).
    *****************************************************************************************************************************************************************/
    private static void applyAttributesCore(ItemStack stack, ReforgeInstance instance,
                                             net.minecraft.world.entity.LivingEntity entity) {
        Reforge reforge = instance.getReforge();
        EquipmentSlot slot = detectSlot(stack);
        CompoundTag tag = stack.getOrCreateTag();

        //Save vanilla attributes (otherwise they would just be gone and it would replace them with the reforge attributes)
        if (!tag.contains("AttributeModifiers")) {
            var defaults = stack.getItem().getDefaultAttributeModifiers(slot);
            ListTag list = new ListTag();
            defaults.forEach((attribute, modifier) -> {
                CompoundTag entry = new CompoundTag();
                entry.putString("AttributeName", BuiltInRegistries.ATTRIBUTE.getKey(attribute).toString());
                entry.putString("Name", modifier.getName());
                entry.putUUID("UUID", modifier.getId());
                entry.putDouble("Amount", modifier.getAmount());
                entry.putInt("Operation", modifier.getOperation().toValue());
                entry.putString("Slot", slot.getName());
                list.add(entry);
            });
            tag.put("AttributeModifiers", list);
            stack.setTag(tag);
        }

        ListTag list = tag.getList("AttributeModifiers", Tag.TAG_COMPOUND);

        List<Reforge.AttributeEntry> attributes = reforge.getAttributes();
        for (int i = 0; i < attributes.size(); i++) {
            Reforge.AttributeEntry entry = attributes.get(i);

            Attribute attribute = entry.getAttribute();
            if (attribute == null) {
                RandomReforges.LOGGER.warn(
                        "[RandomReforges] Attribute '{}' not found. Skipping.",
                        entry.getAttributeId()
                );
                continue;
            }

            // Use computeAmount when entity is available (supports scaling), otherwise use static amount
            double amount = entity != null ? entry.computeAmount(entity) : entry.amount();

            //*** Hardmode: apply per-attribute debuff multiplier to negative amounts ******************************************************
            if (amount < 0) {
                amount *= instance.getDebuffMultiplier(i);
            }

            //*** Perfect reforge: skip negatives (unless hardmode), boost positives ******************************************************
            if (instance.isPerfect()) {
                if (amount < 0 && !ReforgeManager.isHardmodeEnabled()) continue;
                if (amount >= 0) {
                    double bonus = ReforgeManager.getPerfectBonusPercent();
                    if (bonus > 0) amount *= (1.0 + bonus / 100.0);
                }
            }

            UUID uuid = UUID.nameUUIDFromBytes(
                ("RandomReforges:" + reforge.getId() + ":" + entry.getAttributeId() + ":" + slot.getName())
                    .getBytes(StandardCharsets.UTF_8)
            );

            AttributeModifier modifier = new AttributeModifier(
                uuid, "Reforge_" + reforge.getId(), amount, fromString(entry.operation())
            );

            CompoundTag attrTag = new CompoundTag();
            attrTag.putString("AttributeName", BuiltInRegistries.ATTRIBUTE.getKey(attribute).toString());
            attrTag.putString("Name", modifier.getName());
            attrTag.putUUID("UUID", modifier.getId());
            attrTag.putDouble("Amount", modifier.getAmount());
            attrTag.putInt("Operation", modifier.getOperation().toValue());
            attrTag.putString("Slot", slot.getName());
            list.add(attrTag);
        }

        tag.put("AttributeModifiers", list);
        stack.setTag(tag);
    }



    public static EquipmentSlot detectSlot(ItemStack stack) {

        if (stack.getItem() instanceof ArmorItem armor) {
            return armor.getEquipmentSlot();
        }

        if (stack.getItem() instanceof net.minecraft.world.item.TieredItem) {
            return EquipmentSlot.MAINHAND;
        }

        if (stack.getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem) {
            return EquipmentSlot.MAINHAND;
        }

        if (stack.getItem() instanceof net.minecraft.world.item.ShieldItem) {
            return EquipmentSlot.OFFHAND;
        }

        return EquipmentSlot.MAINHAND;
    }

    public static AttributeModifier.Operation fromString(String op) {
        return switch (op == null ? "" : op.toLowerCase()) {
            case "add" -> AttributeModifier.Operation.ADDITION;
            case "multiply_base" -> AttributeModifier.Operation.MULTIPLY_BASE;
            case "multiply_total" -> AttributeModifier.Operation.MULTIPLY_TOTAL;
            default -> AttributeModifier.Operation.ADDITION;
        };
    }
}
