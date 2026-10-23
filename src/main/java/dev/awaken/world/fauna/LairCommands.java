package dev.awaken.world.fauna;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Pair;
import dev.awaken.core.AwakenCommands;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.mobs.def.MobDef;
import dev.awaken.world.fauna.lair.LairStyle;
import dev.awaken.world.fauna.lair.LairWorldgen;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * {@code /awaken fauna lair ...} (admin): {@code list} (lairs the world knows), {@code goto <beast>} (the nearest lair
 * of that beast, found like {@code /locate}; or one built in front of you if none is near), {@code place <beast>}
 * (build one in front of you, for screenshots), {@code hear <beast>} (a rumour, as if you had met its family),
 * {@code why} (would the lair nearest you wake its beast, and if not why not) and {@code reset} (every lair waits for a
 * new beast again).
 */
final class LairCommands {
    private LairCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        var lair = Commands.literal("lair").requires(AwakenCommands.admin());
        lair.then(Commands.literal("list").executes(LairCommands::list));
        lair.then(Commands.literal("goto").then(beastArg().executes(c -> go(c, true))));
        lair.then(Commands.literal("place").then(beastArg().executes(c -> go(c, false))));
        lair.then(Commands.literal("hear").then(beastArg().executes(LairCommands::hear)));
        lair.then(Commands.literal("why").executes(LairCommands::why));
        lair.then(Commands.literal("reset").executes(c -> {
            LairRegistry.get(c.getSource().getServer()).resetFelled();
            c.getSource().sendSuccess(() -> Component.literal("Every lair waits for a new beast"), true);
            return 1;
        }));
        return lair;
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> beastArg() {
        return Commands.argument("beast", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(beasts(), b));
    }

    private static List<String> beasts() {
        List<String> out = new ArrayList<>();
        for (LairStyle s : LairStyle.values()) out.add(s.beast);
        return out;
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        LairRegistry reg = LairRegistry.get(c.getSource().getServer());
        long now = c.getSource().getServer().overworld().getGameTime();
        StringBuilder sb = new StringBuilder("Lairs known to the world: ").append(reg.all().size());
        for (LairRegistry.Entry e : reg.all()) {
            sb.append("\n ").append(e.beast()).append(" at ").append(e.den().toShortString());
            if (e.resident().isPresent()) sb.append(" (its beast is home)");
            else if (e.felledAt() > 0 && now - e.felledAt() < Lairs.RESPAWN) sb.append(" (quiet for ").append((Lairs.RESPAWN - (now - e.felledAt())) / 1200).append(" min)");
        }
        String msg = sb.toString();
        c.getSource().sendSuccess(() -> Component.literal(msg), false);
        return reg.all().size();
    }

    /** {@code goto}: the nearest real lair (or, failing that, a built one); {@code place}: always build one ahead. */
    private static int go(CommandContext<CommandSourceStack> c, boolean find) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        String beast = StringArgumentType.getString(c, "beast");
        LairStyle style = LairStyle.byBeast(beast);
        if (style == null) {
            c.getSource().sendFailure(Component.literal("No lair for " + beast + ". Lairs: " + beasts()));
            return 0;
        }
        ServerLevel level = sp.level().getServer().overworld();
        if (find) {
            Optional<Holder.Reference<Structure>> h = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(LairWorldgen.structure(beast));
            Pair<BlockPos, Holder<Structure>> found = h.isEmpty() ? null
                    : level.getChunkSource().getGenerator().findNearestMapStructure(level, HolderSet.direct(h.get()), sp.blockPosition(), 6, false);
            if (found != null) {
                BlockPos at = found.getFirst();
                level.getChunk(at.getX() >> 4, at.getZ() >> 4);
                LairRegistry.Entry e = LairRegistry.get(level.getServer()).nearest(beast, at, 160);
                BlockPos to = e != null ? e.den().south(14) : at.offset(8, 0, 8);
                level.getChunk(to.getX() >> 4, to.getZ() >> 4);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, to.getX(), to.getZ());
                sp.teleportTo(level, to.getX() + 0.5, y + 0.1, to.getZ() + 0.5, Set.<Relative>of(), 180.0F, 15.0F, true);
                String where = (e != null ? e.den() : at).toShortString();
                c.getSource().sendSuccess(() -> Component.literal("Went to " + style.title + " near " + where), false);
                return 1;
            }
            c.getSource().sendSuccess(() -> Component.literal("No " + beast + " lair within reach: building one ahead of you"), false);
        }
        float yaw = sp.getYRot() * Mth.DEG_TO_RAD;
        int x = Mth.floor(sp.getX() - Mth.sin(yaw) * 16), z = Mth.floor(sp.getZ() + Mth.cos(yaw) * 16);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        BlockPos den = Lairs.placeAt(level, style, new BlockPos(x, y, z), level.getRandom().nextLong());
        c.getSource().sendSuccess(() -> Component.literal("Built " + style.title + "; its den is at " + den.toShortString()), true);
        return 1;
    }

    private static int hear(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        MobDef d = Bestiary.get(StringArgumentType.getString(c, "beast"));
        if (d == null || LairStyle.byBeast(d.id) == null) {
            c.getSource().sendFailure(Component.literal("Lairs: " + beasts()));
            return 0;
        }
        BlockPos at = Lairs.heardNow(sp, d.id);
        String msg = at == null ? "Nothing new: you know one already, or there is none within " + Lairs.HEAR_RANGE + " blocks" : "Heard of a lair near " + at.toShortString();
        c.getSource().sendSuccess(() -> Component.literal(msg), false);
        return at == null ? 0 : 1;
    }

    private static int why(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        ServerLevel level = sp.level();
        LairRegistry reg = LairRegistry.get(level.getServer());
        LairRegistry.Entry e = reg.nearest(null, sp.blockPosition(), 512);
        if (e == null) {
            c.getSource().sendFailure(Component.literal("No lair known within 512 blocks"));
            return 0;
        }
        String v = Lairs.wake(level, reg, e, sp, false);
        String msg = e.beast() + "'s lair at " + e.den().toShortString() + ": " + (v == null ? "its beast would appear" : v);
        c.getSource().sendSuccess(() -> Component.literal(msg), false);
        return v == null ? 1 : 0;
    }
}
