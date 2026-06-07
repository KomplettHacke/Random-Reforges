package de.randomreforges.gui;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

public class HelpGUI extends Screen {
    /*****************************************************************************************************************************************************************
    - Can be accessed via a button in the top right corner in the main page of the GUI (check ReforgeListGUI)
    - explains the mechanics of the mod
    - 1:1 README.md but formatted
    - Splitted in 3 parts:
        Top:    reserved for title
        Middle: actual content (scrollable)
        Footer: for all the buttons
    *****************************************************************************************************************************************************************/
    private static final int TEXT_COLOR   = 0xFFFFFF;
    private static final int HEADER_COLOR = 0xFFDD00;
    private static final int MUTED_COLOR  = 0xAAAAAA;
    private static final int CODE_COLOR   = 0x88FF88;
    private static final int PADDING      = 20;
    private static final int LINE_HEIGHT  = 11;
    private static final int SCROLL_SPEED = LINE_HEIGHT * 3;


    private int scrollOffset = 0;
    private int contentHeight = 0;

    public HelpGUI() {
        super(Component.literal("Help"));        //TODO: Titel verschwindet immer wieder. In 3.0.2 beheben. Nochmal alle GUIs genau überprüfen
    }

    @Override
    protected void init() {
        // Close button – fixed at bottom, outside the scroll area
        this.addRenderableWidget(Button.builder(Component.literal("Close"),
                        btn -> this.minecraft.setScreen(new ReforgeListGUI()))
                .pos(this.width / 2 - 40, this.height - 28)
                .size(80, 20)
                .build());
    }

    /*****************************************************************************************************************************************************************
    Render
    *****************************************************************************************************************************************************************/

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        //Scrollable content - TODO: Fix the var names and the pixels (check ReforgeListGUI)
        int contentTop    = 14;
        int contentBottom = this.height - 36;

        //only work in the scrollable area
        graphics.enableScissor(0, contentTop, this.width, contentBottom);

        int x = PADDING;
        int y = contentTop - scrollOffset;  // shift everything up by scrollOffset

        //*** Title ******************************************************
        graphics.drawCenteredString(this.font, this.title, this.width / 2, y, TEXT_COLOR);
        y += LINE_HEIGHT + 6;

        //*** Reforge ID ******************************************************
        y = section(graphics, x, y, "Reforge ID",
            "An unique internal identifier for the reforge.",
            "Only lowercase letters, numbers and underscores are allowed.",
            "Example:  armor_corrupted,  weapon_heavy",
            null,
            "Used by the /reforge apply <id> command to force-apply a specific reforge."
        );

        //*** Display Name ******************************************************
        y = section(graphics, x, y, "Display Name",
            "The name shown in-game on the item and in the reforge list.",
            "Example:  Corrupted,  Legendary"
        );

        //*** Weight ******************************************************
        y = section(graphics, x, y, "Weight (Chance)",
            "Controls how often this reforge is rolled compared to others.",
            "  0||→  never appears",
            "  100 000||→  median (appears at roughly average frequency)",
            "  1 000 000||→  very common",
            "  1||→  extremely rare",
            "The chance is relative: if all reforges have weight 100 000,",
            "each has an equal probability."
        );

        //*** Reforge List ******************************************************
        y = section(graphics, x, y, "Reforge List",
            "The Reforge List shows all loaded reforges.",
            "Each entry displays:",
            "  Display Name ||(top left)",
            "  Reforge ID   ||(top right)",
            "  Group(s)     ||(bottom left)",
            "  Chance       ||(bottom right)",
            null,
            "The chance shows how likely this reforge is to be",
            "rolled compared to all others in the same group.",
            null,
            "Hover over the green chance percentage to see",
            "the exact chance for every group this reforge",
            "can appear in."
        );

        //*** Tooltip comments ******************************************************
        y = section(graphics, x, y, "Comment",
            "Optional text shown below the item name in the tooltip.",
            "Leave empty for no comment."
        );

        //*** Applies To ******************************************************
        y = section(graphics, x, y, "Applies To",
            "Defines which items can receive this reforge.",
            "  WEAPON",
            "  ARMOR",
            "  TOOL",
            "  BOOTS",
            "  HELMETS",
            "  CHESTPLATES",
            "  LEGGINGS",
            "  SHIELD",
            "  SPELLBOOKS",
            "  CURIO",
            "  ANY",
            null,
            "Custom input field — comma-separated entries:",
            "  mod_id:item_id ||→  Single specific item",
            "  #mod_id:item_tag ||→  All items in a tag",
            "  !mod_id:item_id ||→  Blacklist a specific item",
            "  !#mod_id:item_tag ||→  Blacklist all items in a tag",
            null,
            "Note: \"Applies To\" does not restrict the /reforge apply command."
        );

