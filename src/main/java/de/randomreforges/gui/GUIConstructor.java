package de.randomreforges.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/*****************************************************************************************************************************************************************
Shared GUI rendering utilities used across all RandomReforges screens.
Consolidates scrollbars, panel borders and section headers to ensure
a consistent look in HelpGUI, SettingsGUI and ReforgeEditorGUI.
*****************************************************************************************************************************************************************/
public final class GUIConstructor {

    private GUIConstructor() {} // utility class – no instantiation

    // ── Outer scrollbar ───────────────────────────────────────────────────────

    /*****************************************************************************************************************************************************************
    Outer scrollbar that spans from y=30 to y=(screenHeight-36).
    Rendered AFTER popPose() so it sits on top of the scrollable content and
    below the fixed header/footer areas.
    Does nothing when all content fits on screen without scrolling.
    *****************************************************************************************************************************************************************/
    public static void renderOuterScrollbar(GuiGraphics g, int screenWidth, int screenHeight,
                                             int scrollOffset, int totalContentHeight) {
        int top    = 30;
        int bottom = screenHeight - 36;
        // No scrollbar needed if everything fits
        if (totalContentHeight <= screenHeight - 10) return;
        int trackH    = bottom - top;
        int maxScroll = Math.max(1, totalContentHeight - screenHeight + 10);
        int thumbH    = Math.max(32, trackH * trackH / totalContentHeight);
        int thumbY    = top + (int)((long) scrollOffset * (trackH - thumbH) / maxScroll);
        int x         = screenWidth - 8;
        renderScrollbar(g, x, top, bottom, thumbY, thumbH);
    }

    // ── Panel (inner) scrollbar ───────────────────────────────────────────────

    /*****************************************************************************************************************************************************************
    Inner panel scrollbar rendered to the right of a content panel.

    Parameters:
        panelTop / panelBottom  vertical bounds of the panel
        scrollbarX              absolute X position of the scrollbar track
        scrollOffset            current scroll position (pixels)
        totalContent            total scrollable content height (pixels)
    *****************************************************************************************************************************************************************/
    public static void renderPanelScrollbar(GuiGraphics g,
                                             int panelTop, int panelBottom, int scrollbarX,
                                             int scrollOffset, int totalContent) {
        if (panelBottom <= panelTop) return;
        int visible = panelBottom - panelTop;
        if (totalContent <= visible) return;
        int trackH = panelBottom - panelTop;
        int thumbH = Math.max(12, trackH * visible / totalContent);
        int max    = Math.max(1, totalContent - visible);
        int thumbY = panelTop + (int)((long) scrollOffset * (trackH - thumbH) / max);
        renderScrollbar(g, scrollbarX, panelTop, panelBottom, thumbY, thumbH);
    }

    // ── Core scrollbar renderer ───────────────────────────────────────────────

    /*****************************************************************************************************************************************************************
    Core scrollbar renderer – identical style to MC's AbstractSelectionList:
    - Black track
    - Light-grey thumb with bright left border, dark right border and dark bottom border
    Called directly by HelpGUI which computes thumbY/thumbH itself.
    *****************************************************************************************************************************************************************/
    public static void renderScrollbar(GuiGraphics g,
                                        int x, int top, int bottom,
                                        int thumbY, int thumbH) {
        // Track
        g.fill(x, top, x + 6, bottom, 0xFF000000);
        // Thumb body
        //g.fill(x, thumbY, x + 6, thumbY + thumbH, 0xFF808080, 0xFFC0C0C0);
        // Bright left border
        g.fill(x,     thumbY, x + 5, thumbY + thumbH,     0xFFc0c0c0);
        // Dark right border
        g.fill(x + 5, thumbY, x + 6, thumbY + thumbH - 1, 0xFF808080);
        // Dark bottom border
        g.fill(x, thumbY + thumbH - 1, x + 6, thumbY + thumbH, 0xFF808080);
    }

    // ── Panel border ──────────────────────────────────────────────────────────

    /*****************************************************************************************************************************************************************
    Draws a 1-pixel semi-transparent white border around a content panel.
    left/top/right/bottom are the content coordinates (border is drawn outside).
    *****************************************************************************************************************************************************************/
    public static void drawPanelBorder(GuiGraphics g, int left, int top, int right, int bottom) {
        if (bottom <= top) return;
        g.fill(left - 1, top - 1,  right + 1, top,          0x88FFFFFF); // top edge
        g.fill(left - 1, bottom,   right + 1, bottom + 1,   0x88FFFFFF); // bottom edge
        g.fill(left - 1, top,      left,      bottom,        0x88FFFFFF); // left edge
        g.fill(right,    top,      right + 1, bottom,        0x88FFFFFF); // right edge
    }

    // ── Section header (SettingsGUI style) ────────────────────────────────────

    /*****************************************************************************************************************************************************************
    Renders a coloured section header with a small accent bar on the left.
    Used in SettingsGUI to separate the three settings sections.
    *****************************************************************************************************************************************************************/
    public static void drawSectionHeader(GuiGraphics g, net.minecraft.client.gui.Font font,
                                          int x, int y, String title, int color, int textHeight) {
        g.fill(x, y + 1, x + 2, y + textHeight, color);
        g.drawString(font,
                Component.literal("  " + title)
                        .withStyle(Style.EMPTY.withColor(color).withBold(true)),
                x, y, color, false);
    }

    // ── Multiplayer-Berechtigung ───────────────────────────────────────────────

    /*****************************************************************************************************************************************************************
    Multiplayer-Permission-Check für die Reforge-Editing-GUIs. Nur Spieler mit Permission-Level 2 (OP)
    dürfen Reforges erstellen/bearbeiten/löschen/sperren sowie Settings ändern - gleiche Stufe wie der
    bestehende /reforge-Befehl. Jeder darf diese Screens weiterhin öffnen, um Reforges und ihre Stats
    anzusehen.
    *****************************************************************************************************************************************************************/
    public static boolean canEdit() {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        return player != null && player.hasPermissions(2);
    }
}
