package dev.awaken.enchant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.awaken.Awaken;
import dev.awaken.enchant.EnchantRules;
import dev.awaken.enchant.InscribingTableBlock;
import dev.awaken.enchant.InscribingTableBlockEntity;
import dev.awaken.enchant.Marks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.book.BookModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Inscribing Table as everyone sees it (SPEC §45.10, §45.15): the leather book lying on its brass rest at the back,
 * opening when someone comes near and turning its pages as the writer looks through what they know; the burin standing
 * in the inkwell while nobody writes; the writer's piece on the slate as the thing it is (a sculpted weapon or armour
 * piece is its own mesh, not its icon), sized to the slate; the lapis channel round the slate's edge, which wakes faintly
 * when someone comes close, fills with the enchantment's colour while its glyph is traced and flares when the piece
 * wakes; and, for watchers, the glyph itself burning faintly on the piece (the writer's own client draws theirs
 * brighter, see {@link InscribeScene}).
 */
public final class InscribingTableRenderer implements BlockEntityRenderer<InscribingTableBlockEntity, InscribingTableRenderer.State> {
    static final SpriteId BOOK = new SpriteId(TextureAtlas.LOCATION_BLOCKS, Awaken.id("block/inscribing_book"));
    /** The slate's top, and the channel's square (half its side, from the block's centre). */
    static final float SLATE = 12 / 16F, CHANNEL = 7 / 16F;
    /** How a piece lies: flat on its side (an icon, a weapon), on its back (a breastplate, leggings), or standing (a helm, boots). */
    static final int FLAT = 0, BACK = 1, UPRIGHT = 2;
    /** The burin in the inkwell: made at first need (at class load, during the first resource reload, items aren't bound yet). */
    private static ItemStack burin = ItemStack.EMPTY;
    private static final float[] NO_FLOATS = new float[0];
    private static final int[] NO_INTS = new int[0];

    private final SpriteGetter sprites;
    private final BookModel book;
    private final ItemModelResolver items;

    public InscribingTableRenderer(BlockEntityRendererProvider.Context ctx) {
        sprites = ctx.sprites();
        book = new BookModel(ctx.bakeLayer(ModelLayers.BOOK));
        items = ctx.itemModelResolver();
    }

    public static final class State extends BlockEntityRenderState {
        float fx, fz = 1, rx = 1, rz, yaw;
        float open, flip, time, yRot, age;
        int phase, no, colour;
        boolean armour, mine;
        final ItemStackRenderState piece = new ItemStackRenderState();
        /** The burin in the inkwell (empty while someone writes: it is in their hand). */
        final ItemStackRenderState burin = new ItemStackRenderState();
        /** The book's turn toward the writer's side, the piece's lie, its size, its middle and its top above the slate. */
        float bookYaw, pScale = 0.5F, pcx, pcy, pcz, pTop = 0.03F;
        int lie;
        // the writer's own desk while they choose (SPEC §45.15): their pages over the near edge, the piece's sockets behind it
        int pages, sockets, socketHover, socketHeld;
        float pageYaw, holding;
        final ItemStackRenderState[] page = new ItemStackRenderState[Desk.MAX];
        final float[] pu = new float[Desk.MAX], py = new float[Desk.MAX], pv = new float[Desk.MAX], psz = new float[Desk.MAX],
                lean = new float[Desk.MAX], lit = new float[Desk.MAX], su = new float[Desk.MAX], sv = new float[Desk.MAX];
        final int[] sockNo = new int[Desk.MAX], sockColour = new int[Desk.MAX];

