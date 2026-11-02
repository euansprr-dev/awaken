package dev.awaken.hearth.trial;

import dev.awaken.Awaken;
import dev.awaken.core.DirectorLog;
import dev.awaken.core.api.GateHooks;
import dev.awaken.core.api.PartyHooks;
import dev.awaken.core.api.Strikes;
import dev.awaken.core.api.TrialHooks;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Provings (SPEC §46.4): a Reassessment met as one great fight with a Warden in its own hall, never announced. Built:
 * the first, the Ringer in the Drowned Belfry ({@link Belfry}) for D. You put your hand on the Reading Bell, the bell
 * doesn't stop (the Journey's Great Fault), and the world comes back knee-deep in the Belfry's water; the Ringer drops
 * into the light when you climb onto the chancel. Fall and you wake at the door to go again now, or climb the stair out
 * and come back whenever you like: no fail day. From the third fall the Signal says plainly what keeps catching you. Win
 * and the water drains, its chain lies at your feet (the Tether), the Tenor hangs free over the pit, and you're home
 * with the rank. A party near you at the level comes too, and the Ringer stands longer for each.
 */
public final class Provings {
    private Provings() {}

    public static final String RINGER = "awaken:proving_ringer";
    /** Party members this close to whoever touched the bell come along. */
    static final int PARTY_RADIUS = 24;
    /** The Ringer's health per extra member (as a gate Lord's). */
    static final double PARTY_HEALTH = 0.5;
    /** It drops anyway once you've stood this long in the hall without climbing onto the chancel. */
    static final int DROP_AFTER = 20 * 30;
    /** From the kill to home: the water running off, the chain, the Tenor ringing free. */
    static final int OUTRO = 20 * 15;
    /** The Signal names what keeps felling you from this fall on. */
    static final int HINT_FROM = 3;
    /** Rows of the hall checked a tick when it's put back (a whole one: a fifth as many). */
    static final int ROWS_A_TICK = 8;
    /** The water runs off this many rows a tick, north to south, toward the pit. */
    static final int DRAIN_ROWS = 2;
    /** The lid over the pit cracks when the water starts, then opens a ring every few ticks from the middle. */
    static final int LID_AFTER = 24, LID_RING_TICKS = 3;

    enum Stage { OPENING, WAITING, FIGHT, WON }

    /** One Proving: its hall, its people and its Warden; shared by a party's sessions. */
    static final class Run {
        final int cell;
        final BlockPos centre;
        final ServerLevel level;
        final Rank target;
        final boolean practice;
        final UUID leader;
        final Set<UUID> members = new LinkedHashSet<>();
        final Map<UUID, Integer> falls = new HashMap<>();
        /** Members who fell since the Ringer last dropped: all of them, and it starts over. */
        final Set<UUID> down = new HashSet<>();
        Stage stage = Stage.OPENING;
        long readyAt, waitingSince, wonAt, drainAt = -1, nextBell;
        int drainRow;
        Belfry.@Nullable Repair prep;
        @Nullable UUID ringer, killer;
        boolean drained;

        Run(int cell, BlockPos centre, ServerLevel level, Rank target, boolean practice, UUID leader) {
            this.cell = cell;
            this.centre = centre;
            this.level = level;
            this.target = target;
            this.practice = practice;
            this.leader = leader;
        }
    }

    private static final List<Run> RUNS = new ArrayList<>();
    /** Halls being put back after a run; each cell is freed once its hall stands as built. */
    private static final List<Belfry.Repair> RESTORING = new ArrayList<>();
    private static final Map<Belfry.Repair, Integer> RESTORING_CELL = new HashMap<>();

    static void init() {
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(EventPriority.LOW, Provings::onBossDeath);
        bus.addListener(Provings::onBreak);
        bus.addListener(Provings::onPlace);
        bus.addListener((ServerStoppedEvent e) -> {
            RUNS.clear();
            RESTORING.clear();
            RESTORING_CELL.clear();
        });
    }

    // ---- opening ----------------------------------------------------------------------------------------------------