        //*** Attributes and operators ******************************************************
        y = section(graphics, x, y, "Attributes",
            "Stat modifiers granted by the reforge when the item is equipped.",
            "Each attribute entry has three fields:",
            null,
            "  Attribute ID ||The registry name of the attribute",
            "  ||minecraft:generic.attack_damage",
            "  ||irons_spellbooks:max_mana",
            null,
            "  ||The attribute probably does not exist if it has",
            "  ||not been translated correctly in the tooltip.",
            null,
            "  Value ||The numeric modifier amount (can be negative)",
            null,
            "  Operator ||How the value is applied:",
            "    ADD ||Adds a flat value to the base stat.",
            "    MULTIPLY_BASE ||Multiplies the base stat (before other bonuses).",
            "    MULTIPLY_TOTAL ||Multiplies the final stat (after all bonuses).",
            "    SCALED ||Adds a flat value (optional) and",
            "  ||gives a bonus depending on another attribute."
        );

        //total content height (y is now past the last line, before offset)
        contentHeight = (y + scrollOffset) - contentTop;

        graphics.disableScissor();

        renderScrollbar(graphics, contentTop, contentBottom);

        //render close button in fixed area
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /*****************************************************************************************************************************************************************
    Used in every GUI
    TODO: own file -> no more logic duplicates    (Jede Scrollbar sieht unterschiedlich aus... DRINGEND BEHEBEN! 3.0.2!)
    *****************************************************************************************************************************************************************/
    
    private void renderScrollbar(GuiGraphics graphics, int top, int bottom) {
        int visibleHeight = bottom - top;
        if (contentHeight <= visibleHeight) return; // no scrollbar needed

        int barX         = this.width - 6;
        int trackHeight  = visibleHeight;
        int thumbHeight  = Math.max(20, trackHeight * visibleHeight / contentHeight);
        int maxScroll    = contentHeight - visibleHeight;
        int thumbY       = top + (scrollOffset * (trackHeight - thumbHeight)) / maxScroll;

        //scroll track
        graphics.fill(barX, top, barX + 4, bottom, 0x44FFFFFF);
        //scroll thumb
        graphics.fill(barX, thumbY, barX + 4, thumbY + thumbHeight, 0xAAFFFFFF);
    }

    //Scroll function
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll((int) (-delta * SCROLL_SPEED));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_UP        -> scroll(-LINE_HEIGHT);
            case GLFW.GLFW_KEY_DOWN      -> scroll(LINE_HEIGHT);
            case GLFW.GLFW_KEY_PAGE_UP   -> scroll(-(this.height / 2));
            case GLFW.GLFW_KEY_PAGE_DOWN -> scroll(this.height / 2);
            case GLFW.GLFW_KEY_HOME      -> scrollOffset = 0;
            case GLFW.GLFW_KEY_END       -> scrollOffset = maxScroll();
            default -> { return super.keyPressed(keyCode, scanCode, modifiers); }
        }
        return true;
    }

    private void scroll(int amount) {
        scrollOffset = Math.max(0, Math.min(scrollOffset + amount, maxScroll()));
    }

    private int maxScroll() {
        int visibleHeight = this.height - 36 - 14;
        return Math.max(0, contentHeight - visibleHeight);
    }

    /*****************************************************************************************************************************************************************
    Used to render all the articles in the help menu
    - "||" splits a line into a fixed part (left side) and th right columns.
        - right columns are moved in by 150px (custom tabstop)
    *****************************************************************************************************************************************************************/
    private int section(GuiGraphics g, int x, int startY,
                        String header, String... lines) {
        int y = startY;

        // Header
        g.drawString(this.font,
                Component.literal("▶ " + header)
                        .withStyle(Style.EMPTY.withColor(HEADER_COLOR).withBold(true)),
                x, y, HEADER_COLOR, false);
        y += LINE_HEIGHT + 2;

        for (String line : lines) {
            if (line == null) {
                y += LINE_HEIGHT / 2;
                continue;
            }

            if (line.contains("||")) {
                String[] parts = line.split("\\|\\|", 2);
                g.drawString(this.font,
                        Component.literal(parts[0]).withStyle(Style.EMPTY.withColor(CODE_COLOR)),
                        x + 6, y, CODE_COLOR, false);
                g.drawString(this.font,
                        Component.literal(parts[1]).withStyle(Style.EMPTY.withColor(CODE_COLOR)),
                        x + 150, y, CODE_COLOR, false);
                y += LINE_HEIGHT;
                continue;
            }
            boolean isExample = line.startsWith("  ");
            int color = isExample ? CODE_COLOR : MUTED_COLOR;
            g.drawString(this.font,
                    Component.literal(line).withStyle(Style.EMPTY.withColor(color)),
                    x + 6, y, color, false);
            y += LINE_HEIGHT;
        }
        //Small gap after each section
        y += 6;
        return y;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
