package de.randomreforges.reforge;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import de.randomreforges.RandomReforges;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

/**
 * Handles recalculation of scaled reforge attributes.
 *
 * Uses a dirty-flag pattern:
 *   1. LivingEquipmentChangeEvent marks the player as dirty.
 *   2. PlayerTickEvent (server side, every 5 ticks) flushes all dirty players
 *      in a single pass – collapsing multiple rapid equipment changes into one update.
 */
@Mod.EventBusSubscriber(modid = RandomReforges.MODID)
public class ScalableReforgeHandler {

    private static final int RECALC_INTERVAL = 5; // ticks between flush checks
    private static final String REFORGE_TAG  = "RandomReforges";

    /** Players that need a recalculation on the next flush. */
    public static final Set<UUID> dirtyPlayers = new HashSet<>();

    // ── Mark dirty ────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;

        // Only mark dirty if the player actually has a scaled reforge somewhere
        if (playerHasScaledReforge(player)) {
            dirtyPlayers.add(player.getUUID());
        }
    }

    // ── Flush dirty players ───────────────────────────────────────────────────

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.side != LogicalSide.SERVER) return;

        Player player = event.player;
        if (!(player instanceof ServerPlayer)) return;
        if (player.tickCount % RECALC_INTERVAL != 0) return;

        if (!dirtyPlayers.remove(player.getUUID())) return;

        recalculate(player);
    }

    // ── Core recalculation ────────────────────────────────────────────────────

    /**
     * Recalculates all scaled reforge modifiers for the given player.
     * Removes old modifiers and rewrites them with the current scaled values.
     */
    public static void recalculate(Player player) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) continue;

            CompoundTag tag = stack.getTag();
            if (tag == null || !tag.contains(REFORGE_TAG)) continue;

            ReforgeInstance inst = ReforgeInstance.fromNBT(tag.getCompound(REFORGE_TAG));
            if (inst == null) continue;

            // Only process items that actually have scaled attributes
            boolean hasScaled = inst.getReforge().getAttributes().stream()
                    .anyMatch(Reforge.AttributeEntry::isScaled);
            if (!hasScaled) continue;

            // Remove and reapply with fresh scaled values
            AttributeUtil.removeAttributes(stack);
            AttributeUtil.applyAttributes(stack, inst, player);
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static boolean playerHasScaledReforge(Player player) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) continue;

            CompoundTag tag = stack.getTag();
            if (tag == null || !tag.contains(REFORGE_TAG)) continue;

            ReforgeInstance inst = ReforgeInstance.fromNBT(tag.getCompound(REFORGE_TAG));
            if (inst == null) continue;

            if (inst.getReforge().getAttributes().stream().anyMatch(Reforge.AttributeEntry::isScaled))
                return true;
        }
        return false;
    }
}