    /** The Monolith's window for a Proving's rank: the same measuring as your first day, nothing more. */
    static SignalWindow window(ServerPlayer sp, Rank next) {
        return SignalWindow.builder(SignalWindow.Kind.REASSESSMENT, Component.literal(next.label + "-rank Reassessment"))
                .body(Component.literal("The Registry will measure you again, the same as on your first day. Put your hand on the bell when you're ready."))
                .choice(dev.awaken.hearth.Offers.offer(sp, "trial", 20 * 30, pl -> Trials.start(pl, Trials.Kind.PROVING, next, false)),
                        "Put your hand on it", "Not now")
                .priority(6).ttl(20 * 30).build();
    }

    /** The bell doesn't stop: a cell's hall is readied while it rings, then you (and your party near you) wake in it. */
    static boolean open(ServerPlayer sp, Rank target, boolean practice) {
        MinecraftServer server = sp.level().getServer();
        ServerLevel trials = server.getLevel(Trials.DIMENSION);
        if (trials == null) {
            sp.sendOverlayMessage(Component.literal("The trial grounds are missing from this world."));
            return false;
        }
        if (GateHooks.inGate(sp)) {
            sp.sendOverlayMessage(Component.literal("Finish your gate first."));
            return false;
        }
        int cell = TrialData.get(server).allocate(false);
        begin(sp, trials, cell, Belfry.centre(cell), target, practice);
        return true;
    }

    /** A Proving in a hall built at {@code centre} in {@code level} (GameTests: their server has no trials dimension). */
    static Run openAt(ServerPlayer sp, ServerLevel level, BlockPos centre, Rank target, boolean practice) {
        return begin(sp, level, -1, centre, target, practice);
    }

    private static Run begin(ServerPlayer sp, ServerLevel level, int cell, BlockPos centre, Rank target, boolean practice) {
        MinecraftServer server = sp.level().getServer();
        TrialData data = TrialData.get(server);
        Run run = new Run(cell, centre, level, target, practice, sp.getUUID());
        List<ServerPlayer> who = new ArrayList<>();
        who.add(sp);
        for (UUID u : PartyHooks.members(sp)) {
            ServerPlayer m = server.getPlayerList().getPlayer(u);
            if (m == null || m == sp || m.level() != sp.level() || m.distanceToSqr(sp) > PARTY_RADIUS * PARTY_RADIUS) continue;
            if (Trials.inTrial(m) || GateHooks.inGate(m) || Progression.profile(m).level() < target.levelRequired) continue;
            who.add(m);
        }
        boolean away = level != sp.level();
        long now = level.getGameTime();
        int ringing = 40;
        for (ServerPlayer m : who) {
            if (away) data.returns.put(m.getUUID(), new TrialData.Return(m.level().dimension().identifier().toString(), m.getX(), Trials.standingY(m), m.getZ(), m.getYRot()));
            Trials.Session s = new Trials.Session(m.getUUID(), Trials.Kind.PROVING, target, level, centre.offset(0, 0, Belfry.WAKE_Z), away, practice, cell);
            s.run = run;
            Trials.SESSIONS.put(m.getUUID(), s);
            run.members.add(m.getUUID());
            ringing = Math.max(ringing, ring(m, practice));
        }
        data.setDirty();
        run.readyAt = now + ringing;
        RUNS.add(run);
        DirectorLog.log(sp, "trial", "proving " + target + " in cell " + cell + " with " + who.size() + (practice ? " (practice)" : ""));
        return run;
    }

    /** The Reading Bell rings again, lower, and doesn't stop; for the first time since the awakening the System breaks. */
    private static int ring(ServerPlayer m, boolean practice) {
        m.level().playSound(null, m.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.6F, 0.5F);
        if (practice) return 40;
        dev.awaken.fault.Fault ringer = new dev.awaken.fault.Fault(dev.awaken.fault.Fault.Size.GREAT, "ringer",
                List.of("[ERROR] … THAT'S NOT OUR BELL", "READING · RANK ▮ · RANK ▮ · RANK ▮"), "[ERROR] it rang for you last time too",
                "That isn't the Registry's bell. Something else is ringing it.", null, null, 0, true);
        return dev.awaken.fault.Faults.play(m, ringer) == dev.awaken.fault.Fault.Size.GREAT ? ringer.length() : 60;
    }

    // ---- ticking ----------------------------------------------------------------------------------------------------

