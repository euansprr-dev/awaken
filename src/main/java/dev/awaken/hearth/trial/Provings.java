package dev.awaken.hearth.trial;

import dev.awaken.Awaken;
import dev.awaken.core.DirectorLog;
import dev.awaken.core.api.GateHooks;
import dev.awaken.core.api.PartyHooks;
import dev.awaken.core.api.Notices;
import dev.awaken.core.api.Strikes;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
 * The Provings (SPEC §47.4, §47.6): a Reassessment met as one great fight with a Warden in its own hall, never announced.
 * Each rank's Warden ({@link Warden}: the Ringer for D, the Examiner for C) says how you arrive, what its hall does as the
 * fight turns and what it leaves you; this runs the rest the same for all. You put your hand on the Reading Bell, the
 * bell doesn't stop, and the world comes back in its hall. Fall and you wake where you came in to go again now, or take
 * the way out and come back whenever you like: no fail day. From the third fall the Signal says plainly what keeps
 * catching you. Win and its gift is in your hands, and you're home with the rank. A party near you at the level comes
 * too, and the Warden stands longer for each.
 */
public final class Provings {
    private Provings() {}

    /** Party members this close to whoever touched the bell come along. */
    static final int PARTY_RADIUS = 24;
    /** A Warden's health per extra member (as a gate Lord's). */
    static final double PARTY_HEALTH = 0.5;
    /** It comes anyway once you've stood this long in its hall. */
    static final int COME_AFTER = 20 * 30;
    /** From the kill to home: what it leaves, its gift, its last sounds. */
    static final int OUTRO = 20 * 15;
    /** The Signal names what keeps felling you from this fall on. */
    static final int HINT_FROM = 3;
    /** Rows of the hall checked a tick when it's put back (a whole one: a fifth as many). */
    static final int ROWS_A_TICK = 8;

    enum Stage { OPENING, WAITING, FIGHT, WON }

    /** One Proving: its hall, its people and its Warden; shared by a party's sessions. */
    static final class Run {
        final Warden warden;
        final int cell;
        final BlockPos centre;
        final ServerLevel level;
        final Rank target;
        final boolean practice;
        final UUID leader;
        final Set<UUID> members = new LinkedHashSet<>();
        final Map<UUID, Integer> falls = new HashMap<>();
        /** Members who fell since the Warden last came: all of them, and it starts over. */
        final Set<UUID> down = new HashSet<>();
        Stage stage = Stage.OPENING;
        long readyAt, waitingSince, wonAt, nextAmbient;
        /** The hall's turns so far (the Belfry's water draining; the Reflections' mirrors waking, its walls closing in), the
         * last one's start while it still spreads (-1 once done), and how far it's got. */
        int turns, turnRow;
        long turnAt = -1;
        /** What a Warden keeps of its own for the run (the room the Examiner read). */
        @Nullable Object room;
        Hall.@Nullable Repair prep;
        @Nullable UUID boss, killer;

