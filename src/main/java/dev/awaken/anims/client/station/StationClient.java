package dev.awaken.anims.client.station;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.awaken.anims.Ease;
import dev.awaken.anims.station.StationView;
import dev.awaken.anims.station.StationView.Kind;
import dev.awaken.anims.station.StationView.Station;
import dev.awaken.core.api.FirstPerson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jspecify.annotations.Nullable;

/**
 * Station scenes (Hands P5, SPEC §24; Hands at Work, SPEC §63). At a crafting table the view goes to the table's own
 * place over its near edge (the bench view: the same from wherever you stand), the 3×3 grid IS the table top (each
 * ingredient lies on its square in the world, the result on the right border), and your own hand carries the stack on
 * your pointer over the grid and sets it down ({@link StationHands}). At a furnace, smoker or blast furnace the view crouches to its
 * mouth (StationCamera): what cooks sits on the left inside the recessed mouth, the finished result on the right, the
 * fuel on the coals below, and the cooking and the fuel left run along the strip. The screen ({@link StationScreen}) is the station's vanilla menu, so every item
 * moves by the menu's own clicks and the server checks them; only the station's slots are drawn in the world instead of
 * on the screen. A hand moves only after your own click (cooking on its own moves none). Sneak + use opens the plain
 * screen; moving, jumping, E or Esc leaves. Drawn for you (others see you at the station as before).
 * <p>
 * Lean-in 12 ticks (6 if you come back within 12 s), out 10; the view turns and opens or narrows to frame every slot in
 * the room above the strip (StationView.frame), so the strip, the hint and JEI never cover one; nothing moves the
 * player's own look.
 */
public final class StationClient {
    private StationClient() {}

    /** The station being worked (null: none), its kind, the way into it (horizontal, along an axis). */
    static @Nullable BlockPos table;
    static Kind kind = Kind.TABLE;
    static double fx, fz, rx, rz;
    static @Nullable Station station;
    /** The view that frames the station's slots above the strip (yaw, pitch, field of view), and whether it is set. */
    private static final float[] FRAME = new float[3];
    private static boolean framed;
    private static int openedAt = -1000, closedAt = -1000, ticks, usedAt = -100, clickedAt = -100;
    private static @Nullable BlockPos used;
    private static boolean open;
    /** This visit's glide in and out (ticks), and whether the view is the station's own place (a camera of ours). */
    private static int inTicks = 12, outTicks = 10;
    private static boolean bench;
    /** The view you had before a scene that needed first person (third person: restored when the view is home). */
    private static net.minecraft.client.@Nullable CameraType before;
    private static final ItemStack[] SEEN = new ItemStack[10];
    private static final ItemStackRenderState[] ITEMS = new ItemStackRenderState[10];
    private static final ItemStackRenderState CARRIED = new ItemStackRenderState();
    /** When a stack was set down on each slot by your click, and when the result came together (nanoseconds; 0 never). */
    private static final long[] LAID_AT = new long[10];
    private static long resultAt;
    private static final long SETTLE_NS = 110_000_000L, RESULT_NS = 200_000_000L;
    /** What was used up as a result was taken (SPEC §63.3): each square's stack as it was, and when; and when the rest gave. */
    private static final ItemStackRenderState[] FLECKS = new ItemStackRenderState[10];
    private static final long[] FLECK_AT = new long[10];
    private static long gaveAt;
    private static final long FLECK_NS = 280_000_000L, GIVE_NS = 110_000_000L;
    private static final double[] HELD = new double[3];

    static {
        java.util.Arrays.fill(SEEN, ItemStack.EMPTY);
        for (int i = 0; i < ITEMS.length; i++) ITEMS[i] = new ItemStackRenderState();
        for (int i = 0; i < FLECKS.length; i++) FLECKS[i] = new ItemStackRenderState();
    }

