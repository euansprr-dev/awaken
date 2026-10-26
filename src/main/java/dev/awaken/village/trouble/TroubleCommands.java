package dev.awaken.village.trouble;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.awaken.Awaken;
import dev.awaken.core.PlayDays;
import dev.awaken.living.camp.Camps;
import dev.awaken.village.VillageService;
import dev.awaken.village.api.Villages;
import dev.awaken.village.plan.VillagePlan;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * {@code /awaken village trouble ...} (game masters; tours and tests):
 * <pre>
 * start &lt;kind&gt;      begin one at the village you're in or near (a den, a camp, a gate put there if the world has none)
 * info              the troubles running and the village's memory
 * help              say Help to the trouble at your village (as its card would)
 * end | miss        end it now (as if done) | let its clock run out now (how it ends without you)
 * night             Taken: put the taken one in the nest now; Burning: the scouts; Bell: dusk
 * raid              Burning: the raid now; fire: a roof alight now
 * cut [fraction]    Taken: cut the bonds that far; carry: onto your back; home: into the square
 * deliver           Rebuild: the notices filled; build &lt;fraction&gt;: the masons that far along
 * bell              the village's bell in your hand; hang: hung in its tower as if you did
 * ruin              Burning missed: the houses on the raid side charred, the survivors by the well
 * view &lt;name&gt;       stand at a framing: well, trail, lair, nest, roofs, line, ruin, scaffold, belfry, bell, green, edge
 * quiet on|off      hold the director's own starts (tours)
 * </pre>
 */
public final class TroubleCommands {
    private TroubleCommands() {}

