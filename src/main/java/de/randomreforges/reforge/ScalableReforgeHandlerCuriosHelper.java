package de.randomreforges.reforge;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;


/*****************************************************************************************************************************************************************
Curios-specific recalculation logic for ScalableReforgeHandler.
Separated to prevent class-loading of Curios types when Curios is not installed.
*****************************************************************************************************************************************************************/
public class ScalableReforgeHandlerCuriosHelper {

    private static final String REFORGE_TAG = "RandomReforges";

    /*****************************************************************************************************************************************************************
    Returns true if the player has at least one Curio equipped that carries a scaled reforge.
    *****************************************************************************************************************************************************************/
    public static boolean hasScaledReforge(Player player) {
        boolean[] found = { false };
        CuriosApi.getCuriosHelper()
                .getCuriosHandler(player)
                .ifPresent(handler -> {
                    outer:
                    for (var stacksHandler : handler.getCurios().values()) {
                        var stacks = stacksHandler.getStacks();
                        for (int i = 0; i < stacks.getSlots(); i++) {
                            ItemStack stack = stacks.getStackInSlot(i);
                            if (stack.isEmpty()) continue;
                            CompoundTag tag = stack.getTag();
                            if (tag == null || !tag.contains(REFORGE_TAG)) continue;
                            ReforgeInstance inst = ReforgeInstance.fromNBT(tag.getCompound(REFORGE_TAG));
                            if (inst == null) continue;
                            if (inst.getReforge().getAttributes().stream()
                                    .anyMatch(Reforge.AttributeEntry::isScaled)) {
                                found[0] = true;
                                break outer;
                            }
                        }
                    }
                });
        return found[0];
    }

    /*****************************************************************************************************************************************************************
    Recalculates all scaled-reforge attribute modifiers on Curios items by directly
    replacing the cached AttributeModifier in the player's AttributeMap.
    *****************************************************************************************************************************************************************/
    public static void recalculate(Player player) {
        CuriosApi.getCuriosHelper()
                .getCuriosHandler(player)
                .ifPresent(handler -> handler.getCurios().forEach((slotId, stacksHandler) -> {
                    var stacks = stacksHandler.getStacks();
                    for (int i = 0; i < stacks.getSlots(); i++) {
                        ItemStack stack = stacks.getStackInSlot(i);
                        if (stack.isEmpty()) continue;

                        CompoundTag tag = stack.getTag();
                        if (tag == null || !tag.contains(REFORGE_TAG)) continue;

                        ReforgeInstance inst = ReforgeInstance.fromNBT(tag.getCompound(REFORGE_TAG));
                        if (inst == null) continue;

                        boolean hasScaled = inst.getReforge().getAttributes().stream()
                                .anyMatch(Reforge.AttributeEntry::isScaled);
                        if (!hasScaled) continue;

                        applyModifiers(player, slotId, inst);
                    }
                }));
    }

    /*****************************************************************************************************************************************************************
    Called from ReforgeEditorGUI after a reforge is saved, to immediately refresh all
    Curios items carrying that specific reforge without requiring a re-equip.
    *****************************************************************************************************************************************************************/
    public static void refreshReforgeOnCurios(net.minecraft.server.level.ServerPlayer player, String reforgeId) {
        CuriosApi.getCuriosHelper()
                .getCuriosHandler(player)
                .ifPresent(handler -> handler.getCurios().forEach((slotId, stacksHandler) -> {
                    var stacks = stacksHandler.getStacks();
                    for (int i = 0; i < stacks.getSlots(); i++) {
                        ItemStack stack = stacks.getStackInSlot(i);
                        if (stack.isEmpty()) continue;

                        CompoundTag tag = stack.getTag();
                        if (tag == null || !tag.contains(REFORGE_TAG)) continue;

                        ReforgeInstance inst = ReforgeInstance.fromNBT(tag.getCompound(REFORGE_TAG));
                        if (inst == null || !inst.getReforge().getId().equals(reforgeId)) continue;

                        applyModifiers(player, slotId, inst);
                    }
                }));
    }

    /*****************************************************************************************************************************************************************
    Core: removes stale modifiers and adds fresh ones directly to the player's AttributeMap.
    Uses indexed loop to apply per-attribute debuff multipliers (hardmode).
    *****************************************************************************************************************************************************************/
    private static void applyModifiers(Player player, String slotId, ReforgeInstance inst) {
        String reforgeId = inst.getReforge().getId();
        List<Reforge.AttributeEntry> attributes = inst.getReforge().getAttributes();

        for (int i = 0; i < attributes.size(); i++) {
            Reforge.AttributeEntry entry = attributes.get(i);

            var attr = entry.getAttribute();
            if (attr == null) continue;

            var attrInst = player.getAttribute(attr);
            if (attrInst == null) continue;

            // Same UUID formula as CurioAttributeEvents so we replace the right modifier
            UUID uuid = UUID.nameUUIDFromBytes(
                    ("RandomReforges:" + reforgeId + ":" + entry.getAttributeId() + ":" + slotId)
                            .getBytes(StandardCharsets.UTF_8)
            );

            double amount = entry.computeAmount(player);

            //*** Hardmode: apply per-attribute debuff multiplier ******************************************************
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

            AttributeModifier.Operation op = AttributeUtil.fromString(entry.operation());

            // Remove stale modifier, add fresh one with updated amount
            attrInst.removeModifier(uuid);
            attrInst.addTransientModifier(new AttributeModifier(
                    uuid, "Reforge_" + reforgeId, amount, op));
        }
    }
}