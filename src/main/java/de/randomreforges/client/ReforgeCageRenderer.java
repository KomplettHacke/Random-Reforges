package de.randomreforges.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import de.randomreforges.RandomReforges;
import de.randomreforges.blockentity.ReforgeCageBlockEntity;
import de.randomreforges.blockentity.ReforgeCageModel;
import de.randomreforges.registry.BlockEntityRegistry;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;



/*****************************************************************************************************************************************************************
Renders the Reforge Cage BE core animation
*****************************************************************************************************************************************************************/
public class ReforgeCageRenderer implements BlockEntityRenderer<ReforgeCageBlockEntity> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(RandomReforges.MODID, "textures/block/reforge_cage.png");

    private final ReforgeCageModel model;

    public ReforgeCageRenderer(BlockEntityRendererProvider.Context ctx) {
        this.model = new ReforgeCageModel(ctx.bakeLayer(ReforgeCageModel.LAYER_LOCATION));
    }

    @Override
    public void render(ReforgeCageBlockEntity be, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer,
                       int light, int overlay) {

        poseStack.pushPose();


        poseStack.translate(0.5, 1.5, 0.5);
        poseStack.mulPose(Axis.XP.rotationDegrees(180));

        
        float age = 0f;
        if (be != null && be.getLevel() != null) {
            age = be.getLevel().getGameTime() + partialTicks;
        } else {
            age = partialTicks;
        }


        model.setupAnim(age);

        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutout(TEXTURE));
        model.renderToBuffer(poseStack, vc, light, overlay, 1f, 1f, 1f, 1f);

        poseStack.popPose();
    }
}
