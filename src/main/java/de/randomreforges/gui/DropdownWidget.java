package de.randomreforges.gui;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * Simple dropdown widget. Click to open, click option to select.
 * Renders the dropdown list on top of other widgets, so call renderDropdown()
 * AFTER super.render() in your Screen.
 */
public class DropdownWidget extends AbstractWidget {

    private final List<String> options;
    private int selectedIndex = 0;
    private boolean open = false;

    private static final int OPTION_HEIGHT = 14;
    private static final int BG_COLOR      = 0xFF2A2A2A;
    private static final int HOVER_COLOR   = 0xFF444444;
    private static final int BORDER_COLOR  = 0xFF888888;
    private static final int TEXT_COLOR    = 0xFFFFFFFF;
    private static final int ARROW_COLOR   = 0xFFAAAAAA;

    public DropdownWidget(int x, int y, int width, int height, List<String> options) {
        super(x, y, width, height, Component.empty());
        this.options = options;
    }

    public String getSelected() {
        return options.get(selectedIndex);
    }

    public void setSelected(String value) {
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).equals(value)) { selectedIndex = i; return; }
        }
    }

    public boolean isOpen() { return open; }
    public List<String> getOptions() { return options; }
    public void close()     { open = false; }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!active || !visible) return false;

        // Click on main button → toggle open/close
        if (isMouseOver(mouseX, mouseY)) {
            open = !open;
            return true;
        }

        // Click on an option while open
        if (open) {
            for (int i = 0; i < options.size(); i++) {
                int optY = getY() + height + i * OPTION_HEIGHT;
                if (mouseX >= getX() && mouseX <= getX() + width
                        && mouseY >= optY && mouseY <= optY + OPTION_HEIGHT) {
                    selectedIndex = i;
                    open = false;
                    return true;
                }
            }
            // Click outside → close
            open = false;
        }
        return false;
    }

    /** Call this after all other widgets are rendered so the list appears on top. */
    public void renderDropdown(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!open) return;

        int x = getX();
        int y = getY() + height;
        int w = width;

        // Background + border
        graphics.fill(x - 1,     y - 1,          x + w + 1, y + options.size() * OPTION_HEIGHT + 1, BORDER_COLOR);
        graphics.fill(x,         y,               x + w,     y + options.size() * OPTION_HEIGHT,     BG_COLOR);

        var font = Minecraft.getInstance().font;
        for (int i = 0; i < options.size(); i++) {
            int optY = y + i * OPTION_HEIGHT;
            boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= optY && mouseY <= optY + OPTION_HEIGHT;
            if (hovered || i == selectedIndex) {
                graphics.fill(x, optY, x + w, optY + OPTION_HEIGHT, HOVER_COLOR);
            }
            graphics.drawString(font, options.get(i), x + 3, optY + 3, i == selectedIndex ? 0xFFFFDD00 : TEXT_COLOR, false);
        }
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var font = Minecraft.getInstance().font;

        // Button background
        int bg = (isHovered && !open) ? 0xFF555555 : 0xFF333333;
        graphics.fill(getX(), getY(), getX() + width, getY() + height, 0xFF888888);
        graphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, bg);

        // Selected value
        String label = options.get(selectedIndex);
        graphics.drawString(font, label, getX() + 4, getY() + (height - 8) / 2, TEXT_COLOR, false);

        // Arrow ▼
        graphics.drawString(font, open ? "▲" : "▼", getX() + width - 12, getY() + (height - 8) / 2, ARROW_COLOR, false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {}
}