        State() {
            for (int i = 0; i < page.length; i++) page[i] = new ItemStackRenderState();
        }
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(InscribingTableBlockEntity be, State s, float pt, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(be, s, pt, camera, crumbling);
        var st = be.getBlockState();
        Direction d = st.hasProperty(InscribingTableBlock.FACING) ? st.getValue(InscribingTableBlock.FACING) : Direction.NORTH;
        s.fx = d.getStepX();
        s.fz = d.getStepZ();
        s.rx = s.fz;
        s.rz = -s.fx;
        s.yaw = 180F - d.toYRot();
        s.bookYaw = -d.getClockWise().toYRot();
        s.open = Mth.lerp(pt, be.oOpen, be.open);
        s.flip = Mth.lerp(pt, be.oTurn, be.turn);
        s.time = be.time + pt;
        float or = be.rot - be.oRot;
        while (or >= Mth.PI) or -= Mth.TWO_PI;
        while (or < -Mth.PI) or += Mth.TWO_PI;
        s.yRot = be.oRot + or * pt;
        s.phase = be.phase();
        s.no = be.no();
        s.colour = be.colour();
        s.age = be.getLevel() == null ? 999 : be.getLevel().getGameTime() - be.phaseAt + pt;
        Minecraft mc = Minecraft.getInstance();
        s.mine = mc.player != null && mc.player.getUUID().equals(be.writer());
        // the writer's piece wakes on the scene's clock, so it turns with the mark their client draws over it
        if (s.mine && s.phase == InscribingTableBlockEntity.WAKE && InscribeScene.phase == InscribeScene.WAKE) s.age = InscribeScene.now(pt) - InscribeScene.phaseAt;
        ItemStack piece = be.piece();
        s.armour = EnchantRules.armour(piece);
        if (piece.isEmpty() || (s.mine && InscribeScene.laying(pt))) s.piece.clear();
        else {
            // NONE: a sculpted weapon or armour piece draws as its own mesh there, with no hand's or frame's turn on it
            items.updateForTopItem(s.piece, be.shownPiece(), ItemDisplayContext.NONE, be.getLevel(), null, (int) be.getBlockPos().asLong());
            lay(s, piece);
        }
        if (s.mine && !piece.isEmpty()) InscribeScene.pieceTop(s.pTop);
        // in its inkwell while nobody writes and while the writer still chooses; in their hand from the choice on
        boolean inWell = s.phase == InscribingTableBlockEntity.IDLE
                || s.phase == InscribingTableBlockEntity.BOOK && !(s.mine && InscribeScene.burinOut());
        if (inWell) {
            if (burin.isEmpty()) burin = new ItemStack(dev.awaken.enchant.EnchantContent.BURIN.get());
            items.updateForTopItem(s.burin, burin, ItemDisplayContext.NONE, be.getLevel(), null, 0);
        } else s.burin.clear();
        s.pages = s.sockets = 0;
        if (s.mine && !EnchantClientConfig.classic() && Desk.shown(pt)) {
            Desk.layout(pt);
            s.pageYaw = -d.toYRot();
            s.pages = Desk.OFFERS.size();
            for (int i = 0; i < s.pages; i++) {
                items.updateForTopItem(s.page[i], Desk.stack(Desk.OFFERS.get(i)), ItemDisplayContext.NONE, be.getLevel(), null, i);
                s.pu[i] = Desk.PU[i];
                s.py[i] = Desk.PY[i];
                s.pv[i] = Desk.PV[i];
                s.psz[i] = Desk.PS[i];
                s.lean[i] = Desk.LEAN[i];
                s.lit[i] = Desk.LIT[i];
            }
            if (InscribeScene.phase == InscribeScene.BOOK) {
                s.sockets = Desk.room;
                for (int j = 0; j < s.sockets; j++) {
                    Desk.On on = j < Desk.ON.size() ? Desk.ON.get(j) : null;
                    s.su[j] = Desk.SU[j];
                    s.sv[j] = Desk.SV[j];
                    // 0: an empty socket; -1: something written that isn't one of ours (a plain knot)
                    s.sockNo[j] = on == null ? 0 : on.e() == null ? -1 : on.e().no();
                    s.sockColour[j] = on == null || on.e() == null ? 0xB9C3D2 : on.e().colour();
                }
                s.socketHover = Desk.socket;
                s.socketHeld = Desk.held;
                s.holding = Minecraft.getInstance().gui.screen() instanceof InscribeScreen scr ? Desk.holding(pt, scr.age()) : 0F;
            }
        }
    }

