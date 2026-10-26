package de.randomreforges.reforge;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import de.randomreforges.RandomReforges;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

public class ReforgeManager {

    private static final Map<String, Reforge> REFORGES = new HashMap<>();
    private static final Map<String, Reforge> DEFAULT_REFORGES = new HashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static boolean ignoreDefaultReforges = false;
    private static int     rerollCostLevels      = 5;
    private static String  rerollCostItem        = "";
    private static int     rerollCostItemAmount  = 0;
    private static double  perfectChancePercent  = 0;
    private static int     perfectBonusPercent   = 10;
    private static boolean confirmDelete         = true;

    //*** Hardmode ******************************************************
    private static boolean hardmodeEnabled              = false;
    private static double  hardmodeLockChance           = 20.0;
    private static double  hardmodeDebuffMinPercent     = -50.0;
    private static double  hardmodeDebuffMaxPercent     = 50.0;
    private static boolean hardmodeShowPercentageChange = false;
    private static boolean hardmodeShowRange            = true;

    //*** Reused empty array – avoids allocating new double[0] on every call when hardmode is off ******************************************************
    private static final double[] EMPTY_MULTIPLIERS = new double[0];

    //*** Locked reforges (blacklist – won't appear as new reforges, existing items keep their attributes) ******************************************************
    private static final Set<String> lockedReforges = new HashSet<>();

    public static void load() {
        REFORGES.clear();
        DEFAULT_REFORGES.clear();

        Path dir = FMLPaths.CONFIGDIR.get().resolve("randomreforges");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            RandomReforges.LOGGER.error("Failed to create config directory", e);
            return;
        }

        loadSettings(dir);

        if (!ignoreDefaultReforges) {
            loadDefaultFromJar();
        } else {
            RandomReforges.LOGGER.info("[RandomReforges] Skipping reforges.json (Ignore Default Reforges is enabled)");
        }