    public static void init(IEventBus bus, IEventBus modBus) {
        FurnaceWorld.init(modBus);
        bus.addListener(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut.class, e -> {
            StationCamera.end(); open = false; table = null; before = null;
            StationBodies.clear();
        });
        bus.addListener(SubmitCustomGeometryEvent.class, StationBodies::submit);
        bus.addListener(StationClient::onPlayer);
        bus.addListener(PlayerInteractEvent.RightClickBlock.class, StationClient::onUse);
        bus.addListener(EventPriority.LOW, ScreenEvent.Opening.class, StationClient::onOpening);
        bus.addListener(EventPriority.LOW, ViewportEvent.ComputeCameraAngles.class, StationClient::onAngles);
        bus.addListener(ComputeFovModifierEvent.class, StationClient::onFovModifier);
        bus.addListener(ViewportEvent.ComputeFov.class, StationClient::onFov);
        bus.addListener(SubmitCustomGeometryEvent.class, StationClient::onSubmit);
        // the game's "name of what you now hold" line sits where a scene's strip is: not while a station is worked
        bus.addListener(net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Pre.class, e -> {
            if (e.getName().equals(net.neoforged.neoforge.client.gui.VanillaGuiLayers.SELECTED_ITEM_NAME)
                    && Minecraft.getInstance().gui.screen() instanceof StationScreen<?>) e.setCanceled(true);
        });
        FirstPerson.world("anims_station", 200, StationClient::pose);
    }

    public static void tick() {
        ticks++;
        StationBodies.tick();
        // the lowered view gives up (and the screen shuts) if it lost its level, the scene ended, you died, or a block
        // came between it and your eyes (a second player can place one while you work)
        if (StationCamera.lost() || StationCamera.active() && (!on() || Minecraft.getInstance().player == null
                || !Minecraft.getInstance().player.isAlive() || StationCamera.blocked())) {
            boolean wasTable = kind.bench() && open;
            StationCamera.end();
            bench = false;
            if (wasTable && on() && Minecraft.getInstance().player != null && Minecraft.getInstance().player.isAlive()) {
                // a table's view was cut off (a block set down in its way): back to your own eyes, the screen stays open
                framed = false;
            } else {
                open = false; table = null;
                if (Minecraft.getInstance().gui.screen() instanceof StationScreen<?> && Minecraft.getInstance().player != null) Minecraft.getInstance().player.closeContainer();
            }
        }
        if (StationCamera.active() && weight(0) <= 0) StationCamera.end();
        if (!open || !(Minecraft.getInstance().gui.screen() instanceof StationScreen<?> s)) {
            if (!open && before != null && weight(0) <= 0) restoreView();
            return;
        }
        reframe(s);
        // a brewing stand at work: a bubble in each bottle, every third tick (SPEC §63.8)
        if (kind == Kind.BREWING && ticks % 3 == 0 && s.getMenu() instanceof net.minecraft.world.inventory.BrewingStandMenu brew && brew.getBrewingTicks() > 0
                && !FirstPerson.reducedMotion.getAsBoolean()) {
            for (int i = 0; i < 3; i++) {
                if (brew.getSlot(i).getItem().isEmpty()) continue;
                spot(i, P, 0);
                StationFire.bubble(P[0], P[1] - 0.03, P[2]);
            }
        }
        // what changed on the station after your own click: the hand that carries it dips as it is set down, a stack
        // picked up rises into it, and a change made without carrying (a shift-click, a key) gets one short pass
        AbstractContainerMenu menu = s.getMenu();
        boolean yours = ticks - clickedAt <= 6, tapped = false, took = false;
        int res = kind.result;
        for (int k = 0; k < kind.slots; k++) {
            // the result first: taking it empties the grid too, but the hand went to it (a brewing stand has none)
            int i = res < 0 ? k : (res + k) % kind.slots;
            ItemStack now = menu.getSlot(i).getItem();
            if (ItemStack.matches(now, SEEN[i])) continue;
            ItemStack was = SEEN[i];
            boolean taken = i == res && now.isEmpty() && !was.isEmpty();
            boolean more = !now.isEmpty() && (was.isEmpty() || !ItemStack.isSameItemSameComponents(now, was) || now.getCount() > was.getCount());
            SEEN[i] = now.copy();
            if (i == res && !now.isEmpty() && was.isEmpty()) {
                resultAt = System.nanoTime(); // the result comes together
                if (yours && kind.bench()) StationSounds.result(table);
            }
            if (!yours) continue;
            if (took && i != res && !more) {
                // used up by the result you just took: no hand goes to it; it gives, and a few flecks of it fall
                if (!was.isEmpty() && Minecraft.getInstance().level != null) {
                    Minecraft.getInstance().getItemModelResolver().updateForTopItem(FLECKS[i], was, ItemDisplayContext.FIXED, Minecraft.getInstance().level, null, 200 + i);
                    FLECK_AT[i] = System.nanoTime();
                }
                continue;
            }
            if (i != res && more) {
                LAID_AT[i] = System.nanoTime();
                if (kind == Kind.FURNACE && i == AbstractFurnaceMenu.FUEL_SLOT) StationSounds.fuel(table);
                else StationSounds.lay(table, now, false);
                if (StationHands.carrying() || !menu.getCarried().isEmpty()) StationHands.laid(i);
                else if (!tapped) { StationHands.pass(i); tapped = true; }
                // fuel onto the coals: the fire answers (SPEC §63.6)
                if (kind == Kind.FURNACE && i == AbstractFurnaceMenu.FUEL_SLOT) StationFire.flare(station);
            } else if (taken || (!more && !menu.getCarried().isEmpty())) {
                StationHands.lifted();
                if (taken) {
                    StationSounds.take(table);
                    took = true;
                    gaveAt = System.nanoTime();
                } else StationSounds.lift(table);
                // taking what an anvil, a smithing table or a grindstone made: the other hand's blows on the work,
                // sparks off each (SPEC §63.8); the block's own sound is vanilla's
                if (taken && (kind == Kind.ANVIL || kind == Kind.SMITHING || kind == Kind.GRINDSTONE)) StationHands.blows(kind == Kind.SMITHING ? 1 : 0, kind == Kind.ANVIL ? 3 : 1);
            } else if (!tapped && i != res) { StationHands.pass(i); tapped = true; }
        }
        // the view is home again: the view you had before comes back
        if (!open && before != null && weight(0) <= 0) restoreView();
    }

