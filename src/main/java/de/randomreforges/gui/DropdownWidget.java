package de.randomreforges.gui;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;


/*****************************************************************************************************************************************************************
Dropdown widget used in ReforgeEditorGUI to select the operators (Will also be used in SettingsGUI later)
Important! Always after super.render() to render it on top of everything

Click to open, click to select - I mean, how else, lol
*****************************************************************************************************************************************************************/

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

        //Main button -> Open/Close
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
            // Click outside -> close
            open = false;
        }
        return false;
    }

    
    public void renderDropdown(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!open) return;

        int x = getX();
        int y = getY() + height;
        int w = width;

        //graphic stuff (bg and border)
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

        //button bg
        int bg = (isHovered && !open) ? 0xFF555555 : 0xFF333333;
        graphics.fill(getX(), getY(), getX() + width, getY() + height, 0xFF888888);
        graphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, bg);

        //show selected value
        String label = options.get(selectedIndex);
        graphics.drawString(font, label, getX() + 4, getY() + (height - 8) / 2, TEXT_COLOR, false);

        //arrow up (open)/down (closed)
        graphics.drawString(font, open ? "▲" : "▼", getX() + width - 12, getY() + (height - 8) / 2, ARROW_COLOR, false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {}
}
