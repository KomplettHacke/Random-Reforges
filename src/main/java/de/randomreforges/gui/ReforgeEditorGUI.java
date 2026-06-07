package de.randomreforges.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import de.randomreforges.reforge.Reforge;
import de.randomreforges.reforge.ReforgeManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/**
 * Combined New/Edit GUI for reforges.
 * Pass no argument to open in "New" mode, or a Reforge to open in "Edit" mode.
 */
public class ReforgeEditorGUI extends Screen {

    // ── Mode ──────────────────────────────────────────────────────────────────
    /** null = New Reforge, non-null = Edit Reforge */
    private final Reforge editing;
    private final boolean isNew;

    // ── Basic fields ──────────────────────────────────────────────────────────
    private EditBox idField;
    private EditBox displayNameField;
    private EditBox weightField;
    private EditBox commentField;

    // ── appliesTo ─────────────────────────────────────────────────────────────
    private final Map<String, Checkbox> groupCheckboxes = new LinkedHashMap<>();
    private static final String[] GROUPS = {
            "WEAPON", "TOOL", "ARMOR", "BOOTS", "LEGGINGS",
            "CHESTPLATES", "HELMETS", "SHIELD", "SPELLBOOKS", "CURIO", "ANY"
    };
    private EditBox customAppliesToField;

    /**
     * Single source of truth for checkbox state.
     * Populated from editing reforge in constructor, updated before every rebuild.
     * init() reads this directly so checkboxes are always correct after rebuild.
     */
    private final Map<String, Boolean> savedCbState = new LinkedHashMap<>();

    // ── Attribute rows ────────────────────────────────────────────────────────
    private static final List<String> OPERATORS = List.of("ADD", "MULTIPLY_BASE", "MULTIPLY_TOTAL", "SCALED");

    private record AttrRowData(String attrId, String value, String operator,
                               String scaledBy, String scaleRatio) {
        /** Blank row */
        AttrRowData() { this("", "", "ADD", "", ""); }
        /** Non-scaled shorthand */
        AttrRowData(String id, String val, String op) { this(id, val, op, "", ""); }
        /** isScaled derived from operator – no separate boolean field needed */
        boolean isScaled() { return "SCALED".equals(operator); }
    }

    private final List<AttrRowData> attrData         = new ArrayList<>();
    private final List<EditBox>     attrIdFields     = new ArrayList<>();
    private final List<EditBox>     attrValFields    = new ArrayList<>();
    private final List<DropdownWidget> attrDropdowns = new ArrayList<>();
    private final List<EditBox>     scaledByFields   = new ArrayList<>();
    private final List<EditBox>     scaleRatioFields = new ArrayList<>();

    // ── Attribute panel scroll ────────────────────────────────────────────────
    /** Normal row height: label(9) + field(20) + gap(3) = 32 */
    private static final int ROW_STRIDE_NORMAL = 32;
    /** Scaled row height: normal(32) + gap(2) + label(9) + field(20) + gap(3) = 66 */
    private static final int ROW_STRIDE_SCALED = 66;
    /** Fallback for scroll delta calculations */
    private static final int ROW_STRIDE        = ROW_STRIDE_NORMAL;
    /** How many rows are visible at once in the panel */
    private static final int VISIBLE_ROWS      = 4;

    private int attrScrollOffset = 0;
    private int attrPanelTop    = 0;
    private int attrPanelBottom = 0;
    private int attrPanelLeft   = 0;
    private int attrPanelRight  = 0;

    // ── Outer (whole-GUI) scroll ──────────────────────────────────────────────
    /** Current scroll offset for the whole GUI (used when content > screen height). */
    private int screenScrollOffset = 0;
    /** True height of all content – computed at end of init(). */
    private int totalContentHeight = 0;

    // ── Fixed bottom buttons (rendered outside the scroll pose, like HelpGUI's Close) ──
    private net.minecraft.client.gui.components.Button saveButton;
    private net.minecraft.client.gui.components.Button cancelButton;
    private net.minecraft.client.gui.components.Button deleteButton; // null in New mode

    // ── Misc ──────────────────────────────────────────────────────────────────
    private String  errorMessage          = "";
    private boolean needsRebuild          = false;  // dirty flag for safe deferred rebuild
    /** Custom appliesTo string pre-built from editing reforge items/tags. */
    private String initialCustomAppliesTo = "";

