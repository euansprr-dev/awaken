package dev.awaken.mobs.client.finish;

import dev.awaken.Awaken;
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
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
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
 * While the attack key is held on a beaten body in reach the bars begin to close: the blow is coming. The camera is the
 * game's one cinematic camera ({@link Shots}): it holds the controls, and whatever interrupts it gives the view back.
 * When no shot is clear of the walls, or the setting or reduced motion asks, the same beats play through your own eyes.
 */
public final class FinishClient {
    private FinishClient() {}

    public static final Identifier LAYER = Awaken.id("mobs_finish");

    /** How long both bodies are held as the blow lands (ms), and the shares of the frame its bars take. */
    static final int FREEZE_MS = 280;
    private static final float BAR_SHARE = 0.115F;
    /** The shot: at least and at most this long on the ending (ticks), then this long back to your eyes. */
    private static final int HOLD_MIN = 26, HOLD_MAX = 66, RETURN = 10;
    /** Sneak held this long skips an ending you have watched before (ticks). */
    private static final int SKIP_HOLD = 10;
    private static final int CLASH = 0xFFF2CC;

    // the bars: 0 open, 1 closed; eased once a tick, drawn between ticks
    private static float bars, barsWas;
    private static int attackHeld;
    // the ending now playing near: whose, how far through, and whether it is ours to watch
    private static int boss = -1, age, ending, glow;
    private static boolean mine, shotRunning, skippable;
    private static long flashAt;