    static void tick(MinecraftServer server) {
        for (Iterator<Belfry.Repair> it = RESTORING.iterator(); it.hasNext(); ) {
            Belfry.Repair r = it.next();
            if (r.step(ROWS_A_TICK)) {
                it.remove();
                Integer cell = RESTORING_CELL.remove(r);
                if (cell != null && cell >= 0) TrialData.get(server).free(cell);
            }
        }
        if (RUNS.isEmpty()) return;
        for (Run run : List.copyOf(RUNS)) tick(server, run);
    }

    private static void tick(MinecraftServer server, Run run) {
        long now = run.level.getGameTime();
        List<ServerPlayer> here = new ArrayList<>();
        for (UUID u : run.members) {
            ServerPlayer m = server.getPlayerList().getPlayer(u);
            if (m != null) here.add(m);
        }
        switch (run.stage) {
            case OPENING -> {
                // the hall is put back while the bell rings (a whole one where its land was made before there was a hall)
                if (run.prep == null) run.prep = new Belfry.Repair(run.level, run.centre, !Belfry.standing(run.level, run.centre));
                boolean built = run.prep.step(run.prep.whole ? ROWS_A_TICK / 4 : ROWS_A_TICK * 4);
                if (built && now >= run.readyAt) {
                    clearHall(run);
                    for (ServerPlayer m : here) wake(m, run, true);
                    run.stage = Stage.WAITING;
                    run.waitingSince = now;
                    run.nextBell = now + 20;
                }
                return;
            }
            case WAITING -> {
                if (now >= run.nextBell) swingBells(run, now);
                boolean climbed = false;
                for (ServerPlayer m : here) climbed |= onChancel(run, m);
                if (climbed || now - run.waitingSince >= DROP_AFTER) drop(run, here);
            }
            case FIGHT -> {
                LivingEntity r = ringer(run);
                if (r == null) {
                    // gone without a death (a command, an unload): it drops again
                    run.stage = Stage.WAITING;
                    run.waitingSince = now;
                } else {
                    if (!run.drained && r.getHealth() <= r.getMaxHealth() * 0.5F) drain(run, now);
                    if (r instanceof Mob mob && (mob.getTarget() == null || !mob.getTarget().isAlive()) && !here.isEmpty()) {
                        ServerPlayer near = nearest(here, r);
                        if (near != null && !near.isCreative() && !near.isSpectator()) mob.setTarget(near);
                    }
                }
            }
            case WON -> {
                long t = now - run.wonAt;
                if (t == 30) gather(run, here);
                if (t == 50) chain(run, here);
                if (t == 90) run.level.playSound(null, run.centre.offset(0, Belfry.TENOR_LIP + 4, Belfry.PIT_Z), SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 4F, 0.5F);
                if (t >= OUTRO) {
                    for (ServerPlayer m : here) {
                        Trials.Session s = Trials.SESSIONS.get(m.getUUID());
                        if (s != null) Trials.end(server, s, true, "won");
                    }
                    return;
                }
            }
        }
        drainStep(run, now);
        // the stair's top: climbed out
        for (ServerPlayer m : here) {
            if (m.level() != run.level) continue;
            double lx = m.getX() - run.centre.getX() - 0.5, lz = m.getZ() - run.centre.getZ();
            if (lz >= Belfry.LANDING_Z && Math.abs(lx) <= 3 && m.getY() >= run.centre.getY() + Belfry.LANDING_DY - 0.1) {
                Trials.Session s = Trials.SESSIONS.get(m.getUUID());
                if (s != null) Trials.end(server, s, false, "climbed");
            }
        }
    }

    private static @Nullable ServerPlayer nearest(List<ServerPlayer> here, Entity to) {
        ServerPlayer best = null;
        for (ServerPlayer m : here) if (m.level() == to.level() && (best == null || m.distanceToSqr(to) < best.distanceToSqr(to))) best = m;
        return best;
    }

    private static boolean onChancel(Run run, ServerPlayer m) {
        if (m.level() != run.level) return false;
        double lx = m.getX() - run.centre.getX() - 0.5, lz = m.getZ() - run.centre.getZ() - 0.5;
        return Math.abs(lx) <= Belfry.CHANCEL_X + 0.5 && lz >= Belfry.CHANCEL_N && lz <= Belfry.CHANCEL_S + 0.5
                && m.getY() >= run.centre.getY() + 0.9 && m.getY() <= run.centre.getY() + 6;
    }

