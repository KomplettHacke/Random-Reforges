package de.randomreforges.gui;

import de.randomreforges.gui.GUIConstructor;
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
        super(Component.literal("Help"));
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

        //Fixed title – outside the scrollable area, like ReforgeListGUI
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, TEXT_COLOR);

        //Scrollable content
        int contentTop    = 30;
        int contentBottom = this.height - 36;

        //only work in the scrollable area
        graphics.enableScissor(0, contentTop, this.width, contentBottom);

        int x = PADDING;
        int y = contentTop - scrollOffset;  // shift everything up by scrollOffset

        //*** Reforge ID ******************************************************
        y = section(graphics, x, y, "Reforge ID",
            "An unique internal identifier for the reforge.",
            "Only lowercase letters, numbers and underscores are allowed.",
            "Example:  armor_corrupted,  weapon_heavy"
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
            "  Attribute ID     ||The registry name of the attribute",
            "                   ||minecraft:generic.attack_damage",
            "                   ||irons_spellbooks:max_mana",
            null,
            "                   ||The attribute probably does not exist if it has",
            "                   ||not been translated correctly in the tooltip.",
            null,
            "  Value            ||The numeric modifier amount (can be negative)",
            null,
            "  Operator         ||How the value is applied:",
            "    ADD            ||Adds a flat value to the base stat.",
            "    MULTIPLY_BASE  ||Multiplies the base stat (before other bonuses).",
            "    MULTIPLY_TOTAL ||Multiplies the final stat (after all bonuses).",
            "    SCALED ||Adds a flat value (optional) and",
            "  ||gives a bonus depending on another attribute.",
            null,
            "  Perfect reforged ||Whenever you reforge something, there will be",
            "                   ||a low chance to get a perfect reforge. They",
            "                   ||have no debuffs and their buffs are stronger."
        );

        //*** /reforge ******************************************************
        y = section(graphics, x, y, "/reforge Command",
            "This command is used to modify the reforges on any item. This happens",
            "regardless of any \"appliesTo\" groups or rules.",
            null,
            "Command structure:",
            "  /reforge <player>||Always starts with that.",
            "    apply <ID> [Slot*]||Applies the reforge on the selected",
            "||item.",
            "    clear <reforge> [Slot*]||Removes the reforge on the selected",
            "||item.",
            "    lock [Slot*]||Makes the reforge unchangeable (except commands)",
            "Modded slots (from CuriosAPI) will get detected and added as possible slots",
            "automatically.",
            "*Optional, default: held item."
        );

        //*** Hardmode ******************************************************
        y = section(graphics, x, y, "Hardmode",
            "Hardmode is an optional difficulty mode enabled in the settings.",
            "It affects reforging in two ways:",
            null,
            "  Lock Chance      ||After reforging at the cage, there is a",
            "  ||configurable % chance the item gets permanently",
            "  ||locked. It can no longer be reforged at the cage.",
            "  ||Use /reforge apply to override the lock.",
            null,
            "  Debuff Variance  ||Debuff (negative) attributes are randomly",
            "  ||scaled by a multiplier each time a reforge is",
            "  ||applied. The multiplier is:",
            "  ||  1 + random(Min%, Max%) / 100",
            "  ||Example: Min = -50, Max = 50 means debuffs",
            "  ||can be anywhere from 50% to 150% of normal.",
            "  ||Negative Min values can reduce or even negate",
            "  ||debuffs entirely.",
            null,
            "  Perfect + Hardmode  ||In hardmode, perfect reforges no longer",
            "  ||remove debuffs. All attributes are applied,",
            "  ||including debuffs (with variance applied).",
            "  ||Positive attributes still get the bonus."
        );


        //total content height (y is now past the last line, before offset)
        contentHeight = (y + scrollOffset) - contentTop;

        int visibleHeight = contentBottom - contentTop;

        // Only render scrollbar when content actually overflows – avoids division by zero
        // when contentHeight == visibleHeight and prevents rendering a useless bar.
        if (contentHeight > visibleHeight) {
            int thumbH = Math.max(32, visibleHeight * visibleHeight / contentHeight);
            int thumbY = contentTop + (int)((long) scrollOffset * (visibleHeight - thumbH) / (contentHeight - visibleHeight));
            GUIConstructor.renderScrollbar(graphics, this.width - 8, contentTop, contentBottom, thumbY, thumbH);
        }

        graphics.disableScissor();

        //render close button in fixed area
        super.render(graphics, mouseX, mouseY, partialTick);
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

    /*****************************************************************************************************************************************************************
    Used to render all the articles in the help menu
    - "||" splits a line into a fixed part (left side) and th right columns.
        - right columns are moved in by 150px (custom tabstop)
    *****************************************************************************************************************************************************************/

    private int maxScroll() {
        int visibleHeight = this.height - 36 - 30;
        return Math.max(0, contentHeight - visibleHeight);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Section helper
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Renders a labelled section with indented lines.
     * Pass null as a line to insert a blank line.
     */
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

        //small gap after each section
        y += 6;
        return y;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
