package dev.awaken.mobs.client.finish;

import dev.awaken.core.api.CombatFeedback;
import dev.awaken.core.api.FirstPerson;
import dev.awaken.core.api.Shots;
import dev.awaken.mobs.MobSounds;
import dev.awaken.mobs.client.kit.KitConfig;
import dev.awaken.mobs.entity.AwakenBoss;
import dev.awaken.mobs.net.MobsNet;
import dev.awaken.powers.client.fx.FxClient;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;

/**
 * A boss's finishing blow as the client shows it (SPEC §67.4). The server decides everything and says it once
 * ({@code mobs_finish}, sent as the blow lands and the body dies): who fell, who struck, how long its ending plays.
 * From that one word this plays a fixed sequence, and nothing the server says later can change it:
 * <ul>
 * <li>for everyone near, the blow's light on the body and the body's own light leaving it while its death clip plays
 * (its ending: never vanilla's red flash or white puff);</li>
 * <li>for the one who struck: both bodies held for a beat, one white frame, the frame's bars, and, in the cinematic
 * view, the camera out of their eyes onto a low shot of the two of them for the ending, then back.</li>
 * </ul>
 * While the attack key is held on a beaten body in reach the bars begin to close, and stay closed until the blow has
 * had time to land: the blow is coming. The camera is the game's one cinematic camera ({@link Shots}): it holds the
 * controls, and whatever interrupts it gives the view back. The way back is a cut, never a flight through your own
 * head: the bars close over the shot's last ticks, the view is yours again under them, and they open on your own eyes.
 * When no shot is clear of the walls, or the setting or reduced motion asks, the same beats play through your own eyes.
 * <p>
 * The frame (bars, the white frame, the skip line) is drawn after every HUD layer, not as one: a shot hides the layers.
 */
public final class FinishClient {
    private FinishClient() {}

    /** How long both bodies are held as the blow lands (ms), and the shares of the frame its bars take. */
    static final int FREEZE_MS = 280;
    private static final float BAR_SHARE = 0.115F;
    /** The shot: at least and at most this long on the ending (ticks); the bars close over its last ticks. */
    private static final int HOLD_MIN = 26, HOLD_MAX = 66, SHUT = 5;
    /** The lens: the share of your own field of view the shot draws in to, and how much of the frame's height the body's box stands. */
    private static final float FOV_IN = 0.74F, FILL = 0.62F;
    /** The bars stay closed this long after the attack key is let go on a beaten body (ticks): the blow is on its way. */
    private static final int ARMED = 16;
    /** Sneak held this long skips an ending you have watched before (ticks). */
    private static final int SKIP_HOLD = 10;
    private static final int CLASH = 0xFFF2CC;

    // the bars: 0 open, 1 closed; eased once a tick, drawn between ticks
    private static float bars, barsWas;
    // the way back: 0 open, 1 the bars met (the frame is black); the running shot's age, its length, and a skip asked for
    private static float shut, shutWas;
    private static int shotAge, shotLength, skipAt = -1;
    private static int attackHeld, armed;
    // the ending now playing near: whose, how far through, and whether it is ours to watch
    private static int boss = -1, age, ending, glow;
    private static boolean mine, shotRunning, skippable;
    private static long flashAt;

