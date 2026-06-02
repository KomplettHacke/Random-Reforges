package de.randomreforges.reforge;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraftforge.fml.ModList;

public class Reforge {

    private final String id;
    private final String displayName;
    private final List<String> appliesToGroups;
    private final List<Item> appliesToItems;
    private final List<TagKey<Item>> appliesToTags;
    private final List<Item> excludeItems;
    private final List<String> excludeGroups;
    private final List<TagKey<Item>> excludeTags;
    private final List<AttributeEntry> attributes;
    private final int weight;
    private final String comment;
    public List<String> getAppliesToGroups() { return appliesToGroups; }

    private static final TagKey<Item> SPELLBOOK_TAG = ItemTags.create(new ResourceLocation("curios", "spellbook"));

    public Reforge(String id, String displayName, List<String> appliesTo, List<AttributeEntry> attributes, int weight, String comment) {

        this.id = id;
        this.displayName = displayName == null ? id : displayName;
        this.attributes = attributes == null ? List.of() : List.copyOf(attributes);
        this.weight = Math.max(0, weight);
        this.comment = comment == null ? "" : comment;

        List<String> groups = new ArrayList<>();
        List<Item> items = new ArrayList<>();
        List<TagKey<Item>> tags = new ArrayList<>();
        List<Item> excludes = new ArrayList<>();
        List<String> exGroups = new ArrayList<>();
        List<TagKey<Item>> exTags = new ArrayList<>();
        


        if (appliesTo != null) {
            for (String s : appliesTo) {
                //exclude stuff with !...
                if (s.startsWith("!")) {
                    String ex = s.substring(1);
                    if (ex.startsWith("#")) {
                        //exclude tags
                        exTags.add(ItemTags.create(new ResourceLocation(ex.substring(1))));
                    } else if (ex.contains(":")) {
                        //exclude single items
                        Item it = BuiltInRegistries.ITEM.get(ResourceLocation.parse(ex));
                        if (it != null) excludes.add(it);
                    } else {
                        
                        exGroups.add(ex.toUpperCase(Locale.ROOT));
                    }
                    continue;
                }
                if (s.startsWith("#")) {
                    //tags
                    tags.add(ItemTags.create(new ResourceLocation(s.substring(1))));
                    continue;
                }
                if (s.contains(":")) {
                    //single item
                    Item it = BuiltInRegistries.ITEM.get(ResourceLocation.parse(s));
                    if (it != null) {
                        items.add(it);
                        continue;
                    }
                }
                groups.add(s.toUpperCase(Locale.ROOT));
            }
        }

        this.appliesToGroups = List.copyOf(groups);
        this.appliesToItems = List.copyOf(items);
        this.appliesToTags = List.copyOf(tags);
        this.excludeItems = List.copyOf(excludes);
        this.excludeGroups = List.copyOf(exGroups);
        this.excludeTags = List.copyOf(exTags);
    }

    public String getId() { return id; }
    public String getName() { return displayName; }
    public List<AttributeEntry> getAttributes() { return attributes; }
    public int getWeight() { return weight; }
    public String getComment() { return comment; }
    public List<Item> getAppliesToItems()  { return appliesToItems; }
    public List<TagKey<Item>> getAppliesToTags()    { return appliesToTags; }
    public List<Item> getExcludeItems()    { return excludeItems; }
    public List<String> getExcludeGroups() { return excludeGroups; }


    public boolean appliesToSlot(EquipmentSlot slot, ItemStack stack) {

        if (appliesToGroups.contains("ANY")) {
            return (
                matchesGroup("WEAPON", slot, stack) ||
                matchesGroup("ARMOR", slot, stack) ||
                matchesGroup("TOOL", slot, stack) ||
                matchesGroup("BOOTS", slot, stack) ||
                matchesGroup("HELMETS", slot, stack) ||
                matchesGroup("CHESTPLATES", slot, stack) ||
                matchesGroup("LEGGINGS", slot, stack) ||
                matchesGroup("SPELLBOOKS", slot, stack) ||
                matchesGroup("SHIELD", slot, stack) ||
                matchesGroup("CURIO", slot, stack)
            );
        }

        for (Item ex : excludeItems)
            if (stack.getItem() == ex) return false;

        for (TagKey<Item> tag : excludeTags)
            if (stack.is(tag)) return false;

        for (String grp : excludeGroups)
            if (matchesGroup(grp, slot, stack)) return false;

        for (Item it : appliesToItems)
            if (stack.getItem() == it) return true;

        for (TagKey<Item> tag : appliesToTags)
            if (stack.is(tag)) return true;

        for (String grp : appliesToGroups)
            if (matchesGroup(grp, slot, stack)) return true;

        return false;
    }

