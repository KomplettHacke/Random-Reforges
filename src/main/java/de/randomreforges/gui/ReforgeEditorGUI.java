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

/*****************************************************************************************************************************************************************
Combined GUI to edit and create reforges (prevents logic duplicates)

The player can create and edit custom reforges here.
Required inputs:
- Reforge ID                            Used to identify the reforge                                        armor_ancient
- Display Name                          The name that appears in front of the item name                     Ancient -> Ancient Netherite Chestplate
- Chance                                Weight-based system, chance that the reforge appears                1 super rare, 100000 common, 10000000 almost guaranteed
- Applies To                            Use pre-defined groups or the text field to add items               ARMOR
                                        by their ID or item tags. Use "!" to blacklist groups

Optional inputs:
- Comment                               Funny (or not funny) comment shown in the tooltip                   This armor has been made by the oldest of smiths and has
                                                                                                            been passed down through generations. It is said to have
                                                                                                            a soul of its own.
- Attributes                            Buffs or debuffs that the item gives while equipped.                <Pretty complicated, please check out README.md for more>
                                        Leave empty for plain text without effect (maybe nice
                                        for adventure maps)
*****************************************************************************************************************************************************************/

public class ReforgeEditorGUI extends Screen {

    /*****************************************************************************************************************************************************************
    Switch modes:
    null = New Reforge
    non-null = Edit Reforge
    *****************************************************************************************************************************************************************/
    private final Reforge editing;
    private final boolean isNew;

    //*** Basic Fields ******************************************************
    private EditBox idField;
    private EditBox displayNameField;
    private EditBox weightField;
    private EditBox commentField;

    //*** Applies To (appliesTo) ******************************************************
    private final Map<String, Checkbox> groupCheckboxes = new LinkedHashMap<>();
    private static final String[] GROUPS = {
            "WEAPON", "TOOL", "ARMOR", "BOOTS", "LEGGINGS",
            "CHESTPLATES", "HELMETS", "SHIELD", "SPELLBOOKS", "CURIO", "ANY"
    };
    //*** Applies To (appliesTo, text input) ******************************************************
    private EditBox customAppliesToField;

    /*****************************************************************************************************************************************************************
    Single source of truth for checkbox state.
    Populated from editing reforge in constructor, updated before every rebuild.
    init() reads this directly so checkboxes are always correct after rebuild.
    *****************************************************************************************************************************************************************/
    private final Map<String, Boolean> savedCbState = new LinkedHashMap<>();

    //*** Attribute rows ******************************************************
    private static final List<String> OPERATORS = List.of("ADD", "MULTIPLY_BASE", "MULTIPLY_TOTAL", "SCALED");

    private record AttrRowData(String attrId, String value, String operator,
                               String scaledBy, String scaleRatio) {

        AttrRowData() { this("", "", "ADD", "", ""); }

        AttrRowData(String id, String val, String op) { this(id, val, op, "", ""); }

        boolean isScaled() { return "SCALED".equals(operator); }
    }

    private final List<AttrRowData> attrData         = new ArrayList<>();
    private final List<EditBox>     attrIdFields     = new ArrayList<>();
    private final List<EditBox>     attrValFields    = new ArrayList<>();
    private final List<DropdownWidget> attrDropdowns = new ArrayList<>();
    private final List<EditBox>     scaledByFields   = new ArrayList<>();
    private final List<EditBox>     scaleRatioFields = new ArrayList<>();

    //*** Inner scroll (Attribute container) ******************************************************
    // Normal row height: label(9) + field(20) + gap(3) = 32
    private static final int ROW_STRIDE_NORMAL = 32;
    // Scaled row height: normal(32) + gap(2) + label(9) + field(20) + gap(3) = 66
    private static final int ROW_STRIDE_SCALED = 66;
    //Fallback for scroll delta calculations
    private static final int ROW_STRIDE        = ROW_STRIDE_NORMAL;
    private static final int VISIBLE_ROWS      = 4;

    private int attrScrollOffset = 0;
    private int attrPanelTop    = 0;
    private int attrPanelBottom = 0;
    private int attrPanelLeft   = 0;
    private int attrPanelRight  = 0;

    //*** Outer scroll (whole GUI) ******************************************************
    private int screenScrollOffset = 0;
    private int totalContentHeight = 0;

    //*** Fixed buttons at the bottom ******************************************************
    private net.minecraft.client.gui.components.Button saveButton;
    private net.minecraft.client.gui.components.Button cancelButton;
    private net.minecraft.client.gui.components.Button deleteButton; // null in New mode