    /** Into the water inside the door, facing the chancel: the first time, after a fall, after a reset. */
    private static void wake(ServerPlayer m, Run run, boolean first) {
        Vec3 at = new Vec3(run.centre.getX() + 0.5, run.centre.getY(), run.centre.getZ() + Belfry.WAKE_Z + 0.5);
        m.teleport(new TeleportTransition(run.level, at, Vec3.ZERO, 180F, 0F, TeleportTransition.DO_NOTHING));
        m.resetFallDistance();
        run.level.playSound(null, BlockPos.containing(at), SoundEvents.PLAYER_SPLASH, SoundSource.PLAYERS, 0.8F, 0.8F);
        if (first) run.level.playSound(null, run.centre.offset(0, 20, 0), SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 3F, 0.5F);
    }

    /** Something up in the bells is swinging them: a toll from one hanging bell and then another, low and slow. */
    private static void swingBells(Run run, long now) {
        int[] b = Belfry.HUNG[(int) Math.floorMod(now * 7 + run.cell, Belfry.HUNG.length)];
        BlockPos at = run.centre.offset(b[0], b[3] + 2, b[1]);
        float pitch = 0.5F + 0.08F * (b[2] == 2 ? 3 : 0) + run.level.getRandom().nextFloat() * 0.1F;
        run.level.playSound(null, at, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 3F, pitch);
        run.nextBell = now + 45 + run.level.getRandom().nextInt(30);
    }

