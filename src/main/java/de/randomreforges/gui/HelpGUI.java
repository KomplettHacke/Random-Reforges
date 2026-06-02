package de.randomreforges.gui;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

public class HelpGUI extends Screen {

    private static final int TEXT_COLOR   = 0xFFFFFF;
    private static final int HEADER_COLOR = 0xFFDD00;
    private static final int MUTED_COLOR  = 0xAAAAAA;
    private static final int CODE_COLOR   = 0x88FF88;
    private static final int PADDING      = 20;
    private static final int LINE_HEIGHT  = 11;
    private static final int SCROLL_SPEED = LINE_HEIGHT * 3;

    /** Current scroll offset in pixels (always >= 0). */
    private int scrollOffset = 0;

    /** Total height of all rendered content – measured on first render. */
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

    // ─────────────────────────────────────────────────────────────────────────
    // Render
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        // Scrollable content area: top=14, bottom=this.height-36 (leaves room for Close button)
        int contentTop    = 14;
        int contentBottom = this.height - 36;

        // Enable scissor so content is clipped to the scroll area
        graphics.enableScissor(0, contentTop, this.width, contentBottom);

        int x = PADDING;
        int y = contentTop - scrollOffset;  // shift everything up by scrollOffset

        // Title
        graphics.drawCenteredString(this.font, this.title, this.width / 2, y, TEXT_COLOR);
        y += LINE_HEIGHT + 6;

        // ── Reforge ID ────────────────────────────────────────────────────────
        y = section(graphics, x, y, "Reforge ID",
            "An unique internal identifier for the reforge.",
            "Only lowercase letters, numbers and underscores are allowed.",
            "Example:  armor_corrupted,  weapon_heavy",
            null,
            "Used by the /reforge apply <id> command to force-apply a specific reforge."
        );

        // ── Display Name ──────────────────────────────────────────────────────
        y = section(graphics, x, y, "Display Name",
            "The name shown in-game on the item and in the reforge list.",
            "Example:  Corrupted,  Legendary"
        );

        // ── Weight (Chance) ───────────────────────────────────────────────────
        y = section(graphics, x, y, "Weight (Chance)",
            "Controls how often this reforge is rolled compared to others.",
            "  0||→  never appears",
            "  100 000||→  median (appears at roughly average frequency)",
            "  1 000 000||→  very common",
            "  1||→  extremely rare",
            "The chance is relative: if all reforges have weight 100 000,",
            "each has an equal probability."
        );

        // ── Reforge List ──────────────────────────────────────────────────────
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

        // ── Comment ───────────────────────────────────────────────────────────
        y = section(graphics, x, y, "Comment",
            "Optional text shown below the item name in the tooltip.",
            "Leave empty for no comment."
        );

        // ── Applies To ────────────────────────────────────────────────────────
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

        // ── Attributes ────────────────────────────────────────────────────────
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

        // Record total content height (y is now past the last line, before offset)
        contentHeight = (y + scrollOffset) - contentTop;

        graphics.disableScissor();

        // Scrollbar
        renderScrollbar(graphics, contentTop, contentBottom);

        // Render fixed widgets (Close button) on top
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scrollbar
    // ─────────────────────────────────────────────────────────────────────────

    private void renderScrollbar(GuiGraphics graphics, int top, int bottom) {
        int visibleHeight = bottom - top;
        if (contentHeight <= visibleHeight) return; // no scrollbar needed

        int barX         = this.width - 6;
        int trackHeight  = visibleHeight;
        int thumbHeight  = Math.max(20, trackHeight * visibleHeight / contentHeight);
        int maxScroll    = contentHeight - visibleHeight;
        int thumbY       = top + (scrollOffset * (trackHeight - thumbHeight)) / maxScroll;

        // Track
        graphics.fill(barX, top, barX + 4, bottom, 0x44FFFFFF);
        // Thumb
        graphics.fill(barX, thumbY, barX + 4, thumbY + thumbHeight, 0xAAFFFFFF);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scroll input
    // ─────────────────────────────────────────────────────────────────────────

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
            // "||" splits a line into left (fixed) and right columns
            if (line.contains("||")) {
                String[] parts = line.split("\\|\\|", 2);
                g.drawString(this.font,
                        Component.literal(parts[0]).withStyle(Style.EMPTY.withColor(CODE_COLOR)),
                        x + 6, y, CODE_COLOR, false);
                //fixed X = x + 150
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

        y += 6; // gap after section
        return y;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}