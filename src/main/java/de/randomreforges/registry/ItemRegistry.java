package de.randomreforges.registry;

import de.randomreforges.RandomReforges;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ItemRegistry {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, RandomReforges.MODID);

    public static final RegistryObject<Item> REFORGE_CORE =
            ITEMS.register("reforge_core", () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> SOULLESS_REFORGE_CORE =
            ITEMS.register("soulless_reforge_core", () -> new Item(new Item.Properties()));
}
