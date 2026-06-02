package de.randomreforges.registry;

import de.randomreforges.RandomReforges;
import de.randomreforges.blockentity.ReforgeCageBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class BlockEntityRegistry {

public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, RandomReforges.MODID);

public static final RegistryObject<BlockEntityType<ReforgeCageBlockEntity>> REFORGE_CAGE =
        BLOCK_ENTITIES.register(
                "reforge_cage",
                () -> BlockEntityType.Builder.of(
                                ReforgeCageBlockEntity::new,
                                BlockRegistry.REFORGE_CAGE.get()
                        )
                        .build(null)
        );
}