package de.randomreforges.reforge;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;

public class ReforgeInstance {
    private final Reforge reforge;
    public ReforgeInstance(Reforge reforge) {this.reforge = reforge;}
    public Reforge getReforge() {return reforge;}
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", reforge.getId());
        return tag;
    }

    public static ReforgeInstance fromNBT(CompoundTag tag) {
        if (!tag.contains("id")) return null;

        String id = tag.getString("id");
        Reforge r = ReforgeManager.getById(id);
        if (r == null) return null;

        return new ReforgeInstance(r);
    }


    public Component getDisplayName(ItemStack stack) {
        Component baseName = stack.getItem().getName(stack).copy().withStyle(s -> s.withItalic(false));

        //Special case: "UwU" reforge
        if (reforge.getId().equals("special_uwu")) {
            return Component.literal("UwU")
                    .withStyle(Style.EMPTY.withColor(0xFF69B4).withItalic(false));
        }

        //Normal: <ReforgeName> <OriginalItemName>
        return Component.literal(reforge.getName() + " ")
                .append(baseName)
                .withStyle(Style.EMPTY.withItalic(false));
    }

    //comment in tooltip
    public Component getCommentLine() {
        if (reforge.getComment() == null || reforge.getComment().isEmpty())
            return Component.empty();
        return Component.literal(reforge.getComment())
                .withStyle(Style.EMPTY.withColor(0x555555).withItalic(false));
    }
}