    private boolean matchesGroup(String group, EquipmentSlot slot, ItemStack stack) {

        return switch (group) {

            case "WEAPON" -> stack.getItem() instanceof SwordItem ||
                             stack.getItem() instanceof AxeItem ||
                             stack.getItem() instanceof TridentItem;

            case "ARMOR" -> stack.getItem() instanceof ArmorItem;

            case "BOOTS" -> stack.getItem() instanceof ArmorItem &&
                            ((ArmorItem) stack.getItem()).getEquipmentSlot() == EquipmentSlot.FEET;

            case "HELMETS" -> stack.getItem() instanceof ArmorItem &&
                            ((ArmorItem) stack.getItem()).getEquipmentSlot() == EquipmentSlot.HEAD;

            case "CHESTPLATES" -> stack.getItem() instanceof ArmorItem &&
                            ((ArmorItem) stack.getItem()).getEquipmentSlot() == EquipmentSlot.CHEST;

            case "LEGGINGS" -> stack.getItem() instanceof ArmorItem &&
                            ((ArmorItem) stack.getItem()).getEquipmentSlot() == EquipmentSlot.LEGS;

            case "TOOL" -> stack.getItem() instanceof PickaxeItem ||
                           stack.getItem() instanceof ShovelItem ||
                           stack.getItem() instanceof HoeItem ||
                           stack.getItem() instanceof AxeItem;

            case "SPELLBOOKS" -> stack.is(SPELLBOOK_TAG);

            case "SHIELD" -> stack.getItem() == Items.SHIELD;

            case "CURIO" -> ModList.get().isLoaded("curios") &&
                            de.randomreforges.curios.CuriosUtil.isCurioItem(stack) &&
                            !stack.is(SPELLBOOK_TAG);

            default -> false;
        };
    }

    public static class AttributeEntry {
        private final ResourceLocation attributeId;
        private final double amount;
        private final String operation;
        // Scalable reforge fields – both null/0 means static amount
        private final ResourceLocation scaledBy;
        private final double           scaleRatio;
        private Attribute resolved;
        private Attribute resolvedScaleSource;

        /** Static (non-scaled) constructor */
        public AttributeEntry(ResourceLocation id, double amount, String op) {
            this(id, amount, op, null, 0);
        }

        /** Scalable constructor */
        public AttributeEntry(ResourceLocation id, double amount, String op,
                              ResourceLocation scaledBy, double scaleRatio) {
            this.attributeId  = id;
            this.amount       = amount;
            this.operation    = op == null ? "" : op;
            this.scaledBy     = scaledBy;
            this.scaleRatio   = scaleRatio;
        }

        public Attribute getAttribute() {
            if (resolved == null) resolved = BuiltInRegistries.ATTRIBUTE.get(attributeId);
            return resolved;
        }

        /** Returns the attribute used as scaling source, or null if not scalable. */
        public Attribute getScaleSourceAttribute() {
            if (scaledBy == null) return null;
            if (resolvedScaleSource == null)
                resolvedScaleSource = BuiltInRegistries.ATTRIBUTE.get(scaledBy);
            return resolvedScaleSource;
        }

        public boolean isScaled() { return scaledBy != null && scaleRatio != 0; }

        /**
         * Computes the effective amount.
         * Scaled: amount + (entity base value of scaledBy * scaleRatio)
         * Static: just amount
         */
        public double computeAmount(net.minecraft.world.entity.LivingEntity entity) {
            if (!isScaled() || entity == null) return amount;
            Attribute src = getScaleSourceAttribute();
            if (src == null) return amount;
            var inst = entity.getAttribute(src);
            if (inst == null) return amount;
            // Use getValue() (includes all modifiers) – getBaseValue() is always 0
            // for stats like armor that come entirely from equipment.
            return amount + inst.getValue() * scaleRatio;
        }

        public ResourceLocation getAttributeId()  { return attributeId; }
        public ResourceLocation getScaledBy()     { return scaledBy; }
        public double           getScaleRatio()   { return scaleRatio; }
        public double           amount()          { return amount; }
        public String           operation()       { return operation; }
    }
}