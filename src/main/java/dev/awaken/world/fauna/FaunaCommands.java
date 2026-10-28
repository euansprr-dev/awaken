package dev.awaken.world.fauna;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Pair;
import dev.awaken.core.AwakenCommands;
import dev.awaken.core.api.MobRole;
import dev.awaken.mobs.MobTypes;
import dev.awaken.mobs.Mobs;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.mobs.def.FaunaRole;
import dev.awaken.mobs.def.MobDef;
import dev.awaken.mobs.def.Theme;
import dev.awaken.mobs.entity.AwakenMob;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.attribute.EnvironmentAttributeMap;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * {@code /awaken fauna} (creatures you have met) and the admin tools: {@code census}, {@code goto <family>},
 * {@code reveal}, {@code summon <id> [level]}, {@code lineup [all|p1|p2|<family>]} (creatures in a row, for
 * screenshots), {@code beasts [reset]}, {@code why <id>}, and the lairs: {@code lair list|goto <beast>|place <beast>|
 * hear <beast>|why|reset}.
 */
final class FaunaCommands {
    private FaunaCommands() {}

    static void register() {
        AwakenCommands.add((root, ctx) -> {
            var fauna = Commands.literal("fauna").executes(FaunaCommands::met);
            fauna.then(Commands.literal("census").requires(AwakenCommands.admin()).executes(FaunaCommands::census));
            fauna.then(Commands.literal("reveal").requires(AwakenCommands.admin()).executes(c -> {
                int n = Reveal.play(c.getSource().getPlayerOrException(), 100);
                c.getSource().sendSuccess(() -> Component.literal("Reveal played: " + n + " creatures stepped out"), false);
                return n;
            }));
            fauna.then(Commands.literal("goto").requires(AwakenCommands.admin())
                    .then(Commands.argument("family", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(families(), b))
                            .executes(FaunaCommands::gotoFamily)));
            fauna.then(Commands.literal("summon").requires(AwakenCommands.admin())
                    .then(Commands.argument("id", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(ids(), b))
                            .executes(c -> summon(c, -1))
                            .then(Commands.argument("level", IntegerArgumentType.integer(0, 400)).executes(c -> summon(c, IntegerArgumentType.getInteger(c, "level"))))));
            fauna.then(Commands.literal("lineup").requires(AwakenCommands.admin()).executes(c -> lineup(c, "all"))
                    .then(Commands.argument("which", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(lineups(), b))
                            .executes(c -> lineup(c, StringArgumentType.getString(c, "which")))));
            fauna.then(LairCommands.build());
            fauna.then(Fauna3Commands.mount()); // P3: mounts, the tank and the grotto
            fauna.then(Fauna3Commands.tank());
            fauna.then(Fauna3Commands.grotto());
            fauna.then(Commands.literal("why").requires(AwakenCommands.admin())
                    .then(Commands.argument("id", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(ids(), b))
                            .executes(FaunaCommands::why)));
            fauna.then(Commands.literal("beasts").requires(AwakenCommands.admin()).executes(FaunaCommands::beasts)
                    .then(Commands.literal("reset").executes(c -> {
                        BeastRegistry.get(c.getSource().getServer()).resetCooldown();
                        c.getSource().sendSuccess(() -> Component.literal("Beast cooldown cleared"), true);
                        return 1;
                    })));
            root.then(fauna);
        });
    }

    static List<String> families() {
        List<String> out = new ArrayList<>();
        for (Theme t : Theme.values()) if (t.isWild()) out.add(t.name().toLowerCase(Locale.ROOT));
        return out;
    }

    static Theme family(String name) {
        for (Theme t : Theme.values()) if (t.isWild() && t.name().equalsIgnoreCase(name)) return t;
        return null;
    }

    private static List<String> lineups() {
        List<String> out = new ArrayList<>(List.of("all", "p1", "p2", "p3"));
        out.addAll(families());
        return out;
    }

    private static List<String> ids() {
        List<String> out = new ArrayList<>();
        for (MobDef d : Bestiary.fauna()) out.add(d.id);
        return out;
    }

    private static int met(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        List<String> names = new ArrayList<>();
        for (MobDef d : Bestiary.fauna()) if (Sightings.hasSeen(sp, d.id)) names.add(d.name);
        String msg = "Creatures met: " + names.size() + " / " + Bestiary.fauna().size() + (names.isEmpty() ? "" : " · " + String.join(", ", names));
        c.getSource().sendSuccess(() -> Component.literal(msg), false);
        return names.size();
    }