    /** Your own click on a slot (the screen's): the next change is your hand's. */
    static void clicked() {
        clickedAt = ticks;
    }

    /** Where a container screen draws its slots from (a station scene reports a wider area to other mods). */
    public static int slotLeft(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> s) {
        return s instanceof StationScreen<?> st ? st.slotLeft() : s.getLeftPos();
    }

    public static int slotTop(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> s) {
        return s instanceof StationScreen<?> st ? st.slotTop() : s.getTopPos();
    }

    /** The hand holds the pointer's stack in the world this frame (so no icon is drawn on the pointer). */
    public static boolean handCarries() {
        return StationHands.carrying();
    }

    /** Dev (the pilot's "station <slot> <button>"): a click on a slot of the open table scene, as a player's. */
    public static void devClick(int slot, int button) {
        if (Minecraft.getInstance().gui.screen() instanceof StationScreen<?> s) s.devClick(slot, button);
    }

    /**
     * Dev (the pilot's "hover <slot>"): the mouse over a slot of the open station scene, where the view shows it (a
     * station slot on the station, any other at its place on the strip), so a tour can film a carried stack over the grid.
     */
    public static void devHover(int slot) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof StationScreen<?> s) || station == null) return;
        double gx, gy;
        if (slot < kind.slots) {
            double[] at = new double[3], out = new double[2];
            spot(slot, at, 0);
            var cam = mc.gameRenderer.mainCamera();
            Vec3 o = cam.position();
            float aspect = (float) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
            if (!StationView.project(at[0], at[1], at[2], o.x, o.y, o.z, cam.rotation(), cam.getFov(), aspect, s.width, s.height, out)) return;
            gx = out[0];
            gy = out[1];
        } else {
            var sl = s.getMenu().getSlot(slot);
            gx = s.slotLeft() + sl.x + 8;
            gy = s.slotTop() + sl.y + 8;
        }
        var w = mc.getWindow();
        mc.mouseHandler.onMove(w.handle(), gx * w.getScreenWidth() / w.getGuiScaledWidth(), gy * w.getScreenHeight() / w.getGuiScaledHeight(), 0, 0);
    }

    private static int mainSide() {
        LocalPlayer p = Minecraft.getInstance().player;
        return p != null && p.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT ? 1 : 0;
    }

    private static boolean on() {
        Minecraft mc = Minecraft.getInstance();
        return FirstPerson.stationsOn.getAsBoolean() && (mc.options.getCameraType().isFirstPerson() || before != null || mc.player != null && !mc.player.isSpectator());
    }

    private static void restoreView() {
        if (before != null) Minecraft.getInstance().options.setCameraType(before);
        before = null;
    }

    // ---- opening and closing ----------------------------------------------------------------------------------------

    private static void onUse(PlayerInteractEvent.RightClickBlock e) {
        if (!e.getLevel().isClientSide()) return;
        if (StationView.kindOf(e.getLevel().getBlockState(e.getPos())) == null) return;
        used = e.getPos().immutable();
        usedAt = ticks;
    }

    /** A station's screen opening just after it was used (not sneaking): the scene takes its place. */
    private static void onOpening(ScreenEvent.Opening e) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || used == null || ticks - usedAt > 10 || !on()) return;
        if (p.isShiftKeyDown()) return; // the escape hatch: the plain screen
        // the station you used, and the screen that is its own: the scene takes that screen's place over the same menu
        Kind k = StationView.kindOf(p.level().getBlockState(used));
        if (k == null || !(e.getNewScreen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> vs) || vs instanceof StationScreen<?>
                || !StationView.menuFits(k, vs.getMenu())) return;
        if (k == Kind.FURNACE && !FurnaceWorld.supported(used, p.level().getBlockState(used))) return;
        if (!begin(p, used, k)) return;
        e.setNewScreen(new StationScreen<>(vs.getMenu(), p.getInventory(), vs.getTitle(), k));
    }

    private static boolean begin(LocalPlayer p, BlockPos pos, Kind k) {
        Minecraft mc = Minecraft.getInstance();
        StationCamera.end();
        bench = false;
        double dx = pos.getX() + 0.5 - p.getX(), dz = pos.getZ() + 0.5 - p.getZ();
        BlockState st = p.level().getBlockState(pos);
        double wx, wz;
        if (k == Kind.FURNACE && st.hasProperty(AbstractFurnaceBlock.FACING)) {
            // a furnace is worked from its front, the way in against the way it faces
            Direction d = st.getValue(AbstractFurnaceBlock.FACING);
            wx = -d.getStepX();
            wz = -d.getStepZ();
        } else if (Math.abs(dx) >= Math.abs(dz)) {
            // the grid lines up with the block: the way in snapped to the nearest axis
            wx = Math.signum(dx);
            wz = 0;
        } else {
            wx = 0;
            wz = Math.signum(dz);
        }
        Station made = new Station(pos, k, wx, wz);
        // the view's own place: a furnace's mouth (or no scene), a table's bench view (or, with none clear, your own eyes)
        Vec3 eye = p.getEyePosition(1);
        boolean third = !mc.options.getCameraType().isFirstPerson();
        Vec3 from = third ? mc.gameRenderer.mainCamera().position() : null;
        Vec3 target = null;
        double[] e = new double[3];
        if (k == Kind.FURNACE) {
            if (!StationView.beforeFace(made, p.getX(), p.getZ())) return false;
            for (int i = 0; i < StationView.MOUTHS && target == null && FirstPerson.stationBench.getAsBoolean(); i++) {
                StationView.mouthEye(made, i, e);
                Vec3 t = new Vec3(e[0], e[1], e[2]);
                if (StationCamera.begin(p, t, from)) target = t;
            }
            if (target == null) {
                // no place before the mouth is clear: straight down where you stand, as it was (SPEC §24)
                if (!StationView.furnaceEye(made, eye.x, eye.y, eye.z, e)) return false;
                target = new Vec3(e[0], e[1], e[2]);
                if (!StationCamera.begin(p, target, from)) return false;
            }
        } else if (FirstPerson.stationBench.getAsBoolean()) {
            for (int i = 0; i < StationView.BENCHES && target == null; i++) {
                StationView.benchEye(made, i, e);
                Vec3 t = new Vec3(e[0], e[1], e[2]);
                if (StationCamera.begin(p, t, from)) target = t;
            }
        }
        bench = StationCamera.active();
        table = pos;
        kind = k;
        fx = wx;
        fz = wz;
        rx = -fz;
        rz = fx;
        station = made;
        if (third) {
            // the scene is seen from the station's own view: third person comes back when the view is home
            before = mc.options.getCameraType();
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
        }
        framed = false;
        boolean quick = ticks - closedAt < 240;
        if (bench && k.bench()) {
            inTicks = quick ? 4 : StationView.glideTicks(Math.sqrt((from != null ? from : eye).distanceToSqr(target)));
            outTicks = inTicks;
            openedAt = ticks;
        } else if (k == Kind.FURNACE) {
            // a crouch: down and in over 8 ticks (5 coming back to it), up and out over 7
            StationSounds.kneel(pos);
            inTicks = quick ? 5 : 8;
            outTicks = 7;
            openedAt = ticks;
        } else {
            inTicks = 12;
            outTicks = 10;
            openedAt = ticks - (quick ? 6 : 0);
        }
        open = true;
        java.util.Arrays.fill(SEEN, ItemStack.EMPTY);
        java.util.Arrays.fill(LAID_AT, 0L);
        java.util.Arrays.fill(FLECK_AT, 0L);
        gaveAt = 0;
        resultAt = 0;
        StationHands.reset();
        StationFire.reset();
        return true;
    }

    public static boolean ownsCamera() { return StationCamera.active(); }

    private static void onPlayer(net.neoforged.neoforge.client.event.RenderPlayerEvent.Pre<?> e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || e.getRenderState().id != mc.player.getId()) return;
        // from the station's view your own body is hidden; coming from third person you see it on the way in and out
        if (ownsCamera() && !(before != null && weight(0) < 0.85F)) e.setCanceled(true);
    }

    static void closed() {
        if (!open) return;
        open = false;
        closedAt = ticks;
    }

    /** How far in the scene is (0 out, 1 in): eased in over this visit's glide, and out. */
    static float weight(float pt) {
        float t = ticks + pt;
        float in = Ease.at(Ease.OUT_CUBIC, (t - openedAt) / (float) inTicks);
        if (open) return in;
        return Math.min(in, 1 - Ease.at(Ease.IN_OUT_SINE, (t - closedAt) / (float) outTicks));
    }

    /** How far through its glide the view is, uneased (0 out, 1 in). */
    private static float progress(float pt) {
        float t = ticks + pt;
        float in = Mth.clamp((t - openedAt) / (float) inTicks, 0F, 1F);
        return open ? in : Math.min(in, 1 - Mth.clamp((t - closedAt) / (float) outTicks, 0F, 1F));
    }

    // ---- the slots on the station -----------------------------------------------------------------------------------

    /** What the view leans in to before it is framed: a table's top, a furnace's front. */
    static Vec3 top() {
        BlockPos b = table;
        if (b == null) return Vec3.ZERO;
        if (kind == Kind.FURNACE) return new Vec3(b.getX() + 0.5 - fx * 0.5, b.getY() + 0.36, b.getZ() + 0.5 - fz * 0.5);
        return new Vec3(b.getX() + 0.5, b.getY() + kind.work, b.getZ() + 0.5);
    }

    /** Where a slot lies on the station (StationView.spot). */
    static void spot(int slot, double[] out, int at) {
        if (station != null) StationView.spot(station, slot, out, at);
    }

    /** The slot under the screen's cursor (gui coordinates) in the view as drawn; -1 for none or outside the room. */
    static int pick(StationView.Layout l, double mx, double my, int guiW, int guiH) {
        Minecraft mc = Minecraft.getInstance();
        if (station == null) return -1;
        var cam = mc.gameRenderer.mainCamera();
        Vec3 o = cam.position();
        float aspect = (float) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        return StationView.pick(station, l, o.x, o.y, o.z, cam.rotation(), cam.getFov(), aspect, mx, my, guiW, guiH);
    }

    /** The view that frames every slot in the screen's room, from where you stand (each tick: the screen may resize). */
    private static void reframe(StationScreen<?> s) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (station == null || p == null || s.layout == null) return;
        Vec3 eye = bench || kind == Kind.FURNACE ? StationCamera.eye(p) : p.getEyePosition(1F);
        float aspect = (float) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        float most = Math.min(StationView.MAX_FOV, mc.options.fov().get() * 1.5F);
        StationView.frame(station, eye.x, eye.y, eye.z, s.layout, s.width, s.height, aspect, most, FRAME);
        framed = true;
    }

    // ---- the view ---------------------------------------------------------------------------------------------------

    private static void onAngles(ViewportEvent.ComputeCameraAngles e) {
        float w = weight((float) e.getPartialTick());
        if (w <= 0 || table == null) return;
        if (!framed && Minecraft.getInstance().gui.screen() instanceof StationScreen<?> s) reframe(s);
        float yaw, pitch;
        if (framed) {
            // the view that frames every slot above the strip
            yaw = FRAME[0];
            pitch = FRAME[1];
        } else {
            Vec3 o = e.getCamera().position(), t = top();
            double ax = t.x - o.x, ay = t.y - o.y, az = t.z - o.z;
            yaw = (float) (Mth.atan2(-ax, az) * Mth.RAD_TO_DEG);
            pitch = (float) (-Mth.atan2(ay, Math.sqrt(ax * ax + az * az)) * Mth.RAD_TO_DEG);
        }
        LocalPlayer player = Minecraft.getInstance().player;
        // The camera entity already carries last frame's turn. Always ease from the player's untouched look.
        boolean own = StationCamera.active();
        float fromYaw = own && player != null ? player.getViewYRot(e.getPartialTick()) : e.getYaw();
        float fromPitch = own && player != null ? player.getViewXRot(e.getPartialTick()) : e.getPitch();
        e.setYaw(fromYaw + Mth.wrapDegrees(yaw - fromYaw) * w);
        e.setPitch(Mth.lerp(w, fromPitch, pitch));
        // from your own eyes, the slightest breath while you work; a station's own view holds still (you are aiming)
        if (!own) e.setPitch(e.getPitch() + 0.25F * w * Mth.sin((ticks + (float) e.getPartialTick()) * 0.12F));
        if (!own) return;
        if (kind != Kind.FURNACE) {
            StationCamera.place(w, w, (float) e.getPartialTick(), e.getYaw(), e.getPitch(), 0);
            return;
        }
        // a furnace: the view goes down the way a body does (SPEC §63.6). Down first, on the knees; then in to the
        // mouth; a nod forward as the knees bend, and a small settle as it arrives.
        float u = progress((float) e.getPartialTick());
        float down = 1 - (1 - u) * (1 - u) * (1 - u) * (1 - u), across = Ease.at(Ease.IN_OUT_SINE, u);
        boolean still = FirstPerson.reducedMotion.getAsBoolean();
        if (!still) e.setPitch(e.getPitch() + 5F * Mth.sin(Mth.PI * Mth.clamp(u / 0.6F, 0F, 1F)) * (1 - u));
        float since = ticks + (float) e.getPartialTick() - openedAt - inTicks * 0.8F;
        double settle = open && !still && since > 0 && since < 5 ? 0.03 * Math.sin(Math.PI * since / 5.0) : 0;
        StationCamera.place(across, down, (float) e.getPartialTick(), e.getYaw(), e.getPitch(), settle);
    }

    private static void onFovModifier(ComputeFovModifierEvent e) {
        float w = weight(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
        // the field of view the frame asks for, whatever the speed or the setting would make it
        if (kind.bench() && !bench && table != null && w > 0 && framed) e.setNewFovModifier(Mth.lerp(w, e.getNewFovModifier(), FRAME[2] / Minecraft.getInstance().options.fov().get()));
    }

    private static void onFov(ViewportEvent.ComputeFov e) {
        if (!StationCamera.active() || !framed || table == null) return;
        // A non-player camera does not run ComputeFovModifierEvent; use the supported final FOV hook.
        float w = weight(e.getPartialTick());
        if (w > 0) e.setFOV(Mth.lerp(w, e.getFOV(), FRAME[2]));
    }

    // ---- the world: the items on the station, the hovered slot --------------------------------------------------------

    private static final double[] P = new double[3];
    private static final org.joml.Quaternionf TILT = new org.joml.Quaternionf();

    /** How far a stack your click just set down still has to go to its slot (1 just let go, 0 there). */
    static float unsettled(int slot) {
        if (slot < 0 || slot >= LAID_AT.length || LAID_AT[slot] == 0) return 0F;
        double u = (System.nanoTime() - LAID_AT[slot]) / (double) SETTLE_NS;
        return u >= 1 ? 0F : 1 - Ease.at(Ease.OUT_CUBIC, (float) u);
    }

    private static final java.util.Map<Kind, VoxelShape> OUTLINES = new java.util.EnumMap<>(Kind.class);

    /** A workstation slot's outline: a thin square the slot's own size where a stack lies, a box where it stands. */
    private static VoxelShape outline(Kind k) {
        return OUTLINES.computeIfAbsent(k, x -> x.upright ? Shapes.box(-x.half, -x.half, -x.half, x.half, x.half, x.half)
                : Shapes.box(-x.half, 0, -x.half, x.half, 0.02, x.half));
    }

    /** How many of a stack are drawn lying on a square or in the hand: 1, then 2 up to 5, 3 up to 32, 4 above. */
    static int pile(int count) {
        return count > 32 ? 4 : count > 5 ? 3 : count > 1 ? 2 : 1;
    }

    /** A table square's outline: the square between its lines (StationView.CELL). */
    private static final VoxelShape CELL = Shapes.box(-0.084, 0, -0.084, 0.084, 0.02, 0.084);
    /** A furnace slot's outline: a thin square flat to the front (facing along z, or along x). */
    private static final VoxelShape FACE_Z = Shapes.box(-0.14, -0.14, -0.02, 0.14, 0.14, 0.02), FACE_X = Shapes.box(-0.02, -0.14, -0.14, 0.02, 0.14, 0.14);

    private static void onSubmit(SubmitCustomGeometryEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (!open || table == null || mc.level == null || !(mc.gui.screen() instanceof StationScreen<?> s)) return;
        Vec3 cam = e.getLevelRenderState().cameraRenderState.pos;
        PoseStack ps = e.getPoseStack();
        boolean furnace = kind == Kind.FURNACE;
        // lit by the air over the table (a furnace's own items are FurnaceWorld's, in its mouth, open or shut)
        BlockPos above = table.above();
        int light = LightCoordsUtil.pack(mc.level.getBrightness(LightLayer.BLOCK, above), mc.level.getBrightness(LightLayer.SKY, above));
        float t = ticks + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float yaw = (float) (Mth.atan2(-fx, fz) * Mth.RAD_TO_DEG);
        ItemStack carried = s.carriedShown(s.getMenu().getCarried());
        long now = System.nanoTime();
        for (int i = 0; !furnace && i < kind.slots; i++) {
            ItemStack st = s.getMenu().getSlot(i).getItem();
            // a square a drag has passed over shows what it will get
            if (st.isEmpty() && s.dragging(i)) st = carried;
            if (st.isEmpty()) continue;
            spot(i, P, 0);
            ItemStackRenderState r = ITEMS[i];
            mc.getItemModelResolver().updateForTopItem(r, st, ItemDisplayContext.FIXED, mc.level, null, i);
            boolean result = i == kind.result;
            // set down, a stack drops the last of the way and settles; the result comes together on its border
            double lift = 0;
            float grow = 1F;
            if (result) {
                double u = resultAt == 0 ? 1 : (now - resultAt) / (double) RESULT_NS;
                if (u < 1) {
                    float k = Ease.at(Ease.OUT_CUBIC, (float) u);
                    grow = 0.55F + 0.45F * k;
                    lift = 0.035 * (1 - k);
                }
            } else if (LAID_AT[i] != 0) {
                double u = (now - LAID_AT[i]) / (double) SETTLE_NS;
                if (u < 1) lift = StationView.HOVER * 0.85 * (1 - Ease.at(Ease.OUT_CUBIC, (float) u));
                else LAID_AT[i] = 0;
            } else if (gaveAt != 0) {
                // what is left on the station gives a little as the result is taken from it
                double u = (now - gaveAt) / (double) GIVE_NS;
                if (u < 1) grow = 1F - 0.1F * Mth.sin((float) (u * Math.PI));
            }
            // a stack lies as a small pile (as dropped stacks do)
            int n = pile(st.getCount());
            for (int k = 0; k < n; k++) {
                ps.pushPose();
                ps.translate(P[0] + (rx * 0.012 - fx * 0.012) * k - cam.x, P[1] + lift + 0.006 * k - cam.y, P[2] + (rz * 0.012 - fz * 0.012) * k - cam.z);
                if (kind.upright) {
                    // a brewing stand's bottles stand on its arms, turned to you
                    ps.rotateDegrees(Axis.YP, 180F - yaw);
                    ps.scale(kind.size * grow, kind.size * grow, kind.size * grow);
                } else if (result) {
                    // the result lies flat on its own place, turning slowly where it lies
                    ps.rotateDegrees(Axis.YP, t * 1.5F);
                    ps.rotateDegrees(Axis.XP, 90F);
                    float size = (kind == Kind.TABLE ? 0.17F : kind.size) * grow;
                    ps.scale(size, size, size);
                } else {
                    // an ingredient lies flat on its square, square to the station (a flat item a little over the square
                    // between the lines, a block half that)
                    ps.rotateDegrees(Axis.YP, 180F - yaw);
                    ps.rotateDegrees(Axis.XP, 90F);
                    ps.scale(kind.size * grow, kind.size * grow, kind.size * grow);
                }
                r.submit(ps, e.getSubmitNodeCollector(), light, OverlayTexture.NO_OVERLAY, 0);
                ps.popPose();
            }
        }
        // flecks of what a taken result used up: three small pieces of it thrown from its square, falling back
        for (int i = 0; !furnace && i < kind.slots; i++) {
            if (FLECK_AT[i] == 0) continue;
            float u = (float) ((now - FLECK_AT[i]) / (double) FLECK_NS);
            if (u >= 1 || FLECKS[i].isEmpty()) {
                FLECK_AT[i] = 0;
                continue;
            }
            spot(i, P, 0);
            float size = kind.size * 0.22F * (1F - 0.5F * u);
            for (int k = 0; k < 3; k++) {
                float a = i * 2.4F + k * 2.1F, out = 0.03F + 0.09F * Ease.at(Ease.OUT_CUBIC, u);
                ps.pushPose();
                ps.translate(P[0] + Mth.cos(a) * out - cam.x, P[1] + 0.012 + (0.05 + 0.012 * k) * 4 * u * (1 - u) - cam.y, P[2] + Mth.sin(a) * out - cam.z);
                ps.rotateDegrees(Axis.YP, a * 57.3F + u * 220F);
                ps.rotateDegrees(Axis.XP, 90F - u * 120F);
                ps.scale(size, size, size);
                FLECKS[i].submit(ps, e.getSubmitNodeCollector(), light, OverlayTexture.NO_OVERLAY, 0);
                ps.popPose();
            }
        }
        // the stack in your hand: exactly on the pointer's own line, flat as it will lie, its far edge tipped up to you,
        // rolling a little with the pointer's speed
        if (!furnace && !carried.isEmpty() && StationHands.hit(HELD)) {
            ItemStack shown = carried;
            {
                mc.getItemModelResolver().updateForTopItem(CARRIED, shown, ItemDisplayContext.FIXED, mc.level, null, 99);
                float roll = Mth.clamp((float) StationHands.speedX() * 9F, -14F, 14F);
                int n = pile(shown.getCount());
                for (int k = 0; k < n; k++) {
                    ps.pushPose();
                    ps.translate(HELD[0] + (rx * 0.012 - fx * 0.012) * k - cam.x, HELD[1] + 0.007 * k - cam.y, HELD[2] + (rz * 0.012 - fz * 0.012) * k - cam.z);
                    if (!kind.upright) ps.rotate(TILT.rotationAxis(18F * Mth.DEG_TO_RAD, (float) rx, 0F, (float) rz));
                    ps.rotate(TILT.rotationAxis(-roll * Mth.DEG_TO_RAD, (float) fx, 0F, (float) fz));
                    ps.rotateDegrees(Axis.YP, 180F - yaw);
                    if (!kind.upright) ps.rotateDegrees(Axis.XP, 90F);
                    float held = kind == Kind.TABLE ? 0.16F : kind.size * 0.94F;
                    ps.scale(held, held, held);
                    CARRIED.submit(ps, e.getSubmitNodeCollector(), light, OverlayTexture.NO_OVERLAY, 0);
                    ps.popPose();
                }
            }
        }
        // before a furnace the stack in your hand is held upright, just out from the face (inside the stone it could not
        // be seen), facing you as it will sit in the mouth
        if (furnace && !carried.isEmpty() && StationHands.hit(HELD)) {
            mc.getItemModelResolver().updateForTopItem(CARRIED, carried, ItemDisplayContext.FIXED, mc.level, null, 99);
            float roll = Mth.clamp((float) StationHands.speedX() * 9F, -14F, 14F);
            int n = pile(carried.getCount());
            for (int k = 0; k < n; k++) {
                ps.pushPose();
                ps.translate(HELD[0] + rx * 0.02 * k - fx * 0.012 * k - cam.x, HELD[1] + 0.012 * k - cam.y, HELD[2] + rz * 0.02 * k - fz * 0.012 * k - cam.z);
                ps.rotate(TILT.rotationAxis(-roll * Mth.DEG_TO_RAD, (float) fx, 0F, (float) fz));
                ps.rotateDegrees(Axis.YP, 180F - yaw);
                ps.scale(0.22F, 0.22F, 0.22F);
                CARRIED.submit(ps, e.getSubmitNodeCollector(), LightCoordsUtil.withBlock(light, Math.max(LightCoordsUtil.block(light), 8)), OverlayTexture.NO_OVERLAY, 0);
                ps.popPose();
            }
        }
        StationFire.submit(e, s);
        // the hovered slot's outline: every slot now lies on the station (the table's result on its border, the
        // furnace's result inside its mouth)
        for (int i = 0; i < kind.slots; i++) {
            if (i != s.hovered && !s.dragging(i)) continue;
            spot(i, P, 0);
            ps.pushPose();
            ps.translate(P[0] - cam.x, P[1] - cam.y, P[2] - cam.z);
            e.getSubmitNodeCollector().submitShapeOutline(ps, furnace ? (fx == 0 ? FACE_Z : FACE_X) : kind == Kind.TABLE ? CELL : outline(kind), RenderTypes.lines(),
                    0xCC7FC3FF, 2F, true);
            ps.popPose();
        }
    }

    // ---- the hands --------------------------------------------------------------------------------------------------

    /** The world layer's source: your hands at the station ({@link StationHands}). */
    private static boolean pose(float pt, FirstPerson.Rig rig) {
        Minecraft mc = Minecraft.getInstance();
        float w = weight(pt);
        if (w <= 0.01F || table == null || mc.player == null) return false;
        return StationHands.pose(w, rig, open && mc.gui.screen() instanceof StationScreen<?> sc ? sc : null);
    }
}
