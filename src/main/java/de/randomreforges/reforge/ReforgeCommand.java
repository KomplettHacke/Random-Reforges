package de.randomreforges.reforge;

import java.util.Collection;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;


/*****************************************************************************************************************************************************************
Was the main way to reroll reforges at the beginning as I wasnt sure how to reroll them in survival.

TODO: Rework the whole command

Command structure:
1. /reforge <player> apply <reforge-ID>            Applies the reforge
2. /reforge <player> clear                         Clears the reforge and makes an item unreforgable

Check out applyAttributes and removeAttributes in AttributeUtil.java for core logic
*****************************************************************************************************************************************************************/

public class ReforgeCommand {

    private static final String REFORGE_TAG = "RandomReforges";

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("reforge")
                .requires(src -> src.hasPermission(2))

                .then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.literal("apply")
                                .then(Commands.argument("reforge", StringArgumentType.word())
                                        .executes(ctx -> executeApply(ctx))
                                )
                        )
                )

                .then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.literal("clear")
                                .executes(ctx -> executeClear(ctx))
                        )
                );
    }

    private static int executeApply(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {

        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        String arg = StringArgumentType.getString(ctx, "reforge");

        boolean random = arg.equalsIgnoreCase("random");
        int changed = 0;

        for (ServerPlayer player : targets) {
            ItemStack stack = player.getMainHandItem();
            if (stack.isEmpty()) continue;

            Reforge reforge;

            if (random) {
                reforge = ReforgeManager.getRandomApplicableReforge(stack, null);
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

            applyReforge(player, stack, reforge);
            changed++;
        }
        final int result = changed;
        ctx.getSource().sendSuccess(
                () -> Component.literal("Applied reforges to " + result + " item(s)."), true);

        return changed;
    }

    private static void applyReforge(ServerPlayer player, ItemStack stack, Reforge reforge) {

        CompoundTag tag = stack.getOrCreateTag();
        tag.remove(REFORGE_TAG);
        tag.remove("ReforgeLocked");

        ReforgeInstance inst = new ReforgeInstance(reforge);
        tag.put(REFORGE_TAG, inst.serializeNBT());
        stack.setTag(tag);

        AttributeUtil.removeAttributes(stack);

        boolean isCurio = ModList.get().isLoaded("curios")
                && de.randomreforges.curios.CuriosUtil.isCurioItem(stack);

        if (!isCurio) {
            AttributeUtil.applyAttributes(stack, inst);
        }

        stack.setHoverName(inst.getDisplayName(stack));
    }

    private static int executeClear(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {

        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        int changed = 0;

        for (ServerPlayer player : targets) {
            ItemStack stack = player.getMainHandItem();
            if (stack.isEmpty()) continue;

            CompoundTag tag = stack.getOrCreateTag();

            if (tag.contains(REFORGE_TAG)) {
                tag.remove(REFORGE_TAG);
                tag.putBoolean("ReforgeLocked", true);
                stack.setTag(tag);

                AttributeUtil.removeAttributes(stack);

                stack.setHoverName(stack.getItem().getName(stack).copy().withStyle(s -> s.withItalic(false)));

                changed++;
            }
        }

        final int result = changed;
        ctx.getSource().sendSuccess(
                () -> Component.literal("Cleared reforges to " + result + " item(s)."), true);

        return changed;
    }
}