        Run(Warden warden, int cell, BlockPos centre, ServerLevel level, Rank target, boolean practice, UUID leader) {
            this.warden = warden;
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
    private static final List<Hall.Repair> RESTORING = new ArrayList<>();
    private static final Map<Hall.Repair, Integer> RESTORING_CELL = new HashMap<>();

    static void init() {
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(EventPriority.LOW, Provings::onBossDeath);
        bus.addListener(Provings::onBreak);
        bus.addListener(Provings::onPlace);
        Signal.onAction(COME, Provings::onCome);
        bus.addListener((ServerStoppedEvent e) -> {
            ASKING.clear();
            RUNS.clear();
            RESTORING.clear();
            RESTORING_CELL.clear();
        });
    }

    // ---- opening ----------------------------------------------------------------------------------------------------

    /** The Monolith's window for a Proving's rank: the same measuring as your first day, nothing more. */
    static SignalWindow window(ServerPlayer sp, Rank next) {
        Warden w = Warden.of(next);
        return SignalWindow.builder(SignalWindow.Kind.REASSESSMENT, Component.literal(next.label + "-rank Reassessment"))
                .body(Component.literal(w != null ? w.asked() : ""))
                .choice(dev.awaken.hearth.Offers.offer(sp, "trial", 20 * 30, pl -> Trials.start(pl, Trials.Kind.PROVING, next, false)),
                        "Put your hand on it", "Not now")
                .priority(6).ttl(20 * 30).build();
    }

    /** The bell doesn't stop: a cell's hall is readied while it rings, then you (and your party near you) wake in it. */
    static boolean open(ServerPlayer sp, Rank target, boolean practice) {
        Warden w = Warden.of(target);
        MinecraftServer server = sp.level().getServer();
        ServerLevel trials = server.getLevel(Trials.DIMENSION);
        if (w == null) return false;
        if (trials == null) {
            sp.sendOverlayMessage(Component.literal("The trial grounds are missing from this world."));
            return false;
        }
        if (GateHooks.inGate(sp)) {
            sp.sendOverlayMessage(Component.literal("Finish your gate first."));
            return false;
        }
        // the party near the bell is asked, each by a card of their own (SPEC §70.5); the bell rings when they've answered
        List<ServerPlayer> near = company(sp, null);
        if (!near.isEmpty() && dev.awaken.core.api.FightsHooks.pair()) {
            for (Asking a : ASKING) {
                if (a.starter.equals(sp.getUUID())) {
                    sp.sendOverlayMessage(Component.literal("Still waiting for your party to answer."));
                    return true;
                }
            }
            Asking a = new Asking(sp.getUUID(), target, practice, new HashSet<>(), new HashSet<>(), server.getTickCount() + ASK_TICKS);
            List<String> names = new ArrayList<>();
            for (ServerPlayer m : near) {
                boolean counts = Progression.profile(m).level() >= target.levelRequired;
                a.waiting.add(m.getUUID());
                names.add(m.getGameProfile().name());
                Signal.send(m, SignalWindow.builder(SignalWindow.Kind.REASSESSMENT, Component.literal(sp.getGameProfile().name() + " is starting the " + target.label + "-rank Reassessment"))
                        .body(Component.literal((counts ? "Come along and fight it together. You each fight on your own setting, and it counts for you too if you're ready for this rank."
                                : "You can come along and fight, but it won't count for you yet. Yours opens at level " + target.levelRequired + ".")
                                + " Nobody loses anything by falling: you wake where you came in and can go again."))
                        .choice(COME + ":" + sp.getUUID(), "Come along", "Stay here").priority(6).ttl(ASK_TICKS).build());
            }
            ASKING.add(a);
            Signal.toast(sp, Component.literal("Asked " + String.join(" and ", names) + " to come along. The bell rings as soon as they answer."));
            return true;
        }
        int cell = TrialData.get(server).allocate(false);
        begin(sp, w, trials, cell, w.hall.centre(cell), target, practice, null);
        return true;
    }

    // ---- asking the party ---------------------------------------------------------------------------------------------

    static final int ASK_TICKS = 20 * 30;
    static final String COME = "provingcome";

    /** A Reassessment waiting on the party's answers: who was asked and hasn't said, who is coming, and when it stops waiting. */
    private record Asking(UUID starter, Rank target, boolean practice, Set<UUID> waiting, Set<UUID> coming, long until) {}

    private static final List<Asking> ASKING = new ArrayList<>();

    /** Party members who could come to {@code sp}'s Reassessment now; with {@code only}, just those of them. */
    private static List<ServerPlayer> company(ServerPlayer sp, @Nullable Set<UUID> only) {
        MinecraftServer server = sp.level().getServer();
        List<ServerPlayer> out = new ArrayList<>();
        for (UUID u : PartyHooks.members(sp)) {
            ServerPlayer m = server.getPlayerList().getPlayer(u);
            if (m == null || m == sp || m.level() != sp.level() || m.distanceToSqr(sp) > PARTY_RADIUS * PARTY_RADIUS) continue;
            if (Trials.inTrial(m) || GateHooks.inGate(m) || !Progression.profile(m).awakened()) continue;
            if (only != null && !only.contains(u)) continue;
            out.add(m);
        }
        return out;
    }

    /** A party member's answer to the card at the bell. */
    private static void onCome(ServerPlayer p, String action, boolean accepted) {
        MinecraftServer server = p.level().getServer();
        for (Asking a : ASKING) {
            if (!action.equals(COME + ":" + a.starter) || !a.waiting.remove(p.getUUID())) continue;
            if (accepted) a.coming.add(p.getUUID());
            ServerPlayer starter = server.getPlayerList().getPlayer(a.starter);
            if (starter != null) Signal.toast(starter, Component.literal(p.getGameProfile().name() + (accepted ? " is coming along." : " is staying here.")));
            return;
        }
    }

    /** Once everyone has answered, or the cards have run out: the bell rings for the one who started and whoever said yes. */
    private static void askTick(MinecraftServer server) {
        if (ASKING.isEmpty()) return;
        for (Iterator<Asking> it = ASKING.iterator(); it.hasNext(); ) {
            Asking a = it.next();
            if (!a.waiting.isEmpty() && server.getTickCount() < a.until) continue;
            it.remove();
            ServerPlayer sp = server.getPlayerList().getPlayer(a.starter);
            Warden w = Warden.of(a.target);
            ServerLevel trials = server.getLevel(Trials.DIMENSION);
            if (sp == null || w == null || trials == null || !sp.isAlive() || Trials.inTrial(sp) || GateHooks.inGate(sp)) continue;
            int cell = TrialData.get(server).allocate(false);
            begin(sp, w, trials, cell, w.hall.centre(cell), a.target, a.practice, a.coming);
        }
    }

    /** A Proving in a hall built at {@code centre} in {@code level} (GameTests: their server has no trials dimension). */
    static Run openAt(ServerPlayer sp, ServerLevel level, BlockPos centre, Rank target, boolean practice) {
        return begin(sp, java.util.Objects.requireNonNull(Warden.of(target)), level, -1, centre, target, practice, null);
    }

    /** {@code only}: the party members who said yes at the bell; null takes everyone near (a hall opened without the cards). */
    private static Run begin(ServerPlayer sp, Warden w, ServerLevel level, int cell, BlockPos centre, Rank target, boolean practice, @Nullable Set<UUID> only) {
        MinecraftServer server = sp.level().getServer();
        TrialData data = TrialData.get(server);
        Run run = new Run(w, cell, centre, level, target, practice, sp.getUUID());
        List<ServerPlayer> who = new ArrayList<>();
        who.add(sp);
        for (ServerPlayer m : company(sp, only)) {
            // nobody is skipped in silence (SPEC §70.5): short of the level they come, and are told it doesn't count yet
            // (the card at the bell has already said so to those who were asked)
            if (only == null && Progression.profile(m).level() < target.levelRequired) {
                Signal.toast(m, Component.literal("You're coming along, but this Reassessment doesn't count for you yet. Yours opens at level " + target.levelRequired + "."));
            }
            who.add(m);
        }
        w.before(run, sp);
        boolean away = level != sp.level();
        long now = level.getGameTime();
        int ringing = 40;
        for (ServerPlayer m : who) {
            if (away) data.returns.put(m.getUUID(), new TrialData.Return(m.level().dimension().identifier().toString(), m.getX(), Trials.standingY(m), m.getZ(), m.getYRot()));
            Trials.Session s = new Trials.Session(m.getUUID(), Trials.Kind.PROVING, target, level, w.wakeAt(centre), away, practice, cell);
            s.run = run;
            Trials.SESSIONS.put(m.getUUID(), s);
            run.members.add(m.getUUID());
            ringing = Math.max(ringing, w.ring(m, practice));
        }
        data.setDirty();
        run.readyAt = now + ringing;
        RUNS.add(run);
        DirectorLog.log(sp, "trial", "proving " + target + " in cell " + cell + " with " + who.size() + (practice ? " (practice)" : ""));
        return run;
    }

    // ---- ticking ----------------------------------------------------------------------------------------------------

    static void tick(MinecraftServer server) {
        for (Iterator<Hall.Repair> it = RESTORING.iterator(); it.hasNext(); ) {
            Hall.Repair r = it.next();
            if (r.step(ROWS_A_TICK)) {
                it.remove();
                Integer cell = RESTORING_CELL.remove(r);
                if (cell != null && cell >= 0) TrialData.get(server).free(cell);
            }
        }
        askTick(server);
        if (RUNS.isEmpty()) return;
        for (Run run : List.copyOf(RUNS)) tick(server, run);
    }

    private static void tick(MinecraftServer server, Run run) {
        long now = run.level.getGameTime();
        Warden w = run.warden;
        List<ServerPlayer> here = new ArrayList<>();
        for (UUID u : run.members) {
            ServerPlayer m = server.getPlayerList().getPlayer(u);
            if (m != null) here.add(m);
        }
        switch (run.stage) {
            case OPENING -> {
                // the hall is put back while the bell rings (a whole one where its land was made before there was a hall)
                if (run.prep == null) run.prep = new Hall.Repair(w.hall, run.level, run.centre, !w.hall.standing(run.level, run.centre));
                boolean built = run.prep.step(run.prep.whole ? ROWS_A_TICK / 4 : ROWS_A_TICK * 4);
                if (built && now >= run.readyAt) {
                    clearHall(run);
                    for (ServerPlayer m : here) w.wake(m, run, true);
                    run.stage = Stage.WAITING;
                    run.waitingSince = now;
                    run.nextAmbient = now + 20;
                }
                return;
            }
            case WAITING -> {
                w.waiting(run, here, now);
                if (w.comes(run, here, now)) arrive(run, here);
            }
            case FIGHT -> {
                LivingEntity b = warden(run);
                if (b == null) {
                    // gone without a death (a command, an unload): it comes again
                    run.stage = Stage.WAITING;
                    run.waitingSince = now;
                } else {
                    w.fight(run, b, now);
                    if (b instanceof Mob mob && (mob.getTarget() == null || !mob.getTarget().isAlive()) && !here.isEmpty()) {
                        ServerPlayer near = nearest(here, b);
                        if (near != null && !near.isCreative() && !near.isSpectator()) mob.setTarget(near);
                    }
                }
            }
            case WON -> {
                long t = now - run.wonAt;
                if (t == 30) gather(run, here);
                w.outro(run, here, t);
                if (t >= OUTRO) {
                    for (ServerPlayer m : here) {
                        Trials.Session s = Trials.SESSIONS.get(m.getUUID());
                        if (s != null) Trials.end(server, s, true, "won");
                    }
                    return;
                }
            }
        }
        w.step(run, now);
        // the way out (once it's won, leaving takes the win home with you)
        boolean won = run.stage == Stage.WON;
        for (ServerPlayer m : here) {
            if (m.level() != run.level || !w.out(run, m)) continue;
            Trials.Session s = Trials.SESSIONS.get(m.getUUID());
            if (s != null) Trials.end(server, s, won, won ? "won, left early" : "climbed");
        }
    }

    private static @Nullable ServerPlayer nearest(List<ServerPlayer> here, Entity to) {
        ServerPlayer best = null;
        for (ServerPlayer m : here) if (m.level() == to.level() && (best == null || m.distanceToSqr(to) < best.distanceToSqr(to))) best = m;
        return best;
    }

    /** It comes, standing longer for a party. */
    private static void arrive(Run run, List<ServerPlayer> here) {
        clearWardens(run);
        Warden w = run.warden;
        Vec3 at = w.arrive(run);
        LivingEntity b = Trials.bossOrStandIn(run.level, w.mob, BlockPos.containing(at), run.target.levelRequired, "trial:" + run.leader);
        if (b == null) return;
        b.snapTo(at.x, at.y, at.z, w.arriveYaw(run), 0F);
        // a player on Gentle is half a fighter (SPEC §70.4); stance grows as health does (§70.3)
        double party = dev.awaken.core.api.FightsHooks.fighters(here);
        if (party > 1) {
            var hp = b.getAttribute(Attributes.MAX_HEALTH);
            if (hp != null) hp.addOrReplacePermanentModifier(new AttributeModifier(Awaken.id("proving_party"), PARTY_HEALTH * (party - 1),
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            b.setHealth(b.getMaxHealth());
            dev.awaken.core.api.FightsHooks.sizeStance(b, party);
        }
        if (b instanceof Mob mob) {
            mob.setPersistenceRequired();
            mob.setHomeTo(run.centre.above(), w.home());
            ServerPlayer near = nearest(here, b);
            if (near != null && !near.isCreative() && !near.isSpectator()) mob.setTarget(near);
        }
        run.boss = b.getUUID();
        run.stage = Stage.FIGHT;
        run.down.clear();
        w.came(run, b, here);
    }

    static @Nullable LivingEntity warden(Run run) {
        if (run.boss == null) return null;
        return run.level.getEntity(run.boss) instanceof LivingEntity l && l.isAlive() ? l : null;
    }

    /** Any Warden standing in this hall (an earlier run's, a reset's), and what it brought, goes before it comes again. */
    private static void clearWardens(Run run) {
        if (run.boss != null && run.level.getEntity(run.boss) instanceof Entity e) e.discard();
        run.boss = null;
        for (LivingEntity e : run.level.getEntitiesOfClass(LivingEntity.class, run.warden.hall.box(run.centre), e -> !(e instanceof Player))) e.discard();
    }

    /** Whatever an earlier run left lying in the hall (its drops, a creature): gone before anyone wakes. */
    private static void clearHall(Run run) {
        for (Entity e : run.level.getEntities((Entity) null, run.warden.hall.box(run.centre), e -> !(e instanceof Player))) e.discard();
    }

    // ---- falling ----------------------------------------------------------------------------------------------------

    /** A Proving never kills: you wake where you came in, whole. All of a party down, and the Warden starts over. */
    static void fell(ServerPlayer sp, Trials.Session s, DamageSource source) {
        Run run = s.run;
        sp.setHealth(sp.getMaxHealth());
        sp.clearFire();
        sp.removeAllEffects();
        sp.setAirSupply(sp.getMaxAirSupply());
        if (run == null || run.stage == Stage.OPENING) return;
        int n = run.falls.merge(sp.getUUID(), 1, Integer::sum);
        Strikes.Blow blow = source.getEntity() instanceof LivingEntity by && by.getUUID().equals(run.boss) ? Strikes.lastBlow(by) : null;
        run.warden.wake(sp, run, false);
        DirectorLog.log(sp, "trial", "proving fall " + n + (blow != null ? " to " + blow.title() + " (" + blow.kind() + ")" : ""));
        // CRITICAL, as the hint: the director holds paced lines through a fight, and these are for the fight
        if (n == 1) Signal.toast(sp, Component.literal(run.warden.firstFall()), Notices.Tier.CRITICAL, "proving_fall");
        if (n >= HINT_FROM && run.stage == Stage.FIGHT) {
            String own = run.warden.caught(run, sp);
            Signal.send(sp, own != null ? hint(own) : hint(run.warden.name(), blow));
        }
        run.down.add(sp.getUUID());
        if (run.stage == Stage.FIGHT && run.down.containsAll(run.members)) reset(run);
    }

    /** Everyone fell: the Warden goes, the hall goes back to how it was before it came. */
    private static void reset(Run run) {
        clearWardens(run);
        run.warden.reset(run);
        run.stage = Stage.WAITING;
        run.waitingSince = run.level.getGameTime();
        run.down.clear();
    }

    /** What keeps catching you, said plainly, by the colour of the blow (SPEC §47.4). */
    static SignalWindow hint(String warden, Strikes.@Nullable Blow blow) {
        String name = blow != null ? "Its " + blow.title() : warden;
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
        return hint(body);
    }

    static SignalWindow hint(String body) {
        return SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("What keeps catching you"))
                .body(Component.literal(body)).priority(6).ttl(20 * 12).tier(Notices.Tier.CRITICAL).build();
    }

    // ---- winning ----------------------------------------------------------------------------------------------------

    private static void onBossDeath(LivingDeathEvent e) {
        if (e.isCanceled() || e.getEntity() instanceof Player || RUNS.isEmpty()) return;
        for (Run run : RUNS) {
            if (run.stage != Stage.FIGHT || !e.getEntity().getUUID().equals(run.boss)) continue;
            run.stage = Stage.WON;
            run.wonAt = run.level.getGameTime();
            if (e.getSource().getEntity() instanceof ServerPlayer p) run.killer = p.getUUID();
            run.warden.died(run, run.wonAt);
            return;
        }
    }

    /** What it left (its trophy, what it dropped) goes into the hands of whoever beat it, before the way home. */
    private static void gather(Run run, List<ServerPlayer> here) {
        ServerPlayer to = null;
        for (ServerPlayer m : here) if (to == null || m.getUUID().equals(run.killer)) to = m;
        if (to == null || to.level() != run.level) return;
        for (ItemEntity it : run.level.getEntitiesOfClass(ItemEntity.class, run.warden.hall.box(run.centre))) {
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

    // ---- ending -----------------------------------------------------------------------------------------------------

    /** A member's Proving ends (won, left by the way out, left the game): the run goes on for the rest; the last one closes it. */
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
        Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("You left"))
                .body(Component.literal("The Reading Bell will take you back whenever you're ready.")).priority(5).ttl(20 * 8).build());
    }

    /** The last one's gone: the Warden and what's left go, and the hall is put back before its cell is used again. */
    private static void close(MinecraftServer server, Run run) {
        RUNS.remove(run);
        if (run.stage == Stage.OPENING) {
            if (run.cell >= 0) TrialData.get(server).free(run.cell);
            return;
        }
        clearWardens(run);
        clearHall(run);
        Hall.Repair back = new Hall.Repair(run.warden.hall, run.level, run.centre, false);
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

    /** Tours: your Proving's Warden to {@code fraction} of its health, so its hall turns next tick (half: the Belfry
     * drains, the mirrors wake; a third: the walls close in). */
    public static boolean hurtTo(ServerPlayer sp, float fraction) {
        Run run = runOf(sp);
        LivingEntity r = run != null ? warden(run) : null;
        if (r == null) return false;
        r.setHealth(r.getMaxHealth() * fraction);
        return true;
    }

    /** Tours: your Proving's Warden's stance broken through Arms, as your own blow would break it (SPEC §47.3): it goes
     * down on a knee and a heavy on it now is an Execution. False when no Warden is down. */
    public static boolean breakStance(ServerPlayer sp) {
        Run run = runOf(sp);
        LivingEntity r = run != null ? warden(run) : null;
        if (r == null) return false;
        dev.awaken.core.api.Strikes.posture(r, 10_000, sp); // more than any stance
        return true;
    }

    /** Whether no hall is being put back (tests wait on it). */
    static boolean settled() {
        return RESTORING.isEmpty();
    }
}