    private static int census(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        ServerLevel level = sp.level();
        StringBuilder sb = new StringBuilder("Fauna near you:");
        for (FaunaRole r : FaunaRole.values()) {
            sb.append(' ').append(r.name().toLowerCase(Locale.ROOT)).append(' ')
                    .append(FaunaCensus.countNear(level, r, sp.getX(), sp.getZ(), 128)).append('/').append(r.capPerPlayer);
        }
        long t = FaunaRules.dayTime(level);
        sb.append(" · loaded ").append(FaunaCensus.total(level)).append(" · spawning ").append(FaunaModule.spawningOn(level) ? "on" : "off")
                .append(" · time ").append(t).append(Habitat.Window.DUSK_NIGHT.contains(t) ? " (night)" : " (day)");
        String msg = sb.toString();
        c.getSource().sendSuccess(() -> Component.literal(msg), false);
        return FaunaCensus.total(level);
    }

    /** Teleports to the nearest biome where a family's creatures live (found from the live biome spawns). */
    private static int gotoFamily(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(c, "family");
        Theme theme = family(name);
        if (theme == null) {
            c.getSource().sendFailure(Component.literal("Families: " + families()));
            return 0;
        }
        ServerLevel level = sp.level().getServer().overworld();
        Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(b -> hasFamily(b, theme), sp.blockPosition(), 6400, 32, 64);
        if (found == null) {
            c.getSource().sendFailure(Component.literal("No " + name + " biome within 6400 blocks"));
            return 0;
        }
        BlockPos at = found.getFirst();
        String biome = found.getSecond().unwrapKey().map(k -> k.identifier().toString()).orElse("?");
        // there once its land is made (SPEC §28.9, "Arrivals")
        dev.awaken.core.api.Passage.go(sp, level, at.getX() + 0.5, at.getZ() + 0.5, () -> {
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
            sp.teleportTo(level, at.getX() + 0.5, y + 0.1, at.getZ() + 0.5, Set.<Relative>of(), sp.getYRot(), 10.0F, true);
            c.getSource().sendSuccess(() -> Component.literal("Went to " + biome + " (" + name + ") at " + at.getX() + ", " + y + ", " + at.getZ()), false);
        });
        return 1;
    }

    /** Whether this biome's natural spawns include a creature of the family. */
    static boolean hasFamily(Holder<Biome> biome, Theme theme) {
        EnvironmentAttributeMap.Entry<MobSpawnSettings, ?> e = biome.value().getAttributes().get(EnvironmentAttributes.NATURAL_MOB_SPAWNS);
        if (e == null || !(e.argument() instanceof MobSpawnSettings s)) return false;
        for (var cat : List.of(MobTypes.FAUNA, dev.awaken.mobs.entity.Fauna3Kinds.FAUNA_WATER)) {
            for (Weighted<MobSpawnSettings.SpawnerData> w : s.getMobsToSpawn(cat).unwrap()) {
                MobDef d = MobTypes.def(w.value().type());
                if (d != null && d.theme == theme) return true;
            }
        }
        return false;
    }

    private static int summon(CommandContext<CommandSourceStack> c, int asked) throws CommandSyntaxException {
        CommandSourceStack src = c.getSource();
        ServerPlayer sp = src.getPlayerOrException();
        MobDef d = Bestiary.get(StringArgumentType.getString(c, "id"));
        if (d == null || !d.isFauna()) {
            src.sendFailure(Component.literal("Not an overworld creature. Try one of " + ids()));
            return 0;
        }
        ServerLevel level = sp.level();
        Vec3 look = sp.getLookAngle();
        Vec3 at = sp.position().add(look.x * 5, 0, look.z * 5);
        int lv = asked >= 0 ? asked : FaunaRules.contentLevel(level, BlockPos.containing(at), d.fauna);
        AwakenMob m = Mobs.spawn(level, d, at, lv, new MobRole(d.tier.role, lv, d.theme.id, Optional.empty(), ""), EnumSet.noneOf(dev.awaken.core.Forces.Tag.class));
        if (m == null) {
            src.sendFailure(Component.literal("Nothing spawned."));
            return 0;
        }
        src.sendSuccess(() -> Component.literal("Summoned " + d.name + " (level " + lv + ")"), true);
        return 1;
    }

