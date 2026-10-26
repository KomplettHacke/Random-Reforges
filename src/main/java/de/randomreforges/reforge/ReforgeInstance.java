package de.randomreforges.reforge;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class ReforgeInstance {
    private final Reforge  reforge;
    private final boolean  isPerfect;
    private final double[] debuffMultipliers; // one per attribute entry; empty = all 1.0

    public ReforgeInstance(Reforge reforge)                                                { this(reforge, false, new double[0]); }
    public ReforgeInstance(Reforge reforge, boolean isPerfect)                             { this(reforge, isPerfect, new double[0]); }
    public ReforgeInstance(Reforge reforge, boolean isPerfect, double[] debuffMultipliers) {
        this.reforge           = reforge;
        this.isPerfect         = isPerfect;
        this.debuffMultipliers = debuffMultipliers != null ? debuffMultipliers : new double[0];
    }

    public Reforge  getReforge()  { return reforge; }
    public boolean  isPerfect()   { return isPerfect; }

    /** Returns the debuff multiplier for the attribute at the given index (1.0 if out of bounds). */
    public double getDebuffMultiplier(int attrIndex) {
        if (attrIndex < 0 || attrIndex >= debuffMultipliers.length) return 1.0;
        return debuffMultipliers[attrIndex];
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", reforge.getId());
        if (isPerfect) tag.putBoolean("isPerfect", true);

        // Only persist if at least one multiplier deviates from 1.0
        boolean anyNonTrivial = false;
        for (double m : debuffMultipliers) {
            if (Math.abs(m - 1.0) > 0.001) { anyNonTrivial = true; break; }
        }
        if (anyNonTrivial) {
            ListTag list = new ListTag();
            for (double m : debuffMultipliers) list.add(DoubleTag.valueOf(m));
            tag.put("debuffMultipliers", list);
        }
        return tag;
    }

    public static ReforgeInstance fromNBT(CompoundTag tag) {
        if (!tag.contains("id")) return null;

        String  id = tag.getString("id");
        Reforge r  = ReforgeManager.getById(id);
        if (r == null) return null;

        boolean  isPerfect = tag.getBoolean("isPerfect");
        double[] multipliers;

        if (tag.contains("debuffMultipliers", Tag.TAG_LIST)) {
            ListTag list = tag.getList("debuffMultipliers", Tag.TAG_DOUBLE);
            multipliers = new double[list.size()];
            for (int i = 0; i < list.size(); i++) multipliers[i] = list.getDouble(i);

        } else if (tag.contains("debuffMultiplier")) {
            // Backward compat: single-multiplier format → apply to all negative attributes
            double oldMult = tag.getDouble("debuffMultiplier");
            List<Reforge.AttributeEntry> attrs = r.getAttributes();
            multipliers = new double[attrs.size()];
            for (int i = 0; i < attrs.size(); i++)
                multipliers[i] = attrs.get(i).amount() < 0 ? oldMult : 1.0;

        } else {
            multipliers = new double[0];
        }

        return new ReforgeInstance(r, isPerfect, multipliers);
    }


    public Component getDisplayName(ItemStack stack) {
        Component baseName = stack.getItem().getName(stack).copy().withStyle(s -> s.withItalic(false));

        //Special case: "UwU" reforge
        if (reforge.getId().equals("special_uwu")) {
            return Component.literal("UwU")
                    .withStyle(Style.EMPTY.withColor(0xFF69B4).withItalic(false));
        }

        //Perfect: ✦ <ReforgeName> <OriginalItemName> (gold)
        if (isPerfect) {
            return Component.literal("✦ " + reforge.getName() + " ")
                    .append(baseName)
                    .withStyle(Style.EMPTY.withColor(0xFFDD00).withItalic(false));
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
                .withStyle(Style.EMPTY.withColor(0x555555).withItalic(true));
    }
}