    static final List<String> KINDS = Arrays.stream(TroubleKind.values()).map(k -> k.id).toList();
    static final List<String> VIEWS = List.of("well", "trail", "lair", "nest", "roofs", "line", "ruin", "scaffold", "belfry", "bell", "green", "edge",
            "dance");

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("trouble")
                .then(Commands.literal("start").then(Commands.argument("kind", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(KINDS, b))
                        .executes(c -> start(c, StringArgumentType.getString(c, "kind")))))
                .then(Commands.literal("info").executes(TroubleCommands::info))
                .then(Commands.literal("help").executes(TroubleCommands::help))
                .then(Commands.literal("end").executes(c -> end(c, false)))
                .then(Commands.literal("miss").executes(c -> end(c, true)))
                .then(Commands.literal("night").executes(TroubleCommands::night))
                .then(Commands.literal("raid").executes(TroubleCommands::raid))
                .then(Commands.literal("fire").executes(TroubleCommands::fire))
                .then(Commands.literal("cut").executes(c -> cut(c, 1.0))
                        .then(Commands.argument("fraction", DoubleArgumentType.doubleArg(0, 1)).executes(c -> cut(c, DoubleArgumentType.getDouble(c, "fraction")))))
                .then(Commands.literal("carry").executes(TroubleCommands::carry))
                .then(Commands.literal("home").executes(TroubleCommands::home))
                .then(Commands.literal("deliver").executes(TroubleCommands::deliver))
                .then(Commands.literal("build").then(Commands.argument("fraction", DoubleArgumentType.doubleArg(0, 1))
                        .executes(c -> build(c, DoubleArgumentType.getDouble(c, "fraction")))))
                .then(Commands.literal("bell").executes(TroubleCommands::bell))
                .then(Commands.literal("hang").executes(TroubleCommands::hang))
                .then(Commands.literal("ruin").executes(TroubleCommands::ruin))
                .then(Commands.literal("quiet").then(Commands.argument("on", StringArgumentType.word()).executes(c -> {
                    TroubleDirector.quiet = StringArgumentType.getString(c, "on").equals("on");
                    return 1;
                })))
                .then(Commands.literal("view").then(Commands.argument("name", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(VIEWS, b))
                        .executes(c -> view(c, StringArgumentType.getString(c, "name")))));
    }

    /** The running village you're in, or the nearest within 400. */
    static VillageService.@Nullable Live here(ServerPlayer p) {
        VillageService.Live best = VillageService.at(p.level(), p.blockPosition());
        if (best != null) return best;
        double bd = 400;
        for (VillageService.Live l : VillageService.all()) {
            double d = Math.hypot(l.plan().ox - p.getX(), l.plan().oz - p.getZ());
            if (d < bd) {
                bd = d;
                best = l;
            }
        }
        return best;
    }

    private static @Nullable Trouble trouble(ServerPlayer p, VillageService.Live l) {
        TroubleState st = TroubleState.get(p.level().getServer());
        Trouble t = st.of(l.key());
        return t != null ? t : st.festival(l.key());
    }

    private static int fail(CommandContext<CommandSourceStack> c, String why) {
        c.getSource().sendFailure(Component.literal(why));
        return 0;
    }

    private static void ok(CommandContext<CommandSourceStack> c, String what) {
        c.getSource().sendSuccess(() -> Component.literal(what), false);
        Awaken.LOG.info("[trouble] command: {}", what);
    }

    // ---- starting ------------------------------------------------------------------------------------------------------

    private static int start(CommandContext<CommandSourceStack> c, String kindId) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel level = c.getSource().getServer().overworld();
        VillageService.Live l = here(p);
        if (l == null) return fail(c, "No village running near you.");
        TroubleKind kind = TroubleKind.of(kindId);
        if (kind == null) return fail(c, "No trouble " + kindId);
        Plot.Ctx ctx = TroubleDirector.ctx(level, l.key());
        String cause = switch (kind) {
            case TAKEN -> {
                String n = Taken.nearest(ctx, l);
                if (n == null) {
                    Camps.Camp den = denFor(level, l);
                    n = den == null ? null : "den:" + den.id;
                }
                yield n;
            }
            case BURNING -> {
                campFor(level, l, 4);
                yield "camp";
            }
            case BELL -> "cracked:test";
            case MISSING -> "storm";
            case GREY_FIELDS -> GreyFields.cause(ctx, l);
            case REBUILD -> "damage";
            case FESTIVAL -> "calendar";
        };
        if (cause == null) return fail(c, "Nothing in the world here could cause " + kind.title + " (no lair, den, camp or gate near).");
        Trouble t = TroubleDirector.start(level, l, kind, cause);
        if (t == null) return fail(c, kind.title + " couldn't begin at " + l.plan().name + " (one already running?)");
        ok(c, kind.title + " begins at " + l.plan().name + ": " + t);
        return 1;
    }

    /** A Hollows den 150-220 out from the village (for tours and tests in a world with none near). */
    static Camps.@Nullable Camp denFor(ServerLevel level, VillageService.Live l) {
        VillagePlan p = l.plan();
        for (int k = 0; k < 8; k++) {
            double a = Taken.mix(p.seed, k, 51) % 628 / 100.0;
            double r = p.radius() + 120 + k * 8;
            BlockPos at = new BlockPos((int) (p.ox + Math.cos(a) * r), 0, (int) (p.oz + Math.sin(a) * r));
            if (!level.isLoaded(at)) continue;
            Camps.Camp camp = Camps.placeAt(level, "hollow_den", at, 1, Taken.mix(p.seed, k, 53));
            if (camp != null) return camp;
        }
        return null;
    }

    /** A riftborn camp at a tier 160-240 out (the Burning's raiders come from it). */
    static Camps.@Nullable Camp campFor(ServerLevel level, VillageService.Live l, int tier) {
        BlockPos o = new BlockPos(l.plan().ox, l.plan().baseY, l.plan().oz);
        for (Camps.Camp camp : Camps.get(level.getServer()).all()) {
            if (camp.resolved() && !camp.cleared(PlayDays.current(level.getServer())) && camp.kind.equals("riftborn")
                    && TroubleDirector.horizontal(o, camp.pos()) < 800) {
                camp.tier = Math.max(camp.tier, tier);
                return camp;
            }
        }
        VillagePlan p = l.plan();
        for (int k = 0; k < 8; k++) {
            double a = Taken.mix(p.seed, k, 57) % 628 / 100.0;
            double r = p.radius() + 140 + k * 10;
            BlockPos at = new BlockPos((int) (p.ox + Math.cos(a) * r), 0, (int) (p.oz + Math.sin(a) * r));
            if (!level.isLoaded(at)) continue;
            Camps.Camp camp = Camps.placeAt(level, "riftborn", at, tier, Taken.mix(p.seed, k, 59));
            if (camp != null) return camp;
        }
        return null;
    }

    private static int info(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        TroubleState st = TroubleState.get(c.getSource().getServer());
        for (Trouble t : st.troubles()) ok(c, t.toString());
        VillageService.Live l = here(p);
        if (l != null) {
            TroubleState.Memory m = st.peek(l.key());
            if (m != null)
                ok(c, String.format(Locale.ROOT, "%s: last %d, festival day %d, bell %d (%s), grey %s, sealed %s, left %s, hollowed %s, homeless %s, charred %s, tokens %d",
                        l.plan().name, m.lastEnd, m.festival, m.bell, m.bellBy, m.grey, m.sealed, m.left, m.hollowed, m.homeless, m.charred, m.tokens.size()));
            ok(c, "damage " + Villages.damage(c.getSource().getServer(), l.key()).size() + ", history: " + Villages.history(c.getSource().getServer(), l.key()));
        }
        return 1;
    }

    private static int help(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        VillageService.Live l = here(p);
        Trouble t = l == null ? null : trouble(p, l);
        if (t == null) return fail(c, "No trouble here.");
        TroubleDirector.answer(p, TroubleDirector.ACTION + ":" + t.id, true);
        ok(c, "Help: " + t);
        return 1;
    }

    private static int end(CommandContext<CommandSourceStack> c, boolean miss) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        VillageService.Live l = here(p);
        Trouble t = l == null ? null : trouble(p, l);
        if (t == null) return fail(c, "No trouble here.");
        Plot.Ctx ctx = TroubleDirector.ctx(c.getSource().getServer().overworld(), t.village);
        if (miss) TroubleDirector.plot(t).miss(ctx, t);
        TroubleDirector.end(ctx, t);
        ok(c, (miss ? "Missed: " : "Ended: ") + t);
        return 1;
    }

    // ---- moving one along (tours) ------------------------------------------------------------------------------------------

    private static int night(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        VillageService.Live l = here(p);
        Trouble t = l == null ? null : trouble(p, l);
        if (t == null) return fail(c, "No trouble here.");
        ServerLevel level = c.getSource().getServer().overworld();
        Plot.Ctx ctx = TroubleDirector.ctx(level, t.village);
        if (t.kind.equals(TroubleKind.BURNING.id)) {
            t.put("scouts", 0);
            t.put("raid_day", ctx.day() + 1);
            Burning.forceScouts(ctx, t, l);
        } else if (t.kind.equals(TroubleKind.TAKEN.id)) {
            Taken.forceNest(ctx, t, l);
        }
        ok(c, "night: " + t);
        return 1;
    }

    private static int raid(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        VillageService.Live l = here(p);
        Trouble t = l == null ? null : trouble(p, l);
        if (t == null || !t.kind.equals(TroubleKind.BURNING.id)) return fail(c, "No Burning here.");
        Plot.Ctx ctx = TroubleDirector.ctx(c.getSource().getServer().overworld(), t.village);
        t.put("raid_day", ctx.day());
        Burning.forceRaid(ctx, t, l);
        ok(c, "raid: " + t);
        return 1;
    }

    private static int fire(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        VillageService.Live l = here(p);
        Trouble t = l == null ? null : trouble(p, l);
        if (t == null || !t.kind.equals(TroubleKind.BURNING.id) || t.stage != Burning.RAID) return fail(c, "No raid here.");
        Plot.Ctx ctx = TroubleDirector.ctx(c.getSource().getServer().overworld(), t.village);
        int n = Burning.forceFire(ctx, t, l, p.position());
        ok(c, "fire: " + n + " roofs alight");
        return 1;
    }

    private static int cut(CommandContext<CommandSourceStack> c, double f) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        VillageService.Live l = here(p);
        Trouble t = findTaken(p);
        if (t == null) return fail(c, "No one to cut free.");
        Plot.Ctx ctx = TroubleDirector.ctx(c.getSource().getServer().overworld(), t.village);
        Rescuee r = Taken.rescuee(ctx, t);
        if (r == null) return fail(c, "Not in the nest yet (come near it).");
        if (f >= 1.0) {
            r.cut(0.99f);
            Taken.forceCut(ctx, t, p, r);
        } else r.cut((float) f);
        ok(c, "cut " + f);
        return 1;
    }

    private static @Nullable Trouble findTaken(ServerPlayer p) {
        for (Trouble t : TroubleState.get(p.level().getServer()).troubles())
            if (t.kind.equals(TroubleKind.TAKEN.id) || t.kind.equals(TroubleKind.MISSING.id)) return t;
        return null;
    }

    private static int carry(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Trouble t = findTaken(p);
        if (t == null) return fail(c, "No one to carry.");
        Plot.Ctx ctx = TroubleDirector.ctx(c.getSource().getServer().overworld(), t.village);
        Rescuee r = Taken.rescuee(ctx, t);
        if (r == null) return fail(c, "No one here.");
        if (r.carried()) r.setDown(false);
        r.teleportTo(p.getX(), p.getY(), p.getZ());
        r.mode(Rescuee.WAIT);
        if (!t.helped(p.getUUID())) t.helpers.add(p.getUUID());
        boolean on = r.climbOn(p);
        if (t.stage < Taken.FREED) t.stage = Taken.FREED;
        ok(c, "carry: " + on);
        return on ? 1 : 0;
    }

    private static int home(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Trouble t = findTaken(p);
        if (t == null) return fail(c, "No one to bring home.");
        ServerLevel level = c.getSource().getServer().overworld();
        Plot.Ctx ctx = TroubleDirector.ctx(level, t.village);
        VillageService.Live l = ctx.live();
        Rescuee r = Taken.rescuee(ctx, t);
        if (l == null || r == null) return fail(c, "Not here.");
        if (r.carried()) r.setDown(false);
        Vec3 w = TroubleDirector.well(l);
        Vec3 at = TroubleDirector.green(l, 0.4, l.plan().greenR + 2);
        r.teleportTo(at.x, at.y, at.z);
        if (!t.helped(p.getUUID())) t.helpers.add(p.getUUID());
        t.stage = Taken.FREED;
        Taken.homecoming(ctx, t, l, r);
        ok(c, "home at " + w);
        return 1;
    }

    private static int deliver(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        VillageService.Live l = here(p);
        Trouble t = l == null ? null : trouble(p, l);
        if (t == null || !t.kind.equals(TroubleKind.REBUILD.id)) return fail(c, "No Rebuild here.");
        for (String id : t.text.split("\\|")) dev.awaken.village.Requests.unpost(c.getSource().getServer(), l, id);
        if (!t.helped(p.getUUID())) t.helpers.add(p.getUUID());
        ok(c, "delivered");
        return 1;
    }

    private static int build(CommandContext<CommandSourceStack> c, double f) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        VillageService.Live l = here(p);
        Trouble t = l == null ? null : trouble(p, l);
        if (t == null || !t.kind.equals(TroubleKind.REBUILD.id) || t.stage != Rebuild.BUILDING) return fail(c, "No masons at work here.");
        t.since = c.getSource().getServer().overworld().getGameTime() - (long) (Rebuild.WORK * f);
        ok(c, "build " + f);
        return 1;
    }

    private static int bell(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        VillageService.Live l = here(p);
        if (l == null) return fail(c, "No village near.");
        var stack = BellTrouble.bellItem(l);
        if (!p.getInventory().add(stack)) p.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
        ok(c, "bell");
        return 1;
    }

    private static int hang(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        VillageService.Live l = here(p);
        if (l == null) return fail(c, "No village near.");
        BlockPos bell = BellTrouble.bellPos(l);
        if (bell == null) return fail(c, "No belfry.");
        var stack = BellTrouble.bellItem(l);
        boolean ok = BellTrouble.hang(c.getSource().getServer().overworld(), p, bell, net.minecraft.core.Direction.UP, stack);
        ok(c, "hang: " + ok);
        return ok ? 1 : 0;
    }

    private static int ruin(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        VillageService.Live l = here(p);
        if (l == null) return fail(c, "No village near.");
        ServerLevel level = c.getSource().getServer().overworld();
        Plot.Ctx ctx = TroubleDirector.ctx(level, l.key());
        Trouble t = TroubleState.get(level.getServer()).of(l.key());
        BlockPos from = t != null && t.place != 0 ? BlockPos.of(t.place) : BlockPos.containing(p.position());
        if (t != null && t.kind.equals(TroubleKind.BURNING.id)) {
            Burning.ruin(ctx, t, l);
            TroubleDirector.end(ctx, t);
        } else Burning.burnHouses(ctx, l, ctx.st().memory(l.key()), 3, from, "riftborn");
        // the fire's work now, not a house a second
        for (int i = 0; i < 6; i++) Lasting.tick(ctx, l);
        ok(c, "ruin: " + ctx.st().memory(l.key()).charred);
        return 1;
    }

    // ---- framings ------------------------------------------------------------------------------------------------------------

    private static int view(CommandContext<CommandSourceStack> c, String name) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel level = c.getSource().getServer().overworld();
        VillageService.Live l = here(p);
        if (l == null) return fail(c, "No village near.");
        Plot.Ctx ctx = TroubleDirector.ctx(level, l.key());
        Trouble t = TroubleState.get(level.getServer()).of(l.key());
        if (t == null) t = TroubleState.get(level.getServer()).festival(l.key());
        Vec3[] v = Views.frame(ctx, l, t, name);
        if (v == null) return fail(c, "No framing " + name + " here now.");
        Vec3 eye = v[0], at = v[1];
        double dx = at.x - eye.x, dy = at.y - eye.y, dz = at.z - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz)), pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        p.teleportTo(level, eye.x, eye.y - 1.62, eye.z, Set.of(), yaw, pitch, true);
        ok(c, "view " + name + " at " + BlockPos.containing(eye).toShortString());
        return 1;
    }
}