    /**
     * How the piece lies on the slate and how large, from the bounds of what will be drawn: a flat icon or a weapon on
     * its side, its length across the slate; a breastplate or leggings on their back; a helm or boots standing. Each is
     * scaled so it spans most of the slate, whatever its own size (it was always the 16 pixel icon at half a block).
     */
    private static void lay(State s, ItemStack piece) {
        AABB b = s.piece.getModelBoundingBox();
        double w = b.getXsize(), h = b.getYsize(), deep = b.getZsize();
        s.pcx = (float) ((b.minX + b.maxX) / 2);
        s.pcy = (float) ((b.minY + b.maxY) / 2);
        s.pcz = (float) ((b.minZ + b.maxZ) / 2);
        // an icon is a sixteenth thick; a mesh has a body
        boolean mesh = deep > 0.09;
        var eq = piece.get(net.minecraft.core.component.DataComponents.EQUIPPABLE);
        if (mesh && s.armour && eq != null) {
            // a sculpted piece's item says only "a block" (it is fitted into one, wings and crests and all): its real
            // shape comes from its meshes. Taken as the block, a breastplate floated a third of a block over the slate
            // and its mark stood so high that the view to it went flat
            float[] real = shape(piece);
            if (real != null) {
                w = real[0];
                h = real[1];
                deep = real[2];
            }
            boolean stands = eq.slot() == net.minecraft.world.entity.EquipmentSlot.HEAD || eq.slot() == net.minecraft.world.entity.EquipmentSlot.FEET;
            s.lie = stands ? UPRIGHT : BACK;
            // a helm or boots stand, no taller than a third of a block (the mark is cut over the top of the piece, and
            // you look down at it from a block away); a breastplate or leggings lie on their back, half the slate across
            double scale = stands ? Math.min(0.42 / Math.max(0.05, Math.max(w, deep)), 0.32 / Math.max(0.05, h)) : 0.5 / Math.max(0.05, Math.max(w, h));
            s.pScale = (float) Mth.clamp(scale, 0.2, 1.1);
            s.pTop = (float) ((stands ? h : deep) * s.pScale);
        } else {
            s.lie = FLAT;
            // a weapon or tool lies along its own diagonal; an armour icon sits square
            double span = s.armour ? Math.max(w, h) : Math.hypot(w, h);
            s.pScale = (float) Mth.clamp((s.armour ? 0.5 : 0.8) / Math.max(0.05, span), 0.25, 1.1);
            s.pTop = (float) (Math.max(deep, 1 / 16.0) * s.pScale);
        }
    }

    /** A sculpted armour piece's meshes and the shape measured from them (measured again when a reload brings new ones). */
    private record Shape(Object meshes, float[] size) {}

    private static final java.util.Map<net.minecraft.world.item.Item, Shape> SHAPES = new java.util.IdentityHashMap<>();

