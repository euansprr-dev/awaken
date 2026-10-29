package dev.awaken.living.client;

import dev.awaken.Awaken;
import dev.awaken.living.LivingContent;
import dev.awaken.living.LivingNet;
import dev.awaken.opening.client.Cinema;
import dev.awaken.opening.client.OpeningCamera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * The first thread's two staged moments on screen (SPEC §27.6), filmed with the opening's camera and letterbox
 * ({@link Cinema}); the server ({@code living.FirstScenes}) moves them.
 * <ul>
 * <li>{@link GateShot}: out through the High Gate, the view lifts from your eyes over the gate, narrows onto the
 * Wayfarers' smoke rising over the trees, names it in the letterbox while Hale has his say, and comes back down into
 * your eyes facing it. Ten seconds; hold jump to skip.</li>
 * <li>{@link IlseShot}: by her fire, over your shoulder: Ilse, the fire beside her, her lines one at a time. When she
 * holds the coal out the view comes back to your own eyes and your hand takes it from hers.</li>
 * </ul>
 */
final class FirstFilm {
    private FirstFilm() {}

    private static final Identifier EMBER = Identifier.fromNamespaceAndPath("awaken", "hearth_ember");
    /** Your hand reaches her hand this far (she brings the coal to within about two). */
    static final double REACH = 3.6;

    /** Ilse's scene, while it runs: her entity id, her fire, whether the coal is held out. */
    private static int ilseId = -1;
    private static @Nullable BlockPos fire;
    private static boolean offering, taken;
    /** When the server last moved her scene (client ticks): a scene gone quiet 45 s lets go of you. */
    private static long lastCue;
    /** The lock's owner while the ember is held out (the shared input lock: core.client.InputLock). */
    private static final Object HOLD = "ilse's ember";
    /** Her hand stays out at most half a minute, then the server presses it into yours; past this, let go anyway. */
    static final int HOLD_MAX = 20 * 45;

    static void init() {
        NeoForge.EVENT_BUS.addListener(InputEvent.InteractionKeyMappingTriggered.class, FirstFilm::onUse);
    }

