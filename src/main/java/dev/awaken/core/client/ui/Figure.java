package dev.awaken.core.client.ui;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * A body standing in a screen (SPEC §57.4): the player on Status, in the bag, in the Hall's looks, at the Forge, a creature
 * on a card. It is vanilla's inventory figure, turned the same way, with one difference: the picture is as deep as the
 * figure is big. Vanilla draws its figure into a picture that keeps 1000 pixels in front of the body and 1000 behind,
 * plenty for the bag's little figure but not for ours: at the K menu's size (some 600 pixels a block on a 4K screen) a
 * maul or a spear held out in front, which reaches two blocks and more, was cut through where it passed that depth and
 * came apart in pieces round the body (11 Nov). Here the depth always takes in {@link #REACH} blocks each way (a big
 * creature twice its longest side); a picture small enough to fit in vanilla's depth is drawn exactly as vanilla draws it.
 */
public final class Figure {
    private Figure() {}

    /** How far in front of and behind its middle a figure is kept whole, in blocks: past a spear's point (2.4) and a raised arm. */
    static final float REACH = 4F;
    /** Vanilla's depth each way in a picture, in pixels (PictureInPictureRenderer's orthographic projection). */
    private static final float VANILLA_DEPTH = 1000F;

    private static boolean busy;

    public static void init(IEventBus modBus) {
        modBus.addListener(RegisterPictureInPictureRenderersEvent.class, e -> e.register(State.class, Renderer::new));
    }

    /**
     * True while a figure's body is read or drawn. Whatever asks whose eyes it is drawn for (a weapon's first-person
     * parts, a glow turned to the camera) must take it as seen from in front, in the picture's own space (x right, y
     * down, z toward you), and never as your own hands: the figure stands in a screen whatever the camera is doing.
     */
    public static boolean drawing() {
        return busy;
    }

    /** As {@code InventoryScreen.extractEntityInInventoryFollowsMouse}: the body turns to the pointer. */
    public static void followsMouse(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int size, float offsetY, float mouseX, float mouseY,
                                    LivingEntity entity) {
        float xAngle = (float) Math.atan(((x0 + x1) / 2F - mouseX) / 40F);
        float yAngle = (float) Math.atan(((y0 + y1) / 2F - mouseY) / 40F);
        followsAngle(g, x0, y0, x1, y1, size, offsetY, xAngle, yAngle, entity);
    }

    /** As {@code InventoryScreen.renderEntityInInventoryFollowsAngle}: the body turned by these angles (atan of the pointer's offset). */
    public static void followsAngle(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int size, float offsetY, float xAngle, float yAngle,
                                    LivingEntity entity) {
        Quaternionf xRotation = new Quaternionf().rotateX(yAngle * 20F * (float) (Math.PI / 180));
        Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI).mul(xRotation);
        EntityRenderState state;
        busy = true;
        try {
            state = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity).createRenderState(entity, 1F);
        } finally {
            busy = false;
        }
        state.shadowPieces.clear();
        state.outlineColor = 0;
        if (state instanceof LivingEntityRenderState living) {
            living.bodyRot = 180F + xAngle * 20F;
            living.yRot = xAngle * 20F;
            living.xRot = living.pose != Pose.FALL_FLYING ? -yAngle * 20F : 0F;
            living.boundingBoxWidth = living.boundingBoxWidth / living.scale;
            living.boundingBoxHeight = living.boundingBoxHeight / living.scale;
            living.scale = 1F;
        }
        state.lightCoords = 15728880; // as GuiGraphicsExtractor.entity: full bright
        Vector3f translation = new Vector3f(0F, state.boundingBoxHeight / 2F + offsetY, 0F);
        ScreenRectangle scissor = g.peekScissorStack();
        g.submitPictureInPictureRenderState(new State(state, translation, rotation, xRotation, x0, y0, x1, y1, size, scissor,
                PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissor)));
    }

    /** One figure's picture: vanilla's GuiEntityRenderState, under our own name so our renderer draws it. */
    record State(EntityRenderState renderState, Vector3fc translation, Quaternionfc rotation, @Nullable Quaternionfc overrideCameraAngle,
                 int x0, int y0, int x1, int y1, float scale, @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds)
            implements PictureInPictureRenderState {}

    /** Vanilla's GuiEntityRenderer, with the depth widened to the figure's {@link #REACH} once that passes vanilla's. */
    static final class Renderer extends PictureInPictureRenderer<State> {
        private final Projection deep = new Projection();
        private final ProjectionMatrixBuffer deepBuffer = new ProjectionMatrixBuffer("PIP - awaken figure");
        private int guiScale = 1;

        @Override
        public void prepare(State state, GuiRenderState gui, FeatureRenderDispatcher features, int guiScale) {
            this.guiScale = guiScale;
            busy = true; // the picture is submitted and drawn inside this call
            try {
                super.prepare(state, gui, features, guiScale);
            } finally {
                busy = false;
            }
        }

        @Override
        protected void renderToTexture(State s, PoseStack poseStack, SubmitNodeCollector collector) {
            // the picture's projection is set just before this and bound with the pass just after: widen its depth here
            // (a creature bigger than a player keeps twice its longest side)
            float blocks = Math.max(REACH, 2F * Math.max(s.renderState().boundingBoxWidth, s.renderState().boundingBoxHeight));
            float depth = blocks * s.scale() * guiScale;
            if (depth > VANILLA_DEPTH) {
                deep.setupOrtho(-depth, depth, (s.x1() - s.x0()) * guiScale, (s.y1() - s.y0()) * guiScale, true);
                RenderSystem.setProjectionMatrix(deepBuffer.getBuffer(deep), ProjectionType.ORTHOGRAPHIC);
            }
            Minecraft mc = Minecraft.getInstance();
            mc.gameRenderer.lighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
            poseStack.translate(s.translation().x(), s.translation().y(), s.translation().z());
            poseStack.rotate(s.rotation());
            CameraRenderState camera = new CameraRenderState();
            if (s.overrideCameraAngle() != null) camera.orientation = s.overrideCameraAngle().conjugate(new Quaternionf()).rotateY((float) Math.PI);
            mc.getEntityRenderDispatcher().submit(s.renderState(), camera, 0.0, 0.0, 0.0, poseStack, collector);
        }

        /** As GuiEntityRenderer: the body's middle at the picture's middle (the base class puts the origin at its foot). */
        @Override
        protected float getTranslateY(int height, int guiScale) {
            return height / 2F;
        }

        @Override
        protected String getTextureLabel() {
            return "awaken figure";
        }

        @Override
        public Class<State> getRenderStateClass() {
            return State.class;
        }

        @Override
        public void close() {
            super.close();
            deepBuffer.close();
        }
    }
}