    /**
     * A sculpted armour piece's width, height and depth inside the block its item fills, or null where the piece isn't
     * one: measured from its meshes at rest with the cape left off, as the armour's own item renderer fits it (the
     * longest side to 0.96 of a block, the piece in the block's middle).
     */
    private static float @Nullable [] shape(ItemStack piece) {
        if (!(piece.getItem() instanceof dev.awaken.armour.ForgedArmourItem a)) return null;
        dev.awaken.armour.client.ArmourMesh[] bones = dev.awaken.armour.client.ArmourMeshes.piece(a.set, a.piece);
        if (bones == null) return null;
        Shape kept = SHAPES.get(a);
        if (kept != null && kept.meshes == bones) return kept.size;
        float x0 = Float.MAX_VALUE, y0 = x0, z0 = x0, x1 = -x0, y1 = -x0, z1 = -x0;
        PoseStack ps = new PoseStack();
        org.joml.Vector3f at = new org.joml.Vector3f();
        for (dev.awaken.armour.client.ArmourMeshes.Bone b : dev.awaken.armour.client.ArmourMeshes.BONES) {
            dev.awaken.armour.client.ArmourMesh mesh = bones[b.ordinal()];
            if (mesh == null || b.name().startsWith("CAPE")) continue;
            ps.pushPose();
            dev.awaken.armour.client.FittingStandRenderer.rest(ps, b);
            float[] p = mesh.pos();
            for (int i = 0; i + 2 < p.length; i += 3) {
                ps.last().pose().transformPosition(p[i], p[i + 1], p[i + 2], at);
                x0 = Math.min(x0, at.x);
                x1 = Math.max(x1, at.x);
                y0 = Math.min(y0, at.y);
                y1 = Math.max(y1, at.y);
                z0 = Math.min(z0, at.z);
                z1 = Math.max(z1, at.z);
            }
            ps.popPose();
        }
        float[] size = null;
        if (x1 >= x0) {
            float fit = 0.96F / Math.max(0.25F, Math.max(x1 - x0, Math.max(y1 - y0, z1 - z0)));
            size = new float[]{(x1 - x0) * fit, (y1 - y0) * fit, (z1 - z0) * fit};
        }
        SHAPES.put(a, new Shape(bones, size));
        return size;
    }

