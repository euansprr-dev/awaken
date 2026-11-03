package dev.awaken.bells;

import dev.awaken.core.api.MobHooks;
import dev.awaken.core.api.MobRole;
import dev.awaken.lucidium.Lucidium;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Lucidium's six bells (SPEC §43.13, §43.16 stage 5): one a dream, the next you don't hold, each behind its dream's
 * own small puzzle. The Dreaming Bell simply waits close by, so the first one teaches. The Daydream Bell is somewhere else
 * each time you look away from it; the Lullaby Bell lies by something asleep that mustn't hear you coming; the Falling
 * Bell is at the foot of a fall that only ends when you stop looking down; the thing that chases you carries the
 * Nightmare's Bell; the Lucid Bell is only there once you've noticed what's wrong with the dream. Strike it lucid enough
 * for its rank (C 40, B 60, A 80; the Dreaming Bell asks nothing) and you hold it; wake and it's yours. Wake without it
 * and the Book keeps a Dreamt lead. Not saved: a dream isn't.
 */
final class DreamBells {
    private DreamBells() {}

    /** The dream bells in the order dreams offer them. */
    static final List<String> ORDER = List.of("dreaming", "daydream", "lullaby", "falling", "nightmare", "lucid");
    static final int NEAR = 24, FAR = 40, NOTE_EVERY = 100;
    /** How near the Dreaming Bell waits. */
    static final int WAITS_NEAR = 8, WAITS_FAR = 14;
    /** The Lullaby's sleeper wakes at anyone this near who isn't creeping, or sprinting this near. */
    static final int HUSH = 12, HUSH_RUN = 18;
    /** A fall the dream takes over: this far fallen, looking further down than this (degrees), it lifts you back up. */
    static final double FALL_FROM = 1.5;
    static final float DOWN = 40F, UP = 15F;
    static final int LIFT = 10;

    /** Tonight's bell for one dreamer: which, where (null while its puzzle hides it), and how its puzzle stands. */
    static final class Dream {
        final String bell;
        @Nullable BlockPos at;
        boolean held;
        /** Daydream: it's been in view since it last moved. Falling: times the fall lifted you, and eyes up since. */
        boolean seen, eyesUp;
        int lifted;
        /** Lullaby's sleeper, the Nightmare's chaser. */
        @Nullable UUID beast;
        /** A trial dream's bell (SPEC §9.3): it waits at the end of the dream's road, its puzzle the dream's own. */
        boolean managed;
        @Nullable BlockPos spot;

        Dream(String bell, @Nullable BlockPos at) {
            this.bell = bell;
            this.at = at;
        }
    }

    private static final Map<UUID, Dream> DREAMS = new HashMap<>();

    static int need(BellCatalog.Def d) {
        if (d.id().equals("dreaming")) return 0;
        Rank r = d.rank();
        return r.ordinal() <= Rank.C.ordinal() ? 40 : r == Rank.B ? 60 : 80;
    }

    static @Nullable Dream of(ServerPlayer p) {
        return DREAMS.get(p.getUUID());
    }

