package de.randomreforges.block;

import de.randomreforges.registry.BlockRegistry;
import de.randomreforges.registry.ItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;





public class EmptyReforgeCageBlock extends Block {

    public EmptyReforgeCageBlock(Properties properties) {
        super(properties);
    }

    //non-solid block
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
                                 Player player, InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {

        ItemStack held = player.getItemInHand(hand);

        //holding reforge core?
        if (held.is(ItemRegistry.REFORGE_CORE.get())) {

            level.playSound(null, pos,
                net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_PLACE,
                net.minecraft.sounds.SoundSource.BLOCKS,
                1.0f, 1.2f);

            //replace block
            level.setBlock(pos, BlockRegistry.REFORGE_CAGE.get().defaultBlockState(), 3);

            //delete 1 item
            if (!player.isCreative()) {
                held.shrink(1);
            }

            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }
}
