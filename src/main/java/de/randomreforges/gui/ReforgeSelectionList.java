package de.randomreforges.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import de.randomreforges.reforge.Reforge;
import de.randomreforges.reforge.ReforgeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

public class ReforgeSelectionList extends ObjectSelectionList<ReforgeSelectionList.ReforgeEntry> {

    private static final int ENTRY_HEIGHT = 32;
    private static final int ENTRY_WIDTH  = 280;

    public ReforgeSelectionList(Minecraft mc, int screenWidth, int screenHeight, int topY, int bottomY) {
        super(mc, screenWidth, screenHeight, topY, bottomY, ENTRY_HEIGHT);
        this.setRenderBackground(false);
        this.setRenderTopAndBottom(false);
        refresh();
    }

    public void refresh() {
        refresh("", "Reforge ID");
    }

    public void refresh(String query, String mode) {
        this.clearEntries();
        List<Reforge> sorted = new ArrayList<>(ReforgeManager.getAll());
        sorted.sort(Comparator.comparing(Reforge::getId));

        String q = query.toLowerCase().trim();
        for (Reforge r : sorted) {
            if (q.isEmpty() || matches(r, q, mode)) {
                this.addEntry(new ReforgeEntry(r));
            }
        }
    }

    private boolean matches(Reforge r, String query, String mode) {
        return switch (mode) {
            case "Group"        -> r.getAppliesToGroups().stream()
                                    .anyMatch(g -> g.toLowerCase().contains(query));
            case "Display Name" -> r.getName().toLowerCase().contains(query);
            default             -> r.getId().toLowerCase().contains(query); // "Reforge ID"
        };
    }