    static void tick(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Dream d = DREAMS.get(p.getUUID());
            // the fall needs every tick; the rest once a second
            if (d != null && !d.held && d.bell.equals("falling") && Lucidium.isDreaming(p)) fall(p, d);
            if ((p.tickCount + p.getId()) % 20 != 7) continue;
            if (!Lucidium.isDreaming(p)) {
                if (d != null) woke(p, d);
                continue;
            }
            if (d == null) {
                d = start(p);
                if (d == null) continue;
                DREAMS.put(p.getUUID(), d);
            }
            if (d.held || !(p.level() instanceof ServerLevel level)) continue;
            puzzle(p, level, d);
            BellCatalog.Def def = BellCatalog.of(d.bell);
            long now = level.getGameTime();
            // its note every five seconds, loud enough to follow from the far side of the dream (the Falling Bell's from below)
            BlockPos from = d.at != null ? d.at : d.bell.equals("falling") ? p.blockPosition().below(16) : null;
            if (from != null && def != null && (now + p.getId()) % NOTE_EVERY < 20)
                level.playSound(null, from, BellContent.note(def.no()), SoundSource.AMBIENT, d.bell.equals("lullaby") ? 2.5F : 6F, 1F);
            if (d.at != null)
                level.sendParticles(p, ParticleTypes.END_ROD, true, true, d.at.getX() + 0.5, d.at.getY() + 1.6, d.at.getZ() + 0.5, 3, 0.4, 0.5, 0.4, 0.01);
        }
    }

    /** A dream begins: the next bell not held, set in its puzzle. */
    static @Nullable Dream start(ServerPlayer p) {
        // a trial dream carries its bell at the end of its road, or none (SPEC §9.3)
        if (Lucidium.inTrial(p)) {
            Lucidium.DreamBell b = Lucidium.dreamBell(p);
            if (b == null || Bells.has(p, b.id()) || BellCatalog.of(b.id()) == null) return null;
            Dream d = new Dream(b.id(), null);
            d.managed = true;
            d.spot = b.at();
            return d;
        }
        String bell = null;
        for (String id : ORDER) if (!Bells.has(p, id)) {
            bell = id;
            break;
        }
        if (bell == null || !(p.level() instanceof ServerLevel level)) return null;
        Dream d = new Dream(bell, null);
        switch (bell) {
            case "dreaming" -> {
                if (!set(level, d, spot(p, WAITS_NEAR, WAITS_FAR))) return null;
                Bells.say(p, "[There's a bell in this dream, close by. It's just waiting.]");
            }
            case "falling" -> Bells.say(p, "[Somewhere far below you, a bell is ringing.]");
            case "lucid" -> Bells.say(p, "[Something about this dream isn't right.]");
            case "nightmare" -> {
                BlockPos at = spot(p, NEAR, FAR);
                if (at == null) return null;
                if (!chaser(p, level, d, at) && !set(level, d, at)) return null;
                Bells.say(p, "[Something in this dream is coming for you, and it's carrying a bell.]");
            }
            case "lullaby" -> {
                if (!lullaby(level, d, spot(p, NEAR, FAR))) return null;
                Bells.say(p, "[Somewhere in the dream, a bell is ringing softly, like a lullaby.]");
            }
            default -> {
                if (!set(level, d, spot(p, NEAR, FAR))) return null;
                Bells.say(p, "[Somewhere in the dream, a bell is ringing.]");
            }
        }
        return d;
    }

    /** Once a second while the bell's still to be had: what its puzzle does. */
    private static void puzzle(ServerPlayer p, ServerLevel level, Dream d) {
        if (d.managed) {
            // set down when you come within sight of the end of the road (its chunk is made by then)
            if (d.at == null && d.spot != null && p.blockPosition().closerThan(d.spot, 96) && level.isLoaded(d.spot)) set(level, d, d.spot);
            return;
        }
        switch (d.bell) {
            case "daydream" -> {
                if (d.at == null) return;
                if (looksAt(p, d.at)) d.seen = true;
                else if (d.seen && p.distanceToSqr(Vec3.atCenterOf(d.at)) > 9) {
                    d.seen = false;
                    BellBook b = Bells.book(p);
                    if (set(level, d, spot(p, 16, 28)) && b.once("daydream_moved")) {
                        Bells.save(p, b);
                        Bells.say(p, "[The bell's somewhere else now. It moved the moment you looked away from it.]");
                    }
                }
            }
            case "lullaby" -> {
                if (d.at == null) return;
                double dist = p.distanceToSqr(Vec3.atCenterOf(d.at));
                if (dist < HUSH * HUSH && !p.isShiftKeyDown() || dist < HUSH_RUN * HUSH_RUN && p.isSprinting()) {
                    if (d.beast != null && level.getEntity(d.beast) instanceof Mob m) {
                        m.setNoAi(false);
                        m.setTarget(p);
                    }
                    d.beast = null;
                    level.playSound(null, d.at, SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 1.2F, 1.4F);
                    lullaby(level, d, spot(p, NEAR, FAR));
                    Bells.say(p, "[It woke. The bell's gone somewhere else in the dream. Whatever sleeps beside it mustn't hear you coming.]");
                } else if (d.beast != null && level.getEntity(d.beast) instanceof LivingEntity sleeper)
                    level.sendParticles(ParticleTypes.CLOUD, sleeper.getX(), sleeper.getEyeY() + 0.6, sleeper.getZ(), 1, 0.15, 0.1, 0.15, 0.005);
            }
            case "nightmare" -> {
                if (d.beast == null) return;
                Entity e = level.getEntity(d.beast);
                if (e instanceof LivingEntity chaser && chaser.isAlive()) {
                    if (chaser instanceof Mob m && m.getTarget() == null) m.setTarget(p);
                    d.at = null;
                    return;
                }
                // it fell (or slipped out of the dream): the bell it carried lies where it was last
                BlockPos where = e != null ? e.blockPosition() : p.blockPosition();
                d.beast = null;
                BlockPos at = FoundBells.clear(level, where);
                if (set(level, d, at != null ? at : spot(p, 4, 8))) Bells.say(p, "[It dropped the bell it was carrying.]");
            }
            case "lucid" -> {
                if (d.at == null && Lucidium.signsNoticed(p) > 0 && set(level, d, spot(p, 6, 10)))
                    Bells.say(p, "[Now you've seen what's wrong with the dream, there's a bell where there wasn't one.]");
            }
            default -> {}
        }
    }

    /**
     * The Falling Bell, every tick: a fall you watch never ends (the dream lifts you back up the moment you look down);
     * look up while you fall and it ends, at a bell. Before the dream has lifted you once, a fall is just a fall.
     */
    private static void fall(ServerPlayer p, Dream d) {
        if (!(p.level() instanceof ServerLevel level)) return;
        if (!p.onGround() && p.fallDistance > FALL_FROM && p.getDeltaMovement().y < 0) {
            if (p.getXRot() > DOWN) {
                p.teleportTo(p.getX(), p.getY() + LIFT, p.getZ());
                p.fallDistance = 0;
                d.eyesUp = false;
                if (++d.lifted == 2) Bells.say(p, "[The fall goes on for as long as you watch it.]");
            } else if (d.lifted > 0 && p.getXRot() < UP) d.eyesUp = true;
            return;
        }
        if (p.onGround() && d.eyesUp && d.at == null) {
            d.eyesUp = false;
            BlockPos at = FoundBells.clear(level, p.blockPosition().relative(p.getDirection(), 2));
            if (set(level, d, at)) Bells.say(p, "[The fall's over. The bell's right here at the foot of it.]");
        }
    }

    /** No hurt from a fall the dream took over: you can't be hurt by a fall that was never really there. */
    static boolean softLanding(ServerPlayer p) {
        Dream d = DREAMS.get(p.getUUID());
        return d != null && d.bell.equals("falling") && d.lifted > 0 && Lucidium.isDreaming(p);
    }

    /** The bell moves to {@code at} (its old place cleared). False if there's nowhere. */
    private static boolean set(ServerLevel level, Dream d, @Nullable BlockPos at) {
        if (at == null) return false;
        if (d.at != null && level.getBlockState(d.at).is(BellContent.SLEEPING_BELL.get())) level.setBlock(d.at, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(at, BellContent.SLEEPING_BELL.get().defaultBlockState().setValue(SleepingBellBlock.AWAKE, true), Block.UPDATE_CLIENTS);
        d.at = at;
        return true;
    }

    /** The Lullaby Bell set down at {@code at} with something asleep beside it. */
    private static boolean lullaby(ServerLevel level, Dream d, @Nullable BlockPos at) {
        if (!set(level, d, at)) return false;
        LivingEntity sleeper = MobHooks.spawnThemed(level, "awaken:nightmare/generic", MobRole.Role.NIGHTMARE, at.east(2), 1, Set.of(), "");
        if (sleeper instanceof Mob m) {
            m.setNoAi(true);
            m.setSilent(true);
            d.beast = m.getUUID();
        }
        return true;
    }

    /** The thing that chases you, carrying the Nightmare's Bell where you can see it. False if nothing came. */
    private static boolean chaser(ServerPlayer p, ServerLevel level, Dream d, BlockPos at) {
        int lv = Math.max(1, (int) (Progression.contentLevel(Progression.rank(p)) * 0.8));
        LivingEntity mob = MobHooks.spawnThemed(level, "awaken:nightmare/generic", MobRole.Role.NIGHTMARE, at, lv, Set.of(), "");
        if (!(mob instanceof Mob m)) return false;
        m.setItemSlot(EquipmentSlot.MAINHAND, BellItem.of(BellCatalog.of("nightmare").no()));
        m.setDropChance(EquipmentSlot.MAINHAND, 0F);
        m.setTarget(p);
        d.beast = m.getUUID();
        return true;
    }

    /** Whether the bell's in front of this dreamer's eyes (within 60 degrees, 64 blocks). */
    static boolean looksAt(ServerPlayer p, BlockPos at) {
        Vec3 to = Vec3.atCenterOf(at).subtract(p.getEyePosition());
        double dist = to.length();
        return dist < 1 || dist <= 64 && p.getLookAngle().dot(to.scale(1 / dist)) >= Math.cos(Math.toRadians(60));
    }

    /** Somewhere on the dream's ground {@code near} to {@code far} blocks off, or null. */
    static @Nullable BlockPos spot(ServerPlayer p, int near, int far) {
        if (!(p.level() instanceof ServerLevel level)) return null;
        var r = level.getRandom();
        for (int i = 0; i < 10; i++) {
            double a = r.nextDouble() * Math.PI * 2, dist = near + r.nextInt(far - near + 1);
            int x = (int) Math.floor(p.getX() + Math.cos(a) * dist), z = (int) Math.floor(p.getZ() + Math.sin(a) * dist);
            if (!level.isLoaded(new BlockPos(x, 0, z))) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (y <= level.getMinY() + 1) continue;
            return new BlockPos(x, y, z);
        }
        return null;
    }

    /** A strike in a dream: true if it was this dreamer's bell (held if they're lucid enough). */
    static boolean strike(ServerPlayer p, BlockPos pos) {
        Dream d = DREAMS.get(p.getUUID());
        if (d == null || d.held || !pos.equals(d.at)) return false;
        BellCatalog.Def def = BellCatalog.of(d.bell);
        if (def == null) return true;
        if (!d.managed && Lucidium.lucidity(p) < need(def)) {
            Bells.say(p, "[Your hands go through it. You aren't lucid enough to hold it.]");
            return true;
        }
        d.held = true;
        p.level().setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        p.level().playSound(null, pos, BellContent.note(def.no()), SoundSource.BLOCKS, 3F, 1F);
        Bells.say(p, "[You're holding it. Wake, and it's yours.]");
        if (d.managed) Lucidium.bellTaken(p);
        return true;
    }

    /** Waking: a bell held comes out of the dream with you; one that wasn't stays a Dreamt lead. */
    private static void woke(ServerPlayer p, Dream d) {
        DREAMS.remove(p.getUUID());
        if (d.beast != null)
            for (ServerLevel l : p.level().getServer().getAllLevels()) {
                Entity e = l.getEntity(d.beast);
                if (e != null) e.discard();
            }
        BellCatalog.Def def = BellCatalog.of(d.bell);
        if (def == null) return;
        if (d.held) {
            Bells.ring(p, def, "carried out of a dream");
            return;
        }
        if (d.managed) return; // the dream still waits for you, and the Tasks page says so
        Bells.dreamt(p, def, switch (d.bell) {
            case "daydream" -> "A bell rang in a dream of yours, and every time you looked away it was somewhere else. Keep your eyes on it next time.";
            case "lullaby" -> "A bell rang softly in a dream of yours, by something asleep. Go quietly next time.";
            case "falling" -> "A bell rang far below you in a dream, at the foot of a fall that wouldn't end while you watched it.";
            case "nightmare" -> "Something in a dream of yours was carrying a bell. Stand and face it next time.";
            case "lucid" -> "A dream of yours had a bell hidden in it. Notice what's wrong with the dream and it'll be there.";
            default -> "A bell rang in a dream of yours. You weren't lucid enough to reach it and hold it. Be clearer in the next one.";
        });
    }

    /** The next dream bell this player may dream of: the first not held, its rank reached (the first asks none); "" none. */
    static String next(ServerPlayer p) {
        for (String id : ORDER) {
            if (Bells.has(p, id)) continue;
            BellCatalog.Def d = BellCatalog.of(id);
            if (d == null) return "";
            return id.equals("dreaming") || Progression.rank(p).ordinal() >= d.rank().ordinal() ? id : "";
        }
        return "";
    }

    static void forget(UUID id) {
        DREAMS.remove(id);
    }
}
