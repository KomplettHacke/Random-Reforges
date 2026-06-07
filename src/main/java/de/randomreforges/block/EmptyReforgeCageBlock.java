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

    /*****************************************************************************************************************************************************************
    Block properties:
    - non-solid
    - shape gets ignored by shaders
    - does not block sunlight
    - completely transparent for light
    *****************************************************************************************************************************************************************/
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

    /*****************************************************************************************************************************************************************
    Task: Use Reforge Core to turn Empty Reforge Cage into Reforge Cage BE
    1.    Check players held item
    1.1   Case 1: no Reforge Core -> Interaction failed, skip
    1.2   Case 2: Reforge Core -> next step
    2.    Consume Reforge Core if not in creative
    3.    Replace Empty Reforge Cage with Reforge Cage BE
    *****************************************************************************************************************************************************************/
    
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {

        ItemStack held = player.getItemInHand(hand);


        if (held.is(ItemRegistry.REFORGE_CORE.get())) {

            level.playSound(null, pos,
                net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_PLACE,
                net.minecraft.sounds.SoundSource.BLOCKS,
                1.0f, 1.2f);

            level.setBlock(pos, BlockRegistry.REFORGE_CAGE.get().defaultBlockState(), 3);

            if (!player.isCreative()) {
                held.shrink(1);
            }

            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }
}
