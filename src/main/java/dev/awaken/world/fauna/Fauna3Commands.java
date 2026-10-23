package dev.awaken.world.fauna;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.awaken.core.AwakenCommands;
import dev.awaken.core.Forces;
import dev.awaken.core.api.MobRole;
import dev.awaken.mobs.Mobs;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.mobs.def.MobDef;
import dev.awaken.mobs.entity.AwakenMob;
import dev.awaken.mobs.entity.Fauna3Kinds;
import dev.awaken.mobs.entity.WaterFauna;
import dev.awaken.mobs.entity.WildMount;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/**
 * P3's commands under {@code /awaken fauna}: {@code mount} (your mount and where it is) and, for admins and the autopilot,
 * {@code mount tame <kind> [bridled]}, {@code mount ride}, {@code mount drive <ticks> [jump at]}, {@code mount whistle
 * [home]}; {@code tank [deep]} (a glass tank of sea water in front of you with the Tidewater in it) and {@code grotto
 * [beast]} (a dark stone cave with the Hollows in it). Both builders are also what the GameTests stand on.
 */
final class Fauna3Commands {
    private Fauna3Commands() {}

    static LiteralArgumentBuilder<CommandSourceStack> mount() {
        var m = Commands.literal("mount").executes(Fauna3Commands::status);
        m.then(Commands.literal("tame").requires(AwakenCommands.admin())
                .then(Commands.argument("kind", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(Fauna3Kinds.MOUNTS.keySet(), b))
                        .executes(c -> tame(c, false))
                        .then(Commands.literal("bridled").executes(c -> tame(c, true)))));
        m.then(Commands.literal("ride").requires(AwakenCommands.admin()).executes(Fauna3Commands::ride));
        m.then(Commands.literal("drive").requires(AwakenCommands.admin())
                .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 1200)).executes(c -> drive(c, -1))
                        .then(Commands.argument("jump", IntegerArgumentType.integer(0, 1200)).executes(c -> drive(c, IntegerArgumentType.getInteger(c, "jump"))))));
        m.then(Commands.literal("whistle").requires(AwakenCommands.admin()).executes(c -> whistle(c, false))
                .then(Commands.literal("home").executes(c -> whistle(c, true))));
        return m;
    }

    static LiteralArgumentBuilder<CommandSourceStack> tank() {
        return Commands.literal("tank").requires(AwakenCommands.admin()).executes(c -> tank(c, false))
                .then(Commands.literal("deep").executes(c -> tank(c, true)));
    }

    static LiteralArgumentBuilder<CommandSourceStack> grotto() {
        return Commands.literal("grotto").requires(AwakenCommands.admin()).executes(c -> grotto(c, false))
                .then(Commands.literal("beast").executes(c -> grotto(c, true)));
    }

    // ---- mounts ----------------------------------------------------------------------------------------------------------

    private static int status(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        MountBond b = sp.getData(Mounts.BOND.get());
        String msg;
        if (!b.has()) {
            msg = "No mount yet. From level " + Mounts.MIN_LEVEL + ", sneak and feed a Frost Stag (sweet berries), a Dune Ray (a cactus flower) or a Tidesteed (kelp).";
        } else {
            var e = sp.level().getEntity(b.mount().get());
            String state = e instanceof WildMount m ? (m.bridled() ? "bridled" : "trusts you") + (m.resting() ? ", resting" : "") : "out of reach";
            msg = "Your mount: " + Mounts.name(b) + " (" + state + ") · " + Mounts.where(sp, e != null ? e.blockPosition() : b.last());
        }
        String out = msg;
        c.getSource().sendSuccess(() -> Component.literal(out), false);
        return b.has() ? 1 : 0;
    }

    private static int tame(CommandContext<CommandSourceStack> c, boolean bridled) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        String kind = StringArgumentType.getString(c, "kind");
        WildMount m = spawnMount(sp, kind);
        if (m == null) {
            c.getSource().sendFailure(Component.literal("Not a mount (" + Fauna3Kinds.MOUNTS.keySet() + "), or no room for it"));
            return 0;
        }
        m.tameTo(sp, bridled);
        c.getSource().sendSuccess(() -> Component.literal(m.def().name + " trusts you" + (bridled ? " and wears your bridle" : "")), true);
        return 1;
    }

    /** A mount of this kind in front of the player (a Tidesteed in the nearest water within 10 blocks). */
    static @Nullable WildMount spawnMount(ServerPlayer sp, String kind) {
        MobDef d = Bestiary.get(kind);
        if (d == null || Fauna3Kinds.mount(kind) == null) return null;
        ServerLevel level = sp.level();
        Vec3 look = sp.getLookAngle();
        Vec3 at = sp.position().add(look.x * 3, 0, look.z * 3);
        if (Fauna3Kinds.mount(kind).style() == Fauna3Kinds.Style.SWIM) {
            BlockPos w = WaterFauna.waterNear(level, sp.blockPosition(), 10);
            if (w != null) at = Vec3.atBottomCenterOf(w);
        }
        int lv = FaunaRules.contentLevel(level, BlockPos.containing(at), d.fauna);
        AwakenMob e = Mobs.spawn(level, d, at, lv, new MobRole(d.tier.role, lv, d.theme.id, Optional.empty(), ""), EnumSet.noneOf(Forces.Tag.class));
        return e instanceof WildMount m ? m : null;
    }

    private static int ride(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        MountBond b = sp.getData(Mounts.BOND.get());
        var e = b.has() ? sp.level().getEntity(b.mount().get()) : null;
        if (!(e instanceof WildMount m) || !m.bridled()) {
            c.getSource().sendFailure(Component.literal("No bridled mount near you"));
            return 0;
        }
        m.wake();
        if (m.distanceTo(sp) > 8) m.teleportTo(sp.getX(), sp.getY(), sp.getZ());
        sp.startRiding(m);
        return 1;
    }

    private static int drive(CommandContext<CommandSourceStack> c, int jump) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        if (!(sp.getVehicle() instanceof WildMount m)) {
            c.getSource().sendFailure(Component.literal("You are not riding a mount"));
            return 0;
        }
        m.drive(IntegerArgumentType.getInteger(c, "ticks"), jump);
        return 1;
    }

    private static int whistle(CommandContext<CommandSourceStack> c, boolean home) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        Mounts.Result r = Mounts.whistle(sp, home);
        c.getSource().sendSuccess(() -> Component.literal(r.line()), false);
        return r.ok() ? 1 : 0;
    }

    // ---- the tank and the grotto -----------------------------------------------------------------------------------------

    private static int tank(CommandContext<CommandSourceStack> c, boolean deep) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        ServerLevel level = sp.level();
        BlockPos origin = ahead(sp, deep ? 14 : 9);
        int hw = deep ? 10 : 7, h = deep ? 11 : 6, hd = deep ? 8 : 5;
        buildTank(level, origin, hw, h, hd);
        List<String> who = deep ? List.of("lantern_maw", "glimmerfin", "glimmerfin", "glimmerfin", "breathbell")
                : List.of("glimmerfin", "glimmerfin", "glimmerfin", "glimmerfin", "glimmerfin", "breathbell", "breathbell", "brook_otter", "tidesteed");
        int n = populate(level, origin.above(2), who, hw - 2, hd - 2, h - 3, "awaken_tank");
        c.getSource().sendSuccess(() -> Component.literal("A tank of sea water with " + n + " creatures (tagged awaken_tank)"), false);
        return n;
    }

    /** A glass box of water: floor of sand and gravel at {@code origin}, inside {@code hw} x {@code hd} half-sizes, {@code h} tall. */
    static void buildTank(ServerLevel level, BlockPos origin, int hw, int h, int hd) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = -hw - 1; x <= hw + 1; x++) {
            for (int z = -hd - 1; z <= hd + 1; z++) {
                for (int y = 0; y <= h + 1; y++) {
                    m.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    boolean wall = Math.abs(x) == hw + 1 || Math.abs(z) == hd + 1;
                    BlockState st = y == 0 ? ((x + z) % 3 == 0 ? Blocks.GRAVEL : Blocks.SAND).defaultBlockState()
                            : y == h + 1 ? (wall ? Blocks.GLASS.defaultBlockState() : Blocks.AIR.defaultBlockState())
                            : wall ? Blocks.GLASS.defaultBlockState() : Blocks.WATER.defaultBlockState();
                    level.setBlock(m, st, Block.UPDATE_CLIENTS);
                }
            }
        }
        for (int k = 0; k < (hw * hd) / 4; k++) {
            int x = (k * 37) % (hw * 2 - 1) - hw + 1, z = (k * 23) % (hd * 2 - 1) - hd + 1;
            m.set(origin.getX() + x, origin.getY() + 1, origin.getZ() + z);
            level.setBlock(m, (k % 3 == 0 ? Blocks.TALL_SEAGRASS : Blocks.SEAGRASS).defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private static int grotto(CommandContext<CommandSourceStack> c, boolean beast) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        ServerLevel level = sp.level();
        BlockPos origin = ahead(sp, beast ? 12 : 8);
        int hw = beast ? 9 : 6, h = beast ? 7 : 5, hd = beast ? 8 : 5;
        buildGrotto(level, origin, hw, h, hd);
        List<String> who = beast ? List.of("thousandfoot") : List.of("chime_cricket", "chime_cricket", "wickling", "wickling", "drip_lurker", "hushling", "hushling");
        int n = populate(level, origin.above(1), who, hw - 2, hd - 2, 0, "awaken_grotto");
        c.getSource().sendSuccess(() -> Component.literal("A dark grotto with " + n + " creatures (tagged awaken_grotto)"), false);
        return n;
    }

    /** A sealed stone room (dark: no sky, no light) with dripstone, cave vines and a seam of ore at {@code origin}. */
    static void buildGrotto(ServerLevel level, BlockPos origin, int hw, int h, int hd) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = -hw - 1; x <= hw + 1; x++) {
            for (int z = -hd - 1; z <= hd + 1; z++) {
                for (int y = -1; y <= h + 1; y++) {
                    m.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    boolean shell = y <= 0 || y == h + 1 || Math.abs(x) == hw + 1 || Math.abs(z) == hd + 1;
                    BlockState st = !shell ? Blocks.AIR.defaultBlockState()
                            : y == 0 ? ((x * 3 + z) % 4 == 0 ? Blocks.MOSS_BLOCK : (x + z) % 3 == 0 ? Blocks.DRIPSTONE_BLOCK : Blocks.STONE).defaultBlockState()
                            : (x + y + z) % 5 == 0 ? Blocks.DRIPSTONE_BLOCK.defaultBlockState() : Blocks.TUFF.defaultBlockState();
                    level.setBlock(m, st, Block.UPDATE_CLIENTS);
                }
            }
        }
        // ore for the crickets to find, cave vines for the wicklings, a few dripstone fangs
        level.setBlock(origin.offset(hw - 1, 0, hd - 1), Blocks.DIAMOND_ORE.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(origin.offset(-hw + 1, 0, hd - 1), Blocks.IRON_ORE.defaultBlockState(), Block.UPDATE_CLIENTS);
        for (int k = 0; k < 3; k++) {
            BlockPos top = origin.offset(-2 + k * 2, h, -hd + 2);
            level.setBlock(top, Blocks.CAVE_VINES_PLANT.defaultBlockState().setValue(CaveVines.BERRIES, false), Block.UPDATE_CLIENTS);
            level.setBlock(top.below(), Blocks.CAVE_VINES.defaultBlockState().setValue(CaveVines.BERRIES, false), Block.UPDATE_CLIENTS);
        }
    }

    /** Spawns these creatures inside a box (fliers and swimmers spread through its height); tagged for clearing. */
    static int populate(ServerLevel level, BlockPos base, List<String> ids, int hw, int hd, int hgt, String tag) {
        int n = 0;
        for (int i = 0; i < ids.size(); i++) {
            MobDef d = Bestiary.get(ids.get(i));
            if (d == null) continue;
            double x = base.getX() + 0.5 + Mth.clamp((i * 7) % (hw * 2 + 1) - hw, -hw, hw);
            double z = base.getZ() + 0.5 + Mth.clamp((i * 5) % (hd * 2 + 1) - hd, -hd, hd);
            double y = base.getY() + (hgt > 0 ? (i * 3) % (hgt + 1) : 0);
            AwakenMob e = Mobs.spawn(level, d, new Vec3(x, y, z), 1, new MobRole(d.tier.role, 1, d.theme.id, Optional.empty(), ""), EnumSet.noneOf(Forces.Tag.class));
            if (e == null) continue;
            e.setPersistenceRequired();
            e.addTag(tag); // /kill @e[tag=...] clears them
            n++;
        }
        return n;
    }

    private static BlockPos ahead(ServerPlayer sp, int blocks) {
        Vec3 look = sp.getLookAngle();
        return BlockPos.containing(sp.getX() + look.x * blocks, sp.getY() - 1, sp.getZ() + look.z * blocks);
    }

    static List<String> kinds() {
        return new ArrayList<>(Fauna3Kinds.MOUNTS.keySet());
    }
}