    /** Creatures in a row in front of you (all, P1's, P2's or one family's), standing still, for screenshots. */
    private static int lineup(CommandContext<CommandSourceStack> c, String which) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        ServerLevel level = sp.level();
        float yaw = sp.getYRot() * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 side = new Vec3(fwd.z, 0, -fwd.x);
        Theme only = family(which);
        List<MobDef> all = new ArrayList<>();
        for (MobDef d : Bestiary.fauna()) {
            boolean p1 = d.theme.ordinal() <= Theme.FROSTWILD.ordinal();
            boolean p3 = d.theme.ordinal() >= Theme.TIDEWATER.ordinal();
            if (Habitat.of(d.id).water()) continue; // the Tidewater lines up in water: /awaken fauna tank
            if (only != null ? d.theme == only : which.equals("p1") ? p1 : which.equals("p2") ? !p1 && !p3 : !which.equals("p3") || p3) all.add(d);
        }
        double total = 0;
        for (MobDef d : all) total += gap(d) + 0.2;
        double offset = -total * 0.5;
        int n = 0;
        for (MobDef d : all) {
            double gap = gap(d);
            offset += gap * 0.5;
            Vec3 p = sp.position().add(fwd.scale(d.fauna == FaunaRole.BEAST ? 8 + d.scale * 1.5 : 8)).add(side.scale(offset));
            offset += gap * 0.5 + 0.2;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(p.x), Mth.floor(p.z));
            AwakenMob m = Mobs.spawn(level, d, new Vec3(p.x, d.flies() ? y + 1.0 : y, p.z), 1,
                    new MobRole(d.tier.role, 1, d.theme.id, Optional.empty(), ""), EnumSet.noneOf(dev.awaken.core.Forces.Tag.class));
            if (m == null) continue;
            m.setNoAi(true);
            m.setPersistenceRequired();
            m.addTag("awaken_lineup"); // /kill @e[tag=awaken_lineup] clears the row
            float face = sp.getYRot() + 180.0F;
            m.setYRot(face);
            m.setYHeadRot(face);
            m.setYBodyRot(face);
            n++;
        }
        int count = n;
        c.getSource().sendSuccess(() -> Component.literal("Lined up " + count + " creatures"), false);
        return n;
    }

    /** Room a creature needs in the line-up (its width at its scale). */
    private static double gap(MobDef d) {
        return Math.max(1.6, d.width * d.scale + 0.8);
    }

    /** Would this creature spawn where you stand, and if not, why not (spawn rules only; vanilla's 24-block gap aside). */
    @SuppressWarnings("unchecked")
    private static int why(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        MobDef d = Bestiary.get(StringArgumentType.getString(c, "id"));
        if (d == null || !d.isFauna()) {
            c.getSource().sendFailure(Component.literal("Not an overworld creature. Try one of " + ids()));
            return 0;
        }
        var type = (net.minecraft.world.entity.EntityType<net.minecraft.world.entity.Mob>) (net.minecraft.world.entity.EntityType<?>) MobTypes.type(d);
        String v = FaunaRules.verdict(type, sp.level(), net.minecraft.world.entity.EntitySpawnReason.NATURAL, sp.blockPosition(),
                net.minecraft.util.RandomSource.create(), null, false);
        String msg = d.name + " here: " + (v == null ? "yes" : "no, " + v);
        c.getSource().sendSuccess(() -> Component.literal(msg), false);
        return v == null ? 1 : 0;
    }

    private static int beasts(CommandContext<CommandSourceStack> c) {
        BeastRegistry reg = BeastRegistry.get(c.getSource().getServer());
        long now = c.getSource().getServer().overworld().getGameTime();
        long wait = Math.max(0, BeastRegistry.COOLDOWN - (now - reg.lastSpawn()));
        StringBuilder sb = new StringBuilder("Named beasts: ").append(reg.all().size()).append(" known · next in ").append(wait / 1200).append(" min");
        for (BeastRegistry.Entry e : reg.all()) sb.append("\n ").append(e.def()).append(" at ").append(e.pos().toShortString()).append(" (").append(e.dimension()).append(')');
        String msg = sb.toString();
        c.getSource().sendSuccess(() -> Component.literal(msg), false);
        return reg.all().size();
    }
}
