package de.randomreforges.registry;

import de.randomreforges.RandomReforges;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class CreativeTabRegistry {

        public static final DeferredRegister<CreativeModeTab> TABS =
                DeferredRegister.create(Registries.CREATIVE_MODE_TAB, RandomReforges.MODID);

        public static final RegistryObject<CreativeModeTab> RANDOM_REFORGES_TAB =
                TABS.register("randomreforges_tab", () -> CreativeModeTab.builder()
                        .title(Component.literal("Random Reforges"))
                        .icon(() -> new ItemStack(ItemRegistry.REFORGE_CORE.get()))
                        .displayItems((params, output) -> {
                                output.accept(ItemRegistry.SOULLESS_REFORGE_CORE.get());
                                output.accept(ItemRegistry.REFORGE_CORE.get());

                                output.accept(BlockRegistry.EMPTY_REFORGE_CAGE_ITEM.get());
                                output.accept(BlockRegistry.REFORGE_CAGE_ITEM.get());
                        })
                        .build()
        );
}
