package de.randomreforges.gui;

import java.util.ArrayList;
import java.util.List;

import de.randomreforges.reforge.ReforgeManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

public class SettingsGUI extends Screen {

        private static final int LABEL_COLOR = 0xAAAAAA;
        private static final int DESC_COLOR = 0x777777;
        private static final int HEADER_COLOR = 0xFFDD00;
        private static final int SEP_COLOR = 0x44FFFFFF;
        private static final int FIELD_HEIGHT = 20;
        private static final int TEXT_HEIGHT = 9;

        // ── Checkbox rows (General section) ──────────────────────────────────────
        // Descriptions are anchored to their checkbox's getY() in render() (not to a
        // separately-tracked 'y' variable) so they can never drift out of sync with
        // the checkbox they belong to, regardless of how init()'s layout changes.
        private static final int CHECKBOX_SIZE = 20;
        private static final int CHECKBOX_GAP = 4; // gap between checkbox bottom and its own description
        private static final int DESC_LINE_HEIGHT = TEXT_HEIGHT + 2; // line spacing within a multi-line description
        private static final int SECTION_GAP = 10; // gap after a description block before the next checkbox row

        // ── Item scroll panel ─────────────────────────────────────────────────────
        private static final int PANEL_W = 350;
        private static final int ITEM_ROW_STRIDE = 32;
        private static final int ITEM_VISIBLE_ROWS = 4;
        private static final int ITEM_FIELD_W = 200;
        private static final int ITEM_AMOUNT_W = 50;

        private int itemPanelTop, itemPanelBottom, itemPanelLeft, itemPanelRight;
        private int itemScrollOffset = 0;
        private int addItemButtonY = 0;

        // ── Fixed bottom buttons (rendered outside the scroll pose, like HelpGUI's
        // Close) ──
        private net.minecraft.client.gui.components.Button saveButton;
        private net.minecraft.client.gui.components.Button cancelButton;

        // ── Outer scroll ──────────────────────────────────────────────────────────
        private int screenScrollOffset = 0;
        private int totalContentHeight = 0;

        // ── Widgets ───────────────────────────────────────────────────────────────
        private Checkbox ignoreDefaultReforgesBox;
        private Checkbox confirmDeleteBox;
        private EditBox xpField;
        private EditBox perfectChanceField;
        private EditBox perfectBonusField;

        // ── Section 4: Hardmode ───────────────────────────────────────────────────
        private Checkbox hardmodeEnabledBox;
        private EditBox hardmodeLockChanceField;
        private EditBox hardmodeDebuffMinField;
        private EditBox hardmodeDebuffMaxField;
        private Checkbox hardmodeShowPercentageChangeBox;
        private Checkbox hardmodeShowRangeBox;
        private int hardmodeSectionY = 0;

        private final List<EditBox[]> itemRows = new ArrayList<>();
        private final List<String[]> itemRowData = new ArrayList<>();

        public SettingsGUI() {
                super(Component.literal("Settings"));
                String item = ReforgeManager.getRerollCostItem();
                int amount = ReforgeManager.getRerollCostItemAmount();
                if (!item.isEmpty() || amount > 0)
                        itemRowData.add(new String[] { item, String.valueOf(amount) });
        }