    //*** Misc ******************************************************
    private String  errorMessage          = "";
    private boolean needsRebuild          = false;  // dirty flag for safe deferred rebuild
    private boolean pendingDelete         = false;  // two-click delete confirmation
    //*** Custom "Applies To" string ******************************************************
    private String initialCustomAppliesTo = "";

    private static final int LABEL_COLOR       = 0xAAAAAA;
    private static final int ERROR_COLOR       = 0xFF5555;
    private static final int FIELD_WIDTH       = 150;
    private static final int FIELD_HEIGHT      = 20;
    //*** Delete button per attribute row ******************************************************
    private static final int DEL_BTN_OFFSET_X  = 4 + FIELD_WIDTH + 70 + 130 + 4; // X offset from panel left (right of dropdown)
    private static final int DEL_BTN_W         = 18;

    /*****************************************************************************************************************************************************************
    Constructors
    *****************************************************************************************************************************************************************/

    //New Reforge
    public ReforgeEditorGUI() {
        super(Component.literal("New Reforge"));
        this.editing = null;
        this.isNew   = true;
        for (String g : GROUPS) savedCbState.put(g, false);
    }

    //Edit Reforge
    public ReforgeEditorGUI(Reforge reforge) {
        super(Component.literal("Edit Reforge"));
        this.editing = reforge;
        this.isNew   = false;

        //Pre-fill attribute rows from existing reforge
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
    

        //Pre-build custom appliesTo string from individual items, tags and exclusions
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
        // Exclusions – must be preserved so editing doesn't strip them
        for (net.minecraft.world.item.Item item : reforge.getExcludeItems()) {
            net.minecraft.resources.ResourceLocation rl =
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
            if (rl != null) customParts.add("!" + rl);
        }
        for (String grp : reforge.getExcludeGroups())
            customParts.add("!" + grp);
        for (net.minecraft.tags.TagKey<net.minecraft.world.item.Item> tag
                : reforge.getExcludeTags())
            customParts.add("!#" + tag.location());
        this.initialCustomAppliesTo = String.join(", ", customParts);

        // Pre-fill checkbox state
        for (String g : GROUPS)
            savedCbState.put(g, reforge.getAppliesToGroups().contains(g));
        }
    }

    /*****************************************************************************************************************************************************************
    Initialization
    *****************************************************************************************************************************************************************/

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

        //*** Left column (basic fields) ******************************************************
        idField = makeField(lx, topY + 14, "e.g. armor_corrupted", 64);
        if (!isNew) {
            idField.setValue(editing.getId());
            idField.setEditable(false);   // ID is immutable in edit mode
        }

        displayNameField = makeField(lx, topY + 50, "e.g. Corrupted", 64);
        weightField      = makeField(lx, topY + 86, "Default value: 100000", 10);
        weightField.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
        commentField     = makeField(lx, topY + 122, "Optional", 256);

        if (!isNew) {
            displayNameField.setValue(editing.getName());
            weightField.setValue(String.valueOf(editing.getWeight()));
            commentField.setValue(editing.getComment());
        }

        this.addRenderableWidget(idField);
        this.addRenderableWidget(displayNameField);
        this.addRenderableWidget(weightField);
        this.addRenderableWidget(commentField);

        //*** Right column ("Applies To"-checkboxes) **************************************************
        int col = 0;
        for (String group : GROUPS) {
            int x = rx + (col % 2) * 110;
            int y = topY + 14 + (col / 2) * 22;
            //single source of truth - fixed old problem of relogging on every re-ren
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

        //*** Attribute Section****************************************************
        int attrSectionY = Math.max(topY + 158, customY + 42) + 12;

        this.addRenderableWidget(Button.builder(Component.literal("+ Add Attribute"), btn -> {
            syncAttrDataFromWidgets();
            attrData.add(new AttrRowData());
            attrScrollOffset = maxAttrScroll();
            needsRebuild = true;  // deferred – safe to call from click handler
        }).pos(lx, attrSectionY).size(120, 20).build());

        //panel measurements
        int panelLeft   = lx;
        int panelTop    = attrSectionY + 28;
        int panelRight  = panelLeft + DEL_BTN_OFFSET_X + DEL_BTN_W;
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

            //*** ID ******************************************************
            EditBox idBox = makeField(panelLeft + 4, fieldY, "mod:attribute", 128);
            idBox.setValue(data.attrId());
            this.addRenderableWidget(idBox);
            attrIdFields.add(idBox);

            //*** Value ******************************************************
            EditBox valBox = new EditBox(this.font,
                    panelLeft + 4 + FIELD_WIDTH + 4, fieldY, 60, FIELD_HEIGHT, Component.empty());
            valBox.setMaxLength(16);
            valBox.setHint(Component.literal("Value").withStyle(s -> s.withColor(0x555555)));
            valBox.setFilter(s -> s.isEmpty() || s.matches("-?\\d*\\.?\\d*"));
            valBox.setValue(data.value());
            this.addRenderableWidget(valBox);
            attrValFields.add(valBox);

            //*** Dropdown widget for operators ******************************************************
            DropdownWidget dd = new DropdownWidget(
                    panelLeft + 4 + FIELD_WIDTH + 70, fieldY, 130, FIELD_HEIGHT, OPERATORS);
            dd.setSelected(data.operator());
            this.addRenderableWidget(dd);
            attrDropdowns.add(dd);

            //*** "Scaled By" (only if operator = "SCALED") ******************************************************
            EditBox scaledByBox = makeField(panelLeft + 4, fieldY + FIELD_HEIGHT + 12,
                    "e.g. minecraft:generic.armor", 128);
            scaledByBox.setValue(data.scaledBy());
            scaledByBox.visible = data.isScaled();
            this.addRenderableWidget(scaledByBox);
            scaledByFields.add(scaledByBox);

            //*** "Scale Ratio" (only if operator = "SCALED")) **************************************************
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

        //*** Buttons, fixed at the bottom **************************************************
        int fixedBtnY = this.height - 28;

        //*** Multiplayer: nur OP darf erstellen/bearbeiten/löschen - alle anderen bekommen einen
        //    reinen Ansichts-Button. Die eigentliche Durchsetzung passiert serverseitig (EditReforgePacket).
        boolean canEditNow = GUIConstructor.canEdit();

        if (!canEditNow) {
            saveButton   = null;
            deleteButton = null;
            cancelButton = Button.builder(Component.literal("Close"), btn -> this.onClose())
                    .pos(cx - 40, fixedBtnY).size(80, 20).build();
        } else if (isNew) {
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
                    pendingDelete
                        ? Component.literal("Confirm Delete").withStyle(Style.EMPTY.withColor(0xFF2222))
                        : Component.literal("Delete Reforge").withStyle(Style.EMPTY.withColor(0xFF5555)),
                    btn -> handleDeleteClick())
                    .pos(cx + 28, fixedBtnY).size(90, 20).build();
        }

           //totalContentHeight increased by 100px to use the dropdown widget without problems (no additional 100px would lead to hidden/not rendered dropdown widget)
        totalContentHeight = panelBottom + 12 + 100;
    }


    /*****************************************************************************************************************************************************************
    Attribute panel scroll helpers
    *****************************************************************************************************************************************************************/

    // Cumulative Y offsets for each row -> variable row heights possible
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

    //*** Outer scroll - activate or deactivate ******************************************************

    private boolean needsOuterScroll() {
        return totalContentHeight > this.height - 10;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        double adjustedScrollY = mouseY + screenScrollOffset;

        //set focus on inner scroll if cursor is in the attribute panel
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

    /*****************************************************************************************************************************************************************
    Sync
    Save
    Delete
    *****************************************************************************************************************************************************************/

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

        //*** Multiplayer: an den Server senden statt lokal zu speichern - nur der Server schreibt noch in
        //    custom_reforges.json. OP ist erforderlich (wird serverseitig nochmal geprüft).
        JsonObject obj = new JsonObject();
        obj.addProperty("displayName", displayName);
        JsonArray arr = new JsonArray();
        appliesTo.forEach(arr::add);
        obj.add("appliesTo",  arr);
        obj.addProperty("weight",  weight);
        obj.addProperty("comment", comment);
        obj.add("attributes", attrsJson);

        if (!GUIConstructor.canEdit()) {
            errorMessage = "Only server operators can edit reforges!";
            return;
        }

        de.randomreforges.network.NetworkHandler.CHANNEL.sendToServer(
                de.randomreforges.network.ServerBoundPackets.EditReforgePacket.save(id, obj));
        this.onClose();
    }

    /*****************************************************************************************************************************************************************
    Two-click delete confirmation.
    If confirmDelete is disabled in settings, the first click deletes immediately.
    Otherwise: first click sets pendingDelete, rebuild changes button label to "Confirm Delete".
    Second click then executes the actual delete.
    *****************************************************************************************************************************************************************/
    private void handleDeleteClick() {
        if (!de.randomreforges.reforge.ReforgeManager.isConfirmDelete() || pendingDelete) {
            pendingDelete = false;
            onDelete();
        } else {
            pendingDelete = true;
            needsRebuild = true;
        }
    }

    private void onDelete() {
        if (!GUIConstructor.canEdit()) {
            errorMessage = "Only server operators can edit reforges!";
            return;
        }

        de.randomreforges.network.NetworkHandler.CHANNEL.sendToServer(
            de.randomreforges.network.ServerBoundPackets.EditReforgePacket.delete(editing.getId()));
        this.onClose();
    }

    /*****************************************************************************************************************************************************************
    Render
    *****************************************************************************************************************************************************************/
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        int cx   = this.width / 2;
        int topY = 30;
        int lx   = cx - 190;
        int rx   = cx + 20;


        //no return, return caused flickering
        if (needsRebuild) {
            needsRebuild = false;
            rebuildWidgets();
        }

        //title doesnt move
        graphics.drawCenteredString(this.font, this.title, cx, 12, 0xFFFFFF);

        // Apply outer scroll via pose – no widget rebuild needed
        graphics.pose().pushPose();
        graphics.pose().translate(0, -screenScrollOffset, 0);
        graphics.enableScissor(0, 20, this.width, this.height - 36);

        //*** Labels ******************************************************
        graphics.drawString(this.font, "Reforge ID",            lx, topY + 2,   LABEL_COLOR);
        graphics.drawString(this.font, "Display Name",          lx, topY + 38,  LABEL_COLOR);
        graphics.drawString(this.font, "Chance",                lx, topY + 74,  LABEL_COLOR);
        graphics.drawString(this.font, "Comment",               lx, topY + 110, LABEL_COLOR);
        graphics.drawString(this.font, "Applies To",            rx, topY,       LABEL_COLOR);

        int cbRows  = (int) Math.ceil(GROUPS.length / 2.0);
        int customY = topY + 14 + cbRows * 22 + 8;
        graphics.drawString(this.font, "Custom (items / tags):", rx, customY,   LABEL_COLOR);

        //divider between text inputs and "Applies To"-checkboxes
        graphics.fill(cx + 10, topY, cx + 11, topY + 175, 0x44FFFFFF);

        //*** Attribute panel ******************************************************
        GUIConstructor.drawPanelBorder(graphics, attrPanelLeft, attrPanelTop, attrPanelRight + 10, attrPanelBottom);
        GUIConstructor.renderPanelScrollbar(graphics, attrPanelTop, attrPanelBottom,
                attrPanelRight + 10, attrScrollOffset, totalAttrContentHeight());

        //Show/hide widgets based on visibility within panel, and detect Scaled toggle
        int[] rowOffsets = computeRowOffsets();
        for (int i = 0; i < attrIdFields.size(); i++) {
            int fieldY  = attrPanelTop + rowOffsets[i] - attrScrollOffset + 10;
            
            boolean inView = fieldY >= attrPanelTop - FIELD_HEIGHT && fieldY < attrPanelBottom;

            attrIdFields.get(i).visible     = inView;
            attrValFields.get(i).visible    = inView;
            attrDropdowns.get(i).visible    = inView;
            //Fixed not rendering input fields of second row (scaled) if the first row is not in the container:
            //Scaled fields get their own visibility check based on their own Y position, so they remain visible even when the main row has scrolled above the panel top.
            int scaledFieldY = fieldY + FIELD_HEIGHT + 12;
            boolean scaledAndVisible = attrData.get(i).isScaled() &&
                    scaledFieldY >= attrPanelTop - FIELD_HEIGHT && scaledFieldY < attrPanelBottom;
            scaledByFields.get(i).visible   = scaledAndVisible;
            scaleRatioFields.get(i).visible  = scaledAndVisible;

            //Detect SCALED operator change -> rebuild to hide/show extra fields
            if (attrDropdowns.get(i).getSelected().equals("SCALED") != attrData.get(i).isScaled()) {
                syncAttrDataFromWidgets();
                needsRebuild = true;
            }
        }

        super.render(graphics, mouseX, mouseY, partialTick);
        if (attrPanelBottom > attrPanelTop)
            graphics.enableScissor(
                    attrPanelLeft,
                    attrPanelTop    - screenScrollOffset,
                    attrPanelRight  + 10,
                    attrPanelBottom - screenScrollOffset);

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
            //Hide "Operator" label if any dropdown is open and overlaps this row - rendering the dropdown widget on top of the text didnt work for some reason
            boolean operatorLabelHidden = attrDropdowns.stream().anyMatch(dd -> {
                if (!dd.isOpen()) return false;
                int ddBottom = dd.getY() + dd.getHeight() + dd.getOptions().size() * DropdownWidget.OPTION_HEIGHT;
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

            //*** Delete button (remove attribute row) ******************************************************
            int delBtnX = attrPanelLeft + DEL_BTN_OFFSET_X;
            int delBtnY = rowTop + 10;
            boolean delHov = mouseX >= delBtnX && mouseX <= delBtnX + DEL_BTN_W
                    && (mouseY + screenScrollOffset) >= delBtnY
                    && (mouseY + screenScrollOffset) <= delBtnY + FIELD_HEIGHT;
            graphics.fill(delBtnX - 1, delBtnY - 1, delBtnX + DEL_BTN_W + 1, delBtnY + FIELD_HEIGHT + 1, 0xFF666666);
            graphics.fill(delBtnX,     delBtnY,     delBtnX + DEL_BTN_W,     delBtnY + FIELD_HEIGHT,     delHov ? 0xFF772222 : 0xFF3A1A1A);
            graphics.drawCenteredString(this.font, "\u2715", delBtnX + DEL_BTN_W / 2, delBtnY + (FIELD_HEIGHT - 8) / 2, 0xFFFF5555);
        }

        if (attrPanelBottom > attrPanelTop)
            graphics.disableScissor();

        //Dropdown widget needs to be able to be rendered outside of the attribute container: disableScissor -> Render -> disableScissor again
        for (DropdownWidget dd : attrDropdowns)
            dd.renderDropdown(graphics, mouseX, mouseY);

        //*** Close outer scroll ******************************************************
        graphics.disableScissor();
        graphics.pose().popPose();
        GUIConstructor.renderOuterScrollbar(graphics, this.width, this.height, screenScrollOffset, totalContentHeight);

        //*** Fixed buttons at the bottom ******************************************************
        if (saveButton != null) saveButton.render(graphics, mouseX, mouseY, partialTick);
        cancelButton.render(graphics, mouseX, mouseY, partialTick);
        if (deleteButton != null) deleteButton.render(graphics, mouseX, mouseY, partialTick);
        if (!errorMessage.isEmpty())
            graphics.drawCenteredString(this.font, errorMessage, cx, this.height - 52, ERROR_COLOR);
    }

    /*****************************************************************************************************************************************************************
    Mouse input (LC)
    *****************************************************************************************************************************************************************/
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double adjustedY = mouseY + screenScrollOffset;
        boolean inPanel  = adjustedY >= attrPanelTop && adjustedY <= attrPanelBottom
                && mouseX >= attrPanelLeft && mouseX <= attrPanelRight + 10;

        if (saveButton   != null && saveButton.mouseClicked(mouseX, mouseY, button)) return true;
        if (cancelButton != null && cancelButton.mouseClicked(mouseX, mouseY, button)) return true;
        if (deleteButton != null && deleteButton.mouseClicked(mouseX, mouseY, button)) return true;

        //*** Delete attribute row buttons ******************************************************
        if (inPanel) {
            int[] delOffsets = computeRowOffsets();
            int delBtnX = attrPanelLeft + DEL_BTN_OFFSET_X;
            for (int i = 0; i < attrData.size(); i++) {
                int delBtnY = attrPanelTop + delOffsets[i] - attrScrollOffset + 10;
                if (mouseX >= delBtnX && mouseX <= delBtnX + DEL_BTN_W
                        && adjustedY >= delBtnY && adjustedY <= delBtnY + FIELD_HEIGHT) {
                    syncAttrDataFromWidgets();
                    attrData.remove(i);
                    needsRebuild = true;
                    return true;
                }
            }
        }

        for (DropdownWidget dd : attrDropdowns) {
            if (!dd.isOpen()) continue;
            if (dd.mouseClicked(mouseX, adjustedY, button)) {
                attrDropdowns.stream().filter(d -> d != dd).forEach(DropdownWidget::close);
                return true;
            }
        }

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

    /*****************************************************************************************************************************************************************
    Helpers
    *****************************************************************************************************************************************************************/
    private EditBox makeField(int x, int y, String hint, int maxLen) {
        EditBox box = new EditBox(this.font, x, y, FIELD_WIDTH, FIELD_HEIGHT, Component.empty());
        box.setMaxLength(maxLen);
        if (!hint.isEmpty())
            box.setHint(Component.literal(hint).withStyle(s -> s.withColor(0x555555)));
        return box;
    }

    protected void rebuildWidgets() {
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
        init();

        if (isNew) idField.setValue(savedId);
        displayNameField.setValue(savedName);
        weightField.setValue(savedW);
        commentField.setValue(savedC);
        customAppliesToField.setValue(savedCA);

        int maxScroll = Math.max(0, totalContentHeight - this.height + 10);
        screenScrollOffset = Math.max(0, Math.min(screenScrollOffset, maxScroll));
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
