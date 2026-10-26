package de.randomreforges.reforge;

import java.text.DecimalFormat;
import java.util.List;
import java.util.Locale;

import de.randomreforges.RandomReforges;
import de.randomreforges.registry.BlockRegistry;
import de.randomreforges.registry.ItemRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
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

    private static final java.util.Set<java.util.UUID> pendingHealthClamp = new java.util.HashSet<>();

    @SubscribeEvent
    public static void onEquip(LivingEquipmentChangeEvent event) {
        if (!(event.getEntity() instanceof Player player))
            return;
        if (player.level().isClientSide())
            return;

        ItemStack newStack = event.getTo();
        EquipmentSlot slot = AttributeUtil.detectSlot(newStack);
        boolean isCurio = ModList.get().isLoaded("curios")
                && de.randomreforges.curios.CuriosUtil.isCurioItem(newStack);

        applyInitialReforge(newStack, slot, isCurio, player);

        if (ScalableReforgeHandler.playerHasScaledReforge(player)) {
            ScalableReforgeHandler.dirtyPlayers.add(player.getUUID());
        }

        if (!player.level().isClientSide()) {
            pendingHealthClamp.add(player.getUUID());
        }
    }

    /*****************************************************************************************************************************************************************
     * Applies a random reforge to an item that doesn't have one yet.
     *****************************************************************************************************************************************************************/
    public static void applyInitialReforge(ItemStack stack, EquipmentSlot slot, boolean isCurio,
            net.minecraft.world.entity.LivingEntity entity) {
        if (stack.isEmpty())
            return;

        CompoundTag tag = stack.getOrCreateTag();

        if (tag.contains(REFORGE_TAG)) {
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

        if (tag.getBoolean("ReforgeLocked"))
            return;
        if (tag.contains("Reforgable") && !tag.getBoolean("Reforgable"))
            return;

        Reforge reforge = ReforgeManager.getRandomApplicableReforge(stack, slot);
        if (reforge == null)
            return;

        // Roll perfect chance + per-attribute debuff multipliers (hardmode)
        ReforgeInstance inst = new ReforgeInstance(
                reforge,
                ReforgeManager.rollPerfect(),
                ReforgeManager.rollDebuffMultipliers(reforge));
        tag.put(REFORGE_TAG, inst.serializeNBT());
        stack.setTag(tag);

        if (inst.isPerfect() && entity instanceof net.minecraft.server.level.ServerPlayer sp) {
            grantAdvancement(sp, "perfect_reforge", "got_perfect_reforge");
        }

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
        if (!stack.hasTag())
            return;

        CompoundTag tag = stack.getTag();
        if (!tag.contains(REFORGE_TAG))
            return;

        ReforgeInstance inst = ReforgeInstance.fromNBT(tag.getCompound(REFORGE_TAG));
        if (inst == null)
            return;

        Reforge reforge = inst.getReforge();
        if (reforge == null)
            return;

        Component comment = inst.getCommentLine();
        if (!comment.getString().isEmpty()) {
            if (event.getToolTip().size() >= 1) {
                event.getToolTip().add(1, comment);
            } else {
                event.getToolTip().add(comment);
            }
        }

        event.getToolTip().add(Component.empty());

        List<Reforge.AttributeEntry> attrList = reforge.getAttributes();

        if (inst.isPerfect()) {
            // *** Perfect reforge tooltip
            // ******************************************************
            int bonus = ReforgeManager.getPerfectBonusPercent();
            event.getToolTip().add(
                    Component.literal("\u2726 Perfect Reforge (+" + bonus + "%):")
                            .withStyle(Style.EMPTY.withColor(0xFFDD00).withItalic(false)));

            for (int i = 0; i < attrList.size(); i++) {
                Reforge.AttributeEntry entry = attrList.get(i);
                Attribute attribute = entry.getAttribute();
                if (attribute == null)
                    continue;

                double amt = entry.amount();

                // *** Hardmode: apply per-attribute debuff multiplier
                // ******************************************************
                if (amt < 0)
                    amt *= inst.getDebuffMultiplier(i);

                // *** Perfect reforge: skip negatives (unless hardmode), boost positives
                // ******************************************************
                if (amt < 0 && !ReforgeManager.isHardmodeEnabled())
                    continue;
                if (amt >= 0 && bonus > 0)
                    amt *= (1.0 + bonus / 100.0);

                AttributeModifier.Operation op = AttributeUtil.fromString(entry.operation());
                String sign = amt >= 0 ? "+" : "-";
                // Gold for buffs; red for debuffs shown in hardmode
                int color = amt >= 0 ? 0xFFDD00 : 0xFF5555;

                if (entry.isScaled()) {
                    Attribute srcAttr = entry.getScaleSourceAttribute();
                    String srcName = srcAttr != null
                            ? Component.translatable(srcAttr.getDescriptionId()).getString()
                            : entry.getScaledBy().toString();

                    if (Math.abs(amt) >= 1e-9) {
                        MutableComponent line = Component.literal(sign + formatNumber(Math.abs(amt)) + " ")
                                .append(Component.translatable(attribute.getDescriptionId()))
                                .withStyle(Style.EMPTY.withColor(color).withItalic(false));
                        appendDebuffMultiplier(line, inst, entry, i, op);
                        event.getToolTip().add(line);
                    }

                    double ratio = entry.getScaleRatio();
                    if (Math.abs(ratio) >= 1e-9) {
                        String invText = formatNumber(1.0 / Math.abs(ratio));
                        event.getToolTip().add(
                                Component.literal("+1 ")
                                        .append(Component.translatable(attribute.getDescriptionId()))
                                        .append(Component.literal(" per " + invText + " "))
                                        .append(Component.literal(srcName))
                                        .withStyle(Style.EMPTY.withColor(color).withItalic(false)));
                    }
                } else {
                    String valueText;
                    if (op == AttributeModifier.Operation.MULTIPLY_BASE
                            || op == AttributeModifier.Operation.MULTIPLY_TOTAL) {
                        valueText = formatPercent(Math.abs(amt * 100.0));
                    } else {
                        valueText = formatNumber(Math.abs(amt));
                    }
                    MutableComponent line = Component.literal(sign + valueText + " ")
                            .append(Component.translatable(attribute.getDescriptionId()))
                            .withStyle(Style.EMPTY.withColor(color).withItalic(false));
                    appendDebuffMultiplier(line, inst, entry, i, op);
                    event.getToolTip().add(line);
                }
            }

        } else {
            // *** Normal reforge tooltip
            // ******************************************************
            event.getToolTip().add(
                    Component.literal("Reforge:")
                            .withStyle(Style.EMPTY.withColor(0xAAAAAA).withItalic(false)));

            for (int i = 0; i < attrList.size(); i++) {
                Reforge.AttributeEntry entry = attrList.get(i);
                Attribute attribute = entry.getAttribute();
                if (attribute == null)
                    continue;

                double amt = entry.amount();
                // *** Hardmode: apply per-attribute debuff multiplier
                // ******************************************************
                if (amt < 0)
                    amt *= inst.getDebuffMultiplier(i);

                AttributeModifier.Operation op = AttributeUtil.fromString(entry.operation());
                boolean isPositive = amt > 0;
                boolean isZero = Math.abs(amt) < 1e-9;
                String sign = isPositive ? "+" : (isZero ? "" : "-");
                int color = isPositive ? 0x3F76E4 : 0xFF5555;

                if (entry.isScaled()) {
                    // *** Scaled Reforge (2 lines)
                    // ******************************************************
                    Attribute srcAttr = entry.getScaleSourceAttribute();
                    String srcName = srcAttr != null
                            ? Component.translatable(srcAttr.getDescriptionId()).getString()
                            : entry.getScaledBy().toString();

                    // Line 1: base value
                    if (Math.abs(amt) >= 1e-9) {
                        MutableComponent line = Component.literal(sign + formatNumber(Math.abs(amt)) + " ")
                                .append(Component.translatable(attribute.getDescriptionId()))
                                .withStyle(Style.EMPTY.withColor(color).withItalic(false));
                        appendDebuffMultiplier(line, inst, entry, i, op);
                        event.getToolTip().add(line);
                    }

                    // Line 2: ratio
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
                                        .withStyle(Style.EMPTY.withColor(ratioColor).withItalic(false)));
                    }
                } else {
                    String valueText;
                    if (op == AttributeModifier.Operation.MULTIPLY_BASE
                            || op == AttributeModifier.Operation.MULTIPLY_TOTAL) {
                        valueText = formatPercent(Math.abs(amt * 100.0));
                    } else {
                        valueText = formatNumber(Math.abs(amt));
                    }
                    MutableComponent line = Component.literal(sign + valueText + " ")
                            .append(Component.translatable(attribute.getDescriptionId()))
                            .withStyle(Style.EMPTY.withColor(color).withItalic(false));
                    appendDebuffMultiplier(line, inst, entry, i, op);
                    event.getToolTip().add(line);
                }
            }
        }

        // *** Locked indicator ******************************************************
        if (tag.getBoolean("ReforgeLocked")) {
            event.getToolTip().add(
                    Component.literal("\u2620 Reforge Locked")
                            .withStyle(Style.EMPTY.withColor(0xAAAAAA).withItalic(false)));
        }
    }

    /**
     * Appends inline debuff info to the given component line.
     * Shows ☠ +x% / ☠ -x% (percentage change mode) and/or ☠ [min to max] (range
     * mode)
     * depending on the settings. Nothing is appended if both modes are disabled.
     * Dark red = debuff got stronger, green = debuff was reduced, grey = unchanged.
     */
    private static void appendDebuffMultiplier(MutableComponent line,
            ReforgeInstance inst,
            Reforge.AttributeEntry entry,
            int attrIndex,
            AttributeModifier.Operation op) {
        if (entry.amount() >= 0)
            return; // only debuffs

        boolean showPct   = ReforgeManager.isHardmodeShowPercentageChange() && ReforgeManager.isHardmodeEnabled();
        boolean showRange = ReforgeManager.isHardmodeShowRange() && ReforgeManager.isHardmodeEnabled();
        if (!showPct && !showRange)
            return;

        double mult = inst.getDebuffMultiplier(attrIndex);
        double changePct = (mult - 1.0) * 100.0;
        boolean pctDev = Math.abs(changePct) > 0.001;

        boolean willShowPct = showPct && pctDev;
        boolean willShowRange = showRange;
        if (!willShowPct && !willShowRange)
            return;

        StringBuilder sb = new StringBuilder(" \u2620");

        if (willShowPct) {
            sb.append(" ").append(changePct > 0 ? "+" : "").append(formatNumber(changePct)).append("%");
        }

        if (willShowRange) {
            double minMult = 1.0 + ReforgeManager.getHardmodeDebuffMinPercent() / 100.0;
            double maxMult = 1.0 + ReforgeManager.getHardmodeDebuffMaxPercent() / 100.0;
            double base = entry.amount(); // negative
            double val1 = base * minMult;
            double val2 = base * maxMult;
            double lo = Math.min(val1, val2); // most negative (worst for player)
            double hi = Math.max(val1, val2); // least negative (best for player)

            boolean isPercent = op == AttributeModifier.Operation.MULTIPLY_BASE
                    || op == AttributeModifier.Operation.MULTIPLY_TOTAL;
            String loStr = isPercent ? formatNumber(lo * 100.0) + "%" : formatNumber(lo);
            String hiStr = isPercent ? formatNumber(hi * 100.0) + "%" : formatNumber(hi);

            sb.append(" [").append(loStr).append(" to ").append(hiStr).append("]");
        }

        int color;
        if (changePct > 0.001)
            color = 0xAA0000; // dark red – debuff stronger
        else if (changePct < -0.001)
            color = 0x55AA55; // green – debuff weaker
        else
            color = 0xAAAAAA; // grey – unchanged

        line.append(Component.literal(sb.toString())
                .withStyle(Style.EMPTY.withColor(color).withItalic(false)));
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

    /*****************************************************************************************************************************************************************
     * Soulless Reforge Core -> Reforge Core interaction
     *****************************************************************************************************************************************************************/
    @SubscribeEvent
    public static void onVillagerRightClick(
            net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract event) {

        if (!(event.getTarget() instanceof Villager villager))
            return;

        Player player = event.getEntity();
        Level level = player.level();
        ItemStack stack = player.getItemInHand(event.getHand());

        if (!stack.is(de.randomreforges.registry.ItemRegistry.SOULLESS_REFORGE_CORE.get()))
            return;
        if (level.isClientSide)
            return;

        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            grantAdvancement(sp, "root", "has_soulless_core");
            grantAdvancement(sp, "soulless_core", "has_soulless_core");
        }

        if (!player.isCreative())
            stack.shrink(1);

        player.addItem(new ItemStack(de.randomreforges.registry.ItemRegistry.REFORGE_CORE.get()));

        level.playSound(null, villager.blockPosition(),
                net.minecraft.sounds.SoundEvents.SOUL_ESCAPE,
                net.minecraft.sounds.SoundSource.PLAYERS,
                3.0f, 1.0f);

        villager.discard();

        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    // *** Small hints as ingame guide how to use these items
    // ******************************************************
    @SubscribeEvent
    public static void onModItemTooltip(ItemTooltipEvent event) {
        Item item = event.getItemStack().getItem();

        String key = null;
        if (item == ItemRegistry.SOULLESS_REFORGE_CORE.get())
            key = "item.randomreforges.soulless_reforge_core.desc";
        else if (item == ItemRegistry.REFORGE_CORE.get())
            key = "item.randomreforges.reforge_core.desc";
        else if (item == BlockRegistry.EMPTY_REFORGE_CAGE_ITEM.get())
            key = "block.randomreforges.empty_reforge_cage.desc";
        else if (item == BlockRegistry.REFORGE_CAGE_ITEM.get())
            key = "block.randomreforges.reforge_cage.desc";

        if (key != null) {
            event.getToolTip().add(Component.translatable(key)
                    .withStyle(Style.EMPTY.withColor(0x777777).withItalic(true)));
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END)
            return;
        if (event.player.level().isClientSide())
            return;
        if (!(event.player instanceof net.minecraft.server.level.ServerPlayer sp))
            return;

        if (pendingHealthClamp.remove(sp.getUUID())) {
            if (sp.getHealth() > sp.getMaxHealth())
                sp.setHealth(sp.getMaxHealth());
        }

        if (sp.tickCount % 20 != 0)
            return;
        if (sp.getInventory().hasAnyMatching(stack -> stack.is(ItemRegistry.SOULLESS_REFORGE_CORE.get()))) {
            grantAdvancement(sp, "root", "has_soulless_core");
            grantAdvancement(sp, "soulless_core", "has_soulless_core");
        }

        boolean hasPerfect = sp.getInventory().hasAnyMatching(stack -> {
            if (stack.isEmpty() || !stack.hasTag())
                return false;
            net.minecraft.nbt.CompoundTag t = stack.getTag();
            if (!t.contains(REFORGE_TAG))
                return false;
            ReforgeInstance inst = ReforgeInstance.fromNBT(t.getCompound(REFORGE_TAG));
            return inst != null && inst.isPerfect();
        });
        if (!hasPerfect) {
            for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                net.minecraft.world.item.ItemStack stack = sp.getItemBySlot(slot);
                if (stack.isEmpty() || !stack.hasTag())
                    continue;
                net.minecraft.nbt.CompoundTag t = stack.getTag();
                if (!t.contains(REFORGE_TAG))
                    continue;
                ReforgeInstance inst = ReforgeInstance.fromNBT(t.getCompound(REFORGE_TAG));
                if (inst != null && inst.isPerfect()) {
                    hasPerfect = true;
                    break;
                }
            }
        }
        if (hasPerfect)
            grantAdvancement(sp, "perfect_reforge", "got_perfect_reforge");
    }

    /*****************************************************************************************************************************************************************
     * multiplayer: overwrites local state completely after login
     *****************************************************************************************************************************************************************/
    @SubscribeEvent
    public static void onPlayerLoggedIn(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) {
            de.randomreforges.network.NetworkHandler.CHANNEL.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> sp),
                    de.randomreforges.network.ClientBoundPackets.SyncReforgesPacket.fromCurrentState());
        }
    }

    private static void grantAdvancement(net.minecraft.server.level.ServerPlayer sp,
            String advId, String criterionId) {
        var adv = sp.getServer().getAdvancements()
                .getAdvancement(new net.minecraft.resources.ResourceLocation("randomreforges", advId));
        if (adv != null)
            sp.getAdvancements().award(adv, criterionId);
    }
}