    private static final int LABEL_COLOR  = 0xAAAAAA;
    private static final int ERROR_COLOR  = 0xFF5555;
    private static final int FIELD_WIDTH  = 150;
    private static final int FIELD_HEIGHT = 20;

    // ─────────────────────────────────────────────────────────────────────────
    // Constructors
    // ─────────────────────────────────────────────────────────────────────────

    /** New Reforge mode */
    public ReforgeEditorGUI() {
        super(Component.literal("New Reforge"));
        this.editing = null;
        this.isNew   = true;
        for (String g : GROUPS) savedCbState.put(g, false);
    }

    /** Edit Reforge mode */
    public ReforgeEditorGUI(Reforge reforge) {
        super(Component.literal("Edit Reforge"));
        this.editing = reforge;
        this.isNew   = false;

        // Pre-fill attribute rows from existing reforge
        for (Reforge.AttributeEntry entry : reforge.getAttributes()) {
            boolean scaled  = entry.isScaled();
            String op       = scaled ? "SCALED" : entry.operation().toUpperCase();
            String scaledBy = scaled && entry.getScaledBy() != null
                    ? entry.getScaledBy().toString() : "";
            String ratio    = scaled ? String.valueOf(entry.getScaleRatio()) : "";
            attrData.add(new AttrRowData(
                    entry.getAttributeId().toString(),
                    String.valueOf(entry.amount()),
                    op, scaledBy, ratio
            ));
        }

        // Pre-build custom appliesTo string from individual items and tags
        List<String> customParts = new ArrayList<>();
        for (net.minecraft.world.item.Item item : reforge.getAppliesToItems()) {
            net.minecraft.resources.ResourceLocation rl =
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
            if (rl != null) customParts.add(rl.toString());
        }
        for (net.minecraft.tags.TagKey<net.minecraft.world.item.Item> tag
                : reforge.getAppliesToTags()) {
            customParts.add("#" + tag.location());
        }
        this.initialCustomAppliesTo = String.join(", ", customParts);

        // Pre-fill checkbox state
        for (String g : GROUPS)
            savedCbState.put(g, reforge.getAppliesToGroups().contains(g));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // init
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    protected void init() {
        attrIdFields.clear();
        attrValFields.clear();
        attrDropdowns.clear();
        scaledByFields.clear();
        scaleRatioFields.clear();

        int cx   = this.width / 2;
        int topY = 30;   // fixed – outer scroll applied via PoseStack in render()
        int lx   = cx - 190;
        int rx   = cx + 20;

        // ── Left column: basic fields ─────────────────────────────────────────
        idField = makeField(lx, topY + 14, "e.g. armor_corrupted", 64);
        if (!isNew) {
            idField.setValue(editing.getId());
            idField.setEditable(false);   // ID is immutable in edit mode
        }

        displayNameField = makeField(lx, topY + 50, "e.g. Corrupted", 64);
        weightField      = makeField(lx, topY + 86, "Default value: 100000", 10);
        weightField.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
        commentField     = makeField(lx, topY + 122, "Optional", 128);

        if (!isNew) {
            displayNameField.setValue(editing.getName());
            weightField.setValue(String.valueOf(editing.getWeight()));
            commentField.setValue(editing.getComment());
        }

        this.addRenderableWidget(idField);
        this.addRenderableWidget(displayNameField);
        this.addRenderableWidget(weightField);
        this.addRenderableWidget(commentField);

        // ── Right column: appliesTo ───────────────────────────────────────────
        int col = 0;
        for (String group : GROUPS) {
            int x = rx + (col % 2) * 110;
            int y = topY + 14 + (col / 2) * 22;
            // State comes from savedCbState – single source of truth
            boolean checked = savedCbState.getOrDefault(group, false);
            Checkbox cb = new Checkbox(x, y, 100, 20, Component.literal(group), checked);
            groupCheckboxes.put(group, cb);
            this.addRenderableWidget(cb);
            col++;
        }

        int cbRows  = (int) Math.ceil(GROUPS.length / 2.0);
        int customY = topY + 14 + cbRows * 22 + 8;
        customAppliesToField = makeField(rx, customY + 14, "#mod:tag, namespace:item, ...", 256);
        if (!initialCustomAppliesTo.isEmpty())
            customAppliesToField.setValue(initialCustomAppliesTo);
        this.addRenderableWidget(customAppliesToField);

        // ── Attribute section ─────────────────────────────────────────────────
        int attrSectionY = Math.max(topY + 158, customY + 42) + 12;

        this.addRenderableWidget(Button.builder(Component.literal("+ Add Attribute"), btn -> {
            syncAttrDataFromWidgets();
            attrData.add(new AttrRowData());
            attrScrollOffset = maxAttrScroll();
            needsRebuild = true;  // deferred – safe to call from click handler
        }).pos(lx, attrSectionY).size(120, 20).build());

        // Panel bounds
        int panelLeft   = lx;
        int panelTop    = attrSectionY + 28;
        int panelRight  = panelLeft + FIELD_WIDTH + 4 + 60 + 6 + 130;
        int panelBottom = panelTop + VISIBLE_ROWS * ROW_STRIDE;

        attrPanelLeft   = panelLeft;
        attrPanelTop    = panelTop;
        attrPanelBottom = panelBottom;
        attrPanelRight  = panelRight;

        attrScrollOffset = Math.max(0, Math.min(attrScrollOffset, maxAttrScroll()));

        int[] rowOffsets = computeRowOffsets();

        for (int i = 0; i < attrData.size(); i++) {
            AttrRowData data   = attrData.get(i);
            int         fieldY = panelTop + rowOffsets[i] - attrScrollOffset + 10;

            // Attribute ID
            EditBox idBox = makeField(panelLeft + 4, fieldY, "mod:attribute", 128);
            idBox.setValue(data.attrId());
            this.addRenderableWidget(idBox);
            attrIdFields.add(idBox);

            // Value
            EditBox valBox = new EditBox(this.font,
                    panelLeft + 4 + FIELD_WIDTH + 4, fieldY, 60, FIELD_HEIGHT, Component.empty());
            valBox.setMaxLength(16);
            valBox.setHint(Component.literal("Value").withStyle(s -> s.withColor(0x555555)));
            valBox.setFilter(s -> s.isEmpty() || s.matches("-?\\d*\\.?\\d*"));
            valBox.setValue(data.value());
            this.addRenderableWidget(valBox);
            attrValFields.add(valBox);

            // Operator dropdown
            DropdownWidget dd = new DropdownWidget(
                    panelLeft + 4 + FIELD_WIDTH + 70, fieldY, 130, FIELD_HEIGHT, OPERATORS);
            dd.setSelected(data.operator());
            this.addRenderableWidget(dd);
            attrDropdowns.add(dd);

            // Scaled By field (only visible when operator is SCALED)
            EditBox scaledByBox = makeField(panelLeft + 4, fieldY + FIELD_HEIGHT + 12,
                    "e.g. minecraft:generic.armor", 128);
            scaledByBox.setValue(data.scaledBy());
            scaledByBox.visible = data.isScaled();
            this.addRenderableWidget(scaledByBox);
            scaledByFields.add(scaledByBox);

            // Scale Ratio field (only visible when scaled)
            EditBox ratioBox = new EditBox(this.font,
                    panelLeft + 4 + FIELD_WIDTH + 4, fieldY + FIELD_HEIGHT + 12,
                    60, FIELD_HEIGHT, Component.empty());
            ratioBox.setMaxLength(16);
            ratioBox.setHint(Component.literal("Ratio").withStyle(s -> s.withColor(0x555555)));
            ratioBox.setFilter(s -> s.isEmpty() || s.matches("-?\\d*\\.?\\d*"));
            ratioBox.setValue(data.scaleRatio());
            ratioBox.visible = data.isScaled();
            this.addRenderableWidget(ratioBox);
            scaleRatioFields.add(ratioBox);
        }

        // ── Buttons (fixed at bottom, outside scroll – see render()) ─────────────
        int fixedBtnY = this.height - 28;

        if (isNew) {
            saveButton   = Button.builder(Component.literal("Save"), btn -> onSave())
                    .pos(cx - 52, fixedBtnY).size(50, 20).build();
            cancelButton = Button.builder(Component.literal("Cancel"), btn -> this.onClose())
                    .pos(cx + 2,  fixedBtnY).size(50, 20).build();
            deleteButton = null;
        } else {
            saveButton   = Button.builder(
                    Component.literal("Save").withStyle(Style.EMPTY.withColor(0xFFFFFF)),
                    btn -> onSave())
                    .pos(cx - 82, fixedBtnY).size(50, 20).build();
            cancelButton = Button.builder(
                    Component.literal("Cancel").withStyle(Style.EMPTY.withColor(0xFFFFFF)),
                    btn -> this.onClose())
                    .pos(cx - 27, fixedBtnY).size(50, 20).build();
            deleteButton = Button.builder(
                    Component.literal("Delete Reforge").withStyle(Style.EMPTY.withColor(0xFF5555)),
                    btn -> onDelete())
                    .pos(cx + 28, fixedBtnY).size(90, 20).build();
        }

        // Content height ends at the attr panel – buttons are fixed and not scrolled.
        // +100 px padding so the user can scroll comfortably past the last row.
        totalContentHeight = panelBottom + 12 + 100;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Attribute panel scroll helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Cumulative Y offsets for each row, accounting for variable row heights. */
    private int[] computeRowOffsets() {
        int[] offsets = new int[attrData.size() + 1];
        offsets[0] = 0;
        for (int i = 0; i < attrData.size(); i++)
            offsets[i + 1] = offsets[i] +
                    (attrData.get(i).isScaled() ? ROW_STRIDE_SCALED : ROW_STRIDE_NORMAL);
        return offsets;
    }

    private int totalAttrContentHeight() {
        int total = 0;
        for (AttrRowData row : attrData)
            total += row.isScaled() ? ROW_STRIDE_SCALED : ROW_STRIDE_NORMAL;
        return total;
    }

    private int visiblePanelHeight() { return attrPanelBottom - attrPanelTop; }
    private int maxAttrScroll()      { return Math.max(0, totalAttrContentHeight() - visiblePanelHeight()); }

    private void scrollAttr(int delta) {
        int prev = attrScrollOffset;
        attrScrollOffset = Math.max(0, Math.min(attrScrollOffset + delta, maxAttrScroll()));
        if (attrScrollOffset != prev) {
            syncAttrDataFromWidgets();
            needsRebuild = true;
        }
    }

    // ── Outer scroll ──────────────────────────────────────────────────────────

    private boolean needsOuterScroll() {
        return totalContentHeight > this.height - 10;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        double adjustedScrollY = mouseY + screenScrollOffset;

        // Attr panel gets priority
        if (mouseX >= attrPanelLeft && mouseX <= attrPanelRight
                && adjustedScrollY >= attrPanelTop && adjustedScrollY <= attrPanelBottom) {
            scrollAttr((int) (-delta * ROW_STRIDE));
            return true;
        }

        // Outer scroll – no rebuild needed
        if (needsOuterScroll()) {
            int maxScroll = Math.max(0, totalContentHeight - this.height + 10);
            screenScrollOffset = Math.max(0,
                    Math.min(screenScrollOffset + (int) (-delta * 20), maxScroll));
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Sync / Save / Delete
    // ─────────────────────────────────────────────────────────────────────────

    private void syncAttrDataFromWidgets() {
        // Guard: only sync rows that have corresponding widgets
        int syncCount = Math.min(attrData.size(), attrIdFields.size());
        for (int i = 0; i < syncCount; i++) {
            String op = attrDropdowns.get(i).getSelected();
            attrData.set(i, new AttrRowData(
                    attrIdFields.get(i).getValue(),
                    attrValFields.get(i).getValue(),
                    op,
                    scaledByFields.get(i).getValue(),
                    scaleRatioFields.get(i).getValue()));
        }
    }

    private void onSave() {
        errorMessage = "";
        syncAttrDataFromWidgets();

        String id          = isNew ? idField.getValue().trim() : editing.getId();
        String displayName = displayNameField.getValue().trim();
        String weightStr   = weightField.getValue().trim();
        String comment     = commentField.getValue().trim();

        // Validate
        if (isNew) {
            if (id.isEmpty())
                { errorMessage = "ID cannot be empty!"; return; }
            if (!id.matches("[a-z0-9_]+"))
                { errorMessage = "ID: only lowercase letters, numbers, underscores!"; return; }
            if (ReforgeManager.getById(id) != null)
                { errorMessage = "A reforge with this ID already exists!"; return; }
        }
        if (displayName.isEmpty()) { errorMessage = "Display name cannot be empty!"; return; }
        if (weightStr.isEmpty())   { errorMessage = "Weight cannot be empty!"; return; }

        int weight;
        try {
            weight = Integer.parseInt(weightStr);
            if (weight <= 0) { errorMessage = "Weight must be > 0!"; return; }
        } catch (NumberFormatException e) {
            errorMessage = "Weight must be a number!"; return;
        }

        // appliesTo
        List<String> appliesTo = new ArrayList<>();
        for (Map.Entry<String, Checkbox> e : groupCheckboxes.entrySet())
            if (e.getValue().selected()) appliesTo.add(e.getKey());
        for (String part : customAppliesToField.getValue().split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) appliesTo.add(t);
        }
        if (appliesTo.isEmpty()) {
            errorMessage = "Select at least one group or enter a custom appliesTo!"; return;
        }

        // Attributes
        JsonArray attrsJson = new JsonArray();
        for (AttrRowData row : attrData) {
            String attrId = row.attrId().trim();
            String valStr = row.value().trim();
            if (attrId.isEmpty() || valStr.isEmpty()) continue;

            double amount;
            try { amount = Double.parseDouble(valStr); }
            catch (NumberFormatException e) {
                errorMessage = "Invalid value: " + valStr; return;
            }

            JsonObject a = new JsonObject();
            a.addProperty("attribute", attrId);
            a.addProperty("amount",    amount);

            if (row.isScaled()) {
                // SCALED uses ADD as the base operation in JSON
                a.addProperty("operation", "ADD");
                if (!row.scaledBy().trim().isEmpty() && !row.scaleRatio().trim().isEmpty()) {
                    if (row.scaledBy().trim().equals(attrId)) {
                        errorMessage = "\"Scaled By\" cannot be the same attribute as \"Attribute ID\"!";
                        return;
                    }
                    try {
                        double ratio = Double.parseDouble(row.scaleRatio().trim());
                        a.addProperty("scaledBy",   row.scaledBy().trim());
                        a.addProperty("scaleRatio", ratio);
                    } catch (NumberFormatException e) {
                        errorMessage = "Invalid scale ratio: " + row.scaleRatio(); return;
                    }
                }
            } else {
                a.addProperty("operation", row.operator());
            }

            attrsJson.add(a);
        }

        // Build and save
        JsonObject obj = new JsonObject();
        obj.addProperty("displayName", displayName);
        JsonArray arr = new JsonArray();
        appliesTo.forEach(arr::add);
        obj.add("appliesTo",  arr);
        obj.addProperty("weight",  weight);
        obj.addProperty("comment", comment);
        obj.add("attributes", attrsJson);

        if (ReforgeManager.saveCustomReforge(id, obj)) {
            ReforgeManager.load();
            refreshItemsWithReforge(id);
            this.onClose();
        } else {
            errorMessage = "Failed to save! Check the logs.";
        }
    }

    private void onDelete() {
        if (ReforgeManager.deleteCustomReforge(editing.getId())) {
            ReforgeManager.load();
            this.onClose();
        } else {
            errorMessage = "Cannot delete: only custom reforges can be deleted!";
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Render
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        int cx   = this.width / 2;
        int topY = 30;
        int lx   = cx - 190;
        int rx   = cx + 20;

        // Flush deferred rebuild (safe to do here, outside widget iteration)
        // No return after rebuild – continue rendering in the same frame to avoid flicker
        if (needsRebuild) {
            needsRebuild = false;
            rebuildWidgets();
        }

        // Title stays fixed (not scrolled)
        graphics.drawCenteredString(this.font, this.title, cx, 12, 0xFFFFFF);

        // Apply outer scroll via pose – no widget rebuild needed
        graphics.pose().pushPose();
        graphics.pose().translate(0, -screenScrollOffset, 0);
        graphics.enableScissor(0, 20, this.width, this.height - 36);

        // ── Labels ────────────────────────────────────────────────────────────
        graphics.drawString(this.font, "Reforge ID",            lx, topY + 2,   LABEL_COLOR);
        graphics.drawString(this.font, "Display Name",          lx, topY + 38,  LABEL_COLOR);
        graphics.drawString(this.font, "Chance",                lx, topY + 74,  LABEL_COLOR);
        graphics.drawString(this.font, "Comment",               lx, topY + 110, LABEL_COLOR);
        graphics.drawString(this.font, "Applies To",            rx, topY,       LABEL_COLOR);

        int cbRows  = (int) Math.ceil(GROUPS.length / 2.0);
        int customY = topY + 14 + cbRows * 22 + 8;
        graphics.drawString(this.font, "Custom (items / tags):", rx, customY,   LABEL_COLOR);

        // Column divider
        graphics.fill(cx + 10, topY, cx + 11, topY + 175, 0x44FFFFFF);

        // ── Attribute panel ───────────────────────────────────────────────────
        drawPanelBorder(graphics);
        renderAttrScrollbar(graphics);

        // Show/hide widgets based on visibility within panel, and detect Scaled toggle
        int[] rowOffsets = computeRowOffsets();
        for (int i = 0; i < attrIdFields.size(); i++) {
            int fieldY  = attrPanelTop + rowOffsets[i] - attrScrollOffset + 10;
            // Relaxed: show field if it starts before panel bottom (scissor clips overflow)
            // Fixes rows after SCALED rows being incorrectly hidden
            boolean inView = fieldY >= attrPanelTop - FIELD_HEIGHT && fieldY < attrPanelBottom;

            attrIdFields.get(i).visible     = inView;
            attrValFields.get(i).visible    = inView;
            attrDropdowns.get(i).visible    = inView;

            // Scaled fields get their own visibility check based on their own Y position,
            // so they remain visible even when the main row has scrolled above the panel top.
            int scaledFieldY = fieldY + FIELD_HEIGHT + 12;
            boolean scaledAndVisible = attrData.get(i).isScaled() &&
                    scaledFieldY >= attrPanelTop - FIELD_HEIGHT && scaledFieldY < attrPanelBottom;
            scaledByFields.get(i).visible   = scaledAndVisible;
            scaleRatioFields.get(i).visible  = scaledAndVisible;

            // Detect SCALED operator change → deferred rebuild to show/hide extra fields
            if (attrDropdowns.get(i).getSelected().equals("SCALED") != attrData.get(i).isScaled()) {
                syncAttrDataFromWidgets();
                needsRebuild = true;
            }
        }

        // Render all widgets (inside pose translation)
        super.render(graphics, mouseX, mouseY, partialTick);

        // Inner scissor must be in screen space (subtract outer scroll offset)
        if (attrPanelBottom > attrPanelTop)
            graphics.enableScissor(
                    attrPanelLeft,
                    attrPanelTop    - screenScrollOffset,
                    attrPanelRight  + 10,
                    attrPanelBottom - screenScrollOffset);

        // Row labels inside the panel
        for (int i = 0; i < attrData.size(); i++) {
            int rowTop = attrPanelTop + rowOffsets[i] - attrScrollOffset;
            int stride = attrData.get(i).isScaled() ? ROW_STRIDE_SCALED : ROW_STRIDE_NORMAL;
            boolean inView = rowTop >= attrPanelTop - stride && rowTop < attrPanelBottom;
            if (!inView) continue;

            graphics.drawString(this.font, "Attribute ID",
                    attrPanelLeft + 4,                    rowTop, LABEL_COLOR);
            graphics.drawString(this.font,
                    attrData.get(i).isScaled() ? "Base Value" : "Value",
                    attrPanelLeft + 4 + FIELD_WIDTH + 4,  rowTop, LABEL_COLOR);
            // Hide "Operator" label if any dropdown is open and overlaps this row
            boolean operatorLabelHidden = attrDropdowns.stream().anyMatch(dd -> {
                if (!dd.isOpen()) return false;
                int ddBottom = dd.getY() + dd.getHeight() + dd.getOptions().size() * 14;
                return rowTop + 10 > dd.getY() + dd.getHeight() && rowTop < ddBottom;
            });
            if (!operatorLabelHidden)
                graphics.drawString(this.font, "Operator",
                        attrPanelLeft + 4 + FIELD_WIDTH + 70, rowTop, LABEL_COLOR);

            if (attrData.get(i).isScaled()) {
                graphics.drawString(this.font, "Scaled By",
                        attrPanelLeft + 4,
                        rowTop + 10 + FIELD_HEIGHT + 2, LABEL_COLOR);
                graphics.drawString(this.font, "Scale Ratio",
                        attrPanelLeft + 4 + FIELD_WIDTH + 4,
                        rowTop + 10 + FIELD_HEIGHT + 2, LABEL_COLOR);
            }
        }

        if (attrPanelBottom > attrPanelTop)
            graphics.disableScissor();

        // Dropdown lists rendered AFTER disableScissor so they appear on top of panel border
        for (DropdownWidget dd : attrDropdowns)
            dd.renderDropdown(graphics, mouseX, mouseY);

        // ── Close outer scroll transform ──────────────────────────────────────
        graphics.disableScissor();
        graphics.pose().popPose();

        // Outer scrollbar and error message rendered in screen space (after popPose)
        renderOuterScrollbar(graphics);
        // Dark footer strip – prevents scrolled content from visually colliding with buttons
        //graphics.fill(0, this.height - 36, this.width, this.height, 0xC0101010);
        // Fixed buttons – always at the bottom of the screen, independent of scroll
        saveButton.render(graphics, mouseX, mouseY, partialTick);
        cancelButton.render(graphics, mouseX, mouseY, partialTick);
        if (deleteButton != null) deleteButton.render(graphics, mouseX, mouseY, partialTick);
        if (!errorMessage.isEmpty())
            graphics.drawCenteredString(this.font, errorMessage, cx, this.height - 52, ERROR_COLOR);
    }

    private void renderOuterScrollbar(GuiGraphics g) {
        if (!needsOuterScroll()) return;
        int maxScroll = Math.max(1, totalContentHeight - this.height + 10);
        int x      = this.width - 6;
        int trackH = this.height;
        int thumbH = Math.max(20, trackH * this.height / totalContentHeight);
        int thumbY = (int) ((long) screenScrollOffset * (trackH - thumbH) / maxScroll);
        g.fill(x, 0,      x + 4, this.height,    0x44FFFFFF);
        g.fill(x, thumbY, x + 4, thumbY + thumbH, 0xAAFFFFFF);
    }

    private void drawPanelBorder(GuiGraphics g) {
        if (attrPanelBottom <= attrPanelTop) return;
        int r = attrPanelRight + 10;
        g.fill(attrPanelLeft - 1, attrPanelTop - 1, r + 1, attrPanelTop,           0x88FFFFFF);
        g.fill(attrPanelLeft - 1, attrPanelBottom,  r + 1, attrPanelBottom + 1,    0x88FFFFFF);
        g.fill(attrPanelLeft - 1, attrPanelTop,     attrPanelLeft, attrPanelBottom, 0x88FFFFFF);
        g.fill(r,                 attrPanelTop,     r + 1,         attrPanelBottom, 0x88FFFFFF);
    }

    private void renderAttrScrollbar(GuiGraphics g) {
        int total   = totalAttrContentHeight();
        int visible = visiblePanelHeight();
        if (total <= visible || attrPanelBottom <= attrPanelTop) return;

        int x      = attrPanelRight + 13;
        int trackH = attrPanelBottom - attrPanelTop;
        int thumbH = Math.max(12, trackH * visible / total);
        int thumbY = attrPanelTop + (attrScrollOffset * (trackH - thumbH)) / maxAttrScroll();

        g.fill(x, attrPanelTop, x + 4, attrPanelBottom, 0x44FFFFFF);
        g.fill(x, thumbY,       x + 4, thumbY + thumbH,  0xAAFFFFFF);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Mouse input
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Adjust Y by outer scroll so widgets at their true positions get correct hits
        double adjustedY = mouseY + screenScrollOffset;
        boolean inPanel  = adjustedY >= attrPanelTop && adjustedY <= attrPanelBottom
                && mouseX >= attrPanelLeft && mouseX <= attrPanelRight + 10;

        // Fixed buttons use unadjusted screen-space Y (they are not inside the scroll pose)
        if (saveButton   != null && saveButton.mouseClicked(mouseX, mouseY, button)) return true;
        if (cancelButton != null && cancelButton.mouseClicked(mouseX, mouseY, button)) return true;
        if (deleteButton != null && deleteButton.mouseClicked(mouseX, mouseY, button)) return true;

        // Open dropdowns get priority: their option list may extend outside the panel,
        // so handle them before the inPanel check to avoid accidentally closing them.
        for (DropdownWidget dd : attrDropdowns) {
            if (!dd.isOpen()) continue;
            if (dd.mouseClicked(mouseX, adjustedY, button)) {
                attrDropdowns.stream().filter(d -> d != dd).forEach(DropdownWidget::close);
                return true;
            }
        }

        // Closed dropdowns: only open them for clicks inside the panel.
        for (DropdownWidget dd : attrDropdowns) {
            if (dd.isOpen()) continue; // already handled above
            if (!inPanel) { dd.close(); continue; }
            if (dd.mouseClicked(mouseX, adjustedY, button)) {
                attrDropdowns.stream().filter(d -> d != dd).forEach(DropdownWidget::close);
                return true;
            }
        }

        if (!inPanel) {
            for (EditBox b : attrIdFields)  b.setFocused(false);
            for (EditBox b : attrValFields) b.setFocused(false);
        }
        attrDropdowns.forEach(DropdownWidget::close);
        return super.mouseClicked(mouseX, adjustedY, button);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────
    private EditBox makeField(int x, int y, String hint, int maxLen) {
        EditBox box = new EditBox(this.font, x, y, FIELD_WIDTH, FIELD_HEIGHT, Component.empty());
        box.setMaxLength(maxLen);
        if (!hint.isEmpty())
            box.setHint(Component.literal(hint).withStyle(s -> s.withColor(0x555555)));
        return box;
    }

    protected void rebuildWidgets() {
        // Sync attr rows and checkbox state into their persistent stores before clearing
        syncAttrDataFromWidgets();
        if (!groupCheckboxes.isEmpty())
            groupCheckboxes.forEach((k, v) -> savedCbState.put(k, v.selected()));

        String savedId   = idField              != null ? idField.getValue()              : "";
        String savedName = displayNameField     != null ? displayNameField.getValue()     : "";
        String savedW    = weightField          != null ? weightField.getValue()          : "";
        String savedC    = commentField         != null ? commentField.getValue()         : "";
        String savedCA   = customAppliesToField != null ? customAppliesToField.getValue() : "";

        clearWidgets();
        groupCheckboxes.clear();
        init();  // reads savedCbState and attrData directly

        if (isNew) idField.setValue(savedId);
        displayNameField.setValue(savedName);
        weightField.setValue(savedW);
        commentField.setValue(savedC);
        customAppliesToField.setValue(savedCA);

        // Clamp scroll offset in case content height changed after rebuild
        int maxScroll = Math.max(0, totalContentHeight - this.height + 10);
        screenScrollOffset = Math.max(0, Math.min(screenScrollOffset, maxScroll));
    }

    /*****************************************************************************************************************************************************************
    Task: Refresh all reforged items with the edited reforge

    Problem used to be that the reforges changed in the tooltip (ReforgeManager.load()) but the attributes didnt get applied without applying the reforge again (/reforge, Reforge Cage).
    1. Iterate through every players inventory and check for the items with the reforge ID.
    2. If item found
    2.1 Remove attributes
    2.2 Apply attributes (CuriosAPI handles the Curios items)
    *****************************************************************************************************************************************************************/
    private void refreshItemsWithReforge(String reforgeId) {
        if (this.minecraft == null) return;
        var server = this.minecraft.getSingleplayerServer();
        if (server == null) return;

        for (var player : server.getPlayerList().getPlayers()) {
            for (var slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                net.minecraft.world.item.ItemStack stack = player.getItemBySlot(slot);
                if (stack.isEmpty()) continue;

                net.minecraft.nbt.CompoundTag tag = stack.getTag();
                if (tag == null || !tag.contains("RandomReforges")) continue;

                de.randomreforges.reforge.ReforgeInstance inst =
                        de.randomreforges.reforge.ReforgeInstance.fromNBT(tag.getCompound("RandomReforges"));
                if (inst == null || !inst.getReforge().getId().equals(reforgeId)) continue;

                boolean isCurio = net.minecraftforge.fml.ModList.get().isLoaded("curios")
                        && de.randomreforges.curios.CuriosUtil.isCurioItem(stack);

                de.randomreforges.reforge.AttributeUtil.removeAttributes(stack);
                if (!isCurio) {
                    de.randomreforges.reforge.AttributeUtil.applyAttributes(stack, inst, player);
                }
            }
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
