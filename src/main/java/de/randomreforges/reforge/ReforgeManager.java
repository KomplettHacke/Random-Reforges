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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import de.randomreforges.RandomReforges;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;

public class ReforgeManager {

    private static final Map<String, Reforge> REFORGES = new HashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static boolean ignoreDefaultReforges = false;
    private static int     rerollCostLevels      = 5;
    private static String  rerollCostItem        = "";  // e.g. "minecraft:diamond" or "#forge:gems"
    private static int     rerollCostItemAmount  = 0;

    public static void load() {
        REFORGES.clear();

        Path dir = FMLPaths.CONFIGDIR.get().resolve("randomreforges");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            RandomReforges.LOGGER.error("Failed to create config directory", e);
            return;
        }

        // Load settings first
        loadSettings(dir);

        // Load default reforges.json directly from JAR unless ignored
        if (!ignoreDefaultReforges) {
            loadDefaultFromJar();
        } else {
            RandomReforges.LOGGER.info("[RandomReforges] Skipping reforges.json (Ignore Default Reforges is enabled)");
        }

        // Load custom_reforges.json from filesystem
        loadCustomFromFile(dir.resolve("custom_reforges.json"));
    }

    /** Reads reforges.json directly from the JAR – no config folder copy needed. */
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
    }

    /** Reads custom_reforges.json from the config folder. Creates it if missing. */
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

    /** Parses a JsonObject of reforge entries into REFORGES. Returns count of loaded entries. */
    private static int parseReforges(JsonObject root, String sourceName) {
        int loaded = 0;
        for (var entry : root.entrySet()) {
            String id = entry.getKey();
            try {
                JsonObject obj = entry.getValue().getAsJsonObject();

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
                        // Optional scalable fields
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

                // custom_reforges.json entries override reforges.json if same ID
                REFORGES.put(id, new Reforge(id, name, appliesTo, attrs, weight, comment));
                loaded++;

            } catch (Exception e) {
                RandomReforges.LOGGER.error("Failed to parse reforge '{}' in {}", id, sourceName, e);
            }
        }
        return loaded;
    }

    // ── Settings ──────────────────────────────────────────────────────────────

    public static boolean isIgnoreDefaultReforges()              { return ignoreDefaultReforges; }
    public static void    setIgnoreDefaultReforges(boolean value) { ignoreDefaultReforges = value; }

    public static int    getRerollCostLevels()              { return rerollCostLevels; }
    public static void   setRerollCostLevels(int v)         { rerollCostLevels = Math.max(0, v); }
    public static String getRerollCostItem()                { return rerollCostItem; }
    public static void   setRerollCostItem(String v)        { rerollCostItem = v == null ? "" : v.trim(); }
    public static int    getRerollCostItemAmount()          { return rerollCostItemAmount; }
    public static void   setRerollCostItemAmount(int v)     { rerollCostItemAmount = Math.max(0, v); }

    /** Returns true if rerolling is free (levels=0 AND (item empty OR amount=0)). */
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
            if (obj.has("ignoreDefaultReforges")) {
                ignoreDefaultReforges = obj.get("ignoreDefaultReforges").getAsBoolean();
            }
            if (obj.has("rerollCostLevels")) {
                rerollCostLevels = obj.get("rerollCostLevels").getAsInt();
            }
            if (obj.has("rerollCostItem")) {
                rerollCostItem = obj.get("rerollCostItem").getAsString();
            }
            if (obj.has("rerollCostItemAmount")) {
                rerollCostItemAmount = obj.get("rerollCostItemAmount").getAsInt();
            }
        } catch (Exception e) {
            RandomReforges.LOGGER.error("Failed to load settings.json", e);
        }
    }

    public static void saveSettings() {
        Path path = FMLPaths.CONFIGDIR.get().resolve("randomreforges").resolve("settings.json");
        JsonObject obj = new JsonObject();
        obj.addProperty("ignoreDefaultReforges", ignoreDefaultReforges);
        obj.addProperty("rerollCostLevels",      rerollCostLevels);
        obj.addProperty("rerollCostItem",        rerollCostItem);
        obj.addProperty("rerollCostItemAmount",  rerollCostItemAmount);
        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(obj, writer);
        } catch (Exception e) {
            RandomReforges.LOGGER.error("Failed to save settings.json", e);
        }
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────

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

    // ── Queries ───────────────────────────────────────────────────────────────

    public static Reforge getById(String id) { return REFORGES.get(id); }
    public static Collection<Reforge> getAll() { return REFORGES.values(); }

    public static Reforge getRandomApplicableReforge(ItemStack stack, EquipmentSlot slot) {
        List<Reforge> list = new ArrayList<>();
        for (Reforge r : REFORGES.values()) {
            if (r.appliesToSlot(slot, stack)) list.add(r);
        }
        if (list.isEmpty()) return null;

        int total = list.stream().mapToInt(Reforge::getWeight).sum();
        int pick  = ThreadLocalRandom.current().nextInt(total);
        for (Reforge r : list) {
            pick -= r.getWeight();
            if (pick < 0) return r;
        }
        return list.get(0);
    }
}