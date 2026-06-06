package de.randomreforges.reforge;

import java.text.DecimalFormat;
import java.util.Locale;

import de.randomreforges.RandomReforges;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = RandomReforges.MODID)
public class Events {

    private static final String REFORGE_TAG = "RandomReforges";

    @SubscribeEvent
    public static void onEquip(LivingEquipmentChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        ItemStack newStack = event.getTo();
        EquipmentSlot slot = AttributeUtil.detectSlot(newStack);
        boolean isCurio = ModList.get().isLoaded("curios")
                && de.randomreforges.curios.CuriosUtil.isCurioItem(newStack);

        applyInitialReforge(newStack, slot, isCurio, player);

        // Mark player dirty so scaled reforges get recalculated
        ScalableReforgeHandler.dirtyPlayers.add(player.getUUID());
    }

    /**
     * Applies a random reforge to an item that doesn't have one yet.
     * If the item already has a reforge, refreshes its NBT attributes in case
     * the reforge definition was edited (e.g. via the GUI).
     * Called both from LivingEquipmentChangeEvent (vanilla) and CurioChangeEvent (curios).
     *
     * @param stack   the item to reforge
     * @param slot    the equipment slot (use MAINHAND for curio items)
     * @param isCurio true if the item is a curio – skips NBT attribute application
     *                since CurioAttributeModifierEvent handles that instead
     * @param entity  the entity equipping the item (used for scaled reforge computation)
     */
    public static void applyInitialReforge(ItemStack stack, EquipmentSlot slot, boolean isCurio,
                                           net.minecraft.world.entity.LivingEntity entity) {
        if (stack.isEmpty()) return;

        CompoundTag tag = stack.getOrCreateTag();

        if (tag.contains(REFORGE_TAG)) {
            // Item already has a reforge – refresh NBT attributes in case the reforge was edited.
            // Curio items get their attributes from CurioAttributeModifierEvent, not NBT.
            if (!isCurio) {
                ReforgeInstance inst = ReforgeInstance.fromNBT(tag.getCompound(REFORGE_TAG));
                if (inst != null && inst.getReforge() != null) {
                    AttributeUtil.removeAttributes(stack);

                    if (entity != null) {
                        AttributeUtil.applyAttributes(stack, inst, entity);
                    } else {
                        AttributeUtil.applyAttributes(stack, inst);
                    }
                }
            }
            return;
        }

        if (tag.getBoolean("ReforgeLocked")) return;
        if (tag.contains("Reforgable") && !tag.getBoolean("Reforgable")) return;

        Reforge reforge = ReforgeManager.getRandomApplicableReforge(stack, slot);
        if (reforge == null) return;

        ReforgeInstance inst = new ReforgeInstance(reforge);
        tag.put(REFORGE_TAG, inst.serializeNBT());
        stack.setTag(tag);

        stack.setHoverName(inst.getDisplayName(stack));
        AttributeUtil.removeAttributes(stack);

        if (!isCurio) {
            if (entity != null) {
                AttributeUtil.applyAttributes(stack, inst, entity);
            } else {
                AttributeUtil.applyAttributes(stack, inst);
            }
        }
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!stack.hasTag()) return;

        CompoundTag tag = stack.getTag();
        if (!tag.contains(REFORGE_TAG)) return;

        ReforgeInstance inst = ReforgeInstance.fromNBT(tag.getCompound(REFORGE_TAG));
        if (inst == null) return;

        Reforge reforge = inst.getReforge();
        if (reforge == null) return;

        Component comment = inst.getCommentLine();
        if (!comment.getString().isEmpty()) {
            if (event.getToolTip().size() >= 1) {
                event.getToolTip().add(1, comment);
            } else {
                event.getToolTip().add(comment);
            }
        }

        event.getToolTip().add(Component.empty());
        event.getToolTip().add(
                Component.literal("Reforge:")
                        .withStyle(Style.EMPTY.withColor(0xAAAAAA).withItalic(false))
        );

