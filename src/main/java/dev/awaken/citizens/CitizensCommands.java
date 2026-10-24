package dev.awaken.citizens;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.awaken.Awaken;
import dev.awaken.city.CityPlan;
import dev.awaken.core.AwakenCommands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * {@code /awaken citizens ...} (game masters):
 * <pre>
 * info                  the ledger, bodies, lamps, stalls, events and the timing line
 * who                   the citizen nearest you: name, job, what they're doing
 * show &lt;what&gt;           stand where you can watch it: a job (lamplighter, smith, ...), a citizen's name, stall, event
 * event &lt;kind&gt;          start an event now (burglary, pickpocket, fire, cart, tremor)
 * renown &lt;tier&gt;         how well the city knows you (stranger, familiar, known): every citizen's memory of you
 * greet                 the nearest citizen with a body speaks to you now
 * bodies on|off, cap n  embodiment switches (measuring)
 * window start|end      a labelled timing window in the log
 * </pre>
 */
final class CitizensCommands {
    private CitizensCommands() {}

    static final List<String> SHOW = List.of("lamplighter", "bellringer", "porter", "stallkeeper", "baker", "smith", "watch",
            "crier", "boatman", "washer", "mason", "runner", "shopkeeper", "tavernkeeper", "child", "resident", "stall", "event",
            "oren", "mira", "teodor", "sun", "lamps", "laundry");