    /** It drops into the light at the chancel's middle, facing the door, standing longer for a party. */
    private static void drop(Run run, List<ServerPlayer> here) {
        clearRingers(run);
        BlockPos at = run.centre.offset(0, 1, Belfry.DROP_Z);
        LivingEntity b = Trials.bossOrStandIn(run.level, RINGER, at, run.target.levelRequired, "trial:" + run.leader);
        if (b == null) return;
        b.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0F, 0F);
        int party = Math.max(1, here.size());
        if (party > 1) {
            var hp = b.getAttribute(Attributes.MAX_HEALTH);
            if (hp != null) hp.addOrReplacePermanentModifier(new AttributeModifier(Awaken.id("proving_party"), PARTY_HEALTH * (party - 1),
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            b.setHealth(b.getMaxHealth());
        }
        if (b instanceof Mob mob) {
            mob.setPersistenceRequired();
            // its arena is the whole hall: the door's water is no hiding place, the stair is the way out
            mob.setHomeTo(run.centre.above(), Belfry.SOUTH);
            ServerPlayer near = nearest(here, b);
            if (near != null && !near.isCreative() && !near.isSpectator()) mob.setTarget(near);
        }
        run.ringer = b.getUUID();
        run.stage = Stage.FIGHT;
        run.down.clear();
    }

    static @Nullable LivingEntity ringer(Run run) {
        if (run.ringer == null) return null;
        return run.level.getEntity(run.ringer) instanceof LivingEntity l && l.isAlive() ? l : null;
    }

    /** Any Ringer standing in this hall (an earlier run's, a reset's) goes before another drops. */
    private static void clearRingers(Run run) {
        if (run.ringer != null && run.level.getEntity(run.ringer) instanceof Entity e) e.discard();
        run.ringer = null;
        for (LivingEntity e : run.level.getEntitiesOfClass(LivingEntity.class, hallBox(run), e -> !(e instanceof Player))) e.discard();
    }

    private static AABB hallBox(Run run) {
        BlockPos c = run.centre;
        return new AABB(c.getX() - Belfry.WALL_X, c.getY() + Belfry.LOW, c.getZ() + Belfry.NORTH,
                c.getX() + Belfry.WALL_X + 1, c.getY() + Belfry.ROOF, c.getZ() + Belfry.BACK + 1);
    }

    /** Whatever an earlier run left lying in the hall (its drops, a creature): gone before anyone wakes. */
    private static void clearHall(Run run) {
        for (Entity e : run.level.getEntities((Entity) null, hallBox(run), e -> !(e instanceof Player))) e.discard();
    }

    // ---- the water drains -------------------------------------------------------------------------------------------

    /** Its second phase (at half its health) and its death: the water runs off toward the pit, and the floor over it gives way. */
    private static void drain(Run run, long now) {
        run.drained = true;
        run.drainAt = now;
        run.drainRow = Belfry.NORTH + 1;
        BlockPos pit = run.centre.offset(0, 0, Belfry.PIT_Z);
        run.level.playSound(null, pit, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 4F, 0.5F);
        run.level.playSound(null, pit, SoundEvents.AMBIENT_UNDERWATER_EXIT, SoundSource.BLOCKS, 4F, 0.6F);
        // the lid's ring cracks first: a second's warning before it goes
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int x = -Belfry.PIT_R; x <= Belfry.PIT_R; x++) {
            for (int z = Belfry.PIT_Z - Belfry.PIT_R; z <= Belfry.PIT_Z + Belfry.PIT_R; z++) {
                if (!Belfry.lid(x, z)) continue;
                at.set(run.centre.getX() + x, run.centre.getY(), run.centre.getZ() + z);
                if (!run.level.getBlockState(at).isAir()) run.level.setBlock(at, Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        run.level.playSound(null, pit, SoundEvents.DEEPSLATE_TILES_BREAK, SoundSource.BLOCKS, 3F, 0.5F);
    }

    private static void drainStep(Run run, long now) {
        if (run.drainAt < 0) return;
        long t = now - run.drainAt;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int n = 0; n < DRAIN_ROWS && run.drainRow < Belfry.SOUTH; n++, run.drainRow++) {
            int z = run.drainRow;
            for (int x = -Belfry.WALL_X + 1; x < Belfry.WALL_X; x++) {
                BlockState built = Belfry.block(x, 0, z);
                if (!Belfry.wet(built)) continue;
                at.set(run.centre.getX() + x, run.centre.getY(), run.centre.getZ() + z);
                BlockState now0 = run.level.getBlockState(at);
                if (Belfry.wet(now0)) run.level.setBlock(at, Belfry.dry(now0), Block.UPDATE_CLIENTS);
                if ((x & 7) == 0) run.level.sendParticles(ParticleTypes.SPLASH, at.getX() + 0.5, at.getY() + 0.3, at.getZ() + 0.5, 4, 0.4, 0.1, 0.4, 0.1);
            }
        }
        long ring = (t - LID_AFTER) / LID_RING_TICKS;
        if (t >= LID_AFTER && (t - LID_AFTER) % LID_RING_TICKS == 0 && ring <= Belfry.PIT_R) {
            // the floor gives way a ring at a time from the middle out
            for (int x = -Belfry.PIT_R; x <= Belfry.PIT_R; x++) {
                for (int z = Belfry.PIT_Z - Belfry.PIT_R; z <= Belfry.PIT_Z + Belfry.PIT_R; z++) {
                    if (!Belfry.lid(x, z) || (int) Math.floor(Belfry.pitR(x, z)) != ring) continue;
                    for (int dy = 0; dy >= -1; dy--) {
                        at.set(run.centre.getX() + x, run.centre.getY() + dy, run.centre.getZ() + z);
                        BlockState was = run.level.getBlockState(at);
                        if (was.isAir()) continue;
                        if (dy == 0 && ((x + z) & 1) == 0) run.level.levelEvent(LevelEvent.PARTICLES_AND_SOUND_DESTROY_BLOCK, at, Block.getId(was));
                        run.level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
        if (run.drainRow >= Belfry.SOUTH && ring > Belfry.PIT_R) run.drainAt = -1;
    }

    // ---- falling ----------------------------------------------------------------------------------------------------

    /** A Proving never kills: you wake at the door, whole. All of a party down, and the Ringer starts over. */
    static void fell(ServerPlayer sp, Trials.Session s, DamageSource source) {
        Run run = s.run;
        sp.setHealth(sp.getMaxHealth());
        sp.clearFire();
        sp.removeAllEffects();
        sp.setAirSupply(sp.getMaxAirSupply());
        if (run == null || run.stage == Stage.OPENING) return;
        int n = run.falls.merge(sp.getUUID(), 1, Integer::sum);
        Strikes.Blow blow = source.getEntity() instanceof LivingEntity by && by.getUUID().equals(run.ringer) ? Strikes.lastBlow(by) : null;
        wake(sp, run, false);
        DirectorLog.log(sp, "trial", "proving fall " + n + (blow != null ? " to " + blow.title() + " (" + blow.kind() + ")" : ""));
        if (n == 1) Signal.toast(sp, Component.literal("You wake in the water by the door. The stair behind you climbs out."));
        if (n >= HINT_FROM && run.stage == Stage.FIGHT) Signal.send(sp, hint(blow));
        run.down.add(sp.getUUID());
        if (run.stage == Stage.FIGHT && run.down.containsAll(run.members)) reset(run);
    }

    /** Everyone fell: the Ringer goes back up into the bells, the water comes back, the floor closes over the pit. */
    private static void reset(Run run) {
        clearRingers(run);
        run.drained = false;
        run.drainAt = -1;
        Belfry.Repair back = new Belfry.Repair(run.level, run.centre, false);
        while (!back.step(64)) { /* the whole band at once: it waits for nobody */ }
        run.stage = Stage.WAITING;
        run.waitingSince = run.level.getGameTime();
        run.down.clear();
    }

    /** What keeps catching you, said plainly, by the colour of the blow (SPEC §46.4). */
    static SignalWindow hint(Strikes.@Nullable Blow blow) {
        String name = blow != null ? "Its " + blow.title() : "The Ringer";
        String body = switch (blow != null ? blow.kind() : Strikes.Kind.NONE) {
            case LIGHT -> name + " keeps catching you. The white-gold blows can be turned aside: raise your guard just as one lands"
                    + " and it bounces off. Or step back out of its reach and hit it while it recovers.";
            case HEAVY -> name + " keeps catching you. The amber blows are too heavy to block. Dodge to the side just as it comes"
                    + " down, then hit it while it's still bent over.";
            case PERILOUS -> name + " keeps catching you. Red means nothing stops it, not a block and not a parry. Dash through it"
                    + " just as it reaches you, not away from it.";
            case GRAB -> name + " keeps catching you. It's reaching to grab you. Dash away from its hands, and don't stand in"
                    + " front of it when it comes.";
            default -> name + " keeps catching you. Watch the colour of each blow: white-gold you can turn aside, amber you"
                    + " dodge, red you dash through.";
        };
        return SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("What keeps catching you"))
                .body(Component.literal(body)).priority(6).ttl(20 * 12).build();
    }

    // ---- winning ----------------------------------------------------------------------------------------------------

    private static void onBossDeath(LivingDeathEvent e) {
        if (e.isCanceled() || e.getEntity() instanceof Player || RUNS.isEmpty()) return;
        for (Run run : RUNS) {
            if (run.stage != Stage.FIGHT || !e.getEntity().getUUID().equals(run.ringer)) continue;
            run.stage = Stage.WON;
            run.wonAt = run.level.getGameTime();
            if (e.getSource().getEntity() instanceof ServerPlayer p) run.killer = p.getUUID();
            if (!run.drained) drain(run, run.wonAt);
            return;
        }
    }

    /** What it left (its trophy, what it dropped) goes into the hands of whoever beat it, before the way home. */
    private static void gather(Run run, List<ServerPlayer> here) {
        ServerPlayer to = null;
        for (ServerPlayer m : here) if (to == null || m.getUUID().equals(run.killer)) to = m;
        if (to == null || to.level() != run.level) return;
        for (ItemEntity it : run.level.getEntitiesOfClass(ItemEntity.class, hallBox(run))) {
            ItemStack st = it.getItem().copy();
            to.getInventory().add(st);
            if (st.isEmpty()) {
                it.discard();
            } else {
                it.setItem(st);
                it.teleportTo(to.getX(), to.getY(), to.getZ());
                it.setNoPickUpDelay();
            }
        }
    }

    /** Its chain is lying at your feet: the Tether, which nobody told you about. */
    private static void chain(Run run, List<ServerPlayer> here) {
        for (ServerPlayer m : here) {
            if (m.level() != run.level) continue;
            BlockPos feet = m.blockPosition();
            BlockState links = Blocks.IRON_CHAIN.defaultBlockState().setValue(BlockStateProperties.AXIS, m.getDirection().getAxis() == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
            Direction across = m.getDirection().getClockWise();
            for (int k = -1; k <= 1; k++) {
                BlockPos p = feet.relative(m.getDirection(), 1).relative(across, k);
                if (run.level.getBlockState(p).isAir() && !run.level.getBlockState(p.below()).isAir()) run.level.setBlock(p, links, Block.UPDATE_CLIENTS);
            }
            run.level.playSound(null, feet, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.2F, 0.7F);
            if (run.practice) {
                Signal.toast(m, Component.literal("The Ringer's chain is lying at your feet."));
            } else {
                Signal.toast(m, Component.literal("The Ringer's chain is lying at your feet. It's yours now."));
                TrialHooks.done(m, TrialHooks.RINGER, 0, 0);
            }
        }
    }

    // ---- ending -----------------------------------------------------------------------------------------------------

    /** A member's Proving ends (won, climbed out, left the game): the run goes on for the rest; the last one closes it. */
    static void end(MinecraftServer server, Trials.Session s, boolean passed, String why) {
        Trials.SESSIONS.remove(s.player);
        s.state = passed ? Trials.State.PASSED : Trials.State.FAILED;
        Trials.LAST.put(s.player, s.state);
        Run run = s.run;
        if (run != null && run.members.remove(s.player) && run.members.isEmpty()) close(server, run);
        ServerPlayer sp = server.getPlayerList().getPlayer(s.player);
        if (sp == null) return;
        if (!s.practice && run != null) dev.awaken.fault.Faults.mend(sp); // the bell stops: the System steadies, held or not
        outcome(sp, s, passed, why);
        if (s.teleported && sp.level().dimension() == Trials.DIMENSION) {
            Trials.sendHome(sp);
        } else {
            TrialData data = TrialData.get(server);
            if (data.returns.remove(sp.getUUID()) != null) data.setDirty();
        }
    }

    private static void outcome(ServerPlayer sp, Trials.Session s, boolean passed, String why) {
        DirectorLog.log(sp, "trial", "proving " + (passed ? "won" : "ended") + " (" + why + ")");
        if (passed) {
            boolean real = !s.practice && s.target == Progression.rank(sp).next() && Progression.profile(sp).reassessmentReady();
            if (real) {
                Progression.passReassessment(sp);
            } else {
                Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.REASSESSMENT, Component.literal("Trial passed"))
                        .body(Component.literal("A practice run. The Signal noticed anyway.")).build());
            }
            return;
        }
        // never a fail day: the way out is as good as a try
        Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("You climbed out"))
                .body(Component.literal("The Reading Bell will take you back whenever you're ready.")).priority(5).ttl(20 * 8).build());
    }

    /** The last one's gone: the Ringer and what's left go, and the hall is put back before its cell is used again. */
    private static void close(MinecraftServer server, Run run) {
        RUNS.remove(run);
        if (run.stage == Stage.OPENING) {
            if (run.cell >= 0) TrialData.get(server).free(run.cell);
            return;
        }
        clearRingers(run);
        clearHall(run);
        Belfry.Repair back = new Belfry.Repair(run.level, run.centre, false);
        RESTORING.add(back);
        RESTORING_CELL.put(back, run.cell);
    }

    // ---- the hall stands --------------------------------------------------------------------------------------------

    /** In a Proving the hall can't be broken or built on (the fight is the hall's, and the hall is put back after). */
    private static void onBreak(BreakBlockEvent e) {
        if (e.getPlayer() instanceof ServerPlayer sp && proving(sp)) e.setCanceled(true);
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp && proving(sp)) e.setCanceled(true);
    }

    private static boolean proving(ServerPlayer sp) {
        if (sp.isCreative()) return false;
        Trials.Session s = Trials.SESSIONS.get(sp.getUUID());
        return s != null && s.kind == Trials.Kind.PROVING;
    }

    // ---- for tests --------------------------------------------------------------------------------------------------

    static @Nullable Run runOf(ServerPlayer sp) {
        Trials.Session s = Trials.SESSIONS.get(sp.getUUID());
        return s != null ? s.run : null;
    }

    /** Tours: your Proving's Ringer to under half its health, so the water drains next tick. */
    public static boolean half(ServerPlayer sp) {
        Run run = runOf(sp);
        LivingEntity r = run != null ? ringer(run) : null;
        if (r == null) return false;
        r.setHealth(r.getMaxHealth() * 0.45F);
        return true;
    }

    /** Whether no hall is being put back (tests wait on it). */
    static boolean settled() {
        return RESTORING.isEmpty();
    }
}