    public static void init(IEventBus modBus) {
        modBus.addListener(RegisterGuiLayersEvent.class, e -> e.registerAboveAll(LAYER, FinishClient::layer));
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> tick());
        NeoForge.EVENT_BUS.addListener(RenderGuiLayerEvent.Pre.class, FinishClient::hud);
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
        float wide = Math.max(1.6F, b.getBbWidth() * 1.6F);
        FxClient.tellMark(FxClient.TELL_FLARE, cx, cy, cz, CLASH, wide, 12);
        FxClient.tellMark(FxClient.TELL_FLARE, cx, cy, cz, glow, wide * 1.5F, 18);
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
            FinishShot shot = FinishShot.frame(mc.level, p, b, ending, kind);
            shotRunning = shot != null && Shots.take(shot);
        }
        if (!shotRunning) KitConfig.sawEnding(kind); // through your own eyes it has been seen as soon as it plays
    }

    // ---- once a tick --------------------------------------------------------------------------------------------------

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        barsWas = bars;
        if (p == null || mc.level == null) {
            reset();
            return;
        }
        if (mc.isPaused()) return;
        boolean watching = mine && boss >= 0 && age < ending;
        boolean want = shotRunning || watching || coming(mc, p);
        bars += ((want ? 1F : 0F) - bars) * (want ? 0.4F : 0.22F);
        if (bars < 0.01F) bars = 0F;
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
        if (age > ending + RETURN + 20) {
            boss = -1;
            mine = false;
        }
    }

    /** The blow is coming: the attack key held past a click, on a beaten body in reach. */
    private static boolean coming(Minecraft mc, LocalPlayer p) {
        if (KitConfig.finishView() == KitConfig.FinishView.OFF || !p.isAlive()) return false;
        attackHeld = mc.options.keyAttack.isDown() ? attackHeld + 1 : 0;
        if (attackHeld < 4) return false;
        return !mc.level.getEntitiesOfClass(AwakenBoss.class, p.getBoundingBox().inflate(6.5), AwakenBoss::beaten).isEmpty();
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
        bars = barsWas = 0F;
        boss = -1;
        mine = shotRunning = false;
        attackHeld = 0;
        flashAt = 0;
    }

    // ---- the frame ------------------------------------------------------------------------------------------------------

    private static void layer(GuiGraphicsExtractor g, DeltaTracker dt) {
        float b = Mth.lerp(dt.getGameTimeDeltaPartialTick(true), barsWas, bars);
        int w = g.guiWidth(), h = g.guiHeight();
        int bar = 0;
        if (b > 0.005F) {
            float k = 1F - (1F - b) * (1F - b);
            bar = Math.round(h * BAR_SHARE * k);
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
        if (shotRunning && skippable && bar > 12 && age > 12) {
            Minecraft mc = Minecraft.getInstance();
            String hint = Component.translatable("awaken.finish.skip", mc.options.keyShift.getTranslatedKeyMessage()).getString();
            g.centeredText(mc.font, hint, w / 2, h - bar / 2 - 4, 0xB0FFFFFF);
        }
    }

    /** While the camera is out, nothing of the HUD shows but the frame itself. */
    private static void hud(RenderGuiLayerEvent.Pre e) {
        if (shotRunning && !e.getName().equals(LAYER)) e.setCanceled(true);
    }

    // ---- the shot -------------------------------------------------------------------------------------------------------

    /**
     * Low and to one side of the two of you, a little behind you, pushing in slowly while its ending plays; then back
     * into your own eyes. Framed once, from where everyone stands as the blow lands, and only where the way is clear.
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

        static @Nullable FinishShot frame(Level level, LocalPlayer p, AwakenBoss b, int ending, String kind) {
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
            float tall = b.getBbHeight();
            double mx = (feet.x + at.x) * 0.5, mz = (feet.z + at.z) * 0.5, my = Math.min(feet.y, at.y);
            Vec3 focus = new Vec3(mx + ax * sep * 0.15, my + Math.min(2.4, tall * 0.5 + 0.4), mz + az * sep * 0.15);
            Vec3 chest = new Vec3(at.x, at.y + tall * 0.5, at.z), mine = new Vec3(feet.x, feet.y + 1.2, feet.z);
            double dist = Mth.clamp(sep * 0.9 + tall * 0.9 + 2.0, 4.5, 11.0);
            int hold = 6 + Mth.clamp(ending, HOLD_MIN, HOLD_MAX);
            // to its left, to its right (low, a little behind you), then high behind you
            for (int side = 1; side >= -1; side -= 2) {
                Vec3 c0 = new Vec3(mx - ax * sep * 0.35 - az * side * dist, my + 0.9, mz - az * sep * 0.35 + ax * side * dist);
                Vec3 c1 = c0.lerp(focus, 0.14);
                if (clear(level, p, c0, c1, mine, chest)) return new FinishShot(c0, c1, focus, hold, kind);
            }
            Vec3 c0 = new Vec3(feet.x - ax * 4.2, feet.y + 3.0, feet.z - az * 4.2), c1 = c0.lerp(focus, 0.12);
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
            LocalPlayer p = Minecraft.getInstance().player;
            float k = Mth.clamp(t / hold, 0F, 1F);
            double e = 1 - (1 - k) * (1 - k);
            double x = Mth.lerp(e, from.x, to.x), y = Mth.lerp(e, from.y, to.y), z = Mth.lerp(e, from.z, to.z);
            float yaw = (float) (Mth.atan2(focus.z - z, focus.x - x) * Mth.RAD_TO_DEG) - 90F;
            float pitch = (float) -(Mth.atan2(focus.y - y, Math.sqrt((focus.x - x) * (focus.x - x) + (focus.z - z) * (focus.z - z))) * Mth.RAD_TO_DEG);
            double fov = Mth.lerp(e, 0.94, 0.88);
            if (t > hold && p != null) {
                // back into your own eyes
                float u = Mth.clamp((t - hold) / RETURN, 0F, 1F);
                u = u * u * (3 - 2 * u);
                Vec3 eye = p.getEyePosition();
                x = Mth.lerp(u, x, eye.x);
                y = Mth.lerp(u, y, eye.y);
                z = Mth.lerp(u, z, eye.z);
                yaw = Mth.rotLerp(u, yaw, p.getYRot());
                pitch = Mth.lerp(u, pitch, p.getXRot());
                fov = Mth.lerp(u, fov, 1.0);
            }
            out[0] = x;
            out[1] = y;
            out[2] = z;
            out[3] = yaw;
            out[4] = pitch;
            out[5] = 0;
            out[6] = fov;
        }

        @Override
        public int length() {
            return hold + RETURN;
        }

        @Override
        public void tick(int age) {
            if (!skippable) return;
            sneak = Minecraft.getInstance().options.keyShift.isDown() ? sneak + 1 : 0;
            if (sneak >= SKIP_HOLD) Shots.finish("skip");
        }

        @Override
        public void finished(String reason) {
            shotRunning = false;
            if (reason.equals("done") || reason.equals("skip")) KitConfig.sawEnding(kind);
        }
    }
}
