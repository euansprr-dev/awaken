package dev.awaken.kin.client;

import dev.awaken.holo.client.HoloCanvas;
import dev.awaken.holo.client.HoloWorld;
import dev.awaken.kin.KinBeast;
import dev.awaken.kin.KinNet;
import dev.awaken.kin.KinPlates;
import dev.awaken.living.combat.PlateRules;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Stat;
import dev.awaken.signal.client.Glass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.HashMap;
import java.util.Map;

/**
 * The kin plate on the Holo's glass (SPEC §43.17, {@link HoloWorld}): the creature plate made warm, for the kin and for
 * what is someone's (a pet, a mount that trusts its rider). The lantern's rim; its name (the one you gave it, else its
 * kind) and how close you and its kind are as four marks; its kind and who it lives with; its health as one soft line
 * in its kind's colour (amber when low, never red, no level); how far off its trust is or what it's doing. A Gnawed
 * one's is dark with an ember rim. It shows when you look at one (the same reach and fade as every plate) and for 4 s
 * when one is hurt near you; at most two. The bond's words come from the server ({@link KinNet.Read}); health is read here.
 */
public final class KinPlate {
    private KinPlate() {}

    private static final HoloWorld.Spec SPEC = new HoloWorld.Spec();
    private static final HoloWorld.Content WRITE = KinPlate::write;
    static final int PAD = 6;
    private static final int LANTERN = Glass.LANTERN, GOLD = 0xF2C46A, EMBER = 0xFF7A52, LOW = 0xF2A541, LOW_HI = 0xFFCF7A;
    /** A bond's words are asked again this often while its plate shows (ticks). */
    static final int REASK = 40;

    private static long ticks;
    private static final Map<Integer, Long> LOOK = new HashMap<>(), HURT = new HashMap<>(), FIRST = new HashMap<>(), ASKED = new HashMap<>();
    private static final Map<Integer, KinNet.Bond> BONDS = new HashMap<>();
    // the plates picked this tick (entity ids) and their visibility
    private static final int[] PICKED = new int[KinPlates.MAX];
    private static final float[] VIS = new float[KinPlates.MAX];
    private static int picked;
    // the plate being written (the content callback reads these)
    private static LivingEntity cur;
    private static KinNet.Bond curBond;
    private static String curName = "", curWho = "";

    /** Whether kin plates draw now: plates are on and the Holo's glass loaded. */
    public static boolean on() {
        return dev.awaken.living.client.LivingClient.platesOn() && HoloWorld.on();
    }

    static void onBond(KinNet.Bond m) {
        BONDS.put(m.entity(), m);
    }

    static void reset() {
        LOOK.clear();
        HURT.clear();
        FIRST.clear();
        ASKED.clear();
        BONDS.clear();
        picked = 0;
    }

