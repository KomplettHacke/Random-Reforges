package de.randomreforges.blockentity;

import de.randomreforges.registry.BlockEntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class ReforgeCageBlockEntity extends BlockEntity {

    public ReforgeCageBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegistry.REFORGE_CAGE.get(), pos, state);
    }
}