        /*****************************************************************************************************************************************************************
         * Initialization
         *****************************************************************************************************************************************************************/
        @Override
        protected void init() {
                itemRows.clear();

                int cx = this.width / 2;
                int lx = cx - PANEL_W / 2;
                int y = 40;

                y += TEXT_HEIGHT + 2;
                ignoreDefaultReforgesBox = new Checkbox(lx, y, CHECKBOX_SIZE, CHECKBOX_SIZE,
                                Component.empty(), ReforgeManager.isIgnoreDefaultReforges());
                this.addRenderableWidget(ignoreDefaultReforgesBox);
                // Leave room for this checkbox's 2-line description before the next row starts
                y += CHECKBOX_SIZE + CHECKBOX_GAP + DESC_LINE_HEIGHT * 2 + SECTION_GAP;

                confirmDeleteBox = new Checkbox(lx, y, CHECKBOX_SIZE, CHECKBOX_SIZE,
                                Component.empty(), ReforgeManager.isConfirmDelete());
                this.addRenderableWidget(confirmDeleteBox);
                // Leave room for this checkbox's 1-line description before the next section
                // starts
                y += CHECKBOX_SIZE + CHECKBOX_GAP + DESC_LINE_HEIGHT + 16;

                y += TEXT_HEIGHT + 2 + 21;

                xpField = new EditBox(this.font, lx, y + 14, 50, FIELD_HEIGHT, Component.empty());
                xpField.setMaxLength(6);
                xpField.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
                xpField.setValue(String.valueOf(ReforgeManager.getRerollCostLevels()));
                this.addRenderableWidget(xpField);
                y += FIELD_HEIGHT + TEXT_HEIGHT + TEXT_HEIGHT + 14;

                addItemButtonY = y;
                this.addRenderableWidget(Button.builder(Component.literal("+ Add Item"), btn -> {
                        syncItemRows();
                        itemRowData.add(new String[] { "", "" });
                        itemScrollOffset = maxItemScroll();
                        rebuildWidgets();
                })
                                .pos(lx - 1, addItemButtonY).size(80, 18).build());
                y += 26;

                itemPanelLeft = lx;
                itemPanelTop = y;
                itemPanelBottom = y + ITEM_VISIBLE_ROWS * ITEM_ROW_STRIDE;
                itemPanelRight = lx + PANEL_W;

                itemScrollOffset = Math.max(0, Math.min(itemScrollOffset, maxItemScroll()));

                for (int i = 0; i < itemRowData.size(); i++) {
                        int rowTop = itemPanelTop + i * ITEM_ROW_STRIDE - itemScrollOffset;
                        int fieldY = rowTop + 10;

                        EditBox itemField = new EditBox(this.font, itemPanelLeft + 4, fieldY, ITEM_FIELD_W,
                                        FIELD_HEIGHT, Component.empty());
                        itemField.setMaxLength(128);
                        itemField.setHint(Component.literal("namespace:item  or  #namespace:tag")
                                        .withStyle(s -> s.withColor(0x444444)));
                        itemField.setValue(itemRowData.get(i)[0]);
                        this.addRenderableWidget(itemField);

                        int amountX = itemPanelLeft + 4 + ITEM_FIELD_W + 4;
                        EditBox amountField = new EditBox(this.font, amountX, fieldY, ITEM_AMOUNT_W, FIELD_HEIGHT,
                                        Component.empty());
                        amountField.setMaxLength(6);
                        amountField.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
                        amountField.setValue(itemRowData.get(i)[1]);
                        this.addRenderableWidget(amountField);

                        itemRows.add(new EditBox[] { itemField, amountField });
                }

                y = itemPanelBottom + 16;

                // ── Section 3: Perfect Reforge ────────────────────────────────────────────
                y += TEXT_HEIGHT + 6;

                perfectChanceField = new EditBox(this.font, lx, y + 14, 50, FIELD_HEIGHT, Component.empty());
                perfectChanceField.setMaxLength(5);
                perfectChanceField.setFilter(s -> s.isEmpty() || s.matches("-?\\d*\\.?\\d*"));
                perfectChanceField.setValue(String.valueOf(ReforgeManager.getPerfectChancePercent()));
                this.addRenderableWidget(perfectChanceField);
                y += FIELD_HEIGHT + TEXT_HEIGHT + 14;

                perfectBonusField = new EditBox(this.font, lx, y + 14, 50, FIELD_HEIGHT, Component.empty());
                perfectBonusField.setMaxLength(5);
                perfectBonusField.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
                perfectBonusField.setValue(String.valueOf(ReforgeManager.getPerfectBonusPercent()));
                this.addRenderableWidget(perfectBonusField);
                y += FIELD_HEIGHT + TEXT_HEIGHT + 6;

                // ── Section 4: Hardmode ───────────────────────────────────────────────────
                y += 16;
                hardmodeSectionY = y;
                y += TEXT_HEIGHT + 6 + 2;

                hardmodeEnabledBox = new Checkbox(lx, y, CHECKBOX_SIZE, CHECKBOX_SIZE,
                                Component.empty(), ReforgeManager.isHardmodeEnabled());
                this.addRenderableWidget(hardmodeEnabledBox);
                y += CHECKBOX_SIZE + CHECKBOX_GAP + DESC_LINE_HEIGHT + SECTION_GAP;

                hardmodeLockChanceField = new EditBox(this.font, lx, y + 14, 80, FIELD_HEIGHT, Component.empty());
                hardmodeLockChanceField.setMaxLength(6);
                hardmodeLockChanceField.setFilter(s -> s.isEmpty() || s.matches("\\d*\\.?\\d*"));
                hardmodeLockChanceField.setValue(String.valueOf(ReforgeManager.getHardmodeLockChance()));
                this.addRenderableWidget(hardmodeLockChanceField);
                y += FIELD_HEIGHT + TEXT_HEIGHT + 14;

                hardmodeDebuffMinField = new EditBox(this.font, lx, y + 14, 80, FIELD_HEIGHT, Component.empty());
                hardmodeDebuffMinField.setMaxLength(7);
                hardmodeDebuffMinField.setFilter(s -> s.isEmpty() || s.matches("-?\\d*\\.?\\d*"));
                hardmodeDebuffMinField.setValue(String.valueOf(ReforgeManager.getHardmodeDebuffMinPercent()));
                this.addRenderableWidget(hardmodeDebuffMinField);
                y += FIELD_HEIGHT + TEXT_HEIGHT + 14;

                hardmodeDebuffMaxField = new EditBox(this.font, lx, y + 14, 80, FIELD_HEIGHT, Component.empty());
                hardmodeDebuffMaxField.setMaxLength(7);
                hardmodeDebuffMaxField.setFilter(s -> s.isEmpty() || s.matches("-?\\d*\\.?\\d*"));
                hardmodeDebuffMaxField.setValue(String.valueOf(ReforgeManager.getHardmodeDebuffMaxPercent()));
                this.addRenderableWidget(hardmodeDebuffMaxField);
                y += FIELD_HEIGHT + TEXT_HEIGHT + TEXT_HEIGHT + 20;

                hardmodeShowPercentageChangeBox = new Checkbox(lx, y, CHECKBOX_SIZE, CHECKBOX_SIZE,
                                Component.empty(), ReforgeManager.isHardmodeShowPercentageChange());
                this.addRenderableWidget(hardmodeShowPercentageChangeBox);
                y += CHECKBOX_SIZE + CHECKBOX_GAP + DESC_LINE_HEIGHT + SECTION_GAP;

                hardmodeShowRangeBox = new Checkbox(lx, y, CHECKBOX_SIZE, CHECKBOX_SIZE,
                                Component.empty(), ReforgeManager.isHardmodeShowRange());
                this.addRenderableWidget(hardmodeShowRangeBox);
                y += CHECKBOX_SIZE + CHECKBOX_GAP + DESC_LINE_HEIGHT + SECTION_GAP;

                // Buttons at the bottom
                int fixedBtnY = this.height - 28;
                //*** Multiplayer: nur OP darf Settings ändern - alle anderen bekommen einen reinen Close-Button.
                if (GUIConstructor.canEdit()) {
                        saveButton = Button.builder(Component.literal("Save"), btn -> onSave())
                                        .pos(cx - 52, fixedBtnY).size(50, 20).build();
                        cancelButton = Button.builder(Component.literal("Cancel"), btn -> this.onClose())
                                        .pos(cx + 2, fixedBtnY).size(50, 20).build();
                } else {
                        saveButton = null;
                        cancelButton = Button.builder(Component.literal("Close"), btn -> this.onClose())
                                        .pos(cx - 40, fixedBtnY).size(80, 20).build();
                }

                totalContentHeight = y + 100;
        }