        //Load custom_reforges.json (config/randomreforges/custom_reforges.json)
        loadCustomFromFile(dir.resolve("custom_reforges.json"));
    }

    private static void loadDefaultFromJar() {
        try (InputStream in = RandomReforges.class.getResourceAsStream("/randomreforges/reforges.json")) {
            if (in == null) {
                RandomReforges.LOGGER.error("[RandomReforges] reforges.json not found inside JAR!");
                return;
            }
            try (Reader reader = new InputStreamReader(in)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                int loaded = parseReforges(root, "reforges.json");
                RandomReforges.LOGGER.info("[RandomReforges] Loaded {} reforge(s) from reforges.json (JAR)", loaded);
            }
        } catch (Exception e) {
            RandomReforges.LOGGER.error("Failed to load reforges.json from JAR", e);
        }

        DEFAULT_REFORGES.putAll(REFORGES);
    }

    private static void loadCustomFromFile(Path path) {
        if (!Files.exists(path)) {
            try (Writer w = Files.newBufferedWriter(path)) {
                w.write("{}");
                RandomReforges.LOGGER.info("[RandomReforges] Created empty custom_reforges.json");
            } catch (Exception e) {
                RandomReforges.LOGGER.error("Failed to create custom_reforges.json", e);
            }
            return;
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            int loaded = parseReforges(root, "custom_reforges.json");
            RandomReforges.LOGGER.info("[RandomReforges] Loaded {} reforge(s) from custom_reforges.json", loaded);
        } catch (Exception e) {
            RandomReforges.LOGGER.error("Failed to load custom_reforges.json - skipping!", e);
        }
    }

    //*** JSON Parser ******************************************************
    /** Parses a JsonObject of reforge entries into REFORGES. Returns count of loaded entries. */
    private static int parseReforges(JsonObject root, String sourceName) {
        int loaded = 0;
        for (var entry : root.entrySet()) {
            String id = entry.getKey();
            try {
                parseAndStoreEntry(id, entry.getValue().getAsJsonObject());
                loaded++;
            } catch (Exception e) {
                RandomReforges.LOGGER.error("Failed to parse reforge '{}' in {}", id, sourceName, e);
            }
        }
        return loaded;
    }

    //Parses a single reforge JSON entry and stores/overwrites it in REFORGES
    private static void parseAndStoreEntry(String id, JsonObject obj) {
        String name = obj.has("displayName") ? obj.get("displayName").getAsString() : id;

        List<String> appliesTo = new ArrayList<>();
        if (obj.has("appliesTo")) {
            for (JsonElement e : obj.getAsJsonArray("appliesTo")) {
                String s = e.getAsString();
                appliesTo.add(s.contains(":") || s.startsWith("#") ? s : s.toUpperCase(Locale.ROOT));
            }
        }

        List<Reforge.AttributeEntry> attrs = new ArrayList<>();
        if (obj.has("attributes")) {
            for (JsonElement e : obj.getAsJsonArray("attributes")) {
                JsonObject a = e.getAsJsonObject();

                ResourceLocation scaledBy = a.has("scaledBy")
                        ? ResourceLocation.parse(a.get("scaledBy").getAsString()) : null;
                double scaleRatio = a.has("scaleRatio")
                        ? a.get("scaleRatio").getAsDouble() : 0;
                attrs.add(new Reforge.AttributeEntry(
                        ResourceLocation.parse(a.get("attribute").getAsString()),
                        a.get("amount").getAsDouble(),
                        a.get("operation").getAsString(),
                        scaledBy,
                        scaleRatio
                ));
            }
        }

        int weight  = obj.has("weight")  ? obj.get("weight").getAsInt()    : 1;
        String comment = obj.has("comment") ? obj.get("comment").getAsString() : "";

        // custom_reforges.json entries override reforges.json if same ID (Fix E incremental updates
        // override whatever was there before, same semantics as the old full reload)
        REFORGES.put(id, new Reforge(id, name, appliesTo, attrs, weight, comment));
    }

    //*** Settings ******************************************************

    public static boolean isIgnoreDefaultReforges()               { return ignoreDefaultReforges; }
    public static void    setIgnoreDefaultReforges(boolean value) { ignoreDefaultReforges = value; }

    public static int    getRerollCostLevels()          { return rerollCostLevels; }
    public static void   setRerollCostLevels(int v)     { rerollCostLevels = Math.max(0, v); }
    public static String getRerollCostItem()            { return rerollCostItem; }
    public static void   setRerollCostItem(String v)    { rerollCostItem = v == null ? "" : v.trim(); }
    public static int    getRerollCostItemAmount()      { return rerollCostItemAmount; }
    public static void   setRerollCostItemAmount(int v) { rerollCostItemAmount = Math.max(0, v); }

    public static double getPerfectChancePercent()        { return perfectChancePercent; }
    public static void   setPerfectChancePercent(double v){ perfectChancePercent = Math.max(0, Math.min(100, v)); }
    public static int    getPerfectBonusPercent()         { return perfectBonusPercent; }
    public static void   setPerfectBonusPercent(int v)    { perfectBonusPercent = Math.max(0, v); }

    public static boolean isConfirmDelete()           { return confirmDelete; }
    public static void    setConfirmDelete(boolean v) { confirmDelete = v; }

    //*** Hardmode settings ******************************************************
    public static boolean isHardmodeEnabled()                   { return hardmodeEnabled; }
    public static void    setHardmodeEnabled(boolean v)         { hardmodeEnabled = v; }
    public static double  getHardmodeLockChance()               { return hardmodeLockChance; }
    public static void    setHardmodeLockChance(double v)       { hardmodeLockChance = Math.max(0, Math.min(100, v)); }
    public static double  getHardmodeDebuffMinPercent()         { return hardmodeDebuffMinPercent; }
    public static void    setHardmodeDebuffMinPercent(double v) { hardmodeDebuffMinPercent = v; }
    public static double  getHardmodeDebuffMaxPercent()         { return hardmodeDebuffMaxPercent; }
    public static void    setHardmodeDebuffMaxPercent(double v) { hardmodeDebuffMaxPercent = v; }
    public static boolean isHardmodeShowPercentageChange()                    { return hardmodeShowPercentageChange; }
    public static void    setHardmodeShowPercentageChange(boolean v)          { hardmodeShowPercentageChange = v; }
    public static boolean isHardmodeShowRange()                               { return hardmodeShowRange; }
    public static void    setHardmodeShowRange(boolean v)                     { hardmodeShowRange = v; }

    //*** Locked reforge helpers ******************************************************
    public static boolean isLocked(String id) { return lockedReforges.contains(id); }

    /** Returns an unmodifiable view of the currently locked reforge IDs (used for network sync). */
    public static Set<String> getLockedReforgeIds() { return Collections.unmodifiableSet(lockedReforges); }

    /** Toggles the lock state of the reforge and persists immediately to settings.json. */
    public static void toggleLock(String id) {
        if (lockedReforges.contains(id)) lockedReforges.remove(id);
        else lockedReforges.add(id);
        saveSettings();
    }

    public static void setLockedState(String id, boolean locked) {
        if (locked) lockedReforges.add(id);
        else lockedReforges.remove(id);
    }

    //*** Roll whether the next reforge is Perfect ******************************************************
    public static boolean rollPerfect() {
        if (perfectChancePercent <= 0) return false;
        return ThreadLocalRandom.current().nextDouble() * 100.0 < perfectChancePercent;
    }

    //*** Hardmode: roll whether the item becomes locked after this reforge ******************************************************
    public static boolean rollHardmodeLock() {
        if (!hardmodeEnabled || hardmodeLockChance <= 0) return false;
        return ThreadLocalRandom.current().nextDouble() * 100.0 < hardmodeLockChance;
    }

    /**
     * Hardmode: rolls an independent debuff multiplier for each attribute of the given reforge.
     * Only negative-amount attributes get a random multiplier; positive ones stay at 1.0.
     * Returns EMPTY_MULTIPLIERS when hardmode is disabled (ReforgeInstance treats this as all-1.0).
     */
    public static double[] rollDebuffMultipliers(Reforge reforge) {
        if (!hardmodeEnabled) return EMPTY_MULTIPLIERS;
        List<Reforge.AttributeEntry> attrs = reforge.getAttributes();
        double[] multipliers = new double[attrs.size()];
        for (int i = 0; i < attrs.size(); i++) {
            multipliers[i] = attrs.get(i).amount() < 0 ? rollSingleDebuffMultiplier() : 1.0;
        }
        return multipliers;
    }

    private static double rollSingleDebuffMultiplier() {
        double minMult = 1.0 + hardmodeDebuffMinPercent / 100.0;
        double maxMult = 1.0 + hardmodeDebuffMaxPercent / 100.0;
        if (minMult >= maxMult) return minMult;
        return minMult + ThreadLocalRandom.current().nextDouble() * (maxMult - minMult);
    }

    public static boolean isRerollFree() {
        boolean noLevels = rerollCostLevels <= 0;
        boolean noItem   = rerollCostItem.isEmpty() || rerollCostItemAmount <= 0;
        return noLevels && noItem;
    }

    public static void loadSettings(Path dir) {
        Path path = dir.resolve("settings.json");
        if (!Files.exists(path)) return;
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
            if (obj.has("ignoreDefaultReforges")) ignoreDefaultReforges = obj.get("ignoreDefaultReforges").getAsBoolean();
            if (obj.has("rerollCostLevels"))      rerollCostLevels      = obj.get("rerollCostLevels").getAsInt();
            if (obj.has("rerollCostItem"))        rerollCostItem        = obj.get("rerollCostItem").getAsString();
            if (obj.has("rerollCostItemAmount"))  rerollCostItemAmount  = obj.get("rerollCostItemAmount").getAsInt();
            if (obj.has("perfectChancePercent"))  perfectChancePercent  = obj.get("perfectChancePercent").getAsDouble();
            if (obj.has("perfectBonusPercent"))   perfectBonusPercent   = obj.get("perfectBonusPercent").getAsInt();
            if (obj.has("confirmDelete"))         confirmDelete         = obj.get("confirmDelete").getAsBoolean();
            //*** Hardmode ******************************************************
            if (obj.has("hardmodeEnabled"))          hardmodeEnabled          = obj.get("hardmodeEnabled").getAsBoolean();
            if (obj.has("hardmodeLockChance"))       hardmodeLockChance       = obj.get("hardmodeLockChance").getAsDouble();
            if (obj.has("hardmodeDebuffMinPercent")) hardmodeDebuffMinPercent = obj.get("hardmodeDebuffMinPercent").getAsDouble();
            if (obj.has("hardmodeDebuffMaxPercent")) hardmodeDebuffMaxPercent = obj.get("hardmodeDebuffMaxPercent").getAsDouble();
            if (obj.has("hardmodeShowPercentageChange")) hardmodeShowPercentageChange = obj.get("hardmodeShowPercentageChange").getAsBoolean();
            if (obj.has("hardmodeShowRange"))            hardmodeShowRange            = obj.get("hardmodeShowRange").getAsBoolean();
            //*** Locked reforges ******************************************************
            lockedReforges.clear();
            if (obj.has("lockedReforges"))
                for (JsonElement e : obj.getAsJsonArray("lockedReforges")) lockedReforges.add(e.getAsString());
        } catch (Exception e) {
            RandomReforges.LOGGER.error("Failed to load settings.json", e);
        }
    }

    //*** Function used by SettingsGUI to save everything to settings.json ******************************************************
    public static void saveSettings() {
        Path path = FMLPaths.CONFIGDIR.get().resolve("randomreforges").resolve("settings.json");
        JsonObject obj = new JsonObject();
        obj.addProperty("ignoreDefaultReforges", ignoreDefaultReforges);
        obj.addProperty("rerollCostLevels",      rerollCostLevels);
        obj.addProperty("rerollCostItem",        rerollCostItem);
        obj.addProperty("rerollCostItemAmount",  rerollCostItemAmount);
        obj.addProperty("perfectChancePercent",  perfectChancePercent);
        obj.addProperty("perfectBonusPercent",   perfectBonusPercent);
        obj.addProperty("confirmDelete",         confirmDelete);
        //*** Hardmode ******************************************************
        obj.addProperty("hardmodeEnabled",          hardmodeEnabled);
        obj.addProperty("hardmodeLockChance",       hardmodeLockChance);
        obj.addProperty("hardmodeDebuffMinPercent", hardmodeDebuffMinPercent);
        obj.addProperty("hardmodeDebuffMaxPercent", hardmodeDebuffMaxPercent);
        obj.addProperty("hardmodeShowPercentageChange", hardmodeShowPercentageChange);
        obj.addProperty("hardmodeShowRange",            hardmodeShowRange);
        //*** Locked reforges ******************************************************
        JsonArray lockedArr = new JsonArray();
        lockedReforges.forEach(lockedArr::add);
        obj.add("lockedReforges", lockedArr);
        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(obj, writer);
        } catch (Exception e) {
            RandomReforges.LOGGER.error("Failed to save settings.json", e);
        }
    }


    public static boolean saveCustomReforge(String id, JsonObject reforgeObj) {
        Path path = FMLPaths.CONFIGDIR.get().resolve("randomreforges").resolve("custom_reforges.json");

        JsonObject root = new JsonObject();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                root = JsonParser.parseReader(reader).getAsJsonObject();
            } catch (Exception e) {
                RandomReforges.LOGGER.error("Failed to read custom_reforges.json before saving", e);
                return false;
            }
        }

        root.add(id, reforgeObj);

        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(root, writer);
            return true;
        } catch (Exception e) {
            RandomReforges.LOGGER.error("Failed to write custom_reforges.json", e);
            return false;
        }
    }

    public static boolean deleteCustomReforge(String id) {
        Path path = FMLPaths.CONFIGDIR.get().resolve("randomreforges").resolve("custom_reforges.json");

        if (!Files.exists(path)) return false;

        JsonObject root;
        try (Reader reader = Files.newBufferedReader(path)) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            RandomReforges.LOGGER.error("Failed to read custom_reforges.json for deletion", e);
            return false;
        }

        if (!root.has(id)) return false;

        root.remove(id);

        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(root, writer);
            return true;
        } catch (Exception e) {
            RandomReforges.LOGGER.error("Failed to write custom_reforges.json after deletion", e);
            return false;
        }
    }

    //*** Queries (especially needed to remove reforges) ******************************************************
    public static Reforge getById(String id) { return REFORGES.get(id); }
    public static Collection<Reforge> getAll() { return REFORGES.values(); }

    public static Reforge getRandomApplicableReforge(ItemStack stack, EquipmentSlot slot) {
        List<Reforge> list = new ArrayList<>();
        for (Reforge r : REFORGES.values()) {
            if (lockedReforges.contains(r.getId())) continue;
            if (r.getWeight() <= 0) continue;
            if (r.appliesToSlot(slot, stack)) list.add(r);
        }
        if (list.isEmpty()) return null;

        // For-loop instead of stream to avoid IntStream allocation on every equip event
        int total = 0;
        for (Reforge r : list) total += r.getWeight();
        int pick  = ThreadLocalRandom.current().nextInt(total);
        for (Reforge r : list) {
            pick -= r.getWeight();
            if (pick < 0) return r;
        }
        return list.get(0);
    }

    //*****************************************************************************************************************************************************************
    // Multiplayer sync - serialization / application of the full authoritative state
    //*****************************************************************************************************************************************************************

    /*****************************************************************************************************************************************************************
    Serializes a single loaded Reforge back into the same JSON shape used by reforges.json / custom_reforges.json.
    Used to build the full-state sync packet sent to clients in multiplayer, and (Fix E) for single-reforge diffs.
    *****************************************************************************************************************************************************************/
    private static JsonObject reforgeToJson(Reforge r) {
        JsonObject obj = new JsonObject();
        obj.addProperty("displayName", r.getName());

        JsonArray appliesTo = new JsonArray();
        for (String g : r.getAppliesToGroups()) appliesTo.add(g);
        for (Item item : r.getAppliesToItems()) {
            ResourceLocation rl = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
            if (rl != null) appliesTo.add(rl.toString());
        }
        for (TagKey<Item> tag : r.getAppliesToTags())
            appliesTo.add("#" + tag.location());
        for (Item item : r.getExcludeItems()) {
            ResourceLocation rl = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
            if (rl != null) appliesTo.add("!" + rl);
        }
        for (String g : r.getExcludeGroups()) appliesTo.add("!" + g);
        for (TagKey<Item> tag : r.getExcludeTags())
            appliesTo.add("!#" + tag.location());
        obj.add("appliesTo", appliesTo);

        obj.addProperty("weight", r.getWeight());
        obj.addProperty("comment", r.getComment());

        JsonArray attrs = new JsonArray();
        for (Reforge.AttributeEntry entry : r.getAttributes()) {
            JsonObject a = new JsonObject();
            a.addProperty("attribute", entry.getAttributeId().toString());
            a.addProperty("amount",    entry.amount());
            a.addProperty("operation", entry.operation());
            if (entry.isScaled()) {
                a.addProperty("scaledBy",   entry.getScaledBy().toString());
                a.addProperty("scaleRatio", entry.getScaleRatio());
            }
            attrs.add(a);
        }
        obj.add("attributes", attrs);

        return obj;
    }

    /** Serializes every currently loaded reforge (defaults + custom) into one JSON string for network sync. */
    public static String serializeReforgesToJson() {
        JsonObject root = new JsonObject();
        for (Reforge r : REFORGES.values()) {
            root.add(r.getId(), reforgeToJson(r));
        }
        return root.toString();
    }

    /*****************************************************************************************************************************************************************
    Applies a full state sync received from the server (multiplayer). Completely replaces REFORGES and all
    settings fields - this is what makes local config get "ignored" while connected to a server, per design.
    *****************************************************************************************************************************************************************/
    public static void applySyncedState(String reforgesJson, List<String> syncedLockedReforges,
                                        boolean syncedIgnoreDefaultReforges, int syncedRerollCostLevels,
                                        String syncedRerollCostItem, int syncedRerollCostItemAmount,
                                        double syncedPerfectChancePercent, int syncedPerfectBonusPercent,
                                        boolean syncedConfirmDelete, boolean syncedHardmodeEnabled,
                                        double syncedHardmodeLockChance, double syncedHardmodeDebuffMinPercent,
                                        double syncedHardmodeDebuffMaxPercent, boolean syncedHardmodeShowPercentageChange,
                                        boolean syncedHardmodeShowRange) {
        try {
            JsonObject root = JsonParser.parseString(reforgesJson).getAsJsonObject();
            REFORGES.clear();
            parseReforges(root, "network sync");
        } catch (Exception e) {
            RandomReforges.LOGGER.error("Failed to apply synced reforges from server", e);
        }

        lockedReforges.clear();
        lockedReforges.addAll(syncedLockedReforges);

        applySyncedSettingsOnly(syncedIgnoreDefaultReforges, syncedRerollCostLevels, syncedRerollCostItem,
                syncedRerollCostItemAmount, syncedPerfectChancePercent, syncedPerfectBonusPercent,
                syncedConfirmDelete, syncedHardmodeEnabled, syncedHardmodeLockChance,
                syncedHardmodeDebuffMinPercent, syncedHardmodeDebuffMaxPercent,
                syncedHardmodeShowPercentageChange, syncedHardmodeShowRange);
    }

    public static void applySyncedSettingsOnly(boolean syncedIgnoreDefaultReforges, int syncedRerollCostLevels,
                                               String syncedRerollCostItem, int syncedRerollCostItemAmount,
                                               double syncedPerfectChancePercent, int syncedPerfectBonusPercent,
                                               boolean syncedConfirmDelete, boolean syncedHardmodeEnabled,
                                               double syncedHardmodeLockChance, double syncedHardmodeDebuffMinPercent,
                                               double syncedHardmodeDebuffMaxPercent, boolean syncedHardmodeShowPercentageChange,
                                               boolean syncedHardmodeShowRange) {
        setIgnoreDefaultReforges(syncedIgnoreDefaultReforges);
        setRerollCostLevels(syncedRerollCostLevels);
        setRerollCostItem(syncedRerollCostItem);
        setRerollCostItemAmount(syncedRerollCostItemAmount);
        setPerfectChancePercent(syncedPerfectChancePercent);
        setPerfectBonusPercent(syncedPerfectBonusPercent);
        setConfirmDelete(syncedConfirmDelete);
        setHardmodeEnabled(syncedHardmodeEnabled);
        setHardmodeLockChance(syncedHardmodeLockChance);
        setHardmodeDebuffMinPercent(syncedHardmodeDebuffMinPercent);
        setHardmodeDebuffMaxPercent(syncedHardmodeDebuffMaxPercent);
        setHardmodeShowPercentageChange(syncedHardmodeShowPercentageChange);
        setHardmodeShowRange(syncedHardmodeShowRange);
    }

    //*****************************************************************************************************************************************************************
    // incremental single-reforge updates (used by EditReforgePacket to avoid a full disk reload +
    // full reparse on every single Save/Delete, and by ClientSyncHandler to apply the resulting diff)
    //*****************************************************************************************************************************************************************

    /** Parses and stores/overwrites a single reforge in memory. Returns true on success, false if the JSON was invalid. */
    public static boolean applySingleReforgeUpdate(String id, JsonObject obj) {
        try {
            parseAndStoreEntry(id, obj);
            return true;
        } catch (Exception e) {
            RandomReforges.LOGGER.error("Failed to apply single reforge update '{}'", id, e);
            return false;
        }
    }

    /**
     * Removes a reforge from memory. If a default (JAR) definition with the same ID exists, the reforge
     * falls back to that default instead of disappearing entirely - matches the pre-Fix-E full-reload
     * behavior where custom_reforges.json entries simply "override" reforges.json entries.
     */
    public static void removeReforgeFromMemory(String id) {
        Reforge fallback = DEFAULT_REFORGES.get(id);
        if (fallback != null) {
            REFORGES.put(id, fallback);
        } else {
            REFORGES.remove(id);
        }
    }

    /** Serializes a single currently-loaded reforge to JSON, or null if it doesn't exist. Used to broadcast the fallback-to-default state after a delete. */
    public static String serializeSingleReforgeToJson(String id) {
        Reforge r = REFORGES.get(id);
        return r != null ? reforgeToJson(r).toString() : null;
    }

    /*****************************************************************************************************************************************************************
    Task: Refresh all reforged items with the edited reforge (server-side).
    Moved here from ReforgeEditorGUI (client-only) so it also runs on a dedicated server after a networked
    edit. Same logic as before: items already carrying this reforge need their attributes reapplied since
    the reforge's own attributes may have changed.
    *****************************************************************************************************************************************************************/
    public static void refreshItemsWithReforge(MinecraftServer server, String reforgeId) {
        if (server == null) return;

        for (var player : server.getPlayerList().getPlayers()) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack stack = player.getItemBySlot(slot);
                if (stack.isEmpty()) continue;

                CompoundTag tag = stack.getTag();
                if (tag == null || !tag.contains("RandomReforges")) continue;

                ReforgeInstance inst = ReforgeInstance.fromNBT(tag.getCompound("RandomReforges"));
                if (inst == null || !inst.getReforge().getId().equals(reforgeId)) continue;

                boolean isCurio = ModList.get().isLoaded("curios")
                        && de.randomreforges.curios.CuriosUtil.isCurioItem(stack);

                AttributeUtil.removeAttributes(stack);
                if (!isCurio) {
                    AttributeUtil.applyAttributes(stack, inst, player);
                }
            }

            if (ModList.get().isLoaded("curios")) {
                ScalableReforgeHandlerCuriosHelper.refreshReforgeOnCurios(player, reforgeId);
            }
        }
    }
}
