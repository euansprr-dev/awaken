package dev.awaken.hearth.settle;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.awaken.hearth.HearthData;
import dev.awaken.hearth.HearthRecord;
import dev.awaken.hearth.HearthService;
import dev.awaken.hearth.HearthViews;
import dev.awaken.hearth.TierRules;
import dev.awaken.hearth.Upgrades;
import dev.awaken.hearth.player.HearthPlayer;
import dev.awaken.hearth.stage.HomeStage;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Admin commands for the people of a Hearth and its tiers (tours, tests, the studio): {@code /awaken admin hearth}
 * {@code stage [view <name>|settle]} (a homestead grown into a village, built at your feet, its Hearth yours), {@code people}
 * (who lives here and who waits), {@code know <who>}, {@code arrive <who>} (they walk up the road and move in now if they
 * can), {@code talk <who>} (as if you spoke to them), {@code tier <n>}, {@code evening} (the mine's haul now),
 * {@code wren} (a rope line now), {@code harvest}.
 */
public final class SettleCommands {
    private SettleCommands() {}

    public static List<LiteralArgumentBuilder<CommandSourceStack>> trees() {
        return List.of(stage(), people(), know(), arrive(), talk(), tier(), evening(), wren(), harvest());
    }

    static Optional<HearthRecord> home(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        Optional<HearthRecord> h = HearthService.homeOf(sp);
        if (h.isEmpty()) c.getSource().sendFailure(Component.literal("You have no Hearth."));
        return h;
    }

    static void say(CommandContext<CommandSourceStack> c, String s) {
        c.getSource().sendSuccess(() -> Component.literal(s), false);
    }

    // ---- the staged homestead ----------------------------------------------------------------------------------

    static LiteralArgumentBuilder<CommandSourceStack> stage() {
        return Commands.literal("stage").executes(SettleCommands::buildStage)
                .then(Commands.literal("view").then(Commands.argument("name", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("road", "ilse_door", "ilse_room", "forge", "field", "minehead",
                                "anchor", "kit", "overview", "hall", "atlas", "walker"), b))
                        .executes(c -> view(c, StringArgumentType.getString(c, "name")))))
                .then(Commands.literal("settle").executes(SettleCommands::settleStage))
                .then(Commands.literal("miner").executes(SettleCommands::minerSoon))
                .then(Commands.literal("time").then(Commands.argument("tod", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 23999))
                        .executes(c -> {
                            setClock(c.getSource().getLevel().getServer().overworld(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "tod"));
                            return 1;
                        })))
                .then(Commands.literal("follow").then(Commands.argument("who", StringArgumentType.word()).executes(c -> follow(c, StringArgumentType.getString(c, "who")))))
                .then(Commands.literal("clock").then(Commands.argument("who", StringArgumentType.word())
                        .then(Commands.argument("when", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("breakfast", "work"), b))
                                .executes(c -> clock(c, StringArgumentType.getString(c, "who"), StringArgumentType.getString(c, "when"))))));
    }

    /** A settler by id, given name or role ("oren", "hettie", "miner"), or null. */
    static Folk.@Nullable Settler settler(HearthRecord h, String who) {
        for (Folk.Settler s : h.folk.settlers) {
            if (s.who.id().equals(who) || s.who.given().equalsIgnoreCase(who) || s.who.role().equals(who)) return s;
        }
        return null;
    }

    /** The clock set to three seconds before a settler sets out from home for breakfast, or for the morning's work. */
    static int clock(CommandContext<CommandSourceStack> c, String who, String when) throws CommandSyntaxException {
        Optional<HearthRecord> ho = home(c);
        if (ho.isEmpty()) return 0;
        HearthRecord h = ho.get();
        ServerLevel level = c.getSource().getPlayerOrException().level().getServer().overworld();
        Folk.Settler st = settler(h, who);
        int rec = st == null ? -1 : HomePeople.recordOf(h, st.who.id());
        if (rec < 0) {
            c.getSource().sendFailure(Component.literal("Nobody here called " + who));
            return 0;
        }
        int t = when.equals("work") ? Days.work(rec) : Days.breakfast(rec);
        setClock(level, t - 60);
        say(c, st.who.given() + " sets out at " + t);
        return 1;
    }

    /** The clock on to the next {@code timeOfDay} (always forward: the day's work and the haul follow the clock). */
    static void setClock(ServerLevel level, int timeOfDay) {
        long now = level.getOverworldClockTime(), at = now / 24000 * 24000 + Math.floorMod(timeOfDay, 24000);
        long to = at <= now ? at + 24000 : at;
        level.registryAccess().get(net.minecraft.world.clock.WorldClocks.OVERWORLD)
                .ifPresent(clock -> level.getServer().clockManager().setTotalTicks(clock, to));
    }

    /** The camera just behind a settler's shoulder, looking the way they face (walking: the way they're going). */
    static int follow(CommandContext<CommandSourceStack> c, String who) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        Optional<HearthRecord> ho = home(c);
        if (ho.isEmpty()) return 0;
        Folk.Settler st = settler(ho.get(), who);
        var body = st == null ? null : HomePeople.body(ho.get(), st.who.id());
        if (!(body instanceof net.minecraft.world.entity.LivingEntity e)) {
            c.getSource().sendFailure(Component.literal("Nobody out of doors called " + who));
            return 0;
        }
        look(sp, behind(e));
        return 1;
    }

    static HomeStage.View behind(net.minecraft.world.entity.LivingEntity e) {
        double yaw = Math.toRadians(e.yBodyRot);
        Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw)), right = new Vec3(-fwd.z, 0, fwd.x), at = e.position();
        return new HomeStage.View(at.subtract(fwd.scale(4.2)).add(right.scale(1.6)).add(0, 2.2, 0), at.add(fwd.scale(3)).add(0, 1.1, 0));
    }

    static void look(ServerPlayer sp, HomeStage.View v) {
        Vec3 d = v.look().subtract(v.eye());
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        sp.teleportTo(sp.level().getServer().overworld(), v.eye().x, v.eye().y - 1.62, v.eye().z, java.util.Set.of(), yaw, pitch, true);
    }

    /** The clock set to four seconds before the miner comes up the shaft and the evening's haul is in. */
    static int minerSoon(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Optional<HearthRecord> ho = home(c);
        if (ho.isEmpty()) return 0;
        HearthRecord h = ho.get();
        ServerLevel level = c.getSource().getPlayerOrException().level().getServer().overworld();
        for (Folk.Settler st : h.folk.settlers) {
            if (!"miner".equals(st.who.role()) || !st.home()) continue;
            int rec = HomePeople.recordOf(h, st.who.id());
            if (rec < 0) continue;
            int up = Math.min(Days.surfaces(rec), Work.HAUL_TOD); // he and the haul both within the four seconds
            setClock(level, up - 80);
            say(c, st.who.given() + " comes up at " + up);
            return 1;
        }
        c.getSource().sendFailure(Component.literal("No miner lives here"));
        return 0;
    }

    /** Builds the homestead on the nearest good ground; its Hearth (Tier I, named Ashford) is yours, its rooms read now. */
    static int buildStage(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        ServerLevel level = sp.level().getServer().overworld();
        HearthData data = HearthData.get(level.getServer());
        data.of(sp.getUUID()).ifPresent(old -> {
            HomePeople.stop(old);
            data.remove(old.owner);
        });
        long t0 = System.nanoTime();
        // on dry, gently rolling land near you, clear of any settlement, and you're stood in its yard after
        BlockPos at = HomeStage.site(level, sp.blockPosition());
        HomeStage.Stage st = HomeStage.build(level, at);
        sp.teleportTo(level, at.getX() + 2.5, at.getY(), at.getZ() + 7.5, java.util.Set.of(), 180f, 10f, true);
        HearthRecord h = new HearthRecord(sp.getUUID(), sp.getGameProfile().name(), st.anchor(), HearthService.OVERWORLD);
        h.name = "Ashford";
        h.tier = 1;
        h.spire = st.anchor(); // no Spire pieces handed out for a stage
        h.createdDay = Settlers.today(level.getServer());
        h.folk.ownBeds.put(sp.getUUID(), st.beds().get("owner"));
        data.put(h);
        HearthPlayer hp = HearthService.player(sp);
        hp.respawnAtHearth = true;
        hp.tips.add(dev.awaken.hearth.guide.Guide.TIP_SEEN);
        hp.tips.add(dev.awaken.hearth.guide.Cottage.TIP_OFFERED);
        hp.tips.add(dev.awaken.hearth.guide.Cottage.TIP_DECLINED);
        HearthService.scanNow(level, h);
        HearthService.showTier(level, h);
        HearthViews.refresh(sp);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        say(c, "Ashford staged in " + ms + " ms · rooms " + h.rooms.size() + " · bedrooms " + Settlers.bedrooms(level, h).size()
                + " · spare " + Settlers.spare(level, h).size() + " · shafts " + MineHead.shafts(level, h).size());
        return h.rooms.size();
    }

    static int view(CommandContext<CommandSourceStack> c, String name) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        HomeStage.Stage st = HomeStage.last();
        HomeStage.View v = st == null ? null : st.views().get(name);
        if (name.equals("walker")) {
            // whoever is walking in, from just behind their shoulder: the bundle, and the way ahead
            var npc = Arrival.walker(sp.level().getServer().overworld());
            if (npc != null) v = behind(npc);
        }
        if (v == null) {
            c.getSource().sendFailure(Component.literal("No staged view " + name));
            return 0;
        }
        look(sp, v);
        return 1;
    }

    /** The staged village's people: Oren, Hettie and a miner from the city move straight into their cottages. */
    static int settleStage(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Optional<HearthRecord> ho = home(c);
        HomeStage.Stage st = HomeStage.last();
        if (ho.isEmpty() || st == null) return 0;
        HearthRecord h = ho.get();
        ServerLevel level = c.getSource().getPlayerOrException().level().getServer().overworld();
        int n = 0;
        for (String who : List.of("oren", "hettie", "miner")) {
            Folk.Person p = Settlers.forceKnow(level, h, who);
            BlockPos bed = st.beds().get(who);
            if (p == null || bed == null || h.folk.settler(p.id()) != null) continue;
            Folk.Person settled = Settlers.settled(h, p);
            h.folk.settlers.add(new Folk.Settler(settled, bed, Folk.Settler.HOME, Settlers.today(level.getServer())));
            n++;
        }
        HearthService.dirty(level.getServer());
        HomePeople.changed(h);
        HomePeople.sync(level, h, level.getGameTime());
        say(c, n + " moved in; " + h.folk.living() + " live here");
        return n;
    }

    // ---- people -----------------------------------------------------------------------------------------------

    static LiteralArgumentBuilder<CommandSourceStack> people() {
        return Commands.literal("people").executes(c -> {
            Optional<HearthRecord> ho = home(c);
            if (ho.isEmpty()) return 0;
            HearthRecord h = ho.get();
            ServerLevel level = c.getSource().getPlayerOrException().level().getServer().overworld();
            var s = HomePeople.settlement(h);
            say(c, h.displayName() + " · Tier " + Upgrades.roman(Math.max(1, h.tier)) + " " + TierRules.name(h.tier) + " · living " + h.folk.living()
                    + "/" + Settlers.cap(h.tier) + " · spare bedrooms " + Settlers.spare(level, h).size() + " · on the engine: "
                    + (s == null ? "no" : "#" + s.id() + " " + s.people() + " people, " + s.bodies() + " bodies, awake " + s.awake()));
            for (Folk.Settler st : h.folk.settlers) {
                say(c, "  " + st.who.name() + " (" + st.who.role() + ", " + st.who.trade() + ") · " + (st.home() ? "home" : "coming") + " · bed "
                        + (st.bed == null ? "none" : st.bed.toShortString()) + " · rec " + HomePeople.recordOf(h, st.who.id()));
            }
            for (Settlers.Waiting w : Settlers.waiting(level, h)) say(c, "  waiting: " + w.line());
            for (MineHead.Shaft sh : MineHead.shafts(level, h)) {
                say(c, "  shaft at " + sh.hatch().toShortString() + " · " + sh.depth() + " deep to y " + sh.foot()
                        + sh.lode().map(v -> " · cuts a " + v.ore().word() + " lode").orElse(""));
            }
            return h.folk.living();
        });
    }

    static LiteralArgumentBuilder<CommandSourceStack> know() {
        return Commands.literal("know").then(Commands.argument("who", StringArgumentType.greedyString()).executes(c -> {
            Optional<HearthRecord> ho = home(c);
            if (ho.isEmpty()) return 0;
            ServerLevel level = c.getSource().getPlayerOrException().level().getServer().overworld();
            Folk.Person p = Settlers.forceKnow(level, ho.get(), StringArgumentType.getString(c, "who"));
            say(c, p == null ? "Nobody by that name" : p.name() + " knows you now");
            return p == null ? 0 : 1;
        }));
    }

    /** They're known now and set out at once if they can come; from the staged road when there's one. */
    static LiteralArgumentBuilder<CommandSourceStack> arrive() {
        return Commands.literal("arrive").then(Commands.argument("who", StringArgumentType.word()).executes(c -> {
            Optional<HearthRecord> ho = home(c);
            if (ho.isEmpty()) return 0;
            HearthRecord h = ho.get();
            ServerLevel level = c.getSource().getPlayerOrException().level().getServer().overworld();
            Folk.Person p = Settlers.forceKnow(level, h, StringArgumentType.getString(c, "who"));
            if (p == null) {
                c.getSource().sendFailure(Component.literal("Nobody by that name"));
                return 0;
            }
            HomeStage.Stage st = HomeStage.last();
            BlockPos from = st != null && h.contains(st.road()) ? st.road() : null;
            String why = Settlers.settleNow(level, h, p.id(), from, from == null ? null : st.spots().get("road_end"));
            say(c, why.isEmpty() ? p.given() + " is on the way" : why);
            return why.isEmpty() ? 1 : 0;
        }));
    }

    static LiteralArgumentBuilder<CommandSourceStack> talk() {
        return Commands.literal("talk").then(Commands.argument("who", StringArgumentType.word()).executes(c -> {
            ServerPlayer sp = c.getSource().getPlayerOrException();
            Optional<HearthRecord> ho = home(c);
            if (ho.isEmpty()) return 0;
            HearthRecord h = ho.get();
            String who = StringArgumentType.getString(c, "who");
            var s = HomePeople.settlement(h);
            for (Folk.Settler st : h.folk.settlers) {
                if (!st.who.role().equals(who) && !st.who.id().equals(who) && !st.who.given().equalsIgnoreCase(who)) continue;
                int rec = HomePeople.recordOf(h, st.who.id());
                if (s == null || rec < 0) {
                    c.getSource().sendFailure(Component.literal(st.who.given() + " isn't on the people engine yet"));
                    return 0;
                }
                HomeTalk.talk(sp, s, rec);
                return 1;
            }
            c.getSource().sendFailure(Component.literal("Nobody here by that name"));
            return 0;
        }));
    }

    static LiteralArgumentBuilder<CommandSourceStack> tier() {
        return Commands.literal("tier").then(Commands.argument("n", IntegerArgumentType.integer(1, 5)).executes(c -> {
            Optional<HearthRecord> ho = home(c);
            if (ho.isEmpty()) return 0;
            HearthRecord h = ho.get();
            ServerLevel level = c.getSource().getPlayerOrException().level().getServer().overworld();
            h.tier = IntegerArgumentType.getInteger(c, "n");
            HearthService.dirty(level.getServer());
            Upgrades.grown(level, h);
            h.scheduleScan(level.getGameTime(), 1);
            say(c, "Tier " + Upgrades.roman(h.tier) + " · " + TierRules.name(h.tier) + " · radius " + h.radius());
            return h.tier;
        }));
    }

    static LiteralArgumentBuilder<CommandSourceStack> evening() {
        return Commands.literal("evening").executes(c -> {
            Optional<HearthRecord> ho = home(c);
            if (ho.isEmpty()) return 0;
            ServerLevel level = c.getSource().getPlayerOrException().level().getServer().overworld();
            Work.evening(level, ho.get(), Settlers.today(level.getServer()));
            say(c, "The mine's haul came up (" + ho.get().oresBrought + " ores in all)");
            return 1;
        });
    }

    static LiteralArgumentBuilder<CommandSourceStack> wren() {
        return Commands.literal("wren").executes(c -> {
            Optional<HearthRecord> ho = home(c);
            if (ho.isEmpty()) return 0;
            ServerPlayer sp = c.getSource().getPlayerOrException();
            ServerLevel level = sp.level().getServer().overworld();
            Ropes.Line line = Ropes.string(level, ho.get());
            HearthService.dirty(level.getServer());
            if (line == null) {
                say(c, "No gap for a line");
                return 0;
            }
            say(c, "A line of " + line.length() + " from " + line.start().toShortString());
            if (sp.isSpectator()) {
                // stand off the line's middle, square to it and a little below, looking up at it
                BlockPos mid = line.middle();
                Direction side = line.dir().getClockWise();
                Vec3 eye = Vec3.atCenterOf(mid).add(side.getStepX() * 7.5, -2.0, side.getStepZ() * 7.5);
                Vec3 d = Vec3.atCenterOf(mid).subtract(eye);
                float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
                float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
                sp.teleportTo(level, eye.x, eye.y - 1.62, eye.z, java.util.Set.of(), yaw, pitch, true);
            }
            return 1;
        });
    }

    static LiteralArgumentBuilder<CommandSourceStack> harvest() {
        return Commands.literal("harvest").executes(c -> {
            Optional<HearthRecord> ho = home(c);
            if (ho.isEmpty()) return 0;
            ServerLevel level = c.getSource().getPlayerOrException().level().getServer().overworld();
            int n = Work.harvest(level, ho.get(), 64);
            say(c, n + " crops in");
            return n;
        });
    }
}
