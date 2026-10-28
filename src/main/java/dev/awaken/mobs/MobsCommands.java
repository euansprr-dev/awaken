package dev.awaken.mobs;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.awaken.core.AwakenCommands;
import dev.awaken.core.api.MobHooks;
import dev.awaken.core.api.MobRole;
import dev.awaken.mobs.boss.KitMoves;
import dev.awaken.mobs.boss.Kits;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.mobs.def.MobDef;
import dev.awaken.mobs.entity.AwakenBoss;
import dev.awaken.progression.Progression;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * {@code /awaken admin mob <theme> <role> [level]}, {@code /awaken admin boss <id> [level]},
 * {@code /awaken admin bestiary}, {@code /awaken bosses studio|moves <id>} (the Creature Kit, SPEC §35.6), and the
 * player-facing {@code /awaken mobs duel} (accept the Lantern Broker's duel).
 */
final class MobsCommands {
    private MobsCommands() {}

    static void register() {
        AwakenCommands.add((root, ctx) -> {
            var admin = Commands.literal("admin").requires(AwakenCommands.admin());
            admin.then(Commands.literal("mob")
                    .then(Commands.argument("theme", IdentifierArgument.id()).suggests((c, b) -> SharedSuggestionProvider.suggest(Mobs.themeIds(), b))
                            .then(Commands.argument("role", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(roles(), b))
                                    .executes(c -> mob(c, -1))
                                    .then(Commands.argument("level", IntegerArgumentType.integer(0, 400))
                                            .executes(c -> mob(c, IntegerArgumentType.getInteger(c, "level")))))));
            admin.then(Commands.literal("boss")
                    .then(Commands.argument("id", IdentifierArgument.id()).suggests((c, b) -> SharedSuggestionProvider.suggest(Mobs.bossIds(), b))
                            .executes(c -> boss(c, -1))
                            .then(Commands.argument("level", IntegerArgumentType.integer(0, 400))
                                    .executes(c -> boss(c, IntegerArgumentType.getInteger(c, "level"))))));
            admin.then(Commands.literal("bestiary").executes(MobsCommands::bestiary));
            root.then(admin);
            // the Creature Kit's bosses (SPEC §35.6)
            root.then(Commands.literal("bosses").requires(AwakenCommands.admin())
                    .then(Commands.literal("studio").then(Commands.argument("id", StringArgumentType.word())
                            .suggests((c, b) -> SharedSuggestionProvider.suggest(kitBosses(), b)).executes(MobsCommands::studio)))
                    .then(Commands.literal("moves").then(Commands.argument("id", StringArgumentType.word())
                            .suggests((c, b) -> SharedSuggestionProvider.suggest(kitBosses(), b)).executes(MobsCommands::moves))));
            root.then(Commands.literal("mobs").then(Commands.literal("duel").executes(c -> {
                ServerPlayer sp = c.getSource().getPlayerOrException();
                boolean ok = Mobs.acceptDuel(sp);
                if (!ok) c.getSource().sendFailure(Component.translatable("awaken.mobs.broker.no_offer"));
                return ok ? 1 : 0;
            })));
        });
    }

    private static List<String> roles() {
        List<String> out = new ArrayList<>();
        for (MobRole.Role r : MobRole.Role.values()) if (r != MobRole.Role.NONE) out.add(r.name().toLowerCase(Locale.ROOT));
        return out;
    }

    private static int levelFor(CommandSourceStack src, int asked) {
        if (asked >= 0) return asked;
        ServerPlayer p = src.getPlayer();
        return p != null ? Progression.rank(p).contentLevel : 5;
    }

    private static BlockPos spot(CommandSourceStack src) {
        Vec3 pos = src.getPosition();
        ServerPlayer p = src.getPlayer();
        if (p != null) {
            Vec3 look = p.getLookAngle();
            pos = pos.add(look.x * 4, 0, look.z * 4);
        }
        return BlockPos.containing(pos);
    }

    private static int mob(CommandContext<CommandSourceStack> c, int asked) throws CommandSyntaxException {
        CommandSourceStack src = c.getSource();
        String theme = IdentifierArgument.getId(c, "theme").toString();
        MobRole.Role role;
        try {
            role = MobRole.Role.valueOf(StringArgumentType.getString(c, "role").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            src.sendFailure(Component.literal("Unknown role. Use one of " + roles()));
            return 0;
        }
        int lv = levelFor(src, asked);
        ServerLevel level = src.getLevel();
        LivingEntity e = MobHooks.spawnThemed(level, theme, role, spot(src), lv, Set.of(), "");
        if (e == null) {
            src.sendFailure(Component.literal("Nothing spawned."));
            return 0;
        }
        src.sendSuccess(() -> Component.literal("Spawned " + e.getName().getString() + " (" + role + ", level " + lv + ", " + theme + ")"), true);
        return 1;
    }

    private static int boss(CommandContext<CommandSourceStack> c, int asked) {
        CommandSourceStack src = c.getSource();
        String id = IdentifierArgument.getId(c, "id").toString();
        int lv = levelFor(src, asked);
        LivingEntity e = MobHooks.spawnBoss(src.getLevel(), id, spot(src), lv, "");
        if (e == null) {
            src.sendFailure(Component.literal("Nothing spawned."));
            return 0;
        }
        src.sendSuccess(() -> Component.literal("Spawned " + e.getName().getString() + " (level " + lv + ")"), true);
        return 1;
    }

    private static List<String> kitBosses() {
        List<String> out = new ArrayList<>();
        for (Kits.Kit k : Kits.all().values()) if (k.fight() != null) out.add(k.id());
        return out;
    }

    /** A lit test arena over a stone floor ahead of you, the boss in its middle playing every move in turn; then it fights. */
    private static int studio(CommandContext<CommandSourceStack> c) {
        CommandSourceStack src = c.getSource();
        String id = StringArgumentType.getString(c, "id");
        if (Kits.get(id) == null) {
            src.sendFailure(Component.literal("No kit body called " + id + ". Kit bosses: " + kitBosses()));
            return 0;
        }
        ServerLevel level = src.getLevel();
        Vec3 look = src.getPlayer() != null ? src.getPlayer().getLookAngle().multiply(1, 0, 1).normalize() : new Vec3(0, 0, 1);
        BlockPos centre = BlockPos.containing(src.getPosition().add(look.scale(14)));
        int r = 11, hgt = 14;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                BlockPos col = centre.offset(dx, 0, dz);
                level.setBlock(col.below(), Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                for (int y = 0; y < hgt; y++) {
                    BlockPos p = col.above(y);
                    boolean lamp = y == hgt - 1 && Math.floorMod(dx, 5) == 0 && Math.floorMod(dz, 5) == 0;
                    level.setBlock(p, lamp ? Blocks.LIGHT.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        LivingEntity e = MobHooks.spawnBoss(level, "awaken:" + id, centre, levelFor(src, -1), "");
        if (!(e instanceof AwakenBoss boss) || !boss.studio()) {
            if (e != null) e.discard();
            src.sendFailure(Component.literal(id + " has no fight script."));
            return 0;
        }
        if (src.getPlayer() != null) boss.setTarget(src.getPlayer());
        src.sendSuccess(() -> Component.literal("Studio: " + e.getName().getString() + " plays its " + Kits.get(id).moves().moves().size()
                + " moves in turn, then fights."), true);
        return 1;
    }

    private static int moves(CommandContext<CommandSourceStack> c) {
        String id = StringArgumentType.getString(c, "id");
        Kits.Kit k = Kits.get(id);
        if (k == null) {
            c.getSource().sendFailure(Component.literal("No kit body called " + id + "."));
            return 0;
        }
        StringBuilder sb = new StringBuilder(id).append(": ").append(k.moves().moves().size()).append(" moves");
        for (KitMoves.Move m : k.moves().moves()) {
            sb.append("\n ").append(m.id()).append(m.title().isEmpty() ? "" : " (" + m.title() + ")").append(": ").append(m.kind())
                    .append(", wind-up ").append(m.time().windup()).append(m.time().hold() > 0 ? " (+" + m.time().hold() + " held)" : "")
                    .append(", active ").append(m.time().active()).append(", recovery ").append(m.time().recover()).append(" ticks, range ")
                    .append(m.minRange()).append('-').append(m.maxRange()).append(m.ability().map(a -> ", " + a).orElse(""))
                    .append(m.signature() ? ", signature" : "");
        }
        String msg = sb.toString();
        c.getSource().sendSuccess(() -> Component.literal(msg), false);
        return k.moves().moves().size();
    }

    private static int bestiary(CommandContext<CommandSourceStack> c) {
        int[] counts = new int[dev.awaken.mobs.def.Tier.values().length];
        for (MobDef d : Bestiary.all()) counts[d.tier.ordinal()]++;
        StringBuilder sb = new StringBuilder("Bestiary: ").append(Bestiary.all().size()).append(" creatures");
        for (var t : dev.awaken.mobs.def.Tier.values()) sb.append(" · ").append(t.name().toLowerCase(Locale.ROOT)).append(' ').append(counts[t.ordinal()]);
        String msg = sb.toString();
        c.getSource().sendSuccess(() -> Component.literal(msg), false);
        return Bestiary.all().size();
    }
}
