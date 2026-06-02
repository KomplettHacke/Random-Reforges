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

    private static final int LABEL_COLOR  = 0xAAAAAA;
    private static final int DESC_COLOR   = 0x777777;
    private static final int HEADER_COLOR = 0xFFDD00;
    private static final int SEP_COLOR    = 0x44FFFFFF;
    private static final int FIELD_HEIGHT  = 20;
    private static final int TEXT_HEIGHT   = 9;

    // ── Item scroll panel ─────────────────────────────────────────────────────
    private static final int PANEL_W          = 350;
    private static final int ITEM_ROW_STRIDE  = 32;
    private static final int ITEM_VISIBLE_ROWS = 4;
    private static final int ITEM_FIELD_W     = 200;
    private static final int ITEM_AMOUNT_W    = 50;

    private int itemPanelTop, itemPanelBottom, itemPanelLeft, itemPanelRight;
    private int itemScrollOffset  = 0;
    private int addItemButtonY    = 0;

    // ── Outer scroll ──────────────────────────────────────────────────────────
    private int screenScrollOffset = 0;
    private int totalContentHeight = 0;

    // ── Widgets ───────────────────────────────────────────────────────────────
    private Checkbox       ignoreDefaultReforgesBox;
    private EditBox        xpField;

    private final List<EditBox[]> itemRows    = new ArrayList<>();
    private final List<String[]>  itemRowData = new ArrayList<>();

    public SettingsGUI() {
        super(Component.literal("Settings"));
        String item   = ReforgeManager.getRerollCostItem();
        int    amount = ReforgeManager.getRerollCostItemAmount();
        if (!item.isEmpty() || amount > 0)
            itemRowData.add(new String[]{ item, String.valueOf(amount) });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // init
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    protected void init() {
        itemRows.clear();

        int cx = this.width / 2;
        int lx = cx - PANEL_W / 2;
        int y  = 40;   // fixed – outer scroll via PoseStack

        // ── Section 1: General ────────────────────────────────────────────────
        y += TEXT_HEIGHT + 2;
        ignoreDefaultReforgesBox = new Checkbox(lx, y, 20, 20,
                Component.empty(), ReforgeManager.isIgnoreDefaultReforges());
        this.addRenderableWidget(ignoreDefaultReforgesBox);
        y += 22 + TEXT_HEIGHT + 16;

        // ── Section 2: Reforge Reroll Costs ───────────────────────────────────
        y += TEXT_HEIGHT + 2;

        xpField = new EditBox(this.font, lx, y + 14, 50, FIELD_HEIGHT, Component.empty());
        xpField.setMaxLength(6);
        xpField.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
        xpField.setValue(String.valueOf(ReforgeManager.getRerollCostLevels()));
        this.addRenderableWidget(xpField);
        y += FIELD_HEIGHT + TEXT_HEIGHT + TEXT_HEIGHT + 14;

        // "+ Add Item" button
        addItemButtonY = y;
        this.addRenderableWidget(Button.builder(Component.literal("+ Add Item"), btn -> {
                    syncItemRows();
                    itemRowData.add(new String[]{ "", "" });
                    itemScrollOffset = maxItemScroll();
                    rebuildWidgets();
                })
                .pos(lx - 1, addItemButtonY).size(80, 18).build());
        y += 26;

        // ── Item scroll panel ─────────────────────────────────────────────────
        itemPanelLeft   = lx;
        itemPanelTop    = y;
        itemPanelBottom = y + ITEM_VISIBLE_ROWS * ITEM_ROW_STRIDE;
        itemPanelRight  = lx + PANEL_W;

        itemScrollOffset = Math.max(0, Math.min(itemScrollOffset, maxItemScroll()));

        for (int i = 0; i < itemRowData.size(); i++) {
            int rowTop = itemPanelTop + i * ITEM_ROW_STRIDE - itemScrollOffset;
            int fieldY = rowTop + 10;

            EditBox itemField = new EditBox(this.font, itemPanelLeft + 4, fieldY, ITEM_FIELD_W, FIELD_HEIGHT, Component.empty());
            itemField.setMaxLength(128);
            itemField.setHint(Component.literal("namespace:item  or  #namespace:tag")
                    .withStyle(s -> s.withColor(0x444444)));
            itemField.setValue(itemRowData.get(i)[0]);
            this.addRenderableWidget(itemField);

            int amountX = itemPanelLeft + 4 + ITEM_FIELD_W + 4;
            EditBox amountField = new EditBox(this.font, amountX, fieldY, ITEM_AMOUNT_W, FIELD_HEIGHT, Component.empty());
            amountField.setMaxLength(6);
            amountField.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
            amountField.setValue(itemRowData.get(i)[1]);
            this.addRenderableWidget(amountField);

            itemRows.add(new EditBox[]{ itemField, amountField });
        }

        y = itemPanelBottom + 16;

        // ── Save / Cancel ─────────────────────────────────────────────────────
        this.addRenderableWidget(Button.builder(Component.literal("Save"), btn -> onSave())
                .pos(cx - 52, y).size(50, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), btn -> this.onClose())
                .pos(cx + 2, y).size(50, 20).build());

        totalContentHeight = y + 20;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scroll helpers
    // ─────────────────────────────────────────────────────────────────────────
    private int totalItemContentHeight() { return itemRowData.size() * ITEM_ROW_STRIDE; }
    private int visibleItemPanelHeight() { return itemPanelBottom - itemPanelTop; }
    private int maxItemScroll()          { return Math.max(0, totalItemContentHeight() - visibleItemPanelHeight()); }
    private boolean needsOuterScroll()   { return totalContentHeight > this.height - 10; }

    private void scrollItems(int delta) {
        int prev = itemScrollOffset;
        itemScrollOffset = Math.max(0, Math.min(itemScrollOffset + delta, maxItemScroll()));
        if (itemScrollOffset != prev) { syncItemRows(); rebuildWidgets(); }
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
            screenScrollOffset = Math.max(0, Math.min(screenScrollOffset + (int)(-delta * 20), maxScroll));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double adjustedY = mouseY + screenScrollOffset;
        boolean inPanel = adjustedY >= itemPanelTop && adjustedY <= itemPanelBottom
                       && mouseX >= itemPanelLeft && mouseX <= itemPanelRight;
        if (!inPanel) {
            for (EditBox[] row : itemRows) { row[0].setFocused(false); row[1].setFocused(false); }
        }
        return super.mouseClicked(mouseX, adjustedY, button);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Sync / Save
    // ─────────────────────────────────────────────────────────────────────────
    private void syncItemRows() {
        itemRowData.clear();
        for (EditBox[] row : itemRows)
            itemRowData.add(new String[]{ row[0].getValue(), row[1].getValue() });
    }

    private void onSave() {
        syncItemRows();
        ReforgeManager.setIgnoreDefaultReforges(ignoreDefaultReforgesBox.selected());
        String xpStr = xpField.getValue().trim();
        ReforgeManager.setRerollCostLevels(xpStr.isEmpty() ? 0 : Integer.parseInt(xpStr));
        String item = ""; int amount = 0;
        for (String[] row : itemRowData) {
            if (!row[0].trim().isEmpty() && !row[1].trim().isEmpty()) {
                item   = row[0].trim();
                amount = Integer.parseInt(row[1].trim());
                break;
            }
        }
        ReforgeManager.setRerollCostItem(item);
        ReforgeManager.setRerollCostItemAmount(amount);
        ReforgeManager.saveSettings();
        ReforgeManager.load();
        this.minecraft.setScreen(new ReforgeListGUI());
    }

    protected void rebuildWidgets() {
        boolean savedIgnore = ignoreDefaultReforgesBox != null && ignoreDefaultReforgesBox.selected();
        String  savedXp     = xpField != null ? xpField.getValue()
                                              : String.valueOf(ReforgeManager.getRerollCostLevels());
        clearWidgets();
        itemRows.clear();
        init();
        if (savedIgnore != ReforgeManager.isIgnoreDefaultReforges()) ignoreDefaultReforgesBox.onPress();
        xpField.setValue(savedXp);
        int maxScroll = Math.max(0, totalContentHeight - this.height + 10);
        screenScrollOffset = Math.max(0, Math.min(screenScrollOffset, maxScroll));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Render
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);

        int cx = this.width / 2;
        int lx = cx - PANEL_W / 2;
        int rx = cx + PANEL_W / 2;
        int y  = 40;

        // Title fixed (not scrolled)
        g.drawCenteredString(this.font, this.title, cx, 15, LABEL_COLOR);

        // Outer scroll via pose
        g.pose().pushPose();
        g.pose().translate(0, -screenScrollOffset, 0);
        g.enableScissor(0, 20, this.width, this.height);

        // ── Section 1: General ────────────────────────────────────────────────
        sectionHeader(g, lx - 11, y, "▶ General");
        y += TEXT_HEIGHT + 6;
        y += 22;

        if (ignoreDefaultReforgesBox != null) {
            g.drawString(this.font, "Ignore Default Reforges",
                    lx + 86, ignoreDefaultReforgesBox.getY() + 6, LABEL_COLOR, false);
        }
        g.drawString(this.font,
                Component.literal("When enabled, only reforges from")
                        .withStyle(Style.EMPTY.withColor(DESC_COLOR).withItalic(true)),
                lx + 86, y, DESC_COLOR, false);
        y += TEXT_HEIGHT + 6;
        g.drawString(this.font,
                Component.literal("custom_reforges.json are loaded.")
                        .withStyle(Style.EMPTY.withColor(DESC_COLOR).withItalic(true)),
                lx + 86, y, DESC_COLOR, false);
        y += TEXT_HEIGHT + 6;
        g.fill(lx, y, rx, y + 1, SEP_COLOR);
        y += 6;

        // ── Section 2: Reforge Reroll Costs ───────────────────────────────────
        sectionHeader(g, lx - 11, y, "▶ Reforge Reroll Costs");
        y += TEXT_HEIGHT + 2;

        if (xpField != null) {
            g.drawString(this.font,
                    Component.literal("XP Cost (Levels)").withStyle(Style.EMPTY.withColor(LABEL_COLOR)),
                    lx + 86, xpField.getY() + 6, LABEL_COLOR, false);
            g.drawString(this.font,
                    Component.literal("Set to 0 for no level cost.")
                            .withStyle(Style.EMPTY.withColor(DESC_COLOR).withItalic(true)),
                    lx + 86, xpField.getY() + xpField.getHeight() + 4, DESC_COLOR, false);
        }
        y += FIELD_HEIGHT + TEXT_HEIGHT + 12;

        g.drawString(this.font,
                Component.literal("Leave item/tag empty for no item cost.")
                        .withStyle(Style.EMPTY.withColor(DESC_COLOR).withItalic(true)),
                lx + 86, addItemButtonY + 5, DESC_COLOR, false);
        y += 26;

        // ── Item panel ────────────────────────────────────────────────────────
        drawPanelBorder(g);

        for (int i = 0; i < itemRows.size(); i++) {
            int fieldY  = itemPanelTop + i * ITEM_ROW_STRIDE - itemScrollOffset + 10;
            boolean vis = fieldY >= itemPanelTop - FIELD_HEIGHT && fieldY + FIELD_HEIGHT <= itemPanelBottom;
            itemRows.get(i)[0].visible = vis;
            itemRows.get(i)[1].visible = vis;
        }

        super.render(g, mouseX, mouseY, partialTick);

        // Inner scissor in screen space
        if (itemPanelBottom > itemPanelTop)
            g.enableScissor(itemPanelLeft,
                    itemPanelTop    - screenScrollOffset,
                    itemPanelRight  + 10,
                    itemPanelBottom - screenScrollOffset);

        for (int i = 0; i < itemRowData.size(); i++) {
            int rowTop = itemPanelTop + i * ITEM_ROW_STRIDE - itemScrollOffset;
            boolean vis = rowTop >= itemPanelTop - ITEM_ROW_STRIDE && rowTop < itemPanelBottom;
            if (!vis) continue;
            g.drawString(this.font,
                    Component.literal("Item / Item Tag").withStyle(Style.EMPTY.withColor(LABEL_COLOR)),
                    itemPanelLeft + 4, rowTop, LABEL_COLOR, false);
            g.drawString(this.font,
                    Component.literal("Amount: ").withStyle(Style.EMPTY.withColor(LABEL_COLOR)),
                    itemPanelLeft + 4 + ITEM_FIELD_W + 4, rowTop, LABEL_COLOR, false);
        }

        if (itemPanelBottom > itemPanelTop)
            g.disableScissor();

        renderItemScrollbar(g);

        // Close outer scroll transform
        g.disableScissor();
        g.pose().popPose();

        // Outer scrollbar in screen space
        renderOuterScrollbar(g);
    }

    private void renderOuterScrollbar(GuiGraphics g) {
        if (!needsOuterScroll()) return;
        int maxScroll = Math.max(1, totalContentHeight - this.height + 10);
        int x      = this.width - 6;
        int trackH = this.height;
        int thumbH = Math.max(20, trackH * this.height / totalContentHeight);
        int thumbY = (int)((long) screenScrollOffset * (trackH - thumbH) / maxScroll);
        g.fill(x, 0,      x + 4, this.height,     0x44FFFFFF);
        g.fill(x, thumbY, x + 4, thumbY + thumbH,  0xAAFFFFFF);
    }

    private void drawPanelBorder(GuiGraphics g) {
        if (itemPanelBottom <= itemPanelTop) return;
        int l = itemPanelLeft - 1, r = itemPanelRight + 1;
        int t = itemPanelTop,      b = itemPanelBottom;
        g.fill(l + 1, t, r - 1, b, 0x44000000);
        g.fill(l, t - 1, r, t,     0x88FFFFFF);
        g.fill(l, b,     r, b + 1, 0x88FFFFFF);
        g.fill(l, t,     l + 1, b, 0x88FFFFFF);
        g.fill(r - 1, t, r, b,     0x88FFFFFF);
    }

    private void renderItemScrollbar(GuiGraphics g) {
        int total   = totalItemContentHeight();
        int visible = visibleItemPanelHeight();
        if (total <= visible || itemPanelBottom <= itemPanelTop) return;
        int x      = itemPanelRight + 4;
        int trackH = itemPanelBottom - itemPanelTop;
        int thumbH = Math.max(12, trackH * visible / total);
        int max    = maxItemScroll();
        int thumbY = max > 0 ? itemPanelTop + (itemScrollOffset * (trackH - thumbH)) / max : itemPanelTop;
        g.fill(x, itemPanelTop, x + 4, itemPanelBottom, 0x44FFFFFF);
        g.fill(x, thumbY,       x + 4, thumbY + thumbH,  0xAAFFFFFF);
    }

    private void sectionHeader(GuiGraphics g, int lx, int y, String title) {
        g.fill(lx, y + 1, lx + 2, y + TEXT_HEIGHT, HEADER_COLOR);
        g.drawString(this.font,
                Component.literal("  " + title).withStyle(Style.EMPTY.withColor(HEADER_COLOR).withBold(true)),
                lx, y, HEADER_COLOR, false);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}