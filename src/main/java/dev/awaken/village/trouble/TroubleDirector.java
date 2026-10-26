package dev.awaken.village.trouble;

import dev.awaken.Awaken;
import dev.awaken.citizens.api.Settlement;
import dev.awaken.core.PlayDays;
import dev.awaken.core.api.EconomyHooks;
import dev.awaken.core.api.HearthHooks;
import dev.awaken.core.api.NowHooks;
import dev.awaken.core.api.PartyHooks;
import dev.awaken.living.api.Quests;
import dev.awaken.progression.Progression;
import dev.awaken.progression.XpBudget;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import dev.awaken.village.VillageService;
import dev.awaken.village.api.Troubles;
import dev.awaken.village.api.Villages;
import dev.awaken.village.plan.VillagePlan;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The village troubles' director (SPEC §28.7, PLAN 6.2): looks at each village that is awake for something in the
 * world that would trouble it (a beast's lair or a den near, a camp grown big enough to raid, a gate due to break, a
 * bell gone, a storm, the damage done) and starts at most one trouble a village and two near any player, never in a
 * world's first two hours of play, never with the world option {@code awaken:calm_villages} on (festivals and growth go
 * on). Runs each trouble's plot every second while its village is awake, and ends it on its own clock when nobody came.
 * The call is always in the world; the Journal holds a trouble only once a player said Help. Everything it pays goes
 * through the emerald, standing, a keepsake and the "village" XP budget.
 */
public final class TroubleDirector {
    private TroubleDirector() {}

    static final String ACTION = "village_trouble";
    /** No trouble in a world's first two hours of play (play ticks). */
    static final long FIRST_HOURS = 2L * 60 * 60 * 20;
    /** The young-world rule's length (tests move it). */
    static long firstHours = FIRST_HOURS;
    /** At most this many troubles within {@link #NEAR} of any player. */
    static final int NEAR = 1500, PER_PLAYER = 2;
    /** Two play-days' breather after a village's trouble ends before its next. */
    static final int REST_DAYS = 2;
    /** A trouble counts for a helper's party within this. */
    static final int PARTY = 48;
    /** XP a trouble pays goes through this budget (fractions of a level per ten minutes). */
    static final String BUDGET = "village";

    static final Map<TroubleKind, Plot> PLOTS = new EnumMap<>(TroubleKind.class);

    static {
        PLOTS.put(TroubleKind.TAKEN, new Taken());
        PLOTS.put(TroubleKind.BURNING, new Burning());
        PLOTS.put(TroubleKind.REBUILD, new Rebuild());
        PLOTS.put(TroubleKind.BELL, new BellTrouble());
        PLOTS.put(TroubleKind.MISSING, new Missing());
        PLOTS.put(TroubleKind.GREY_FIELDS, new GreyFields());
        PLOTS.put(TroubleKind.FESTIVAL, new Festival());
    }

    /** Tests and tours: the director's own starts held (only troubles started by hand run). */
    public static boolean quiet;
    /** The state the last tick saw (the board's notices and the festival's music read it without a server). */
    static volatile @Nullable TroubleState last;

    /** Whether a village is in a trouble now (not a festival): the board's own mending notices wait. */
    public static boolean busy(String village) {
        TroubleState st = last;
        return st != null && st.of(village) != null;
    }

