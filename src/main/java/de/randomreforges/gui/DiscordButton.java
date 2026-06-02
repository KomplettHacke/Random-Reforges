package de.randomreforges.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class DiscordButton extends AbstractButton {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("randomreforges", "textures/gui/discord.png");

    private static final String DISCORD_URL = "https://discord.com/invite/DDgKjEcb8N";

    public DiscordButton(int x, int y) {
        super(x, y, 20, 20, Component.literal("Discord"));
    }

    @Override
    public void onPress() {
        Util.getPlatform().openUri(DISCORD_URL);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int bg = isHovered ? 0xFF555555 : 0xFF333333;
        graphics.fill(getX(),     getY(),     getX() + width,     getY() + height,     0xFF888888);
        graphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, bg);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(TEXTURE,
                getX() + 2, getY() + 2,
                0, 0,
                16, 16,
                16, 16
        );
        RenderSystem.disableBlend();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, Component.literal("Discord"));
    }
}