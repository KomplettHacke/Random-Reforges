package de.randomreforges.gui;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ReforgeListGUI extends Screen {

    private static final int TOP_Y      = 32;
    private static final int BOTTOM_GAP = 36; // reserved for search bar

    private ReforgeSelectionList list;
    private EditBox              searchField;
    private DropdownWidget       searchMode;

    private static final List<String> SEARCH_MODES = List.of("Reforge ID", "Display Name", "Group");

    public ReforgeListGUI() {
        super(Component.literal("Reforges"));
    }

    @Override
    protected void init() {
        // List ends above the search bar
        list = new ReforgeSelectionList(
                this.minecraft, this.width, this.height,
                TOP_Y, this.height - BOTTOM_GAP
        );
        this.addRenderableWidget(list);

        // ── Top-right buttons ─────────────────────────────────────────────────
        this.addRenderableWidget(Button.builder(Component.literal("+"),
                        btn -> this.minecraft.setScreen(new ReforgeEditorGUI()))
                .pos(this.width - 28, 6).size(20, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("⚙"),
                        btn -> this.minecraft.setScreen(new SettingsGUI()))
                .pos(this.width - 52, 6).size(20, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("?"),
                        btn -> this.minecraft.setScreen(new HelpGUI()))
                .pos(this.width - 76, 6).size(20, 20).build());

        // ── Top-left: Discord button ──────────────────────────────────────────
        this.addRenderableWidget(new DiscordButton(8, 6));

        // ── Search bar (bottom center) ────────────────────────────────────────
        int barY       = this.height - BOTTOM_GAP + 9;
        int dropWidth  = 110;
        int fieldWidth = 180;
        int gap        = 4;
        int totalW     = fieldWidth + gap + dropWidth;
        int startX     = (this.width - totalW) / 2;

        searchField = new EditBox(this.font, startX, barY, fieldWidth, 18, Component.empty());
        searchField.setMaxLength(64);
        searchField.setHint(Component.literal("Search...").withStyle(s -> s.withColor(0x555555)));
        searchField.setResponder(text -> applyFilter());
        this.addRenderableWidget(searchField);

        searchMode = new DropdownWidget(startX + fieldWidth + gap, barY, dropWidth, 18, SEARCH_MODES);
        this.addRenderableWidget(searchMode);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void applyFilter() {
        if (list != null && searchField != null && searchMode != null) {
            list.refresh(searchField.getValue(), searchMode.getSelected());
            list.setScrollAmount(0);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Render
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);

        if (list.children().isEmpty()) {
            String msg = (searchField != null && !searchField.getValue().isEmpty())
                    ? "No results."
                    : "No reforges loaded. Press + to add one.";
            graphics.drawCenteredString(this.font, Component.literal(msg),
                    this.width / 2, this.height / 2, 0x777777);
        }

        // Dropdown opens UPWARD – draw it after everything else so it appears on top
        renderUpwardDropdown(graphics, mouseX, mouseY);
    }


    private void renderUpwardDropdown(GuiGraphics graphics, int mouseX, int mouseY) {
        if (searchMode == null || !searchMode.isOpen()) return;

        int optH    = 14;
        int x       = searchMode.getX();
        int w       = searchMode.getWidth();
        int baseY   = searchMode.getY();     // top edge of the button
        int totalH  = SEARCH_MODES.size() * optH;
        int listTop = baseY - totalH;        // list opens ABOVE the button

        // Border + background
        graphics.fill(x - 1,  listTop - 1, x + w + 1, baseY + 1, 0xFF888888);
        graphics.fill(x,      listTop,     x + w,     baseY,     0xFF2A2A2A);

        for (int i = 0; i < SEARCH_MODES.size(); i++) {
            int    optY = listTop + i * optH;
            boolean hov = mouseX >= x && mouseX <= x + w
                       && mouseY >= optY && mouseY <= optY + optH;
            boolean sel = SEARCH_MODES.get(i).equals(searchMode.getSelected());
            if (hov || sel) graphics.fill(x, optY, x + w, optY + optH, 0xFF444444);
            graphics.drawString(this.font, SEARCH_MODES.get(i),
                    x + 3, optY + 3, sel ? 0xFFFFDD00 : 0xFFFFFFFF, false);
        }
    }

    /*****************************************************************************************************************************************************************
    Input
    *****************************************************************************************************************************************************************/
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (searchMode != null && searchMode.isOpen()) {
            // Check if a list option was clicked
            int optH    = 14;
            int x       = searchMode.getX();
            int w       = searchMode.getWidth();
            int baseY   = searchMode.getY();
            int totalH  = SEARCH_MODES.size() * optH;
            int listTop = baseY - totalH;

            if (mouseX >= x && mouseX <= x + w
                    && mouseY >= listTop && mouseY < baseY) {
                int idx = (int) ((mouseY - listTop) / optH);
                if (idx >= 0 && idx < SEARCH_MODES.size()) {
                    String prev = searchMode.getSelected();
                    searchMode.setSelected(SEARCH_MODES.get(idx));
                    searchMode.close();
                    if (!SEARCH_MODES.get(idx).equals(prev)) applyFilter();
                    return true;
                }
            }

            searchMode.close();
            return super.mouseClicked(mouseX, mouseY, button);
        }

        boolean result = super.mouseClicked(mouseX, mouseY, button);

        applyFilter();
        return result;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