    public static void init(IEventBus modBus) {
        TroubleContent.register(modBus);
        TroubleNet.init(modBus);
        XpBudget.register(BUDGET, 0.5);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            tick(e.getServer());
            Festival.music(e.getServer());
            Race.tick(e.getServer());
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent e) -> {
            last = null;
            Race.RUNS.clear();
        });
        dev.awaken.village.Requests.onFilled((p, l, id) -> {
            // a mending notice filled puts that much back; a trouble's own notices move it on in its tick
            if (id.startsWith("mend") && p.level() instanceof ServerLevel level) Rebuild.mend(level, l, 24);
        });
        // someone on your back stops the body's moves (both sides: the carry's speed modifier is synced)
        dev.awaken.core.api.MovementHooks.addBurden(Rescuee::carrying);
        NeoForge.EVENT_BUS.addListener(TroubleEvents::onLeftClick);
        NeoForge.EVENT_BUS.addListener(TroubleEvents::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(TroubleEvents::onDeath);
        PlayDays.onNewPlayDay(TroubleDirector::newDay);
        Signal.onAction(ACTION, TroubleDirector::answer);
        NowHooks.register(TroubleDirector::now);
        Quests.addJournal(TroubleDirector::journalRows);
        Troubles.install(new Access());
        TroubleTests.register();
    }

    static Plot plot(Trouble t) {
        TroubleKind k = TroubleKind.of(t.kind);
        return PLOTS.get(k == null ? TroubleKind.FESTIVAL : k);
    }

    static Plot.Ctx ctx(ServerLevel level, @Nullable String village) {
        MinecraftServer server = level.getServer();
        return new Plot.Ctx(level, TroubleState.get(server), village == null ? null : VillageService.live(village), level.getGameTime(),
                PlayDays.current(server), TroubleText.tod(level));
    }

    // ---- the tick ---------------------------------------------------------------------------------------------------

    /**
     * Whether this second is one of every {@code ticks} (at {@code phase} ticks into it). The troubles tick once a second
     * on the server's own count, which a loaded world's clock is offset from by any amount: counted in seconds of the
     * clock, so it holds whatever the offset.
     */
    static boolean every(Plot.Ctx c, int ticks, int phase) {
        long period = Math.max(1, ticks / 20);
        return Math.floorMod(c.now() / 20, period) == (phase / 20) % period;
    }

    static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 13) return;
        ServerLevel level = server.overworld();
        if (!VillageService.enabled(server)) return;
        TroubleState st = TroubleState.get(server);
        last = st;
        long day = PlayDays.current(server);
        for (Trouble t : st.troubles()) {
            Plot.Ctx c = ctx(level, t.village);
            Plot plot = plot(t);
            try {
                if (day >= t.due && !plot.holds(c, t)) {
                    plot.miss(c, t);
                    end(c, t);
                    continue;
                }
                if (c.live() != null && c.live().settlement().awake()) plot.tick(c, t);
            } catch (RuntimeException e) {
                Awaken.LOG.error("[trouble] {} failed; it ends", t, e);
                end(c, t);
            }
        }
        if (!st.clear.isEmpty() && server.getTickCount() % 100 == 13) clearLaid(level, st);
        // what troubles left: the fire's work, smoke over the ruins, the survivors by the well
        for (VillageService.Live l : VillageService.all()) {
            if (!l.settlement().awake()) continue;
            Plot.Ctx c = ctx(level, l.key());
            Lasting.tick(c, l);
            // growth: prosperity raising a house, a family moving in (it goes on with calm villages on)
            TroubleState.Memory mem = st.memory(l.key());
            dev.awaken.village.growth.Growth.tick(level, l, mem.growth, day, c.tod(), st::setDirty, st.of(l.key()) != null);
            dev.awaken.village.growth.Growth.restartIfAlone(level, l);
        }
        if (server.getTickCount() % 200 == 13 && !quiet) consider(level, st, day);
    }

    /** Each awake village: its festival's day, then (unless calm) whether something in the world troubles it now. */
    static void consider(ServerLevel level, TroubleState st, long day) {
        boolean calm = TroubleContent.calm(level);
        boolean young = PlayDays.playTicks(level.getServer()) < firstHours;
        for (VillageService.Live l : VillageService.all()) {
            if (!l.settlement().awake()) continue;
            Plot.Ctx c = ctx(level, l.key());
            Festival.consider(c, l);
            if (calm || young || st.of(l.key()) != null) continue;
            TroubleState.Memory mem = st.memory(l.key());
            if (day - mem.lastEnd < REST_DAYS) continue;
            BlockPos origin = new BlockPos(l.plan().ox, l.plan().baseY, l.plan().oz);
            if (HearthHooks.inside(level, origin) || crowded(level, st, origin)) continue;
            // what the world holds: a gate due to break near it, its bell gone, a lair or a den near, a storm, its damage
            for (TroubleKind k : new TroubleKind[]{TroubleKind.GREY_FIELDS, TroubleKind.BELL, TroubleKind.TAKEN, TroubleKind.MISSING, TroubleKind.REBUILD}) {
                String cause = cause(c, l, k);
                if (cause == null) continue;
                if (start(level, l, k, cause) != null) break;
            }
        }
    }

    /** Whether some player near this place already has two troubles near them. */
    static boolean crowded(ServerLevel level, TroubleState st, BlockPos at) {
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || horizontal(p.blockPosition(), at) > NEAR) continue;
            int n = 0;
            for (Trouble t : st.troubles) {
                if (t.kind.equals(TroubleKind.FESTIVAL.id)) continue;
                VillageService.Live l = VillageService.live(t.village);
                BlockPos o = l != null ? new BlockPos(l.plan().ox, l.plan().baseY, l.plan().oz) : BlockPos.of(t.place);
                if (horizontal(p.blockPosition(), o) <= NEAR) n++;
            }
            if (n >= PER_PLAYER) return true;
        }
        return false;
    }

    static double horizontal(BlockPos a, BlockPos b) {
        return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
    }

    /** Why this kind of trouble would come to this village now (a cause in the world), or null. */
    static @Nullable String cause(Plot.Ctx c, VillageService.Live l, TroubleKind k) {
        return switch (k) {
            case TAKEN -> Taken.cause(c, l);
            case BELL -> BellTrouble.cause(c, l);
            case MISSING -> Missing.cause(c, l);
            case GREY_FIELDS -> GreyFields.cause(c, l);
            case REBUILD -> Rebuild.cause(c, l);
            default -> null;
        };
    }

    /** Starts a trouble at a village from its cause (commands and tests force one). Null: it couldn't begin there. */
    public static @Nullable Trouble start(ServerLevel level, VillageService.Live l, TroubleKind kind, String cause) {
        TroubleState st = TroubleState.get(level.getServer());
        if (kind != TroubleKind.FESTIVAL && st.of(l.key()) != null) return null;
        if (kind == TroubleKind.FESTIVAL && st.festival(l.key()) != null) return null;
        Plot.Ctx c = ctx(level, l.key());
        Trouble t = st.add(l.key(), kind, c.day(), c.now(), c.day() + 3, cause);
        boolean ok;
        try {
            ok = plot(t).begin(c, t);
        } catch (RuntimeException e) {
            Awaken.LOG.error("[trouble] {} couldn't begin", t, e);
            ok = false;
        }
        if (!ok) {
            plot(t).cleanup(c, t);
            st.remove(t);
            return null;
        }
        st.setDirty();
        Awaken.LOG.info("[trouble] begins: {} ({})", t, l.plan().name);
        return t;
    }

    /** Over: what it laid goes (now where loaded, later where not), the village rests, helpers' Journals change. */
    static void end(Plot.Ctx c, Trouble t) {
        plot(t).cleanup(c, t);
        for (long p : t.laid) if (!unlay(c.level(), p)) c.st().clear.add(p);
        t.laid.clear();
        c.st().remove(t);
        if (!t.kind.equals(TroubleKind.FESTIVAL.id)) c.st().memory(t.village).lastEnd = c.day();
        c.st().setDirty();
        for (UUID id : t.helpers) {
            ServerPlayer p = c.level().getServer().getPlayerList().getPlayer(id);
            if (p != null) Quests.journalChanged(p);
        }
        Awaken.LOG.info("[trouble] ends: {}", t);
    }

    // ---- blocks a trouble lays ------------------------------------------------------------------------------------------

    /** Puts one of a trouble's marks in the world (only into air or replaceable plants) and remembers it. */
    static boolean lay(ServerLevel level, Trouble t, BlockPos pos, BlockState s) {
        if (!level.isLoaded(pos)) return false;
        BlockState was = level.getBlockState(pos);
        if (!was.isAir() && !was.canBeReplaced()) return false;
        level.setBlock(pos, s, Block.UPDATE_CLIENTS);
        t.laid.add(pos.asLong());
        return true;
    }

    /** Takes a mark away if it's still ours (anything a player put there since stays). */
    static boolean unlay(ServerLevel level, long p) {
        BlockPos pos = BlockPos.of(p);
        if (!level.isLoaded(pos)) return false;
        BlockState s = level.getBlockState(pos);
        if (ours(s)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        return true;
    }

    static boolean ours(BlockState s) {
        return s.is(TroubleContent.DRAG_MARKS.get()) || s.is(TroubleContent.TORN_CLOTH.get()) || s.is(Blocks.COBWEB) || s.is(Blocks.SCAFFOLDING)
                || s.getBlock() instanceof dev.awaken.living.wayfarer.Bedroll || s.is(Blocks.CAMPFIRE) || s.is(Blocks.LANTERN)
                || s.is(dev.awaken.citizens.CitizensContent.CITY_FIRE.get());
    }

    private static void clearLaid(ServerLevel level, TroubleState st) {
        st.clear.removeIf(p -> unlay(level, p));
        st.setDirty();
    }

    // ---- the village's people in a trouble ---------------------------------------------------------------------------------

    /** The well's middle, on the green (where the square gathers). */
    static Vec3 well(VillageService.Live l) {
        VillagePlan p = l.plan();
        BlockPos g = p.ground(p.well[0], p.well[1]);
        return new Vec3(g.getX() + 0.5, g.getY() + 1, g.getZ() + 0.5);
    }

    /** A place on the green at an angle (radians) and a distance from the well, standing height on its ground. */
    static Vec3 green(VillageService.Live l, double angle, double r) {
        VillagePlan p = l.plan();
        double x = p.well[0] + 0.5 + Math.cos(angle) * r, z = p.well[1] + 0.5 + Math.sin(angle) * r;
        BlockPos g = p.ground((int) Math.floor(x), (int) Math.floor(z));
        return new Vec3(p.ox + x, g.getY() + 1, p.oz + z);
    }

    /** A world position's standing height on the plan's ground (cells off the plan: the world's own top). */
    static Vec3 stand(ServerLevel level, VillageService.Live l, double wx, double wz) {
        VillagePlan p = l.plan();
        int px = (int) Math.floor(wx) - p.ox, pz = (int) Math.floor(wz) - p.oz;
        if (p.owns((int) Math.floor(wx), (int) Math.floor(wz))) return new Vec3(wx, p.ground(px, pz).getY() + 1, wz);
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(wx), (int) Math.floor(wz));
        return new Vec3(wx, y, wz);
    }

    static float yaw(Vec3 from, Vec3 to) {
        return (float) Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z));
    }

    static void stage(VillageService.Live l, int rec, Vec3 at, float yaw, String act, String prop) {
        if (rec >= 0) l.settlement().stage(rec, at, yaw, act, prop);
    }

    static void release(VillageService.Live l, int rec) {
        if (rec >= 0) l.settlement().stage(rec, null, Float.NaN, "", "");
    }

    /** Everyone of a record's household (the record first), by the plan. */
    static List<Integer> household(VillageService.Live l, int rec) {
        VillagePlan p = l.plan();
        List<Integer> out = new ArrayList<>();
        if (rec < 0 || rec >= p.people.size()) return out;
        int h = p.people.get(rec).household();
        out.add(rec);
        for (int i = 0; i < p.people.size(); i++) if (i != rec && p.people.get(i).household() == h) out.add(i);
        return out;
    }

    /** The building a record lives in (its plan index), or -1. */
    static int homeOf(VillageService.Live l, int rec) {
        VillagePlan p = l.plan();
        if (rec < 0 || rec >= p.people.size()) return -1;
        return p.households.get(p.people.get(rec).household()).building();
    }

    /** Grown-ups free to stand in a trouble (not away, not the ones given), nearest the well first, up to n. */
    static List<Integer> crowd(VillageService.Live l, int n, Set<Integer> not, String... jobsFirst) {
        Settlement s = l.settlement();
        List<Integer> out = new ArrayList<>();
        List<String> first = List.of(jobsFirst);
        for (int pass = 0; pass < 2 && out.size() < n; pass++) {
            for (int i = 0; i < s.people() && out.size() < n; i++) {
                if (not.contains(i) || out.contains(i) || s.isAway(i) || s.age(i) == 0) continue;
                boolean want = first.contains(s.job(i));
                if (pass == 0 && !want || pass == 1 && want) continue;
                out.add(i);
            }
        }
        return out;
    }

    /** A line said by a village person to everyone within 40 of them (over their head). */
    static void say(ServerLevel level, VillageService.Live l, int rec, String line, int ticks) {
        if (line.isEmpty() || rec < 0) return;
        Vec3 at = l.settlement().where(rec);
        if (at == null) at = well(l);
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(at) > 40 * 40) continue;
            l.settlement().say(p, rec, VillageService.fill(l, p, line), ticks);
        }
    }

    /** The usual names a trouble's lines fill in. */
    static String fill(Plot.Ctx c, Trouble t, String line) {
        VillageService.Live l = c.live();
        String person = l != null && t.person >= 0 ? TroubleText.given(l.settlement().personName(t.person)) : "them";
        String kin = l != null && t.kin >= 0 ? TroubleText.given(l.settlement().personName(t.kin)) : "us";
        String dir = "away";
        if (l != null && t.place != 0) {
            BlockPos at = BlockPos.of(t.place);
            dir = TroubleText.compass(at.getX() - l.plan().ox, at.getZ() - l.plan().oz);
        }
        return TroubleText.fill(line, "person", person, "kin", kin, "village", l != null ? l.plan().name : "the village", "place",
                t.placeName.isEmpty() ? "the wilds" : t.placeName, "dir", dir, "beast", t.text.isEmpty() ? "something" : t.text);
    }

    // ---- asking, answering ----------------------------------------------------------------------------------------------

    /** A village person spoken to: if their village's trouble stands them in the square, the trouble answers. */
    public static boolean talk(ServerPlayer p, Settlement s, int rec) {
        if (!(p.level() instanceof ServerLevel level)) return false;
        TroubleState st = TroubleState.get(level.getServer());
        // the Hollowed's cure, whether their trouble's over or not
        for (VillageService.Live l : VillageService.all()) {
            if (l.settlement() == s && GreyFields.cure(ctx(level, l.key()), l, p, rec)) return true;
        }
        for (Trouble t : st.troubles()) {
            VillageService.Live l = VillageService.live(t.village);
            if (l == null || l.settlement() != s) continue;
            if (plot(t).ask(ctx(level, t.village), t, p, rec)) return true;
        }
        // someone you brought home greets you by name (the first time you speak each play-day; after that, as anyone does)
        var mine = st.rescued.get(p.getUUID());
        if (mine != null) {
            for (Troubles.Rescued r : mine) {
                VillageService.Live l = VillageService.live(r.village());
                if (l == null || l.settlement() != s || r.rec() != rec) continue;
                TroubleState.Memory mem = st.memory(l.key());
                String key = "greeted_" + rec + "_" + p.getUUID().hashCode();
                long today = PlayDays.current(level.getServer());
                if (mem.get(key, -1) == today) return false;
                mem.data.put(key, today);
                st.setDirty();
                s.say(p, rec, VillageService.fill(l, p, TroubleText.line(level, "village.trouble.rescued.greet", today + rec)), 120);
                return true;
            }
        }
        return false;
    }

    /** The card a trouble's asker shows: Help / Not now. */
    static void card(ServerPlayer p, Trouble t, String title, String body) {
        if (t.helped(p.getUUID())) return;
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(title)).body(Component.literal(body))
                .choice(ACTION + ":" + t.id, "Help", "Not now").topic("village_trouble").ttl(20 * 40).build());
    }

    static void answer(ServerPlayer p, String action, boolean yes) {
        if (!yes || !(p.level() instanceof ServerLevel level)) return;
        int id;
        try {
            id = Integer.parseInt(action.substring(ACTION.length() + 1));
        } catch (RuntimeException e) {
            return;
        }
        TroubleState st = TroubleState.get(level.getServer());
        Trouble t = st.byId(id);
        if (t == null) {
            p.sendOverlayMessage(Component.literal("It's over now, one way or another."));
            return;
        }
        Plot.Ctx c = ctx(level, t.village);
        List<ServerPlayer> joined = new ArrayList<>();
        joined.add(p);
        // a trouble counts for the party: anyone of it within 48 is in too
        for (UUID m : PartyHooks.members(p)) {
            ServerPlayer o = level.getServer().getPlayerList().getPlayer(m);
            if (o != null && o != p && o.level() == level && o.distanceTo(p) <= PARTY) joined.add(o);
        }
        for (ServerPlayer o : joined) {
            if (t.helped(o.getUUID())) continue;
            t.helpers.add(o.getUUID());
            plot(t).help(c, t, o);
            Quests.journalChanged(o);
        }
        st.setDirty();
        Awaken.LOG.info("[trouble] {} said Help to {}", p.getName().getString(), t);
    }

    /** A Rescuee was used by a player. */
    static void touched(ServerLevel level, ServerPlayer p, Rescuee r) {
        TroubleState st = TroubleState.get(level.getServer());
        Trouble t = st.byId(r.trouble);
        if (t == null) {
            // their trouble's over (a stopped server, a command): they go home on their own
            r.say("I can find my own way from here. Thank you.");
            r.discard();
            return;
        }
        plot(t).touched(ctx(level, t.village), t, p, r);
    }

    /** A carved token was read. */
    static void readToken(ServerLevel level, ServerPlayer p, BlockPos pos) {
        for (var e : TroubleState.get(level.getServer()).memory.entrySet()) {
            String words = e.getValue().tokens.get(pos.asLong());
            if (words != null) {
                p.sendSystemMessage(Component.literal(words).withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
                return;
            }
        }
        p.sendOverlayMessage(Component.literal("A carved token, nailed up by the door."));
    }

    // ---- paying ---------------------------------------------------------------------------------------------------------

    /**
     * Pays a trouble's helpers within {@code within} of {@code at} (and their party there): emeralds once each, standing,
     * XP through the village budget, a keepsake if given. Returns who was paid.
     */
    static List<ServerPlayer> pay(Plot.Ctx c, Trouble t, Vec3 at, double within, int emeralds, int standing, double xp, @Nullable String why,
                                  java.util.function.@Nullable Function<ServerPlayer, net.minecraft.world.item.ItemStack> keepsake) {
        List<ServerPlayer> paid = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (ServerPlayer p : c.level().players()) {
            if (p.isSpectator() || p.distanceToSqr(at) > within * within) continue;
            boolean in = t.helped(p.getUUID());
            if (!in) for (UUID h : t.helpers) {
                ServerPlayer hp = c.level().getServer().getPlayerList().getPlayer(h);
                if (hp != null && PartyHooks.sameParty(hp, p)) in = true;
            }
            if (!in || !seen.add(p.getUUID())) continue;
            paid.add(p);
            String claim = ("village/trouble/" + t.id + "/" + p.getUUID()).toLowerCase(Locale.ROOT);
            boolean got = emeralds > 0 && EconomyHooks.pay(p, emeralds, Awaken.id(claim));
            if (c.live() != null && standing != 0) VillageService.standing(p, c.live(), standing, "trouble " + t.kind);
            if (xp > 0) Progression.worldXp(p, BUDGET, xp);
            Progression.count(p, "awaken:village_troubles_helped");
            if (keepsake != null) {
                var stack = keepsake.apply(p);
                if (!stack.isEmpty() && !p.getInventory().add(stack)) p.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
            }
            if (got) p.sendOverlayMessage(Component.translatable("awaken.village.request.paid", emeralds));
            if (why != null) Signal.toast(p, Component.literal(why), dev.awaken.core.api.Notices.Tier.IMPORTANT, "village_trouble");
        }
        if (c.live() != null) {
            Villages.addProsperity(c.level().getServer(), t.village, 4);
            Villages.addMood(c.level().getServer(), t.village, 10);
        }
        return paid;
    }

    /** A helper's name for the history ("Kestrel", "Kestrel and Ash"). */
    static String names(List<ServerPlayer> ps) {
        if (ps.isEmpty()) return "a hunter";
        if (ps.size() == 1) return ps.getFirst().getName().getString();
        return ps.getFirst().getName().getString() + " and " + (ps.size() == 2 ? ps.get(1).getName().getString() : "friends");
    }

    // ---- days -----------------------------------------------------------------------------------------------------------

    static void newDay(MinecraftServer server, long day) {
        ServerLevel level = server.overworld();
        TroubleState st = TroubleState.get(server);
        // (troubles' own clocks run in the tick: their due days are play-days)
        Festival.newDay(level, st, day);
        BellTrouble.newDay(level, st, day);
    }

    // ---- the Now tracker and the Journal ----------------------------------------------------------------------------------

    static List<NowHooks.Suggestion> now(ServerPlayer p) {
        if (!(p.level() instanceof ServerLevel level) || level.dimension() != net.minecraft.world.level.Level.OVERWORLD) return List.of();
        List<NowHooks.Suggestion> out = new ArrayList<>();
        for (Trouble t : TroubleState.get(level.getServer()).troubles()) {
            if (!t.helped(p.getUUID())) continue;
            Plot.Ctx c = ctx(level, t.village);
            String line = plot(t).journal(c, t, p);
            BlockPos goal = plot(t).goal(c, t, p);
            out.add(goal == null ? NowHooks.Suggestion.of("village_trouble_" + t.id, 72, line)
                    : NowHooks.Suggestion.at("village_trouble_" + t.id, 72, line, goal));
        }
        return out;
    }

    static List<Quests.JournalRow> journalRows(ServerPlayer p) {
        if (!(p.level() instanceof ServerLevel level)) return List.of();
        List<Quests.JournalRow> out = new ArrayList<>();
        TroubleState st = TroubleState.get(level.getServer());
        Trouble newest = null;
        for (Trouble t : st.troubles()) if (t.helped(p.getUUID()) && (newest == null || t.day > newest.day)) newest = t;
        for (Trouble t : st.troubles()) {
            if (!t.helped(p.getUUID())) continue;
            Plot.Ctx c = ctx(level, t.village);
            TroubleKind k = TroubleKind.of(t.kind);
            String village = c.live() != null ? c.live().plan().name : t.village;
            out.add(new Quests.JournalRow((k == null ? t.kind : k.title) + " · " + village, plot(t).journal(c, t, p), t == newest));
        }
        // what villages remember of you: where you're known, the last thing that happened there
        int rows = 0;
        for (VillageService.Live l : VillageService.all()) {
            if (rows >= 3 || VillageService.tier(p, l) < Villages.KNOWN) continue;
            List<String> h = Villages.history(level.getServer(), l.key());
            if (h.isEmpty()) continue;
            out.add(new Quests.JournalRow(l.plan().name + " · " + VillageService.standingName(p, l), h.getLast(), false));
            rows++;
        }
        return out;
    }

    // ---- the API ------------------------------------------------------------------------------------------------------------

    static final class Access implements Troubles.Access {
        @Override
        public List<Troubles.Rescued> rescued(ServerPlayer player) {
            var l = TroubleState.get(player.level().getServer()).rescued.get(player.getUUID());
            return l == null ? List.of() : List.copyOf(l);
        }

        @Override
        public void settle(ServerPlayer player, Troubles.Rescued who, String hearthName) {
            TroubleState st = TroubleState.get(player.level().getServer());
            var l = st.rescued.get(player.getUUID());
            if (l == null) return;
            for (int i = 0; i < l.size(); i++) {
                Troubles.Rescued r = l.get(i);
                if (!r.village().equals(who.village()) || r.rec() != who.rec()) continue;
                l.set(i, new Troubles.Rescued(r.village(), r.villageName(), r.rec(), r.name(), r.trade(), r.age(), r.day(), r.trouble(), true));
                Villages.history(player.level().getServer(), r.village(),
                        TroubleText.given(r.name()) + " went to live at " + hearthName + " with " + player.getName().getString() + ".");
                st.setDirty();
            }
        }

        @Override
        public boolean raidComing(ServerLevel level, String village, int camp, String campName, int tier, long raidDay) {
            return Burning.coming(level, village, camp, campName, tier, raidDay);
        }

        @Override
        public boolean lairSealed(ServerLevel level, BlockPos den) {
            String key = "lair:" + den.asLong();
            for (TroubleState.Memory m : TroubleState.get(level.getServer()).memory.values()) if (m.sealed.contains(key)) return true;
            return false;
        }
    }

    static double dist(Vec3 a, Vec3 b) {
        return Math.sqrt(a.distanceToSqr(b));
    }

    static float wrap(float deg) {
        return Mth.wrapDegrees(deg);
    }
}