    static void onScene(LivingNet.FirstScene m) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        lastCue = LivingClient.ticks;
        switch (m.kind()) {
            case LivingNet.FirstScene.GATE -> {
                String[] parts = m.text().split("\n", 2);
                String[] said = parts[0].split("\\|", 2);
                String[] land = parts.length > 1 ? parts[1].split("\\|") : new String[0];
                PulseFx.camp(m.at());
                PulseFx.feature(GateShot.LENGTH + 40);
                Cinema.take(new GateShot(p, m.at(), m.n(), said.length == 2 ? said[0] : "", said.length == 2 ? said[1] : "",
                        land.length == 3 ? land[0] : "", land.length == 3 ? land[1] + " to " + land[2] : ""));
                Awaken.LOG.info("[living] the High Gate's shot of the camp's smoke, {} m", m.n());
            }
            case LivingNet.FirstScene.BEGIN -> {
                ilseId = m.entity();
                fire = m.at();
                offering = false;
                taken = false;
                Cinema.bars(true);
                Entity ilse = mc.level.getEntity(ilseId);
                if (ilse != null) Cinema.take(new IlseShot(p, ilse, m.at()));
            }
            case LivingNet.FirstScene.LINE -> {
                String[] said = m.text().split("\\|", 2);
                if (said.length == 2) Cinema.line(said[0], said[1], m.n());
            }
            case LivingNet.FirstScene.DELIVERED -> {
                Cinema.sound(SoundEvents.BOOK_PAGE_TURN, 0.9F, 0.9F);
                Cinema.barNote("LETTERS DELIVERED  1/1", "BRAM'S LETTERS, INTO ILSE'S HANDS", 90);
            }
            case LivingNet.FirstScene.OFFER -> {
                offering = true;
                Entity ilse = mc.level.getEntity(m.entity());
                // your own eyes come back facing her hand: the hand-over is yours to make
                if (ilse != null) face(p, ilse.position().add(0, 1.1, 0));
                hold(p);
                Cinema.hud(false);
                if (OpeningCamera.shot() instanceof IlseShot s) s.back();
            }
            case LivingNet.FirstScene.END -> end();
            default -> {}
        }
    }

    /** The scene is over: the bars lift, the hands and feet are yours again. */
    private static void end() {
        if (OpeningCamera.shot() instanceof IlseShot s) s.back();
        Cinema.bars(false);
        Cinema.hud(true);
        Cinema.prompt(null, "");
        // her last words fade with the scene (on a slow client a line could outstay it by ten seconds)
        Cinema.shortenLines(80);
        release();
        ilseId = -1;
        offering = false;
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) {
            ilseId = -1;
            return;
        }
        if (ilseId >= 0 && LivingClient.ticks - lastCue > 20 * 45) end();
        if (!offering || taken) return;
        Entity ilse = mc.level.getEntity(ilseId);
        if (ilse != null && !Cinema.filming() && !Cinema.handing() && ilse.distanceTo(p) > 2.4) face(p, ilse.position().add(0, 1.1, 0));
        boolean can = ilse != null && ilse.distanceTo(p) < REACH && !Cinema.handing() && !Cinema.filming();
        String use = mc.options.keyUse.getTranslatedKeyMessage().getString();
        Cinema.prompt(can ? use : null, can ? "Take the ember" : "");
    }

    /** Use takes the coal from Ilse's hand; nothing else happens with a click while her scene runs. */
    private static void onUse(InputEvent.InteractionKeyMappingTriggered e) {
        if (ilseId < 0) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.gui.screen() != null) return;
        e.setCanceled(true);
        e.setSwingHand(false);
        if (!e.isUseItem() || !offering || taken || Cinema.handing()) return;
        if (!(mc.level.getEntity(ilseId) instanceof LivingEntity ilse) || ilse.distanceTo(p) > REACH) return;
        Cinema.prompt(null, "");
        ItemStack coal = new ItemStack(BuiltInRegistries.ITEM.getValue(EMBER));
        Cinema.handOver(ilse, coal, () -> {
            taken = true;
            Cinema.sound(SoundEvents.FIRECHARGE_USE, 1.6F, 0.25F);
            ClientPacketDistributor.sendToServer(new LivingNet.FirstSceneReady(LivingNet.FirstSceneReady.TOOK));
        });
    }

    private static void hold(LocalPlayer p) {
        dev.awaken.core.client.InputLock.hold(HOLD, HOLD_MAX);
    }

    private static void release() {
        dev.awaken.core.client.InputLock.release(HOLD);
    }

    /** Turns the player's own view toward a point (their eyes come back there). */
    static void face(LocalPlayer p, Vec3 at) {
        Vec3 e = p.getEyePosition();
        double dx = at.x - e.x, dy = at.y - e.y, dz = at.z - e.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz)), pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        p.setYRot(yaw);
        p.setXRot(Mth.clamp(pitch, -60, 60));
        p.yRotO = yaw;
        p.xRotO = p.getXRot();
        p.setYHeadRot(yaw);
        p.yHeadRotO = yaw;
    }

    static double ease(double k) {
        k = Mth.clamp(k, 0, 1);
        return k * k * (3 - 2 * k);
    }

    static void aim(double[] out, double x, double y, double z, Vec3 look, double roll, double fov) {
        out[0] = x;
        out[1] = y;
        out[2] = z;
        double dx = look.x - x, dy = look.y - y, dz = look.z - z;
        out[3] = Math.toDegrees(Math.atan2(-dx, dz));
        out[4] = -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        out[5] = roll;
        out[6] = fov;
    }

    static Vec3 lerp(Vec3 a, Vec3 b, double k) {
        return new Vec3(Mth.lerp(k, a.x, b.x), Mth.lerp(k, a.y, b.y), Mth.lerp(k, a.z, b.z));
    }

    /** Nothing solid between a and b. */
    static boolean clear(LocalPlayer p, Vec3 a, Vec3 b) {
        return p.level().clip(new ClipContext(a, b, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p)).getType() == HitResult.Type.MISS;
    }

    // ---- the High Gate -----------------------------------------------------------------------------------------------

    /** Out through the High Gate: up over the gate, onto the smoke, the place named, back down into your eyes. */
    static final class GateShot implements OpeningCamera.Shot {
        static final int IN = 30, RISE = 90, HOLD = 160, LENGTH = 205;

        private final Vec3 eye, look0, smoke, base, crane, toward;
        private final int metres;
        private final String who, line, land, levels;

        GateShot(LocalPlayer p, BlockPos fire, int metres, String who, String line, String land, String levels) {
            this.metres = metres;
            this.who = who;
            this.line = line;
            this.land = land;
            this.levels = levels;
            eye = p.getEyePosition();
            look0 = eye.add(p.getViewVector(1F).scale(20));
            base = Vec3.atBottomCenterOf(fire);
            // the column leans downwind as it climbs (PulseFx.campfire): aim at its middle, well over the canopy
            smoke = base.add(7, 62, 3);
            Vec3 flat = new Vec3(base.x - eye.x, 0, base.z - eye.z);
            toward = flat.lengthSqr() < 1e-4 ? new Vec3(0, 0, -1) : flat.normalize();
            crane = pickCrane(p);
        }

        /** Up over the gate and on a little toward the smoke, with nothing in the way; lower or back if something is. */
        private Vec3 pickCrane(LocalPlayer p) {
            double[][] spots = {{8, 13}, {4, 15}, {0, 12}, {-4, 10}, {0, 7}, {0, 4}}; // {on toward the smoke, up}
            for (double[] s : spots) {
                Vec3 c = eye.add(toward.scale(s[0])).add(0, s[1], 0);
                if (clear(p, eye, c) && clear(p, c, c.add(smoke.subtract(c).normalize().scale(40)))) return c;
            }
            return eye.add(0, 2, 0);
        }

        @Override
        public int length() {
            return LENGTH;
        }

        @Override
        public boolean skippable() {
            return true;
        }

        @Override
        public void tick(int age) {
            if (age == 2) Cinema.bars(true);
            // the land you're stepping into, as the view lifts over the gate: the first time it's named
            if (age == IN + 8 && !land.isEmpty()) {
                Cinema.title(land.toUpperCase(java.util.Locale.ROOT), "THE LAND AROUND THE CITY · CREATURES LEVEL " + levels.toUpperCase(java.util.Locale.ROOT), 64);
                Cinema.sound(LivingContent.REGION_ENTER.get(), 0.8F, 0.6F);
            }
            if (age == RISE + 6) {
                Cinema.barNote("THE LAST FIRE", "UP THE NORTH ROAD  ·  " + metres + " M AWAY  ·  YOUR NEXT STOP", HOLD - RISE + 10);
            }
            if (age == RISE + 16 && !line.isEmpty()) Cinema.line(who, line, 20 * 4 + line.length());
            if (age == LENGTH - 22) Cinema.bars(false);
        }

        @Override
        public void pose(float t, double[] out) {
            if (t < IN) {
                // a breath in your own eyes, turning to the smoke
                double k = ease(t / IN);
                aim(out, eye.x, eye.y, eye.z, lerp(look0, smoke, k), 0, 1 - 0.08 * k);
            } else if (t < RISE) {
                // up over the gate, the view narrowing onto the column
                double k = ease((t - IN) / (RISE - IN));
                Vec3 c = lerp(eye, crane, k);
                aim(out, c.x, c.y, c.z, smoke, 0, Mth.lerp(k, 0.92, 0.6));
            } else if (t < HOLD) {
                // the hold: a slow push along the road toward it
                double k = (t - RISE) / (HOLD - RISE);
                Vec3 c = crane.add(toward.scale(2.5 * k));
                aim(out, c.x, c.y, c.z, smoke.add(0, -4 * k, 0), 0, Mth.lerp(k, 0.6, 0.54));
            } else {
                // back down into your eyes, facing it
                double k = ease((t - HOLD) / (LENGTH - HOLD));
                Vec3 c = lerp(crane.add(toward.scale(2.5)), eye, k);
                Vec3 ahead = eye.add(toward.scale(30)).add(0, 3, 0);
                aim(out, c.x, c.y, c.z, lerp(smoke.add(0, -4, 0), ahead, k), 0, Mth.lerp(k, 0.54, 1));
            }
        }

        @Override
        public boolean hideBody(double camDistSq) {
            return camDistSq < 1.0;
        }

        @Override
        public void finished(String reason) {
            Cinema.bars(false);
            LocalPlayer p = Minecraft.getInstance().player;
            if (p != null) face(p, eye.add(toward.scale(30)).add(0, 3, 0));
        }
    }

    // ---- Ilse by her fire --------------------------------------------------------------------------------------------

    /** Over your shoulder: Ilse with her fire beside her, a slow push while she talks; back to your eyes for the coal. */
    static final class IlseShot implements OpeningCamera.Shot {
        static final int IN = 26, BACK = 22;

        private final Entity ilse;
        private final Vec3 eye, fireAt, look0;
        private Vec3 cam, look;
        private int backAt = -1;

        IlseShot(LocalPlayer p, Entity ilse, BlockPos fire) {
            this.ilse = ilse;
            eye = p.getEyePosition();
            look0 = eye.add(p.getViewVector(1F).scale(6));
            fireAt = Vec3.atBottomCenterOf(fire).add(0, 0.5, 0);
            cam = frame(p);
            look = target();
        }

        /** Behind your shoulder on the fire's side, so your head is off to one side and she and the fire are both clear. */
        private Vec3 frame(LocalPlayer p) {
            Vec3 head = ilse.position().add(0, ilse.getEyeHeight(), 0);
            Vec3 flat = new Vec3(head.x - eye.x, 0, head.z - eye.z);
            Vec3 fwd = flat.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : flat.normalize();
            Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
            double side = fireAt.subtract(eye).dot(right) >= 0 ? 1 : -1;
            for (double back : new double[]{1.8, 1.3, 0.9}) {
                Vec3 c = eye.add(fwd.scale(-back)).add(right.scale(side * 1.05)).add(0, 0.2, 0);
                if (clear(p, eye, c)) return c;
                c = eye.add(fwd.scale(-back)).add(right.scale(-side * 1.05)).add(0, 0.2, 0);
                if (clear(p, eye, c)) return c;
            }
            return eye;
        }

        /** Her face, drawn a little toward the fire. */
        private Vec3 target() {
            Vec3 head = ilse.position().add(0, ilse.getEyeHeight() - 0.15, 0);
            return lerp(head, fireAt, 0.22);
        }

        /** The coal is held out (or the scene ended): back into your eyes. */
        void back() {
            if (backAt < 0) backAt = OpeningCamera.age();
        }

        @Override
        public int length() {
            return backAt < 0 ? -1 : backAt + BACK;
        }

        @Override
        public boolean skippable() {
            return backAt < 0;
        }

        /** A held skip: the server moves her on to the coal (the view comes back then). */
        @Override
        public void skip() {
            ClientPacketDistributor.sendToServer(new LivingNet.FirstSceneReady(LivingNet.FirstSceneReady.SKIP));
        }

        @Override
        public void tick(int age) {
            if (!ilse.isRemoved()) look = lerp(look, target(), 0.15);
        }

        @Override
        public void pose(float t, double[] out) {
            LocalPlayer p = Minecraft.getInstance().player;
            if (backAt >= 0 && p != null) {
                double k = ease((t - backAt) / BACK);
                Vec3 c = lerp(pushed(backAt), p.getEyePosition(), k);
                Vec3 own = p.getEyePosition().add(p.getViewVector(1F).scale(4));
                aim(out, c.x, c.y, c.z, lerp(look, own, k), 0, Mth.lerp(k, 0.82, 1));
            } else if (t < IN) {
                double k = ease(t / IN);
                Vec3 c = lerp(eye, cam, k);
                aim(out, c.x, c.y, c.z, lerp(look0, look, k), 0, Mth.lerp(k, 1, 0.82));
            } else {
                Vec3 c = pushed(t);
                aim(out, c.x, c.y, c.z, look, 0, 0.82);
            }
        }

        /** The frame, pushed slowly in toward her while she talks (half a block over twenty seconds). */
        private Vec3 pushed(float t) {
            double k = Mth.clamp((t - IN) / 400.0, 0, 1);
            return lerp(cam, look, 0.12 * k);
        }

        @Override
        public boolean hideBody(double camDistSq) {
            return camDistSq < 1.0;
        }

        @Override
        public void finished(String reason) {
            Awaken.LOG.info("[living] Ilse's shot ended ({})", reason.toLowerCase(Locale.ROOT));
        }
    }
}