    @Override public int getRowLeft()          { return (this.width - ENTRY_WIDTH) / 2; }
    @Override public int getRowWidth()         { return ENTRY_WIDTH; }
    @Override protected int getScrollbarPosition() { return this.width - 8; }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_UP        -> { this.setScrollAmount(this.getScrollAmount() - ENTRY_HEIGHT); return true; }
            case GLFW.GLFW_KEY_DOWN      -> { this.setScrollAmount(this.getScrollAmount() + ENTRY_HEIGHT); return true; }
            case GLFW.GLFW_KEY_PAGE_UP   -> { this.setScrollAmount(this.getScrollAmount() - this.height / 2); return true; }
            case GLFW.GLFW_KEY_PAGE_DOWN -> { this.setScrollAmount(this.getScrollAmount() + this.height / 2); return true; }
            case GLFW.GLFW_KEY_HOME      -> { this.setScrollAmount(0); return true; }
            case GLFW.GLFW_KEY_END       -> { this.setScrollAmount(Double.MAX_VALUE); return true; }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }


    // ─────────────────────────────────────────────────────────────────────────
    public class ReforgeEntry extends Entry<ReforgeEntry> {

        private final Reforge reforge;

        ReforgeEntry(Reforge reforge) { this.reforge = reforge; }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left,
                           int width, int height, int mouseX, int mouseY,
                           boolean hovered, float partialTick) {

            var font = Minecraft.getInstance().font;

            if (hovered || ReforgeSelectionList.this.getSelected() == this) {
                graphics.fill(left, top, left + width, top + height, 0x44FFFFFF);
            }

            // Display name (white, left)
            graphics.drawString(font, reforge.getName(), left + 4, top + 4, 0xFFFFFF, false);

            // ID (grey, right)
            String idText = reforge.getId();
            graphics.drawString(font,
                    Component.literal(idText).withStyle(Style.EMPTY.withColor(0x777777)),
                    left + width - font.width(idText) - 4, top + 4, 0xFFFFFF, false);

            // Groups (small italic, bottom-left)
            String groups = String.join(", ", reforge.getAppliesToGroups());
            if (!groups.isEmpty()) {
                graphics.drawString(font,
                        Component.literal(groups).withStyle(Style.EMPTY.withColor(0x555555).withItalic(true)),
                        left + 4, top + 19, 0xFFFFFF, false);
            }

            // Chance (bottom-right)
            String chanceText = buildChanceText();
            int chanceX = left + width - font.width(chanceText) - 4;
            int chanceY = top + 19;
            graphics.drawString(font,
                    Component.literal(chanceText).withStyle(Style.EMPTY.withColor(0x44AA44)),
                    chanceX, chanceY, 0xFFFFFF, false);

            // Tooltip: show all group chances when hovering over the chance text
            if (mouseX >= chanceX && mouseX <= chanceX + font.width(chanceText)
                    && mouseY >= chanceY && mouseY <= chanceY + font.lineHeight) {
                java.util.List<Component> tooltip = buildChanceTooltip();
                if (!tooltip.isEmpty()) {
                    graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
                }
            }
        }

        private java.util.List<Component> buildChanceTooltip() {
            java.util.List<Reforge> all = new java.util.ArrayList<>(ReforgeManager.getAll());
            java.util.List<String> realGroups = List.of(
                "WEAPON", "ARMOR", "TOOL", "BOOTS", "HELMETS",
                "CHESTPLATES", "LEGGINGS", "SPELLBOOKS", "SHIELD", "CURIO"
            );

            List<String> groups = reforge.getAppliesToGroups();
            java.util.List<String> effectiveGroups = groups.contains("ANY")
                    ? realGroups : groups;

            java.util.List<Component> lines = new java.util.ArrayList<>();
            lines.add(Component.literal("Chance per Group:")
                    .withStyle(Style.EMPTY.withColor(0xAAAAAA).withUnderlined(true)));

            for (String group : effectiveGroups) {
                int totalWeight = all.stream()
                        .filter(r -> appliesToGroup(r, group))
                        .mapToInt(Reforge::getWeight)
                        .sum();

                if (totalWeight <= 0) continue;

                double chance = (double) reforge.getWeight() / totalWeight * 100.0;
                String pct = chance <= 0 ? "" :
                             chance < 0.01
                        ? "<0.01%"
                        : chance >= 1
                        ? String.format("%.1f%%", chance)
                        : String.format("%.2f%%", chance);

                lines.add(Component.literal(group + ": ")
                        .withStyle(Style.EMPTY.withColor(0x777777))
                        .append(Component.literal(pct)
                                .withStyle(Style.EMPTY.withColor(0x44AA44))));
            }

            return lines;
        }

        /**
         * Returns true if the given reforge can appear on items of the given group.
         * Accounts for ANY and the ARMOR family (ARMOR covers all sub-groups and vice versa).
         */
        private boolean appliesToGroup(Reforge r, String group) {
            List<String> g = r.getAppliesToGroups();
            if (g.contains("ANY")) return true;
            if (g.contains(group)) return true;

            // ARMOR family: ARMOR covers all sub-groups
            java.util.Set<String> armorFamily = java.util.Set.of(
                "ARMOR", "BOOTS", "HELMETS", "CHESTPLATES", "LEGGINGS"
            );
            if (armorFamily.contains(group) && g.stream().anyMatch(armorFamily::contains)) return true;

            return false;
        }

        private String buildChanceText() {
            List<String> groups = reforge.getAppliesToGroups();
            if (groups.isEmpty()) return "";

            java.util.List<Reforge> all = new java.util.ArrayList<>(ReforgeManager.getAll());
            java.util.List<String> realGroups = List.of(
                "WEAPON", "ARMOR", "TOOL", "BOOTS", "HELMETS",
                "CHESTPLATES", "LEGGINGS", "SPELLBOOKS", "SHIELD", "CURIO"
            );

            java.util.List<String> effectiveGroups = groups.contains("ANY") ? realGroups : groups;

            double maxChance = 0;
            String maxGroup  = "";

            for (String group : effectiveGroups) {
                int totalWeight = all.stream()
                        .filter(r -> appliesToGroup(r, group))
                        .mapToInt(Reforge::getWeight)
                        .sum();

                if (totalWeight <= 0) continue;

                double chance = (double) reforge.getWeight() / totalWeight * 100.0;
                if (chance > maxChance) {
                    maxChance = chance;
                    maxGroup  = group;
                }
            }

            if (maxChance <= 0) return "";

            String pct = maxChance <= 0 ? "" :
                         maxChance < 0.01
                    ? "<0.01%"
                    : maxChance >= 1
                    ? String.format("%.1f%%", maxChance)
                    : String.format("%.2f%%", maxChance);

            return effectiveGroups.size() == 1 ? pct : pct + " (" + maxGroup + ")";
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            ReforgeSelectionList.this.setSelected(this);
            // Open EditReforgeGUI on click
            Minecraft.getInstance().setScreen(new ReforgeEditorGUI(reforge));
            return true;
        }

        @Override
        public Component getNarration() { return Component.literal(reforge.getName()); }
    }
    
}