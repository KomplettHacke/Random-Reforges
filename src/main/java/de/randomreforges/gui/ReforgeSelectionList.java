package de.randomreforges.gui;

import de.randomreforges.gui.ReforgeEditorGUI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.lwjgl.glfw.GLFW;

import de.randomreforges.reforge.Reforge;
import de.randomreforges.reforge.ReforgeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

public class ReforgeSelectionList extends ObjectSelectionList<ReforgeSelectionList.ReforgeEntry> {

    private static final int ENTRY_HEIGHT  = 32;
    private static final int ENTRY_WIDTH   = 280;
    //*** Lock checkbox layout constants ******************************************************
    // Checkbox occupies [left+2 .. left+17], content (name/groups) starts at left+20
    private static final int LOCK_AREA_END = 20;  // X offset where clickable lock area ends
    private static final int CB_OFFSET_X   = 3;   // left padding before box
    private static final int CB_OFFSET_Y   = 8;   // top padding (centers 16px icon in 32px row)
    private static final int CB_SIZE       = 16;  // icon size in pixels (matches texture dimensions)

    //*** Lock icon textures ******************************************************
    private static final net.minecraft.resources.ResourceLocation TEXTURE_LOCKED =
            new net.minecraft.resources.ResourceLocation("randomreforges", "textures/gui/reforge_locked.png");
    private static final net.minecraft.resources.ResourceLocation TEXTURE_UNLOCKED =
            new net.minecraft.resources.ResourceLocation("randomreforges", "textures/gui/reforge_unlocked.png");

    //*** ARMOR family used in appliesToGroup – static to avoid per-call allocation ******************************************************
    private static final Set<String> ARMOR_FAMILY = Set.of(
            "ARMOR", "BOOTS", "HELMETS", "CHESTPLATES", "LEGGINGS"
    );

    //*** Last filter state – needed to refresh after lock toggles ******************************************************
    private String lastQuery = "";
    private String lastMode  = "Reforge ID";

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
        this.lastQuery = query;
        this.lastMode  = mode;
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
            default             -> r.getId().toLowerCase().contains(query);
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


    public class ReforgeEntry extends Entry<ReforgeEntry> {

        private final Reforge reforge;

        //*** Cached per-entry chance data – computed once in constructor, valid for the lifetime of this entry ******************************************************
        // Chance calculations exclude locked reforges from the total weight so displayed percentages
        // reflect actual rolling odds. Locked reforges themselves show an empty chance string.
        private final String             cachedChanceText;
        private final List<Component>    cachedChanceTooltip;

        ReforgeEntry(Reforge reforge) {
            this.reforge = reforge;

            if (ReforgeManager.isLocked(reforge.getId())) {
                // Locked reforges don't appear in rolls – no meaningful chance to display
                this.cachedChanceText    = "";
                this.cachedChanceTooltip = List.of();
            } else {
                List<Reforge> all = new ArrayList<>(ReforgeManager.getAll());
                this.cachedChanceText    = computeChanceText(all);
                this.cachedChanceTooltip = computeChanceTooltip(all);
            }
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left,
                           int width, int height, int mouseX, int mouseY,
                           boolean hovered, float partialTick) {

            var font = Minecraft.getInstance().font;

            if (hovered || ReforgeSelectionList.this.getSelected() == this) {
                graphics.fill(left, top, left + width, top + height, 0x44FFFFFF);
            }

            //*** Lock icon ******************************************************
            boolean locked = ReforgeManager.isLocked(reforge.getId());
            int cbX = left + CB_OFFSET_X;
            int cbY = top  + CB_OFFSET_Y;
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
            graphics.blit(locked ? TEXTURE_LOCKED : TEXTURE_UNLOCKED,
                    cbX, cbY, 0, 0, CB_SIZE, CB_SIZE, CB_SIZE, CB_SIZE);
            com.mojang.blaze3d.systems.RenderSystem.disableBlend();

            //*** Lock checkbox tooltip ******************************************************
            boolean cbHovered = mouseX >= cbX && mouseX <= cbX + CB_SIZE
                    && mouseY >= cbY && mouseY <= cbY + CB_SIZE;
            if (cbHovered) {
                String tip = locked ? "Click to unlock (allow rolling)" : "Click to lock (prevent rolling)";
                graphics.renderTooltip(font, Component.literal(tip), mouseX, mouseY);
            }

            //Display name (white, left)
            graphics.drawString(font, reforge.getName(), left + LOCK_AREA_END, top + 4, 0xFFFFFF, false);

            //ID (grey, right)
            String idText = reforge.getId();
            graphics.drawString(font,
                    Component.literal(idText).withStyle(Style.EMPTY.withColor(0x777777)),
                    left + width - font.width(idText) - 4, top + 4, 0xFFFFFF, false);

            //Groups (small italic, bottom-left)
            String groups = String.join(", ", reforge.getAppliesToGroups());
            if (!groups.isEmpty()) {
                graphics.drawString(font,
                        Component.literal(groups).withStyle(Style.EMPTY.withColor(0x555555).withItalic(true)),
                        left + LOCK_AREA_END, top + 19, 0xFFFFFF, false);
            }

            //Chance (bottom-right) – cached value, reflects actual rolling odds
            int chanceX = left + width - font.width(cachedChanceText) - 4;
            int chanceY = top + 19;
            if (!cachedChanceText.isEmpty()) {
                graphics.drawString(font,
                        Component.literal(cachedChanceText).withStyle(Style.EMPTY.withColor(0x44AA44)),
                        chanceX, chanceY, 0xFFFFFF, false);

                //Tooltip: show all group chances when hovering over the chance text
                if (mouseX >= chanceX && mouseX <= chanceX + font.width(cachedChanceText)
                        && mouseY >= chanceY && mouseY <= chanceY + font.lineHeight) {
                    if (!cachedChanceTooltip.isEmpty()) {
                        graphics.renderComponentTooltip(font, cachedChanceTooltip, mouseX, mouseY);
                    }
                }
            }
        }