    static void register() {
        AwakenCommands.add((root, ctx) -> root.then(Commands.literal("citizens").requires(AwakenCommands.admin())
                .then(Commands.literal("info").executes(c -> info(c.getSource())))
                .then(Commands.literal("who").executes(c -> who(c.getSource())))
                .then(Commands.literal("show").then(Commands.argument("what", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(SHOW, b))
                        .executes(c -> show(c.getSource(), StringArgumentType.getString(c, "what")))))
                .then(Commands.literal("event").then(Commands.argument("kind", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(CityEvents.KINDS, b))
                        .executes(c -> event(c.getSource(), StringArgumentType.getString(c, "kind")))))
                .then(Commands.literal("renown").then(Commands.argument("tier", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("stranger", "familiar", "known"), b))
                        .executes(c -> renown(c.getSource(), StringArgumentType.getString(c, "tier")))))
                .then(Commands.literal("greet").executes(c -> greet(c.getSource())))
                .then(Commands.literal("bodies").then(Commands.argument("on", StringArgumentType.word()).executes(c -> {
                    Citizens z = need(c.getSource());
                    if (z == null) return 0;
                    z.crowd.on = StringArgumentType.getString(c, "on").equals("on");
                    if (!z.crowd.on) z.crowd.despawnAll();
                    return say(c.getSource(), "bodies " + z.crowd.on);
                })))
                .then(Commands.literal("cap").then(Commands.argument("n", IntegerArgumentType.integer(0, 200)).executes(c -> {
                    Citizens z = need(c.getSource());
                    if (z == null) return 0;
                    z.crowd.capPlayer = IntegerArgumentType.getInteger(c, "n");
                    return say(c.getSource(), "cap " + z.crowd.capPlayer);
                })))
                .then(Commands.literal("window")
                        .then(Commands.literal("start").then(Commands.argument("label", StringArgumentType.word()).executes(c -> {
                            Citizens z = need(c.getSource());
                            if (z == null) return 0;
                            z.crowd.stats.startWindow(StringArgumentType.getString(c, "label"));
                            z.ledger.resetCounters();
                            z.crowd.resetCounters();
                            return say(c.getSource(), "window " + z.crowd.stats.windowLabel);
                        })))
                        .then(Commands.literal("end").executes(c -> {
                            Citizens z = need(c.getSource());
                            if (z == null || z.crowd.stats.windowLabel == null) return 0;
                            String label = z.crowd.stats.windowLabel;
                            Awaken.LOG.info("[citizens] {} | inview={} | {}", z.crowd.stats.endWindow(), z.crowd.inView(), z.line());
                            return say(c.getSource(), label + " done");
                        })))));
    }

    private static Citizens need(CommandSourceStack src) {
        Citizens c = Citizens.active;
        if (c == null) src.sendFailure(Component.literal("Carillon has no citizens in this world"));
        return c;
    }

    private static int say(CommandSourceStack src, String s) {
        src.sendSuccess(() -> Component.literal(s), false);
        return 1;
    }

    private static int info(CommandSourceStack src) {
        Citizens c = need(src);
        if (c == null) return 0;
        CitizensData d = c.data;
        int lit = 0, broken = 0, open = 0;
        for (byte b : d.lamps) {
            if (b == CitizensData.LAMP_LIT) lit++;
            if (b == CitizensData.LAMP_BROKEN) broken++;
        }
        for (byte b : d.stallOpen) if (b != 0) open++;
        String s = String.format(Locale.ROOT, "%d citizens: %d inside, %d walking, %d at work or about | bodies %d | lamps lit %d/%d (broken %d) | "
                        + "stalls open %d/%d | events %d | repairs %d | time of day %d",
                c.roster.citizens, c.ledger.count(CitizensData.INSIDE), c.ledger.count(CitizensData.WALK), c.ledger.count(CitizensData.STAND),
                c.crowd.bodies.size(), lit, d.lamps.length, broken, open, d.stallOpen.length, d.events.size(), d.repairs.size(), c.tod());
        Awaken.LOG.info("[citizens] info: {} | {}", s, c.line());
        return say(src, s);
    }

    private static int who(CommandSourceStack src) throws CommandSyntaxException {
        Citizens c = need(src);
        if (c == null) return 0;
        ServerPlayer p = src.getPlayerOrException();
        int best = -1;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < c.ledger.n; i++) {
            double dd;
            CitizenBody b = c.ledger.body[i];
            if (b != null) dd = b.distanceToSqr(p);
            else if (c.ledger.position(i, c.ledger.tmp)) dd = sq(c.ledger.tmp[0] - p.getX()) + sq(c.ledger.tmp[2] - p.getZ());
            else continue;
            if (dd < bd) {
                bd = dd;
                best = i;
            }
        }
        if (best < 0) return 0;
        return say(src, describe(c, best) + String.format(Locale.ROOT, " (%.0f blocks)", Math.sqrt(bd)));
    }

    static String describe(Citizens c, int i) {
        Roster r = c.roster;
        CitizensData d = c.data;
        int seg = c.ledger.segIndex(i);
        String act = seg < 0 ? "-" : Roster.ACTS[r.segAct[seg]];
        int place = d.place[i];
        String pk = place >= 0 ? Roster.PLACE_KINDS[r.pKind[place]] : "-";
        return String.format(Locale.ROOT, "#%d %s, %s: %s %s at %s (state %d, role %d%s)", i, r.isCitizen(i) ? r.name(i) : "stranger",
                Roster.JOBS[r.job[i]], act, place, pk, d.state[i], d.role[i], d.hurtUntil[i] > 0 ? ", hurt" : "");
    }

    private static double sq(double v) {
        return v * v;
    }

    /** Stands the player where a scene can be watched: a few blocks off, on open ground, looking at it. */
    private static int show(CommandSourceStack src, String what) throws CommandSyntaxException {
        Citizens c = need(src);
        if (c == null) return 0;
        ServerPlayer p = src.getPlayerOrException();
        Ledger l = c.ledger;
        Roster r = c.roster;
        double[] at = new double[3];
        boolean found = false;
        String label = what;
        switch (what) {
            case "stall" -> {
                Roster.Stall st = r.stalls.get(0);
                at[0] = l.cx + (st.x0() + st.x1() + 1) / 2.0;
                at[1] = l.streetY + 2;
                at[2] = l.cz + st.z1() + 0.5;
                found = true;
            }
            case "event" -> {
                if (!c.data.events.isEmpty()) found = c.events.where(c.data.events.getLast(), at);
            }
            case "lamps" -> {
                // a lamp on the busiest stretch of avenue (the one with the most lamps within 40 blocks)
                int bestLamp = -1, bestCount = -1;
                for (int k = 0; k < r.lampX.length; k++) {
                    int n = 0;
                    for (int q = 0; q < r.lampX.length; q++) if (Math.hypot(r.lampX[q] - r.lampX[k], r.lampZ[q] - r.lampZ[k]) < 40) n++;
                    if (n > bestCount) {
                        bestCount = n;
                        bestLamp = k;
                    }
                }
                if (bestLamp >= 0) {
                    BlockPosHolder.set(at, l.cx + r.lampX[bestLamp] + 0.5, l.streetY + r.pRy[r.lampPlace[bestLamp]] + 3, l.cz + r.lampZ[bestLamp] + 0.5);
                    found = true;
                }
            }
            case "laundry" -> {
                if (!r.lines.isEmpty()) {
                    Roster.Line ln = r.lines.getFirst();
                    BlockPosHolder.set(at, l.cx + (ln.ax() + ln.bx()) / 2.0 + 0.5, l.streetY + ln.ry() + 4, l.cz + (ln.az() + ln.bz()) / 2.0 + 0.5);
                    found = true;
                }
            }
            default -> {
                int pick = pickCitizen(c, what);
                if (pick >= 0) {
                    label = describe(c, pick);
                    CitizenBody b = l.body[pick];
                    if (b != null) BlockPosHolder.set(at, b.getX(), b.getY() + 1.2, b.getZ());
                    else if (l.position(pick, at)) at[1] += 1.2;
                    else {
                        // inside: the door they're behind
                        l.placeWorld(Math.max(0, c.data.place[pick]), at);
                        at[1] += 1.2;
                    }
                    found = true;
                }
            }
        }
        if (!found) {
            src.sendFailure(Component.literal("Nothing to show for " + what));
            return 0;
        }
        double[] eye = vantage(c, at[0], at[2], 7.0);
        double ex = eye[0], ez = eye[1], ey = Math.max(at[1] - 1.2, c.ledger.streetY + liftAt(c, ex, ez) + 1.0);
        float yaw = (float) Math.toDegrees(Math.atan2(-(at[0] - ex), at[2] - ez));
        float pitch = (float) -Math.toDegrees(Math.atan2(at[1] - (ey + 1.62), Math.max(0.5, Math.hypot(at[0] - ex, at[2] - ez))));
        p.teleportTo(c.level, ex, ey, ez, Set.of(), yaw, pitch, true);
        Awaken.LOG.info("[citizens] show {}: camera at {} {} {}", what, String.format(Locale.ROOT, "%.1f", ex), String.format(Locale.ROOT, "%.1f", ey),
                String.format(Locale.ROOT, "%.1f", ez));
        Awaken.LOG.info("[citizens] show {}: {} at {} {} {}", what, label, String.format(Locale.ROOT, "%.1f", at[0]),
                String.format(Locale.ROOT, "%.1f", at[1]), String.format(Locale.ROOT, "%.1f", at[2]));
        return say(src, "Showing " + label);
    }

    private static final class BlockPosHolder {
        static void set(double[] a, double x, double y, double z) {
            a[0] = x;
            a[1] = y;
            a[2] = z;
        }
    }

    /** A citizen of that job (or that name) at work now: standing at a workplace beats walking beats inside. */
    static int pickCitizen(Citizens c, String what) {
        Roster r = c.roster;
        CitizensData d = c.data;
        int named = r.namedIndex(what);
        if (named >= 0) return named;
        int job = -1;
        for (int j = 0; j < Roster.JOBS.length; j++) if (Roster.JOBS[j].equals(what)) job = j;
        if (job < 0) return -1;
        int best = -1, bestScore = -1;
        for (int i = 0; i < r.citizens; i++) {
            if (r.job[i] != job || d.role[i] != 0) continue;
            int score = 0;
            if (d.state[i] == CitizensData.STAND) score = 3;
            else if (d.state[i] == CitizensData.WALK) score = 2;
            int place = d.place[i];
            if (score == 3 && place >= 0 && r.pKind[place] != Roster.P_DOOR && r.pKind[place] != Roster.P_TAVERN_OUT && r.pKind[place] != Roster.P_SIT)
                score = 4;
            if (d.dwellUntil[i] > 0) score = 3; // mid-stop on a round: they'll walk on soon
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    /** An open spot 4-11 blocks from a point with a clear line of open ground to it, nearest to {@code want}. */
    static double[] vantage(Citizens c, double x, double z, double want) {
        CityPlan plan = CityPlan.get();
        int cx = c.ledger.cx, cz = c.ledger.cz;
        double bestScore = Double.MAX_VALUE;
        double[] best = null;
        for (int a = 0; a < 32; a++) {
            double ang = a * Math.PI / 16;
            for (double dist = 4; dist <= 11; dist += 1) {
                double px = x + Math.cos(ang) * dist, pz = z + Math.sin(ang) * dist;
                int ix = (int) Math.floor(px - cx), iz = (int) Math.floor(pz - cz);
                if (!open(plan.kind(ix, iz))) continue;
                if (!clear(plan, x - cx, z - cz, px - cx, pz - cz)) continue;
                // room round the camera: not pressed against a wall or a drainpipe
                int room = 0;
                for (int ox = -2; ox <= 2; ox++) for (int oz = -2; oz <= 2; oz++) if (open(plan.kind(ix + ox, iz + oz))) room++;
                double score = Math.abs(dist - want) + (25 - room) * 0.6;
                if (score < bestScore) {
                    bestScore = score;
                    best = new double[]{Math.floor(px) + 0.5, Math.floor(pz) + 0.5};
                }
            }
        }
        if (best != null) return best;
        // nothing with a clear line: the nearest open ground
        double bd = Double.MAX_VALUE;
        best = new double[]{x, z};
        for (int ox = -12; ox <= 12; ox++) for (int oz = -12; oz <= 12; oz++) {
            int ix = (int) Math.floor(x - cx) + ox, iz = (int) Math.floor(z - cz) + oz;
            if (!open(plan.kind(ix, iz))) continue;
            double dd = Math.abs(Math.hypot(ox, oz) - 5);
            if (dd < bd) {
                bd = dd;
                best = new double[]{ix + cx + 0.5, iz + cz + 0.5};
            }
        }
        return best;
    }

    private static boolean open(byte k) {
        return CityPlan.walkable(k);
    }

    private static boolean clear(CityPlan plan, double ax, double az, double bx, double bz) {
        double len = Math.hypot(bx - ax, bz - az);
        int n = (int) Math.ceil(len * 2);
        for (int s = 1; s < n; s++) {
            double t = s / (double) n;
            if (t * len < 1.6) continue; // the target may stand in a doorway or on a roof edge

            byte k = plan.kind((int) Math.floor(ax + (bx - ax) * t), (int) Math.floor(az + (bz - az) * t));
            if (k == CityPlan.PLOT || k == CityPlan.LANDMARK || k == CityPlan.WALL) return false;
        }
        return true;
    }

    private static int liftAt(Citizens c, double x, double z) {
        return CityPlan.get().lift((int) Math.floor(x - c.ledger.cx), (int) Math.floor(z - c.ledger.cz));
    }

    private static int event(CommandSourceStack src, String kind) throws CommandSyntaxException {
        Citizens c = need(src);
        if (c == null) return 0;
        ServerPlayer p = src.getPlayer();
        String s = c.events.start(kind, p, c.level.getGameTime());
        if (s == null) {
            src.sendFailure(Component.literal("Couldn't start " + kind));
            return 0;
        }
        return say(src, s);
    }

    private static int renown(CommandSourceStack src, String tier) throws CommandSyntaxException {
        Citizens c = need(src);
        if (c == null) return 0;
        ServerPlayer p = src.getPlayerOrException();
        int know = switch (tier) {
            case "known" -> 70;
            case "familiar" -> 30;
            default -> 0;
        };
        c.recognition.setAll(p, know);
        return say(src, "The city now knows " + p.getName().getString() + " as " + tier);
    }

    private static int greet(CommandSourceStack src) throws CommandSyntaxException {
        Citizens c = need(src);
        if (c == null) return 0;
        ServerPlayer p = src.getPlayerOrException();
        // whoever is most squarely in view within 14 blocks (else the nearest)
        var look = p.getViewVector(1f);
        CitizenBody b = null;
        double best = -1e9;
        for (CitizenBody o : c.crowd.bodies) {
            if (o.rec < 0 || !c.roster.isCitizen(o.rec)) continue;
            double dx = o.getX() - p.getX(), dy = o.getEyeY() - p.getEyeY(), dz = o.getZ() - p.getZ(), dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist > 14) continue;
            double score = (dx * look.x + dy * look.y + dz * look.z) / Math.max(1e-3, dist) * 10 - dist * 0.2;
            if (score > best) {
                best = score;
                b = o;
            }
        }
        if (b == null) return 0;
        c.recognition.greet(b, b.rec, p, c.level.getGameTime(), true);
        return say(src, describe(c, b.rec) + " greets you");
    }
}