        reforge.getAttributes().forEach(entry -> {
            Attribute attribute = entry.getAttribute();
            if (attribute == null) return;

            double amt = entry.amount();
            AttributeModifier.Operation op = AttributeUtil.fromString(entry.operation());

            boolean isPositive = amt > 0;
            boolean isZero = Math.abs(amt) < 1e-9;
            String sign = isPositive ? "+" : (isZero ? "" : "-");
            int color = isPositive ? 0x3F76E4 : 0xFF5555;

            if (entry.isScaled()) {
                // Scaled reforge: two separate lines
                Attribute srcAttr = entry.getScaleSourceAttribute();

                String srcName = srcAttr != null
                        ? Component.translatable(srcAttr.getDescriptionId()).getString()
                        : entry.getScaledBy().toString();

                // Line 1: base amount (only if non-zero)
                if (Math.abs(amt) >= 1e-9) {
                    String baseText = sign + formatNumber(Math.abs(amt));

                    event.getToolTip().add(
                            Component.literal(baseText + " ")
                                    .append(Component.translatable(attribute.getDescriptionId()))
                                    .withStyle(Style.EMPTY.withColor(color).withItalic(false))
                    );
                }

                // Line 2: "+1 [Attribute] per [1/ratio] [ScaledBy]"
                // Inverting the ratio avoids tiny decimals (e.g. 0.001 → "per 1000 Max Mana").
                double ratio = entry.getScaleRatio();
                if (Math.abs(ratio) >= 1e-9) {
                    boolean ratioPositive = ratio > 0;
                    int ratioColor = ratioPositive ? 0x3F76E4 : 0xFF5555;
                    String ratioSign = ratioPositive ? "+" : "-";
                    String invText = formatNumber(1.0 / Math.abs(ratio));

                    event.getToolTip().add(
                            Component.literal(ratioSign + "1 ")
                                    .append(Component.translatable(attribute.getDescriptionId()))
                                    .append(Component.literal(" per " + invText + " "))
                                    .append(Component.literal(srcName))
                                    .withStyle(Style.EMPTY.withColor(ratioColor).withItalic(false))
                    );
                }
            } else {
                String valueText;

                if (op == AttributeModifier.Operation.MULTIPLY_BASE
                        || op == AttributeModifier.Operation.MULTIPLY_TOTAL) {
                    double percent = Math.abs(amt * 100.0);
                    valueText = formatPercent(percent);
                } else {
                    valueText = formatNumber(Math.abs(amt));
                }

                event.getToolTip().add(
                        Component.literal(sign + valueText + " ")
                                .append(Component.translatable(attribute.getDescriptionId()))
                                .withStyle(Style.EMPTY.withColor(color).withItalic(false))
                );
            }
        });
    }

    private static String formatPercent(double percent) {
        if (Math.abs(percent - Math.round(percent)) < 0.0001) {
            return String.format(Locale.ROOT, "%d%%", Math.round(percent));
        } else {
            return String.format(Locale.ROOT, "%.1f%%", percent);
        }
    }

    private static String formatNumber(double value) {
        if (Math.abs(value - Math.round(value)) < 0.0001) {
            return String.format(Locale.ROOT, "%d", Math.round(value));
        } else {
            DecimalFormat df = new DecimalFormat("#.##");
            df.setDecimalSeparatorAlwaysShown(false);
            return df.format(value);
        }
    }

    @SubscribeEvent
    public static void onVillagerRightClick(net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract event) {

        if (!(event.getTarget() instanceof Villager villager)) {
            return;
        }

        Player player = event.getEntity();
        Level level = player.level();
        ItemStack stack = player.getItemInHand(event.getHand());

        //holding core
        if (!stack.is(de.randomreforges.registry.ItemRegistry.SOULLESS_REFORGE_CORE.get())) {
            return;
        }

        //server only
        if (level.isClientSide) {
            return;
        }

        //consume core
        if (!player.isCreative()) {
            stack.shrink(1);
        }

        //replace with reforge core
        player.addItem(new ItemStack(de.randomreforges.registry.ItemRegistry.REFORGE_CORE.get()));

        //sound effect
        level.playSound(null, villager.blockPosition(),
                net.minecraft.sounds.SoundEvents.SOUL_ESCAPE,
                net.minecraft.sounds.SoundSource.PLAYERS,
                3.0f, 1.0f);

        //remove villager
        villager.discard();

        //dont open gui
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }
}
