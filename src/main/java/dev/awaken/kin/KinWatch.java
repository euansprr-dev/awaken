package dev.awaken.kin;

import dev.awaken.binoculars.BinocularsRelay;
import dev.awaken.core.api.HoloBand;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * How you come to know the kin (SPEC §43.5, JOURNEY part 2 "How you find out"): reading one (looking at it a moment with
 * the band on or through the Binoculars files it on the Kin list), watching it (near, calm, weapon away, while it shows
 * its want: three lines, vague to plain), what scares it (its kind's Kinship with you), and the System's bracketed
 * translations up close, more as Kinship grows. Hurting one costs Kinship; killing one costs a lot, and its kind runs
 * from you for days.
 */
public final class KinWatch {
    private KinWatch() {}

    /** Reading: how long you must look (ticks), how far, how narrowly (cos of the cone's half angle). */
    static final int READ_TICKS = 20, READ_RANGE = 32, READ_RANGE_GLASS = 128;
    static final double READ_COS = Math.cos(Math.toRadians(9)), READ_COS_GLASS = Math.cos(Math.toRadians(3.5));
    /** Watching: within this, for this long while it shows its want; and at least this long between two lines. */
    static final int WATCH_RANGE = 16, WATCH_TICKS = 60, WATCH_GAP = 400, LESSON_WATCH_TICKS = 30, LESSON_WATCH_GAP = 80;
    /** Up close, a translation at most this often per player. */
    static final int SUBTITLE_RANGE = 8, SUBTITLE_GAP = 20 * 25;

    private record Reading(int entity, int ticks) {}

    private static final Map<UUID, Reading> READING = new HashMap<>();
    private static final Map<UUID, Long> NEXT_SUBTITLE = new HashMap<>(), NEXT_OFFER = new HashMap<>();

    static void serverStopped() {
        READING.clear();
        NEXT_SUBTITLE.clear();
        NEXT_OFFER.clear();
    }

    /** Every 5 ticks for each player: reading, watching, what it says up close, offers home to a trusted one nearby. */
    static void tick(MinecraftServer server) {
        if (server.getTickCount() % 5 != 0) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.isSpectator()) continue;
            ServerLevel level = p.level();
            boolean glass = BinocularsRelay.up(p);
            int range = glass ? READ_RANGE_GLASS : READ_RANGE;
            List<KinBeast> near = level.getEntitiesOfClass(KinBeast.class, new AABB(p.blockPosition()).inflate(range), b -> b.isAlive());
            if (near.isEmpty()) {
                READING.remove(p.getUUID());
                continue;
            }
            Kinship ks = Kinship.of(p);
            read(p, ks, near, glass);
            for (KinBeast b : near) {
                double d2 = b.distanceToSqr(p);
                if (d2 <= WATCH_RANGE * WATCH_RANGE) watch(p, ks, b);
                if (d2 <= SUBTITLE_RANGE * SUBTITLE_RANGE) subtitle(p, ks, b);
                if (d2 <= 64 && b.trusted.contains(p.getUUID()) && b.home == null && b.follow == null) {
                    long now = level.getGameTime();
                    if (NEXT_OFFER.getOrDefault(p.getUUID(), 0L) <= now) {
                        NEXT_OFFER.put(p.getUUID(), now + 20 * 60);
                        KinHome.offer(b, p);
                    }
                }
                if (b.follow != null && b.follow.equals(p.getUUID())) KinHome.arrive(b, p);
            }
        }
    }

    // ---- reading ----------------------------------------------------------------------------------------------------

    private static void read(ServerPlayer p, Kinship ks, List<KinBeast> near, boolean glass) {
        if (!glass && !HoloBand.worn(p)) {
            READING.remove(p.getUUID());
            return;
        }
        Vec3 eye = p.getEyePosition(), look = p.getViewVector(1F);
        double cos = glass ? READ_COS_GLASS : READ_COS;
        KinBeast best = null;
        double bestDot = cos;
        for (KinBeast b : near) {
            if (ks.kinds.containsKey(b.kind()) && ks.kinds.get(b.kind()).met) continue;
            Vec3 to = b.position().add(0, b.getBbHeight() * 0.5, 0).subtract(eye);
            double len = to.length();
            if (len < 1e-3 || len > (glass ? READ_RANGE_GLASS : READ_RANGE)) continue;
            double dot = to.scale(1 / len).dot(look);
            if (dot > bestDot && p.hasLineOfSight(b)) {
                best = b;
                bestDot = dot;
            }
        }
        if (best == null) {
            READING.remove(p.getUUID());
            return;
        }
        Reading r = READING.get(p.getUUID());
        int ticks = r != null && r.entity() == best.getId() ? r.ticks() + 5 : 5;
        if (ticks >= READ_TICKS) {
            READING.remove(p.getUUID());
            met(p, best);
        } else READING.put(p.getUUID(), new Reading(best.getId(), ticks));
    }

    /** Files a kind on the Kin list: its name, where and when you saw it, that it could live with you. */
    static void met(ServerPlayer p, KinBeast b) {
        Kinship ks = Kinship.of(p);
        Kinship.Kind s = ks.kind(b.kind());
        if (s.met) return;
        s.met = true;
        s.heard = true;
        s.metDay = p.level().getOverworldClockTime() / 24000L;
        s.metX = b.getBlockX();
        s.metZ = b.getBlockZ();
        Kinship.save(p, ks);
        Progression.count(p, "awaken:kin_read");
        KinKind k = b.kind();
        say(p, "[A " + k.title + ": " + k.sort + ". It could live with you. Filed on your Hearth page.]", 10);
    }

    // ---- watching ---------------------------------------------------------------------------------------------------

    /** Whether the player is calm enough to watch: crouched or trusted by its kind, nothing drawn, not running. */
    static boolean calm(ServerPlayer p, Kinship ks, KinKind k) {
        if (p.isSprinting() || armed(p)) return false;
        return p.isCrouching() || ks.step(k).ordinal() >= Kinship.Step.CURIOUS.ordinal();
    }

    static boolean armed(ServerPlayer p) {
        ItemStack s = p.getMainHandItem();
        return s.is(ItemTags.SWORDS) || s.is(ItemTags.AXES) || s.is(Items.BOW) || s.is(Items.CROSSBOW) || s.is(Items.TRIDENT) || s.is(Items.MACE)
                || s.is(ItemTags.SPEARS);
    }

    private static void watch(ServerPlayer p, Kinship ks, KinBeast b) {
        Kinship.Kind s = ks.kinds.get(b.kind());
        if (s == null || !s.met || s.watched >= 3 || b.home != null) return;
        if (b.act() != KinBeast.WANT || !calm(p, ks, b.kind())) {
            if (p.getUUID().equals(b.watcher)) b.watchTicks = 0;
            return;
        }
        if (b.watcher == null || !b.watcher.equals(p.getUUID())) {
            b.watcher = p.getUUID();
            b.watchTicks = 0;
        }
        b.watchTicks += 5;
        boolean lesson = KinHome.lesson(b);
        long now = p.level().getGameTime();
        if (b.watchTicks < (lesson ? LESSON_WATCH_TICKS : WATCH_TICKS) || now - s.lastWatch < (lesson ? LESSON_WATCH_GAP : WATCH_GAP)) return;
        b.watchTicks = 0;
        s.watched++;
        s.lastWatch = now;
        Kinship.save(p, ks);
        String line = b.kind().watch.get(s.watched - 1);
        say(p, "[" + line + "]", 9);
        if (s.watched == 3) Progression.count(p, "awaken:kin_wants_known");
    }

    // ---- what it says up close ----------------------------------------------------------------------------------------

    private static void subtitle(ServerPlayer p, Kinship ks, KinBeast b) {
        Kinship.Kind s = ks.kinds.get(b.kind());
        if (s == null || !s.met) return;
        long now = p.level().getGameTime();
        if (NEXT_SUBTITLE.getOrDefault(p.getUUID(), 0L) > now) return;
        NEXT_SUBTITLE.put(p.getUUID(), now + SUBTITLE_GAP);
        String line = translation(p, ks, b);
        if (line.isEmpty()) return;
        s.lastLine = line;
        Kinship.save(p, ks);
        p.sendSystemMessage(Component.literal(line).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
    }

    /** The System's words for its sounds, by Kinship (JOURNEY part 2, "The System is the link"). */
    static String translation(ServerPlayer p, Kinship ks, KinBeast b) {
        KinKind k = b.kind();
        if (b.act() == KinBeast.SLEEP) return "[It's asleep. Its breathing is very slow.]";
        if (ks.afraid(k, p.level().getGameTime())) return "[It remembers what you did. It's telling the others to run.]";
        if (b.trusted.contains(p.getUUID()) || p.getUUID().equals(b.keeper))
            return b.home != null ? "[It calls you " + k.callsYou + ". It's happy here.]" : "[It calls you " + k.callsYou + ". It's waiting to see where you go.]";
        return switch (ks.step(k)) {
            case WARY -> "[It doesn't trust you.]";
            case CURIOUS -> switch (k) {
                case PEBBLEPUP -> "[It's looking at the stones in your pockets.]";
                case LAMPWING -> "[It's looking at the light you carry.]";
                case BURROWBUN -> "[It's watching where you put your feet.]";
                case TOTTER -> "[It's counting the things you carry.]";
                case THATCHBACK -> "[It's looking at your axe.]";
                case EMBERKIT -> "[It's watching your torch.]";
                case PUFFWOOL -> "[It's looking at your hands.]";
                case DUMPLIN -> "[They're looking up at you. You're taller than they are.]";
                case PONDLING -> "[It's watching your bucket.]";
                case SNUFFLET -> "[It's watching your shovel.]";
                case CHIMEWREN -> "[It's trying out the sound of your footsteps.]";
                case BUMBLEPUFF -> "[It's sniffing at your hands.]";
                case LOOMWORM -> "[It's looking at the leaves on your boots.]";
                case CLOUDCALF -> "[It's circling a little nearer, watching which way you go.]";
                case BOULDERBACK -> "[It's watching your hands, not your face.]";
                default -> "[It's watching what you do.]";
            };
            case FRIENDLY -> switch (k) {
                case PEBBLEPUP -> "[It wants the shiny ones, not the dull ones.]";
                case LAMPWING -> "[It likes a warm light. The blue ones frighten it.]";
                case BURROWBUN -> "[It says the ground here is too hard.]";
                case MOSSBACK -> "[It's thinking about the sun.]";
                case TOTTER -> "[It wants the mess put back in order.]";
                case THATCHBACK -> "[It says its dam's too small, and the trees are too big for it.]";
                case EMBERKIT -> "[It's cold. It's always cold.]";
                case PUFFWOOL -> "[It says the burrs itch.]";
                case DUMPLIN -> "[They've lost their way home. They'd follow anyone tall enough to see.]";
                case PONDLING -> "[It says the water tastes wrong.]";
                case SNUFFLET -> "[It says there's something good down there.]";
                case CHIMEWREN -> "[It's tired of its own songs. It wants a new one.]";
                case BUMBLEPUFF -> "[It says there's nothing left to eat round here.]";
                case LOOMWORM -> "[It's hungry, and it's fussy.]";
                case CLOUDCALF -> "[It says it can hear them, but they're too far to see.]";
                case BOULDERBACK -> "[It says your light hurts its eyes.]";
                default -> "";
            };
            case KIN -> "[It's telling the others you're all right. It calls you " + k.callsYou + ".]";
        };
    }

    // ---- what scares it, when it sleeps, when it shows its want --------------------------------------------------------

    /** Whether this player makes it run (its kind's Kinship with them, their manner, a killing it remembers). */
    static boolean scares(KinBeast b, ServerPlayer p) {
        if (p.isCreative() || p.isSpectator()) return false;
        if (p.getUUID().equals(b.keeper) || b.trusted.contains(p.getUUID()) || p.getUUID().equals(b.leader)) return false;
        Kinship ks = Kinship.of(p);
        KinKind k = b.kind();
        if (ks.afraid(k, p.level().getGameTime())) return true;
        if (KinHome.lesson(b) && !armed(p)) return false; // the first fire's Lampwing is the gentle lesson
        return switch (ks.step(k)) {
            case WARY -> !(p.isCrouching() && !armed(p));
            case CURIOUS -> armed(p) || p.isSprinting();
            default -> false;
        };
    }

    static boolean night(ServerLevel level) {
        long t = level.getOverworldClockTime() % 24000L;
        return t >= 12500 && t <= 23400;
    }

    /** It shows its want now: Lampwings after dark, the rest by day. */
    static boolean wantsNow(KinBeast b) {
        if (!(b.level() instanceof ServerLevel sl)) return false;
        return b.kind() == KinKind.LAMPWING ? night(sl) || KinHome.lesson(b) : !night(sl);
    }

    /** It sleeps now: Lampwings by day (on the ground, wings folded), the rest at night. */
    static boolean asleepNow(KinBeast b) {
        if (!(b.level() instanceof ServerLevel sl) || b.follow != null || b.hideUntil > sl.getGameTime()) return false;
        if (KinHome.lesson(b)) return false;
        return b.kind() == KinKind.LAMPWING ? !night(sl) : night(sl);
    }

    // ---- hurt and killed ------------------------------------------------------------------------------------------------

    static void hurtBy(KinBeast b, ServerPlayer p) {
        if (b.hurtTime > 0) return;
        Kinship.add(p, b.kind(), -Kinship.HURT);
        say(p, "[It cried out. Its kind won't forget that soon.]", 6);
    }

    static void killedBy(KinBeast b, ServerPlayer p) {
        Kinship ks = Kinship.of(p);
        Kinship.Kind s = ks.kind(b.kind());
        s.points = Math.max(0, s.points - Kinship.KILL);
        s.fearUntil = p.level().getGameTime() + Kinship.FEAR_TICKS;
        Kinship.save(p, ks);
        say(p, "[The " + b.kind().title + "s saw that. They'll run from you for days.]", 8);
    }

    // ---- the System's lines --------------------------------------------------------------------------------------------

    static void say(ServerPlayer p, String line, int seconds) {
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(line)).topic("kin").ttl(20 * seconds).build());
    }

    static void chime(ServerPlayer p) {
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), KinContent.CHIME.get(), SoundSource.PLAYERS, 0.5F, 1F);
    }
}