    public static void init() {
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> tick());
        // after every layer: the cinematic camera hides the HUD's layers while a shot runs, and the frame must stay
        NeoForge.EVENT_BUS.addListener(RenderGuiEvent.Post.class, e -> frame(e.getGuiGraphics(), e.getPartialTick()));
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> reset());
        // a body that died to a finishing blow leaves by its own light, never vanilla's white puff
        AwakenBoss.vanish = FinishClient::vanish;
    }

    // ---- the server's one word ----------------------------------------------------------------------------------------

    public static void onFinish(MobsNet.Finish msg) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || !(mc.level.getEntity(msg.boss()) instanceof AwakenBoss b)) return;
        boss = msg.boss();
        age = 0;
        ending = Mth.clamp(msg.ticks(), 20, 80);
        glow = b.def().glow;
        // the blow's light, for everyone: white-gold where it landed, the body's own colour behind it, sparks the way it went
        double cx = b.getX(), cy = b.getY() + b.getBbHeight() * 0.55, cz = b.getZ();
        float wide = Math.max(1.1F, b.getBbWidth() * 1.05F);
        FxClient.tellMark(FxClient.TELL_FLARE, cx, cy, cz, CLASH, wide, 10);
        FxClient.tellMark(FxClient.TELL_FLARE, cx, cy, cz, glow, wide * 1.35F, 16);
        Entity striker = mc.level.getEntity(msg.striker());
        if (striker != null) {
            double dx = cx - striker.getX(), dz = cz - striker.getZ(), n = Math.max(1.0E-3, Math.sqrt(dx * dx + dz * dz));
            for (int i = 0; i < 5; i++) {
                double a = (i - 2) * 0.35;
                FxClient.guardSpark(cx, cy, cz, dx / n * Math.cos(a) - dz / n * Math.sin(a), 0.35 + 0.1 * i, dz / n * Math.cos(a) + dx / n * Math.sin(a), CLASH, 1F, false);
            }
        }
        mc.level.playLocalSound(cx, cy, cz, MobSounds.FINISH_BLOW.get(), SoundSource.HOSTILE, 1.4F, 1.0F, false);

        mine = msg.striker() == p.getId();
        KitConfig.FinishView view = KitConfig.finishView();
        if (!mine || view == KitConfig.FinishView.OFF) {
            mine = false;
            return;
        }
        boolean reduced = FirstPerson.reducedMotion.getAsBoolean();
        flashAt = System.nanoTime();
        CombatFeedback.hitstop(msg.boss(), FREEZE_MS);
        CombatFeedback.hitstop(p.getId(), FREEZE_MS);
        CombatFeedback.hold(FREEZE_MS);
        String kind = b.def().id;
        skippable = KitConfig.endingSeen(kind);
        if (view == KitConfig.FinishView.CINEMATIC && !reduced) {
            FinishShot shot = FinishShot.frame(mc, p, b, ending, kind);
            shotAge = 0;
            skipAt = -1;
            shotLength = shot == null ? 0 : shot.length();
            shotRunning = shot != null && Shots.take(shot);
        }
        if (!shotRunning) KitConfig.sawEnding(kind); // through your own eyes it has been seen as soon as it plays
    }

    // ---- once a tick --------------------------------------------------------------------------------------------------

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        barsWas = bars;
        shutWas = shut;
        if (p == null || mc.level == null) {
            reset();
            return;
        }
        if (mc.isPaused()) return;
        boolean watching = mine && boss >= 0 && age < ending;
        boolean want = shotRunning || watching || coming(mc, p);
        bars += ((want ? 1F : 0F) - bars) * (want ? 0.4F : 0.22F);
        if (bars < 0.01F) bars = 0F;
        // the way back: the bars meet over the shot's last ticks (or a skip), and open again once the view is yours
        boolean closing = shotRunning && (skipAt >= 0 || shotLength > 0 && shotAge >= shotLength - SHUT);
        shut = Mth.clamp(closing ? shut + (1F - shut) * 0.55F + 0.08F : shut - shut * 0.3F - 0.02F, 0F, 1F);
        if (boss < 0) return;
        age++;
        // its own light leaves it while its ending plays
        if (age <= ending && mc.level.getEntity(boss) instanceof AwakenBoss b) {
            float h = b.getBbHeight(), w = Math.max(0.5F, b.getBbWidth() * 0.5F);
            for (int i = 0; i < 2; i++) {
                double x = b.getX() + (b.getRandom().nextDouble() - 0.5) * 2 * w, z = b.getZ() + (b.getRandom().nextDouble() - 0.5) * 2 * w;
                double y = b.getY() + b.getRandom().nextDouble() * h * 0.85;
                FxClient.weaponMote(i == 0 ? FxClient.MOTE_EMBER : FxClient.MOTE_WISP, x, y, z, 0, 0.035 + b.getRandom().nextDouble() * 0.03, 0, glow, 0.16F + 0.1F * i, false);
            }
        }
        if (age > ending + SHUT + 30) {
            boss = -1;
            mine = false;
        }
    }

    /** The blow is coming: the attack key held past a click on a beaten body in reach, and for a moment after it is let go. */
    private static boolean coming(Minecraft mc, LocalPlayer p) {
        if (KitConfig.finishView() == KitConfig.FinishView.OFF || !p.isAlive()) {
            armed = 0;
            return false;
        }
        attackHeld = mc.options.keyAttack.isDown() ? attackHeld + 1 : 0;
        if (attackHeld >= 4 && !mc.level.getEntitiesOfClass(AwakenBoss.class, p.getBoundingBox().inflate(6.5), AwakenBoss::beaten).isEmpty()) armed = ARMED;
        else if (armed > 0) armed--;
        return armed > 0;
    }

    /** The body is gone: a last flare of its own colour and its light let go, where vanilla puffs white smoke. */
    private static void vanish(AwakenBoss b) {
        int rgb = b.def().glow;
        double cx = b.getX(), cy = b.getY() + b.getBbHeight() * 0.4, cz = b.getZ();
        FxClient.tellMark(FxClient.TELL_FLARE, cx, cy, cz, rgb, Math.max(1.4F, b.getBbWidth() * 1.3F), 14);
        float h = b.getBbHeight(), w = Math.max(0.5F, b.getBbWidth() * 0.6F);
        for (int i = 0; i < 28; i++) {
            double x = b.getX() + (b.getRandom().nextDouble() - 0.5) * 2 * w, z = b.getZ() + (b.getRandom().nextDouble() - 0.5) * 2 * w;
            double y = b.getY() + b.getRandom().nextDouble() * h * 0.7;
            FxClient.weaponMote((i & 1) == 0 ? FxClient.MOTE_MOTE : FxClient.MOTE_WISP, x, y, z, (x - cx) * 0.02, 0.03 + b.getRandom().nextDouble() * 0.05, (z - cz) * 0.02,
                    rgb, 0.18F, false);
        }
    }

    private static void reset() {
        bars = barsWas = shut = shutWas = 0F;
        boss = -1;
        mine = shotRunning = false;
        attackHeld = armed = shotAge = shotLength = 0;
        skipAt = -1;
        flashAt = 0;
    }

    // ---- the frame ------------------------------------------------------------------------------------------------------

    private static void frame(GuiGraphicsExtractor g, DeltaTracker dt) {
        float pt = dt.getGameTimeDeltaPartialTick(true);
        float b = Mth.lerp(pt, barsWas, bars), sh = Mth.lerp(pt, shutWas, shut);
        int w = g.guiWidth(), h = g.guiHeight();
        int bar = 0;
        if (b > 0.005F || sh > 0.005F) {
            float k = 1F - (1F - b) * (1F - b);
            float open = BAR_SHARE * k, met = sh * sh * (3F - 2F * sh);
            bar = sh >= 0.995F ? (h + 1) / 2 : Math.round(h * (open + (0.5F - open) * met));
            g.fill(0, 0, w, bar, 0xFF000000);
            g.fill(0, h - bar, w, h, 0xFF000000);
        }
        if (flashAt != 0) {
            float f = (System.nanoTime() - flashAt) / 220.0e6F;
            if (f < 1F) {
                float a = (1F - f) * (1F - f) * (FirstPerson.reducedMotion.getAsBoolean() ? 0.3F : 0.85F);
                g.fill(0, 0, w, h, ARGB.color(Math.round(a * 255), 0xFFF6E0));
            } else flashAt = 0;
        }
        if (shotRunning && skippable && skipAt < 0 && sh < 0.05F && bar > 12 && age > 12) {
            Minecraft mc = Minecraft.getInstance();
            String hint = Component.translatable("awaken.finish.skip", mc.options.keyShift.getTranslatedKeyMessage()).getString();
            g.centeredText(mc.font, hint, w / 2, h - bar / 2 - 4, 0xB0FFFFFF);
        }
    }

    // ---- the shot -------------------------------------------------------------------------------------------------------

    /**
     * Low and to one side of the two of you, a little to your end of the line between you, drawn in on the body and
     * pushing in slowly while its ending plays. Framed once, from where everyone stands as the blow lands, as far off as
     * fits the two of you across and the body {@link #FILL} of the frame high, and only where the way is clear. It ends
     * on a cut (the bars meet), never on a flight back.
     */
    static final class FinishShot implements Shots.Shot {
        private final Vec3 from, to, focus;
        private final int hold;
        private final String kind;
        private int sneak;

        private FinishShot(Vec3 from, Vec3 to, Vec3 focus, int hold, String kind) {
            this.from = from;
            this.to = to;
            this.focus = focus;
            this.hold = hold;
            this.kind = kind;
        }

        static @Nullable FinishShot frame(Minecraft mc, LocalPlayer p, AwakenBoss b, int ending, String kind) {
            Level level = p.level();
            Vec3 feet = p.position(), at = b.position();
            double ax = at.x - feet.x, az = at.z - feet.z, sep = Math.sqrt(ax * ax + az * az);
            if (sep < 0.5) {
                float yaw = p.getYRot() * Mth.DEG_TO_RAD;
                ax = -Mth.sin(yaw);
                az = Mth.cos(yaw);
                sep = 0.5;
            } else {
                ax /= sep;
                az /= sep;
            }
            float tall = Math.max(1.8F, b.getBbHeight());
            double my = Math.min(feet.y, at.y);
            // what the lens sees a block away, up and across: your own field of view, drawn in
            double half = Math.tan(Math.toRadians(mc.options.fov().get() * FOV_IN * 0.5));
            double aspect = (double) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
            double dist = Mth.clamp(Math.max((sep + b.getBbWidth() + 2.2) / (2 * half * aspect), tall / FILL / (2 * half)), 3.2, 14.0);
            // it looks between you, nearer the body, a little under its middle (a body's box stands taller than it does)
            Vec3 focus = new Vec3(at.x - ax * sep * 0.38, my + tall * 0.42, at.z - az * sep * 0.38);
            Vec3 chest = new Vec3(at.x, at.y + tall * 0.5, at.z), mine = new Vec3(feet.x, feet.y + 1.2, feet.z);
            int hold = 6 + Mth.clamp(ending, HOLD_MIN, HOLD_MAX);
            double low = my + 0.5 + tall * 0.06;
            // to its left, to its right (low, a little to your end), then high behind you
            for (int side = 1; side >= -1; side -= 2) {
                Vec3 c0 = new Vec3(focus.x - ax * sep * 0.25 - az * side * dist, low, focus.z - az * sep * 0.25 + ax * side * dist);
                Vec3 c1 = c0.lerp(new Vec3(focus.x, low, focus.z), 0.12);
                if (clear(level, p, c0, c1, mine, chest)) return new FinishShot(c0, c1, focus, hold, kind);
            }
            Vec3 c0 = new Vec3(feet.x - ax * 3.4, feet.y + 2.6, feet.z - az * 3.4), c1 = c0.lerp(focus, 0.12);
            return clear(level, p, c0, c1, mine, chest) ? new FinishShot(c0, c1, focus, hold, kind) : null;
        }

        /** Both ends of the push stand in open air and see both bodies. */
        private static boolean clear(Level level, LocalPlayer p, Vec3 c0, Vec3 c1, Vec3 mine, Vec3 chest) {
            for (Vec3 c : new Vec3[] {c0, c1}) {
                if (!level.noBlockCollision(null, new AABB(c.x - 0.35, c.y - 0.35, c.z - 0.35, c.x + 0.35, c.y + 0.35, c.z + 0.35))) return false;
                if (level.clip(new ClipContext(c, mine, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, p)).getType() != HitResult.Type.MISS) return false;
                if (level.clip(new ClipContext(c, chest, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, p)).getType() != HitResult.Type.MISS) return false;
            }
            return true;
        }

        @Override
        public void pose(float t, double[] out) {
            float k = Mth.clamp(t / hold, 0F, 1F);
            double e = 1 - (1 - k) * (1 - k);
            double x = Mth.lerp(e, from.x, to.x), y = Mth.lerp(e, from.y, to.y), z = Mth.lerp(e, from.z, to.z);
            out[0] = x;
            out[1] = y;
            out[2] = z;
            out[3] = (float) (Mth.atan2(focus.z - z, focus.x - x) * Mth.RAD_TO_DEG) - 90F;
            out[4] = (float) -(Mth.atan2(focus.y - y, Math.sqrt((focus.x - x) * (focus.x - x) + (focus.z - z) * (focus.z - z))) * Mth.RAD_TO_DEG);
            out[5] = 0;
            out[6] = Mth.lerp(e, FOV_IN + 0.03, FOV_IN - 0.02);
        }

        @Override
        public int length() {
            return hold + SHUT;
        }

        @Override
        public void tick(int age) {
            shotAge = age;
            if (skipAt >= 0) {
                // a skip closes the bars as the shot's own end does, then gives the view back under them
                if (age >= skipAt + SHUT) Shots.finish("skip");
                return;
            }
            if (!skippable) return;
            sneak = Minecraft.getInstance().options.keyShift.isDown() ? sneak + 1 : 0;
            if (sneak >= SKIP_HOLD) skipAt = age;
        }

        @Override
        public void finished(String reason) {
            shotRunning = false;
            shotLength = 0;
            skipAt = -1;
            if (reason.equals("done") || reason.equals("skip")) KitConfig.sawEnding(kind);
        }
    }
}