    // ---- the tick ------------------------------------------------------------------------------------------------------

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ticks++;
        Player p = mc.player;
        if (p == null || mc.level == null || !on()) {
            picked = 0;
            return;
        }
        Entity riding = p.getVehicle(); // the ride line speaks for it
        double range = PlateRules.lookRange(Progression.profile(p).stat(Stat.PER), dev.awaken.core.api.CameraHooks.zoom());
        Vec3 eye = p.getEyePosition(), to = eye.add(p.getViewVector(1).scale(range));
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(p, eye, to, new AABB(eye, to).inflate(1.0),
                e -> e != riding && e instanceof LivingEntity && e.isAlive() && KinPlates.wears(e), range * range);
        if (hit != null) {
            var block = mc.level.clip(new ClipContext(eye, hit.getLocation(), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, p));
            if (block.getType() == HitResult.Type.MISS || block.getLocation().distanceToSqr(eye) >= hit.getLocation().distanceToSqr(eye))
                LOOK.put(hit.getEntity().getId(), ticks);
        }
        // the creature you mean (Aim's mark) keeps its plate, as a creature plate does (SPEC §40.6)
        int meant = dev.awaken.core.api.Aim.target();
        if (meant >= 0 && meant != (riding == null ? -1 : riding.getId()) && mc.level.getEntity(meant) instanceof LivingEntity m
                && m.isAlive() && KinPlates.wears(m))
            LOOK.put(meant, ticks);
        // one that's hurt near you (yours, or a Gnawed one you're fighting): every other tick is enough, a flinch lasts ten
        if (ticks % 2 == 0)
            for (LivingEntity e : mc.level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(KinPlates.HURT_RANGE),
                    e -> e.hurtTime > 0 && e != riding && e.isAlive() && KinPlates.wears(e)))
                HURT.put(e.getId(), ticks);
        // the two most visible: looked at first, then the nearest hurt
        picked = 0;
        pick(mc, p, LOOK, range);
        pick(mc, p, HURT, range);
        for (int i = 0; i < picked; i++) {
            int id = PICKED[i];
            FIRST.putIfAbsent(id, ticks);
            Long asked = ASKED.get(id);
            if (asked == null || ticks - asked >= REASK) {
                ASKED.put(id, ticks);
                ClientPacketDistributor.sendToServer(new KinNet.Read(id));
            }
        }
        FIRST.keySet().removeIf(id -> !isPicked(id));
        if (ticks % 100 == 0) {
            LOOK.values().removeIf(t -> ticks - t > 200);
            HURT.values().removeIf(t -> ticks - t > 200);
            ASKED.keySet().removeIf(id -> mc.level.getEntity(id) == null);
            BONDS.keySet().removeIf(id -> mc.level.getEntity(id) == null);
        }
    }

    private static void pick(Minecraft mc, Player p, Map<Integer, Long> from, double range) {
        int best = -1;
        float bestVis = 0;
        double bestD = Double.MAX_VALUE;
        for (var en : from.entrySet()) {
            if (picked >= KinPlates.MAX) return;
            int id = en.getKey();
            if (isPicked(id) || !(mc.level.getEntity(id) instanceof LivingEntity e) || !e.isAlive()) continue;
            double d = e.distanceTo(p);
            float v = KinPlates.visibility(ticks, LOOK.getOrDefault(id, Long.MIN_VALUE), HURT.getOrDefault(id, Long.MIN_VALUE), d, range);
            if (v > 0.01F && (v > bestVis + 0.001F || Math.abs(v - bestVis) <= 0.001F && d < bestD)) {
                best = id;
                bestVis = v;
                bestD = d;
            }
        }
        if (best >= 0 && picked < KinPlates.MAX) {
            PICKED[picked] = best;
            VIS[picked++] = bestVis;
            if (from == HURT) pick(mc, p, from, range); // a second hurt one may take the other place
        }
    }

    private static boolean isPicked(int id) {
        for (int i = 0; i < picked; i++) if (PICKED[i] == id) return true;
        return false;
    }

    // ---- drawing --------------------------------------------------------------------------------------------------------

    static void extract(ExtractLevelRenderStateEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || picked == 0 || !on()) return;
        float pt = e.getRenderState().worldPartialTicks;
        Font font = mc.font;
        for (int i = 0; i < picked; i++) {
            if (!(mc.level.getEntity(PICKED[i]) instanceof LivingEntity ent)) continue;
            Long first = FIRST.get(ent.getId());
            float vis = VIS[i] * (first == null ? 0 : Math.min(1, (ticks - first + 1) / 4F)); // grows in over 4 ticks
            if (vis <= 0.02F) continue;
            KinNet.Bond bond = BONDS.get(ent.getId());
            cur = ent;
            curBond = bond;
            curName = name(ent);
            curWho = bond == null || bond.who().isEmpty() ? "" : Character.isLowerCase(bond.who().charAt(0))
                    ? ent.getType().getDescription().getString() + " · " + bond.who() : bond.who();
            String note = bond == null ? "" : bond.note();
            int marks = bond != null && bond.step() >= 0 ? 4 * 7 + 4 : 0;
            int words = Math.max(font.width(curName) + marks, Math.round(font.width(curWho) * 0.85F));
            if (!note.isEmpty()) words = Math.max(words, Math.round(font.width(note) * 0.8F));
            SPEC.w = Mth.clamp(words + PAD * 2, 90, 210);
            SPEC.h = note.isEmpty() ? 36 : 45;
            SPEC.x = Mth.lerp(pt, ent.xo, ent.getX());
            SPEC.y = Mth.lerp(pt, ent.yo, ent.getY()) + ent.getBbHeight() + 0.3;
            SPEC.z = Mth.lerp(pt, ent.zo, ent.getZ());
            SPEC.alpha = vis;
            SPEC.accent = bond != null && bond.tone() == KinPlates.GNAWED ? EMBER : LANTERN;
            SPEC.scan = -1;
            HoloWorld.plate(SPEC, WRITE);
        }
        cur = null;
    }

    /** The name you gave it, else its kind's. */
    static String name(LivingEntity e) {
        return e.hasCustomName() && e.getCustomName() != null ? e.getCustomName().getString() : e.getType().getDescription().getString();
    }

    /** Its kind's colour: a kin's own, else the lantern's gold. */
    static int colour(LivingEntity e) {
        return e instanceof KinBeast b ? b.kind().colour & 0xFFFFFF : GOLD;
    }

    private static void write(HoloCanvas cv) {
        LivingEntity e = cur;
        KinNet.Bond bond = curBond;
        Font font = Minecraft.getInstance().font;
        int w = SPEC.w, h = SPEC.h;
        boolean gnawed = bond != null && bond.tone() == KinPlates.GNAWED;
        if (gnawed) cv.fillF(0, 0, w, h, 0x8C0A0306, 0x8C120408); // the Deep's dark over the glass
        int kind = gnawed ? EMBER : colour(e);
        // the name, and how close you and its kind are: four marks, Wary to Kin
        int marks = bond != null && bond.step() >= 0 ? 4 * 7 + 4 : 0;
        String name = curName;
        int room = w - PAD * 2 - marks;
        if (font.width(name) > room) name = font.plainSubstrByWidth(name, Math.max(10, room - font.width("…"))) + "…";
        cv.textF(name, PAD, 5, 0xFF000000 | Glass.TITLE, false);
        if (marks > 0) for (int k = 0; k < 4; k++) mark(cv, w - PAD - 4 - (3 - k) * 7, 6.5F, k <= bond.step() ? 0xFF000000 | GOLD : 0x55000000 | GOLD);
        // its kind and who it lives with
        if (!curWho.isEmpty()) {
            int tone = switch (bond.tone()) {
                case KinPlates.WILD -> Glass.GOOD;
                case KinPlates.GNAWED -> EMBER;
                case KinPlates.OTHER -> Glass.LABEL;
                default -> kind == GOLD ? GOLD : lighten(kind);
            };
            cv.textF(curWho, PAD, 16, 0xFF000000 | tone, false, 0.85F);
        }
        // its health: one soft line in its kind's colour; amber when low, never red
        float frac = Mth.clamp(e.getHealth() / Math.max(1F, e.getMaxHealth()), 0, 1);
        boolean low = frac <= 1 / 3F;
        float pulse = low ? 0.8F + 0.2F * Mth.sin(ticks * 0.4F) : 1F;
        float bx0 = PAD, bx1 = w - PAD, by0 = 27, by1 = 30;
        cv.fillF(bx0, by0, bx1, by1, 0x9905080E, 0x9905080E);
        if (frac > 0) {
            int c0 = low ? LOW : kind, c1 = low ? LOW_HI : lighten(kind);
            cv.fillF(bx0, by0, bx0 + (bx1 - bx0) * frac, by1, argb(pulse, c1), argb(pulse, c0));
        }
        // how far off its trust is, or what it's doing
        if (bond != null && !bond.note().isEmpty()) {
            String note = bond.note();
            float noteRoom = (w - PAD * 2) / 0.8F;
            if (font.width(note) > noteRoom) note = font.plainSubstrByWidth(note, Math.max(10, (int) noteRoom - font.width("…"))) + "…";
            cv.textF(note, PAD, 34, 0xFF000000 | Glass.LABEL, false, 0.8F);
        }
    }

    /** A small diamond, 5 units across. */
    private static void mark(HoloCanvas cv, float cx, float cy, int argb) {
        for (int r = -2; r <= 2; r++) {
            float half = 2.5F - Math.abs(r);
            cv.fillF(cx - half, cy + r, cx + half, cy + r + 1, argb, argb);
        }
    }

    private static int lighten(int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        return (r + (255 - r) / 2) << 16 | (g + (255 - g) / 2) << 8 | (b + (255 - b) / 2);
    }

    private static int argb(float a, int rgb) {
        return ((int) (Mth.clamp(a, 0, 1) * 255) << 24) | (rgb & 0xFFFFFF);
    }
}
