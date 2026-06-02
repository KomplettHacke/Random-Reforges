package de.randomreforges.block;

import de.randomreforges.blockentity.ReforgeCageBlockEntity;
import de.randomreforges.reforge.AttributeUtil;
import de.randomreforges.reforge.Reforge;
import de.randomreforges.reforge.ReforgeInstance;
import de.randomreforges.reforge.ReforgeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.ModList;

public class ReforgeCageBlock extends BaseEntityBlock {

    public ReforgeCageBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ReforgeCageBlockEntity(pos, state);
    }

    // non-solid block
    public boolean isSolidRender(BlockState state, BlockGetter level, BlockPos pos) {
        return false;
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        return false;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 0;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand,
                                 net.minecraft.world.phys.BlockHitResult hit) {

        //logic only serverside
        if (level.isClientSide) return InteractionResult.SUCCESS;

        ItemStack held = player.getItemInHand(hand);

        //maybe delete it?
        CompoundTag tag = held.getTag();
        if (tag == null || !tag.contains("RandomReforges")) {
            return InteractionResult.PASS;
        }

        ReforgeInstance inst = ReforgeInstance.fromNBT(tag.getCompound("RandomReforges"));
        if (inst == null) {
            return InteractionResult.PASS;
        }

        // ── Cost check ────────────────────────────────────────────────────────
        int    costLevels = ReforgeManager.getRerollCostLevels();
        String costItem   = ReforgeManager.getRerollCostItem();
        int    costAmount = ReforgeManager.getRerollCostItemAmount();

        boolean needLevels = costLevels > 0;
        boolean needItem   = !costItem.isEmpty() && costAmount > 0;

        if (!player.isCreative()) {
            boolean hasLevels = !needLevels || player.experienceLevel >= costLevels;
            boolean hasItem   = !needItem   || findCostItem(player, costItem, costAmount) != null;

            if (!hasLevels || !hasItem) {
                if (!hasLevels) {
                    player.sendSystemMessage(
                        net.minecraft.network.chat.Component.literal("You have less than " + costLevels + " levels")
                            .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0xFF5555).withItalic(false))
                    );
                }
                if (!hasItem) {
                    String itemName = costItem.startsWith("#")
                        ? costItem
                        : net.minecraft.core.registries.BuiltInRegistries.ITEM
                            .get(new net.minecraft.resources.ResourceLocation(costItem))
                            .getDescription().getString();
                    player.sendSystemMessage(
                        net.minecraft.network.chat.Component.literal("You have less than " + costAmount + " of " + itemName)
                            .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0xFF5555).withItalic(false))
                    );
                }
                return InteractionResult.FAIL;
            }
        }

        var slot = AttributeUtil.detectSlot(held);
        Reforge newReforge = ReforgeManager.getRandomApplicableReforge(held, slot);
        if (newReforge == null) return InteractionResult.PASS;

        // ── Deduct costs ─────────────────────────────────────────────────────
        if (!player.isCreative()) {
            if (needLevels) player.giveExperienceLevels(-costLevels);
            if (needItem)   consumeCostItem(player, costItem, costAmount);
        }

        ReforgeInstance newInst = new ReforgeInstance(newReforge);

        // Update NBT
        tag.put("RandomReforges", newInst.serializeNBT());
        held.setTag(tag);

        // Update item name
        held.setHoverName(newInst.getDisplayName(held));

        //curios reforges have their own attribute appliance
        boolean isCurio = ModList.get().isLoaded("curios")
                && de.randomreforges.curios.CuriosUtil.isCurioItem(held);

        AttributeUtil.removeAttributes(held);
        if (!isCurio) {
            AttributeUtil.applyAttributes(held, newInst);
        }

        level.playSound(null, pos,
                net.minecraft.sounds.SoundEvents.GRINDSTONE_USE,
                net.minecraft.sounds.SoundSource.BLOCKS,
                1.0f, 1.2f);

        level.playSound(null, pos,
                net.minecraft.sounds.SoundEvents.ANVIL_LAND,
                net.minecraft.sounds.SoundSource.BLOCKS,
                1.0f, 1.0f);

        return InteractionResult.SUCCESS;
    }

    /**
     * Finds a matching ItemStack in the player's inventory.
     * Supports "namespace:item" and "#namespace:tag".
     * Returns null if the player doesn't have enough.
     */
    private net.minecraft.world.item.ItemStack findCostItem(Player player, String itemStr, int amount) {
        if (itemStr.startsWith("#")) {
            net.minecraft.tags.TagKey<net.minecraft.world.item.Item> tag =
                net.minecraft.tags.ItemTags.create(
                    new net.minecraft.resources.ResourceLocation(itemStr.substring(1)));
            int found = 0;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                net.minecraft.world.item.ItemStack s = player.getInventory().getItem(i);
                if (!s.isEmpty() && s.is(tag)) found += s.getCount();
            }
            if (found >= amount) return net.minecraft.world.item.ItemStack.EMPTY; // dummy non-null
        } else {
            net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM
                .get(new net.minecraft.resources.ResourceLocation(itemStr));
            if (item == null) return null;
            int found = 0;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                net.minecraft.world.item.ItemStack s = player.getInventory().getItem(i);
                if (!s.isEmpty() && s.is(item)) found += s.getCount();
            }
            if (found >= amount) return net.minecraft.world.item.ItemStack.EMPTY;
        }
        return null;
    }

    /** Removes the cost items from the player's inventory. */
    private void consumeCostItem(Player player, String itemStr, int amount) {
        int remaining = amount;
        for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
            net.minecraft.world.item.ItemStack s = player.getInventory().getItem(i);
            if (s.isEmpty()) continue;

            boolean matches = itemStr.startsWith("#")
                ? s.is(net.minecraft.tags.ItemTags.create(
                    new net.minecraft.resources.ResourceLocation(itemStr.substring(1))))
                : s.is(net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .get(new net.minecraft.resources.ResourceLocation(itemStr)));

            if (matches) {
                int take = Math.min(remaining, s.getCount());
                s.shrink(take);
                remaining -= take;
            }
        }
    }
}