        /*****************************************************************************************************************************************************************
        Task: Check if the reforge can appear on the group.

        Special handling for ARMOR and ANY
        *****************************************************************************************************************************************************************/
        private boolean appliesToGroup(Reforge r, String group) {
            List<String> g = r.getAppliesToGroups();
            if (g.contains("ANY")) return true;
            if (g.contains(group)) return true;
            if (ARMOR_FAMILY.contains(group) && g.stream().anyMatch(ARMOR_FAMILY::contains)) return true;
            return false;
        }

        /*****************************************************************************************************************************************************************
        Chance helpers – called once in the constructor, not per frame.
        Locked reforges are excluded from total weight so the percentage reflects actual rolling odds.
        *****************************************************************************************************************************************************************/
        private String computeChanceText(List<Reforge> all) {
            List<String> groups = reforge.getAppliesToGroups();
            if (groups.isEmpty()) return "";

            List<String> realGroups = List.of(
                "WEAPON", "ARMOR", "TOOL", "BOOTS", "HELMETS",
                "CHESTPLATES", "LEGGINGS", "SPELLBOOKS", "SHIELD", "CURIO"
            );
            List<String> effectiveGroups = groups.contains("ANY") ? realGroups : groups;

            double maxChance = 0;
            String maxGroup  = "";

            for (String group : effectiveGroups) {
                int totalWeight = all.stream()
                        .filter(r -> appliesToGroup(r, group))
                        .filter(r -> !ReforgeManager.isLocked(r.getId()))
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

            String pct = maxChance < 0.01
                    ? "<0.01%"
                    : maxChance >= 1
                    ? String.format("%.1f%%", maxChance)
                    : String.format("%.2f%%", maxChance);

            return effectiveGroups.size() == 1 ? pct : pct + " (" + maxGroup + ")";
        }

        private List<Component> computeChanceTooltip(List<Reforge> all) {
            List<String> realGroups = List.of(
                "WEAPON", "ARMOR", "TOOL", "BOOTS", "HELMETS",
                "CHESTPLATES", "LEGGINGS", "SPELLBOOKS", "SHIELD", "CURIO"
            );

            List<String> groups = reforge.getAppliesToGroups();
            List<String> effectiveGroups = groups.contains("ANY") ? realGroups : groups;

            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal("Chance per Group:")
                    .withStyle(Style.EMPTY.withColor(0xAAAAAA).withUnderlined(true)));

            for (String group : effectiveGroups) {
                int totalWeight = all.stream()
                        .filter(r -> appliesToGroup(r, group))
                        .filter(r -> !ReforgeManager.isLocked(r.getId()))
                        .mapToInt(Reforge::getWeight)
                        .sum();
                if (totalWeight <= 0) continue;

                double chance = (double) reforge.getWeight() / totalWeight * 100.0;
                String pct = chance < 0.01
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

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            //*** Lock checkbox click – only check X since ObjectSelectionList already confirmed the Y is in this row ******************************************************
            int rowLeft = ReforgeSelectionList.this.getRowLeft();
            if (mouseX >= rowLeft && mouseX <= rowLeft + LOCK_AREA_END) {
                //multiplayer op required:
                //no local state save, everything handled on server and synchronized with client
                if (GUIConstructor.canEdit()) {
                        de.randomreforges.network.NetworkHandler.CHANNEL.sendToServer(
                            new de.randomreforges.network.ServerBoundPackets.ToggleLockPacket(reforge.getId()));
                }
                return true;
            }

            ReforgeSelectionList.this.setSelected(this);
            Minecraft.getInstance().setScreen(new ReforgeEditorGUI(reforge));
            return true;
        }

        @Override
        public Component getNarration() { return Component.literal(reforge.getName()); }
    }
    
}