    @Override
    public void submit(State s, PoseStack ps, SubmitNodeCollector out, CameraRenderState camera) {
        boolean writing = s.phase != InscribingTableBlockEntity.IDLE;
        // the book catches the channel's light while someone writes
        int light = writing ? LightCoordsUtil.pack(Math.max(LightCoordsUtil.block(s.lightCoords), 11), LightCoordsUtil.sky(s.lightCoords)) : s.lightCoords;

        // the book on its brass rest, a little to the writer's left at the back: shut when nobody is near, open as they come
        ps.pushPose();
        float bu = -0.175F, bv = 0.35F;
        ps.translate(0.5F + s.rx * bu - s.fx * bv, 17.4F / 16F + 0.06F, 0.5F + s.rz * bu - s.fz * bv);
        ps.rotateDegrees(Axis.YP, s.bookYaw);
        ps.rotateDegrees(Axis.ZP, 74F);
        ps.scale(0.62F, 0.62F, 0.62F);
        ps.translate(0F, -0.125F, 0F);
        float ff1 = Mth.frac(s.flip + 0.25F) * 1.6F - 0.3F, ff2 = Mth.frac(s.flip + 0.75F) * 1.6F - 0.3F;
        BookModel.State bs = BookModel.State.forAnimation(s.time, Mth.clamp(ff1, 0F, 1F), Mth.clamp(ff2, 0F, 1F), 0.2F + 0.8F * s.open);
        out.submitModel(book, bs, ps, light, OverlayTexture.NO_OVERLAY, -1, BOOK, sprites, 0);
        ps.popPose();

        // the burin standing in the inkwell at the writer's right, its point down in the lapis
        if (!s.burin.isEmpty()) {
            ps.pushPose();
            float iu = 0.306F, iv = 0.275F;
            ps.translate(0.5F + s.rx * iu - s.fx * iv, 14.2F / 16F + 0.19F, 0.5F + s.rz * iu - s.fz * iv);
            ps.rotateDegrees(Axis.YP, s.yaw);
            ps.rotateDegrees(Axis.ZP, 215F);
            ps.scale(0.42F, 0.42F, 0.42F);
            s.burin.submit(ps, out, s.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            ps.popPose();
        }

        float lift = wakeLift(s.phase, s.age);
        if (!s.piece.isEmpty()) {
            ps.pushPose();
            ps.translate(0.5F + s.fx * 0.08F, SLATE + 0.004F + lift + s.pTop / 2F, 0.5F + s.fz * 0.08F);
            ps.rotateDegrees(Axis.YP, s.yaw + (float) Math.toDegrees(wakeSpin(s.phase == InscribingTableBlockEntity.WAKE ? s.age : 0)));
            switch (s.lie) {
                case BACK -> ps.rotateDegrees(Axis.XP, -90F);
                case UPRIGHT -> {}
                default -> {
                    ps.rotateDegrees(Axis.XP, 90F);
                    // a weapon or tool lies across the slate, its length along the writer's left and right
                    if (!s.armour) ps.rotateDegrees(Axis.ZP, 45F);
                }
            }
            ps.scale(s.pScale, s.pScale, s.pScale);
            ps.translate(-s.pcx, -s.pcy, -s.pcz);
            s.piece.submit(ps, out, light, OverlayTexture.NO_OVERLAY, 0);
            ps.popPose();
        }

        // the writer's pages: each the real page, leaning back toward their eyes, bright; what can't go on lies dim on the slate
        for (int i = 0; i < s.pages; i++) {
            if (s.page[i].isEmpty() || s.psz[i] <= 0.004F) continue;
            ps.pushPose();
            ps.translate(0.5F + s.rx * s.pu[i] - s.fx * s.pv[i], s.py[i], 0.5F + s.rz * s.pu[i] - s.fz * s.pv[i]);
            ps.rotateDegrees(Axis.YP, s.pageYaw);
            ps.rotateDegrees(Axis.XP, -s.lean[i]);
            ps.scale(s.psz[i], s.psz[i], s.psz[i]);
            int pl = s.lit[i] >= 0.99F ? LightCoordsUtil.FULL_BRIGHT : LightCoordsUtil.pack(Math.round(15 * s.lit[i]), LightCoordsUtil.sky(s.lightCoords));
            s.page[i].submit(ps, out, pl, OverlayTexture.NO_OVERLAY, 0);
            ps.popPose();
        }

        if (s.open <= 0.01F && !writing) return;
        final float fx = s.fx, fz = s.fz, rx = s.rx, rz = s.rz, open = s.open, age = s.age, time = s.time;
        final int phase = s.phase, no = s.no, colour = s.colour == 0 ? InscribeScene.LAPIS : s.colour;
        final boolean mine = s.mine;
        final int sockets = s.sockets, socketHover = s.socketHover, socketHeld = s.socketHeld;
        final float holding = s.holding;
        // copies only while the writer's own sockets show (the state is filled again before this draws)
        final float[] su = sockets > 0 ? s.su.clone() : NO_FLOATS, sv = sockets > 0 ? s.sv.clone() : NO_FLOATS;
        final int[] sockNo = sockets > 0 ? s.sockNo.clone() : NO_INTS, sockColour = sockets > 0 ? s.sockColour.clone() : NO_INTS;
        final float top = Math.max(0.03F, s.pTop + 0.006F);
        out.submitCustomGeometry(ps, RenderTypes.lightning(), (pose, vc) -> {
            Geo g = new Geo(pose, vc, fx, fz, rx, rz);
            float y = SLATE + 0.003F;
            // the piece's sockets, let into the slate down its left side: a glyph in its frame for each thing written, an
            // empty frame for each free
            for (int j = 0; j < sockets; j++) {
                boolean over = j == socketHover;
                float sy = SLATE + 0.004F, h = Desk.SOCKET * (over ? 1.25F : 1F);
                if (sockNo[j] == 0) g.square(su[j], sv[j], sy, h, 0.01F, InscribeScene.argb(InscribeScene.LAPIS, over ? 0.7F : 0.42F));
                else {
                    g.square(su[j], sv[j], sy, h * 1.25F, 0.008F, InscribeScene.argb(sockColour[j], over ? 0.6F : 0.35F));
                    g.glyph(sockNo[j], su[j], sv[j], sy + 0.0005F, h, InscribeScene.argb(InscribeScene.hot(sockColour[j], over ? 0.5F : 0.25F), over ? 1F : 0.92F), 0.014F);
                    // a held press closes a ring round it; closed, it is scraped off
                    if (j == socketHeld && holding > 0) g.ring(su[j], sv[j], sy + 0.001F, h * 1.7F, holding, 0.012F, InscribeScene.argb(0xFF7A6A, 0.9F));
                }
            }
            switch (phase) {
                case InscribingTableBlockEntity.TRACE -> {
                    // the channel fills from the front round both sides to the back as the mark is traced
                    float fill = Mth.clamp(age / 40F, 0F, 1F);
                    g.channel(y, 1F, InscribeScene.argb(InscribeScene.LAPIS, 0.12F));
                    g.channel(y + 0.0005F, fill, InscribeScene.argb(colour, 0.55F + 0.1F * Mth.sin(time * 0.2F)));
                    if (!mine) g.mark(no, SLATE + top, InscribeScene.argb(colour, 0.22F + 0.1F * Mth.sin(time * 0.25F)), 0.03F, 0);
                }
                case InscribingTableBlockEntity.WAKE -> {
                    float k = Mth.clamp(age / 50F, 0F, 1F);
                    g.channel(y, 1F, InscribeScene.argb(InscribeScene.hot(colour, 0.5F * (1 - k)), 0.9F - 0.5F * k));
                    if (!mine) g.mark(no, SLATE + top + lift, InscribeScene.argb(InscribeScene.hot(colour, 0.6F * (1 - k)), 0.85F - 0.4F * k), 0.05F, wakeSpin(age));
                }
                default -> g.channel(y, 1F, InscribeScene.argb(InscribeScene.LAPIS, (writing ? 0.24F : 0.14F) * open * (0.8F + 0.2F * Mth.sin(time * 0.07F))));
            }
        });
    }

    /** The piece rises a hair as it wakes, and settles again. */
    static float wakeLift(int phase, float age) {
        if (phase != InscribingTableBlockEntity.WAKE) return 0;
        float up = Mth.clamp(age / 8F, 0F, 1F);
        up = up * up * (3 - 2 * up);
        return 0.04F * up * (1F - Mth.clamp((age - 40F) / 20F, 0F, 1F));
    }

    /** And turns once, after the lift and before it settles (radians). */
    static double wakeSpin(float age) {
        float k = Mth.clamp((age - 6F) / 34F, 0F, 1F);
        return Mth.TWO_PI * k * k * (3 - 2 * k);
    }

    @Override
    public AABB getRenderBoundingBox(InscribingTableBlockEntity be) {
        var p = be.getBlockPos();
        return new AABB(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1.6, p.getZ() + 1);
    }

    /** Light lying on the slate, in the block's own space (u to the writer's right, v away from them, from the centre). */
    private record Geo(PoseStack.Pose pose, VertexConsumer vc, float fx, float fz, float rx, float rz) {
        float x(float u, float v) {
            return 0.5F + rx * u - fx * v;
        }

        float z(float u, float v) {
            return 0.5F + rz * u - fz * v;
        }

        /** The channel's square, lit from the front middle round both sides; {@code fill} of the way to the back. */
        void channel(float y, float fill, int argb) {
            float h = CHANNEL, len = 4 * h * fill;
            for (int side = -1; side <= 1; side += 2) {
                // front middle → front corner → back corner → back middle
                float[] us = {0, side * h, side * h, 0}, vs = {-h, -h, h, h};
                float left = len;
                for (int i = 0; i < 3 && left > 0; i++) {
                    float seg = (i == 1 ? 2 * h : h), t = Math.min(1F, left / seg);
                    float u0 = us[i], v0 = vs[i], u1 = Mth.lerp(t, us[i], us[i + 1]), v1 = Mth.lerp(t, vs[i], vs[i + 1]);
                    strip(u0, v0, u1, v1, y, 0.03F, argb);
                    left -= seg;
                }
            }
        }

        /** The mark of enchantment {@code no} on the piece (which sits 0.08 toward the writer), turned by {@code spin}. */
        void mark(int no, float y, int argb, float width, double spin) {
            if (no <= 0) return;
            float c = (float) Math.cos(spin), sn = (float) Math.sin(spin), px = 0.5F + fx * 0.08F, pz = 0.5F + fz * 0.08F;
            for (float[] s : Marks.of(no)) {
                int n = s.length / 2;
                for (int i = 0; i + 1 < n; i++) {
                    float ox0 = (rx * s[i * 2] - fx * s[i * 2 + 1]) * 0.2F, oz0 = (rz * s[i * 2] - fz * s[i * 2 + 1]) * 0.2F;
                    float ox1 = (rx * s[i * 2 + 2] - fx * s[i * 2 + 3]) * 0.2F, oz1 = (rz * s[i * 2 + 2] - fz * s[i * 2 + 3]) * 0.2F;
                    line(px + ox0 * c + oz0 * sn, pz - ox0 * sn + oz0 * c, px + ox1 * c + oz1 * sn, pz - ox1 * sn + oz1 * c, y, width, argb);
                }
            }
        }

        /** A glyph lying on the slate at (u, v), {@code half} its half size (number below 1: the plain knot). */
        void glyph(int no, float cu, float cv, float y, float half, int argb, float width) {
            for (float[] s : Marks.of(no)) {
                int n = s.length / 2;
                for (int i = 0; i + 1 < n; i++)
                    strip(cu + s[i * 2] * half, cv + s[i * 2 + 1] * half, cu + s[i * 2 + 2] * half, cv + s[i * 2 + 3] * half, y, width, argb);
            }
        }

        /** An empty socket: a small square of light. */
        void square(float cu, float cv, float y, float half, float width, int argb) {
            strip(cu - half, cv - half, cu + half, cv - half, y, width, argb);
            strip(cu + half, cv - half, cu + half, cv + half, y, width, argb);
            strip(cu + half, cv + half, cu - half, cv + half, y, width, argb);
            strip(cu - half, cv + half, cu - half, cv - half, y, width, argb);
        }

        /** {@code part} of a ring, from its far side round clockwise. */
        void ring(float cu, float cv, float y, float r, float part, float width, int argb) {
            int steps = Math.max(1, Math.round(28 * part));
            for (int i = 0; i < steps; i++) {
                float a0 = Mth.TWO_PI * i / 28F, a1 = Mth.TWO_PI * (i + 1) / 28F;
                strip(cu + Mth.sin(a0) * r, cv + Mth.cos(a0) * r, cu + Mth.sin(a1) * r, cv + Mth.cos(a1) * r, y, width, argb);
            }
        }

        /** A soft line between two points on the slate (u, v): bright down its middle, fading to nothing at its sides. */
        void strip(float u0, float v0, float u1, float v1, float y, float width, int argb) {
            line(x(u0, v0), z(u0, v0), x(u1, v1), z(u1, v1), y, width, argb);
        }

        /** The same between two points in the block's own space. */
        void line(float x0, float z0, float x1, float z1, float y, float width, int argb) {
            float dx = x1 - x0, dz = z1 - z0, l = Mth.sqrt(dx * dx + dz * dz);
            if (l < 1e-5F) return;
            float nx = -dz / l * width / 2, nz = dx / l * width / 2;
            int rim = argb & 0x00FFFFFF;
            quad(x0, z0, x1, z1, x1 + nx, z1 + nz, x0 + nx, z0 + nz, y, argb, rim);
            quad(x0, z0, x1, z1, x1 - nx, z1 - nz, x0 - nx, z0 - nz, y, argb, rim);
        }

        void quad(float x0, float z0, float x1, float z1, float x2, float z2, float x3, float z3, float y, int inner, int outer) {
            vc.addVertex(pose, x0, y, z0).setColor(inner);
            vc.addVertex(pose, x1, y, z1).setColor(inner);
            vc.addVertex(pose, x2, y, z2).setColor(outer);
            vc.addVertex(pose, x3, y, z3).setColor(outer);
        }
    }
}
