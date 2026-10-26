package de.randomreforges.reforge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;


/*****************************************************************************************************************************************************************
Command structure:
1. /reforge <player> apply <reforge-ID> [slot]     Applies the reforge to the item in the given slot (default: hand)
2. /reforge <player> clear [slot]                  Removes the reforge and locks the slot (default: hand)
3. /reforge <player> lock  [slot]                  Locks the current reforge so the Reforge Cage cannot reroll it.

/reforge apply does NOT trigger the hardmode lock roll (admin command bypasses it).
*****************************************************************************************************************************************************************/

public class ReforgeCommand {

    private static final String REFORGE_TAG = "RandomReforges";
    private static final String LOCK_TAG    = "ReforgeLocked";

    private static final String[] VANILLA_SLOT_SUGGESTIONS = { "hand", "helmet", "chestplate", "leggings", "legs", "boots" };

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("reforge")
                .requires(src -> src.hasPermission(2))

                .then(Commands.argument("targets", EntityArgument.players())

                        .then(Commands.literal("apply")
                                .then(Commands.argument("reforge", StringArgumentType.word())
                                        .executes(ctx -> executeApply(ctx, null))
                                        .then(Commands.argument("slot", StringArgumentType.word())
                                                .suggests((ctx, builder) -> buildSlotSuggestions(ctx, builder))
                                                .executes(ctx -> executeApply(ctx, StringArgumentType.getString(ctx, "slot")))
                                        )
                                )
                        )

                        .then(Commands.literal("clear")
                                .executes(ctx -> executeClear(ctx, null))
                                .then(Commands.argument("slot", StringArgumentType.word())
                                        .suggests((ctx, builder) -> buildSlotSuggestions(ctx, builder))
                                        .executes(ctx -> executeClear(ctx, StringArgumentType.getString(ctx, "slot")))
                                )
                        )

                        .then(Commands.literal("lock")
                                .executes(ctx -> executeLock(ctx, null))
                                .then(Commands.argument("slot", StringArgumentType.word())
                                        .suggests((ctx, builder) -> buildSlotSuggestions(ctx, builder))
                                        .executes(ctx -> executeLock(ctx, StringArgumentType.getString(ctx, "slot")))
                                )
                        )
                );
    }

    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> buildSlotSuggestions(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {

        List<String> suggestions = new ArrayList<>(Arrays.asList(VANILLA_SLOT_SUGGESTIONS));

        if (ModList.get().isLoaded("curios")) {
            try {
                Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "targets");
                if (!players.isEmpty())
                    suggestions.addAll(ReforgeCommandCuriosHelper.getSlotIds(players.iterator().next()));
            } catch (Exception ignored) {}
        }

        return SharedSuggestionProvider.suggest(suggestions, builder);
    }

    private static EquipmentSlot parseVanillaSlot(String slotName) {
        if (slotName == null) return EquipmentSlot.MAINHAND;
        return switch (slotName.toLowerCase()) {
            case "hand"              -> EquipmentSlot.MAINHAND;
            case "helmet"            -> EquipmentSlot.HEAD;
            case "chestplate"        -> EquipmentSlot.CHEST;
            case "leggings", "legs"  -> EquipmentSlot.LEGS;
            case "boots"             -> EquipmentSlot.FEET;
            default                  -> null;
        };
    }

    private static List<ItemStack> getStacksForSlot(ServerPlayer player, String slotArg) {
        EquipmentSlot vanillaSlot = parseVanillaSlot(slotArg);
        if (vanillaSlot != null) {
            ItemStack stack = player.getItemBySlot(vanillaSlot);
            return stack.isEmpty() ? List.of() : List.of(stack);
        }
        if (ModList.get().isLoaded("curios"))
            return ReforgeCommandCuriosHelper.getStacksInSlot(player, slotArg);
        return List.of();
    }

    private static int executeApply(CommandContext<CommandSourceStack> ctx, String slotArg)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {

        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        String arg    = StringArgumentType.getString(ctx, "reforge");
        boolean random = arg.equalsIgnoreCase("random");
        int changed = 0;

        for (ServerPlayer player : targets) {
            List<ItemStack> stacks = getStacksForSlot(player, slotArg);

            if (stacks.isEmpty()) {
                player.sendSystemMessage(Component.literal("No item in slot: " + (slotArg != null ? slotArg : "hand")));
                continue;
            }

            for (ItemStack stack : stacks) {
                Reforge reforge;

                if (random) {
                    reforge = ReforgeManager.getRandomApplicableReforge(stack, parseVanillaSlot(slotArg));
                    if (reforge == null) {
                        player.sendSystemMessage(Component.literal("No applicable reforges for this item."));
                        continue;
                    }
                } else {
                    reforge = ReforgeManager.getById(arg);
                    if (reforge == null) {
                        player.sendSystemMessage(Component.literal("Unknown reforge id: " + arg));
                        continue;
                    }
                }

                String itemName = stack.getItem().getName(stack).getString();
                applyReforge(player, stack, reforge);
                changed++;

                final String msg = "Applied " + reforge.getName() + " to " + itemName;
                ctx.getSource().sendSuccess(() -> Component.literal(msg), true);
            }
        }

        return changed;
    }

    /*****************************************************************************************************************************************************************
    Task: Apply a reforge directly, bypassing ReforgeLocked.
    /reforge apply does NOT trigger the hardmode lock roll (admin command).
    Does roll per-attribute debuff multipliers (hardmode debuff variance still applies).
    *****************************************************************************************************************************************************************/
    private static void applyReforge(ServerPlayer player, ItemStack stack, Reforge reforge) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.remove(REFORGE_TAG);
        tag.remove(LOCK_TAG);

        ReforgeInstance inst = new ReforgeInstance(
                reforge,
                ReforgeManager.rollPerfect(),
                ReforgeManager.rollDebuffMultipliers(reforge)
        );
        tag.put(REFORGE_TAG, inst.serializeNBT());
        stack.setTag(tag);

        if (inst.isPerfect()) {
            var adv = player.getServer().getAdvancements()
                    .getAdvancement(new net.minecraft.resources.ResourceLocation("randomreforges", "perfect_reforge"));
            if (adv != null) player.getAdvancements().award(adv, "got_perfect_reforge");
        }

        AttributeUtil.removeAttributes(stack);

        boolean isCurio = ModList.get().isLoaded("curios")
                && de.randomreforges.curios.CuriosUtil.isCurioItem(stack);

        if (!isCurio) {
            AttributeUtil.applyAttributes(stack, inst);
        }

        stack.setHoverName(inst.getDisplayName(stack));
    }

    private static int executeClear(CommandContext<CommandSourceStack> ctx, String slotArg)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {

        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        int changed = 0;

        for (ServerPlayer player : targets) {
            List<ItemStack> stacks = getStacksForSlot(player, slotArg);

            if (stacks.isEmpty()) {
                player.sendSystemMessage(Component.literal("No item in slot: " + (slotArg != null ? slotArg : "hand")));
                continue;
            }

            for (ItemStack stack : stacks) {
                CompoundTag tag = stack.getOrCreateTag();
                if (!tag.contains(REFORGE_TAG)) continue;

                String itemName = stack.getItem().getName(stack).getString();
                tag.remove(REFORGE_TAG);
                tag.putBoolean(LOCK_TAG, true);
                stack.setTag(tag);

                AttributeUtil.removeAttributes(stack);
                stack.setHoverName(stack.getItem().getName(stack).copy().withStyle(s -> s.withItalic(false)));
                changed++;

                final String msg = "Cleared reforge from " + itemName;
                ctx.getSource().sendSuccess(() -> Component.literal(msg), true);
            }
        }

        return changed;
    }

    private static int executeLock(CommandContext<CommandSourceStack> ctx, String slotArg)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {

        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        int changed = 0;

        for (ServerPlayer player : targets) {
            List<ItemStack> stacks = getStacksForSlot(player, slotArg);

            if (stacks.isEmpty()) {
                player.sendSystemMessage(Component.literal("No item in slot: " + (slotArg != null ? slotArg : "hand")));
                continue;
            }

            for (ItemStack stack : stacks) {
                CompoundTag tag = stack.getOrCreateTag();

                if (!tag.contains(REFORGE_TAG)) {
                    player.sendSystemMessage(Component.literal(
                            stack.getItem().getName(stack).getString() + " has no reforge to lock."));
                    continue;
                }

                if (tag.getBoolean(LOCK_TAG)) {
                    player.sendSystemMessage(Component.literal(
                            stack.getItem().getName(stack).getString() + " is already locked."));
                    continue;
                }

                String itemName = stack.getItem().getName(stack).getString();
                tag.putBoolean(LOCK_TAG, true);
                stack.setTag(tag);
                changed++;

                final String msg = "Locked reforge on " + itemName;
                ctx.getSource().sendSuccess(() -> Component.literal(msg), true);
            }
        }

        return changed;
    }
}