        /*****************************************************************************************************************************************************************
        Scroll helpers
        *****************************************************************************************************************************************************************/
        private int totalItemContentHeight() {
                return itemRowData.size() * ITEM_ROW_STRIDE;
        }

        private int visibleItemPanelHeight() {
                return itemPanelBottom - itemPanelTop;
        }

        private int maxItemScroll() {
                return Math.max(0, totalItemContentHeight() - visibleItemPanelHeight());
        }

        private boolean needsOuterScroll() {
                return totalContentHeight > this.height - 10;
        }

        private void scrollItems(int delta) {
                int prev = itemScrollOffset;
                itemScrollOffset = Math.max(0, Math.min(itemScrollOffset + delta, maxItemScroll()));
                if (itemScrollOffset != prev) {
                        syncItemRows();
                        rebuildWidgets();
                }
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
                double adjustedY = mouseY + screenScrollOffset;
                if (mouseX >= itemPanelLeft && mouseX <= itemPanelRight
                                && adjustedY >= itemPanelTop && adjustedY <= itemPanelBottom) {
                        scrollItems((int) (-delta * ITEM_ROW_STRIDE));
                        return true;
                }
                if (needsOuterScroll()) {
                        int maxScroll = Math.max(0, totalContentHeight - this.height + 10);
                        screenScrollOffset = Math.max(0, Math.min(screenScrollOffset + (int) (-delta * 20), maxScroll));
                        return true;
                }
                return super.mouseScrolled(mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
                // Fixed buttons use unadjusted screen-space Y (not inside the scroll pose)
                if (saveButton != null && saveButton.mouseClicked(mouseX, mouseY, button))
                        return true;
                if (cancelButton != null && cancelButton.mouseClicked(mouseX, mouseY, button))
                        return true;

                double adjustedY = mouseY + screenScrollOffset;
                boolean inItemPanel = adjustedY >= itemPanelTop && adjustedY <= itemPanelBottom
                                && mouseX >= itemPanelLeft && mouseX <= itemPanelRight;
                if (!inItemPanel) {
                        for (EditBox[] row : itemRows) {
                                row[0].setFocused(false);
                                row[1].setFocused(false);
                        }
                }
                return super.mouseClicked(mouseX, adjustedY, button);
        }

        /*****************************************************************************************************************************************************************
         * Sync
         * Save
         *****************************************************************************************************************************************************************/
        private void syncItemRows() {
                itemRowData.clear();
                for (EditBox[] row : itemRows)
                        itemRowData.add(new String[] { row[0].getValue(), row[1].getValue() });
        }

        private void onSave() {
                if (!GUIConstructor.canEdit()) return;

                syncItemRows();
                String item = "";
                int amount = 0;
                for (String[] row : itemRowData) {
                        if (!row[0].trim().isEmpty() && !row[1].trim().isEmpty()) {
                                item = row[0].trim();
                                amount = Integer.parseInt(row[1].trim());
                                break;
                        }
                }

                String xpStr     = xpField.getValue().trim();
                String chanceStr = perfectChanceField.getValue().trim();
                String bonusStr  = perfectBonusField.getValue().trim();
                String lockStr   = hardmodeLockChanceField.getValue().trim();
                String minStr    = hardmodeDebuffMinField.getValue().trim();
                String maxStr    = hardmodeDebuffMaxField.getValue().trim();

                //multiplayer: send to server instead of saving into config files
                de.randomreforges.network.NetworkHandler.CHANNEL.sendToServer(new de.randomreforges.network.ServerBoundPackets.SettingsUpdatePacket(
                        ignoreDefaultReforgesBox.selected(),
                        confirmDeleteBox.selected(),
                        xpStr.isEmpty() ? 0 : Integer.parseInt(xpStr),
                        item,
                        amount,
                        chanceStr.isEmpty() ? 0 : Double.parseDouble(chanceStr),
                        bonusStr.isEmpty() ? 0 : Integer.parseInt(bonusStr),
                        hardmodeEnabledBox.selected(),
                        lockStr.isEmpty() ? 0 : Double.parseDouble(lockStr),
                        minStr.isEmpty() ? 0 : Double.parseDouble(minStr),
                        maxStr.isEmpty() ? 0 : Double.parseDouble(maxStr),
                        hardmodeShowPercentageChangeBox.selected(),
                        hardmodeShowRangeBox.selected()
                ));

                this.minecraft.setScreen(new ReforgeListGUI());
        }

        protected void rebuildWidgets() {
                boolean savedIgnore = ignoreDefaultReforgesBox != null && ignoreDefaultReforgesBox.selected();
                boolean savedConfirm = confirmDeleteBox != null && confirmDeleteBox.selected();
                boolean savedHardmode = hardmodeEnabledBox != null ? hardmodeEnabledBox.selected()
                                : ReforgeManager.isHardmodeEnabled();
                boolean savedShowPct = hardmodeShowPercentageChangeBox != null
                                ? hardmodeShowPercentageChangeBox.selected()
                                : ReforgeManager.isHardmodeShowPercentageChange();
                boolean savedShowRng = hardmodeShowRangeBox != null ? hardmodeShowRangeBox.selected()
                                : ReforgeManager.isHardmodeShowRange();
                String savedXp = xpField != null ? xpField.getValue()
                                : String.valueOf(ReforgeManager.getRerollCostLevels());
                String savedChance = perfectChanceField != null ? perfectChanceField.getValue()
                                : String.valueOf(ReforgeManager.getPerfectChancePercent());
                String savedBonus = perfectBonusField != null ? perfectBonusField.getValue()
                                : String.valueOf(ReforgeManager.getPerfectBonusPercent());
                String savedLock = hardmodeLockChanceField != null ? hardmodeLockChanceField.getValue()
                                : String.valueOf(ReforgeManager.getHardmodeLockChance());
                String savedMin = hardmodeDebuffMinField != null ? hardmodeDebuffMinField.getValue()
                                : String.valueOf(ReforgeManager.getHardmodeDebuffMinPercent());
                String savedMax = hardmodeDebuffMaxField != null ? hardmodeDebuffMaxField.getValue()
                                : String.valueOf(ReforgeManager.getHardmodeDebuffMaxPercent());
                clearWidgets();
                itemRows.clear();
                init();
                if (savedIgnore != ReforgeManager.isIgnoreDefaultReforges())
                        ignoreDefaultReforgesBox.onPress();
                if (savedConfirm != ReforgeManager.isConfirmDelete())
                        confirmDeleteBox.onPress();
                if (savedHardmode != ReforgeManager.isHardmodeEnabled())
                        hardmodeEnabledBox.onPress();
                if (savedShowPct != ReforgeManager.isHardmodeShowPercentageChange())
                        hardmodeShowPercentageChangeBox.onPress();
                if (savedShowRng != ReforgeManager.isHardmodeShowRange())
                        hardmodeShowRangeBox.onPress();
                xpField.setValue(savedXp);
                perfectChanceField.setValue(savedChance);
                perfectBonusField.setValue(savedBonus);
                hardmodeLockChanceField.setValue(savedLock);
                hardmodeDebuffMinField.setValue(savedMin);
                hardmodeDebuffMaxField.setValue(savedMax);
                int maxScroll = Math.max(0, totalContentHeight - this.height + 10);
                screenScrollOffset = Math.max(0, Math.min(screenScrollOffset, maxScroll));
        }

        /*****************************************************************************************************************************************************************
         * Render
         *****************************************************************************************************************************************************************/
        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                this.renderBackground(graphics);

                int cx = this.width / 2;
                int lx = cx - PANEL_W / 2;
                int rx = cx + PANEL_W / 2;
                int y = 40;

                graphics.drawCenteredString(this.font, this.title, cx, 10, 0xFFFFFF);

                graphics.pose().pushPose();
                graphics.pose().translate(0, -screenScrollOffset, 0);
                graphics.enableScissor(0, 20, this.width, this.height - 36);

                GUIConstructor.drawSectionHeader(graphics, this.font, lx - 11, y, "▶ General", HEADER_COLOR,
                                TEXT_HEIGHT);
                y += TEXT_HEIGHT + 6;

                // Each checkbox's label and description are anchored to that checkbox's own
                // getY() instead of the loose 'y' tracker above, so they can never end up
                // visually attached to the wrong checkbox even if init()'s spacing changes.
                if (ignoreDefaultReforgesBox != null) {
                        int boxY = ignoreDefaultReforgesBox.getY();
                        graphics.drawString(this.font, "Ignore Default Reforges", lx + 86, boxY + 6, LABEL_COLOR,
                                        false);

                        int descY = boxY + CHECKBOX_SIZE + CHECKBOX_GAP;
                        graphics.drawString(this.font,
                                        Component.literal("When enabled, only reforges from")
                                                        .withStyle(Style.EMPTY.withColor(DESC_COLOR).withItalic(true)),
                                        lx + 86, descY, DESC_COLOR, false);
                        descY += DESC_LINE_HEIGHT;
                        graphics.drawString(this.font,
                                        Component.literal("custom_reforges.json are loaded.")
                                                        .withStyle(Style.EMPTY.withColor(DESC_COLOR).withItalic(true)),
                                        lx + 86, descY, DESC_COLOR, false);
                }

                if (confirmDeleteBox != null) {
                        int boxY = confirmDeleteBox.getY();
                        graphics.drawString(this.font, "Confirm Delete", lx + 86, boxY + 6, LABEL_COLOR, false);

                        int descY = boxY + CHECKBOX_SIZE + CHECKBOX_GAP;
                        graphics.drawString(this.font,
                                        Component.literal("If enabled, click \"Delete Reforge\" twice to confirm.")
                                                        .withStyle(Style.EMPTY.withColor(DESC_COLOR).withItalic(true)),
                                        lx + 86, descY, DESC_COLOR, false);

                        // Continue the shared layout flow from below this checkbox's description
                        y = descY + DESC_LINE_HEIGHT + 6;
                }

                graphics.fill(lx, y, rx, y + 1, SEP_COLOR);
                y += 6;

                GUIConstructor.drawSectionHeader(graphics, this.font, lx - 11, y, "▶ Reforge Reroll Costs",
                                HEADER_COLOR, TEXT_HEIGHT);
                y += TEXT_HEIGHT + 2;

                if (xpField != null) {
                        graphics.drawString(this.font,
                                        Component.literal("XP Cost (Levels)")
                                                        .withStyle(Style.EMPTY.withColor(LABEL_COLOR)),
                                        lx + 86, xpField.getY() + 6, LABEL_COLOR, false);
                        graphics.drawString(this.font,
                                        Component.literal("Set to 0 for no level cost.")
                                                        .withStyle(Style.EMPTY.withColor(DESC_COLOR).withItalic(true)),
                                        lx + 86, xpField.getY() + xpField.getHeight() + 4, DESC_COLOR, false);
                }
                y += FIELD_HEIGHT + TEXT_HEIGHT + 12;

                graphics.drawString(this.font,
                                Component.literal("Leave item/tag empty for no item cost.")
                                                .withStyle(Style.EMPTY.withColor(DESC_COLOR).withItalic(true)),
                                lx + 86, addItemButtonY + 5, DESC_COLOR, false);
                y += 26;

                graphics.fill(itemPanelLeft, itemPanelTop, itemPanelRight, itemPanelBottom, 0x44000000);
                GUIConstructor.drawPanelBorder(graphics, itemPanelLeft, itemPanelTop, itemPanelRight, itemPanelBottom);

                for (int i = 0; i < itemRows.size(); i++) {
                        int fieldY = itemPanelTop + i * ITEM_ROW_STRIDE - itemScrollOffset + 10;
                        boolean vis = fieldY >= itemPanelTop - FIELD_HEIGHT && fieldY + FIELD_HEIGHT <= itemPanelBottom;
                        itemRows.get(i)[0].visible = vis;
                        itemRows.get(i)[1].visible = vis;
                }

                super.render(graphics, mouseX, mouseY, partialTick);

                if (itemPanelBottom > itemPanelTop)
                        graphics.enableScissor(itemPanelLeft,
                                        itemPanelTop - screenScrollOffset,
                                        itemPanelRight + 10,
                                        itemPanelBottom - screenScrollOffset);

                for (int i = 0; i < itemRowData.size(); i++) {
                        int rowTop = itemPanelTop + i * ITEM_ROW_STRIDE - itemScrollOffset;
                        boolean vis = rowTop >= itemPanelTop - ITEM_ROW_STRIDE && rowTop < itemPanelBottom;
                        if (!vis)
                                continue;
                        graphics.drawString(this.font,
                                        Component.literal("Item / Item Tag")
                                                        .withStyle(Style.EMPTY.withColor(LABEL_COLOR)),
                                        itemPanelLeft + 4, rowTop, LABEL_COLOR, false);
                        graphics.drawString(this.font,
                                        Component.literal("Amount: ").withStyle(Style.EMPTY.withColor(LABEL_COLOR)),
                                        itemPanelLeft + 4 + ITEM_FIELD_W + 4, rowTop, LABEL_COLOR, false);
                }

                if (itemPanelBottom > itemPanelTop)
                        graphics.disableScissor();

                // ── Section 3: Perfect Reforge labels ────────────────────────────────────
                if (perfectChanceField != null && perfectBonusField != null) {
                        int secY = itemPanelBottom + 22;
                        graphics.fill(lx, secY - 4, rx, secY - 3, SEP_COLOR);
                        GUIConstructor.drawSectionHeader(graphics, this.font, lx - 11, secY, "▶ Perfect Reforge",
                                        HEADER_COLOR, TEXT_HEIGHT);
                        graphics.drawString(this.font,
                                        Component.literal("Chance (%)").withStyle(Style.EMPTY.withColor(LABEL_COLOR)),
                                        lx + 86, perfectChanceField.getY() + 6, LABEL_COLOR, false);
                        graphics.drawString(this.font,
                                        Component.literal("Set to 0 to disable. Rolls per reforge.")
                                                        .withStyle(Style.EMPTY.withColor(DESC_COLOR).withItalic(true)),
                                        lx + 86, perfectChanceField.getY() + perfectChanceField.getHeight() + 4,
                                        DESC_COLOR, false);
                        graphics.drawString(this.font,
                                        Component.literal("Bonus (%)").withStyle(Style.EMPTY.withColor(LABEL_COLOR)),
                                        lx + 86, perfectBonusField.getY() + 6, LABEL_COLOR, false);
                        graphics.drawString(this.font,
                                        Component.literal("All positive attributes are multiplied by (1 + Bonus/100).")
                                                        .withStyle(Style.EMPTY.withColor(DESC_COLOR).withItalic(true)),
                                        lx + 86, perfectBonusField.getY() + perfectBonusField.getHeight() + 4,
                                        DESC_COLOR, false);
                }

                // ── Section 4: Hardmode labels ────────────────────────────────────────────
                if (hardmodeEnabledBox != null) {
                        graphics.fill(lx, hardmodeSectionY - 4, rx, hardmodeSectionY - 3, SEP_COLOR);
                        GUIConstructor.drawSectionHeader(graphics, this.font, lx - 11, hardmodeSectionY, "▶ Hardmode",
                                        HEADER_COLOR, TEXT_HEIGHT);
                        int boxY = hardmodeEnabledBox.getY();
                        graphics.drawString(this.font, "Enable Hardmode", lx + 86, boxY + 6, LABEL_COLOR, false);
                        graphics.drawString(this.font,
                                        Component.literal("Reforges can lock items permanently and debuffs vary.")
                                                        .withStyle(Style.EMPTY.withColor(DESC_COLOR).withItalic(true)),
                                        lx + 86, boxY + CHECKBOX_SIZE + CHECKBOX_GAP, DESC_COLOR, false);
                }
                if (hardmodeLockChanceField != null) {
                        graphics.drawString(this.font,
                                        Component.literal("Lock Chance (%)")
                                                        .withStyle(Style.EMPTY.withColor(LABEL_COLOR)),
                                        lx + 86, hardmodeLockChanceField.getY() + 6, LABEL_COLOR, false);
                        graphics.drawString(this.font,
                                        Component.literal(
                                                        "Chance to lock item after reforging at the cage. 0 = disabled.")
                                                        .withStyle(Style.EMPTY.withColor(DESC_COLOR).withItalic(true)),
                                        lx + 86,
                                        hardmodeLockChanceField.getY() + hardmodeLockChanceField.getHeight() + 4,
                                        DESC_COLOR, false);
                }
                if (hardmodeDebuffMinField != null) {
                        graphics.drawString(this.font,
                                        Component.literal("Debuff Change Min (%)")
                                                        .withStyle(Style.EMPTY.withColor(LABEL_COLOR)),
                                        lx + 86, hardmodeDebuffMinField.getY() + 6, LABEL_COLOR, false);
                        graphics.drawString(this.font,
                                        Component.literal("Min debuff modifier. Negative = debuffs can be reduced.")
                                                        .withStyle(Style.EMPTY.withColor(DESC_COLOR).withItalic(true)),
                                        lx + 86, hardmodeDebuffMinField.getY() + hardmodeDebuffMinField.getHeight() + 4,
                                        DESC_COLOR, false);
                }
                if (hardmodeDebuffMaxField != null) {
                        graphics.drawString(this.font,
                                        Component.literal("Debuff Change Max (%)")
                                                        .withStyle(Style.EMPTY.withColor(LABEL_COLOR)),
                                        lx + 86, hardmodeDebuffMaxField.getY() + 6, LABEL_COLOR, false);
                        graphics.drawString(this.font,
                                        Component.literal("Max debuff modifier. Positive = debuffs can be stronger.")
                                                        .withStyle(Style.EMPTY.withColor(DESC_COLOR).withItalic(true)),
                                        lx + 86, hardmodeDebuffMaxField.getY() + hardmodeDebuffMaxField.getHeight() + 4,
                                        DESC_COLOR, false);
                }
                if (hardmodeShowPercentageChangeBox != null) {
                        int boxY = hardmodeShowPercentageChangeBox.getY();
                        graphics.drawString(this.font, "Show Percentage Change", lx + 86, boxY + 6, LABEL_COLOR, false);
                        graphics.drawString(this.font,
                                        Component.literal("Shows \u2620 +x% / \u2620 -x% inline next to each debuff.")
                                                        .withStyle(Style.EMPTY.withColor(DESC_COLOR).withItalic(true)),
                                        lx + 86, boxY + CHECKBOX_SIZE + CHECKBOX_GAP, DESC_COLOR, false);
                }
                if (hardmodeShowRangeBox != null) {
                        int boxY = hardmodeShowRangeBox.getY();
                        graphics.drawString(this.font, "Show Range", lx + 86, boxY + 6, LABEL_COLOR, false);
                        graphics.drawString(this.font,
                                        Component.literal("Shows \u2620 [min to max] possible debuff values.")
                                                        .withStyle(Style.EMPTY.withColor(DESC_COLOR).withItalic(true)),
                                        lx + 86, boxY + CHECKBOX_SIZE + CHECKBOX_GAP, DESC_COLOR, false);
                }

                GUIConstructor.renderPanelScrollbar(graphics, itemPanelTop, itemPanelBottom,
                                itemPanelRight + 4, itemScrollOffset, totalItemContentHeight());

                graphics.disableScissor();
                graphics.pose().popPose();

                GUIConstructor.renderOuterScrollbar(graphics, this.width, this.height, screenScrollOffset,
                                totalContentHeight);
                if (saveButton != null) saveButton.render(graphics, mouseX, mouseY, partialTick);
                cancelButton.render(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        public boolean isPauseScreen() {
                return false;
        }
}
