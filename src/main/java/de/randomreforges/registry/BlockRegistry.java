package de.randomreforges.registry;

import de.randomreforges.RandomReforges;
import de.randomreforges.block.EmptyReforgeCageBlock;
import de.randomreforges.block.ReforgeCageBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class BlockRegistry {

        public static final DeferredRegister<Block> BLOCKS =
                DeferredRegister.create(ForgeRegistries.BLOCKS, RandomReforges.MODID);

        public static final DeferredRegister<Item> BLOCK_ITEMS =
                DeferredRegister.create(ForgeRegistries.ITEMS, RandomReforges.MODID);


        //blocks
        public static final RegistryObject<Block> EMPTY_REFORGE_CAGE =
                BLOCKS.register("empty_reforge_cage",
                        () -> new EmptyReforgeCageBlock(BlockBehaviour.Properties.of().strength(2f).requiresCorrectToolForDrops().noOcclusion()));

        public static final RegistryObject<Block> REFORGE_CAGE =
                BLOCKS.register("reforge_cage",
                        () -> new ReforgeCageBlock(BlockBehaviour.Properties.of().strength(2f).requiresCorrectToolForDrops().lightLevel(state -> 15).noOcclusion()));

        

        //block items
        public static final RegistryObject<Item> EMPTY_REFORGE_CAGE_ITEM =
                BLOCK_ITEMS.register("empty_reforge_cage",
                () -> new BlockItem(EMPTY_REFORGE_CAGE.get(), new Item.Properties()));

        public static final RegistryObject<Item> REFORGE_CAGE_ITEM =
                BLOCK_ITEMS.register("reforge_cage",
                        () -> new BlockItem(REFORGE_CAGE.get(), new Item.Properties()));



}
