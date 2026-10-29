package dev.awaken.kin;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.awaken.core.AwakenCommands;
import dev.awaken.hearth.Hearth;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Optional;

/**
 * Admin tools for the kin: {@code /awaken admin kin summon <kind> | home <kind> | meet <kind> | watch <kind> <lines> |
 * points <kind> <n> | trust | lesson | site | page}. {@code home} brings one home to the player's Hearth at once (for
 * tours), {@code lesson} sends the first fire's Lampwing now, {@code site} fills the nearest wild site.
 */
final class KinCommands {
    private KinCommands() {}

    static void register() {
        AwakenCommands.add((root, ctx) -> {
            var kind = Commands.argument("kind", StringArgumentType.word())
                    .suggests((c, b) -> SharedSuggestionProvider.suggest(KinKind.livingNow().stream().map(KinKind::id), b));
            root.then(Commands.literal("admin").requires(AwakenCommands.admin()).then(Commands.literal("kin")
                    .then(Commands.literal("summon").then(kind.executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        KinKind k = KinKind.of(StringArgumentType.getString(c, "kind"));
                        BlockPos at = KinHelp.ground(p.level(), p.getBlockX() + 4, p.getBlockZ());
                        KinBeast b = KinHome.spawn(p.level(), k, k == KinKind.LAMPWING ? at.above(2) : at, EntitySpawnReason.COMMAND);
                        return b == null ? 0 : 1;
                    })))
                    .then(Commands.literal("home").then(Commands.argument("kind", StringArgumentType.word())
                            .suggests((c, b) -> SharedSuggestionProvider.suggest(KinKind.livingNow().stream().map(KinKind::id), b)).executes(c -> {
                                ServerPlayer p = c.getSource().getPlayerOrException();
                                return home(p, KinKind.of(StringArgumentType.getString(c, "kind"))) ? 1 : 0;
                            })))
                    .then(Commands.literal("meet").then(Commands.argument("kind", StringArgumentType.word())
                            .suggests((c, b) -> SharedSuggestionProvider.suggest(KinKind.livingNow().stream().map(KinKind::id), b)).executes(c -> {
                                ServerPlayer p = c.getSource().getPlayerOrException();
                                KinKind k = KinKind.of(StringArgumentType.getString(c, "kind"));
                                Kinship ks = Kinship.of(p);
                                Kinship.Kind s = ks.kind(k);
                                s.met = s.heard = true;
                                s.metDay = p.level().getOverworldClockTime() / 24000L;
                                s.metX = p.getBlockX();
                                s.metZ = p.getBlockZ();
                                Kinship.save(p, ks);
                                return 1;
                            })))
                    .then(Commands.literal("watch").then(Commands.argument("kind", StringArgumentType.word())
                            .suggests((c, b) -> SharedSuggestionProvider.suggest(KinKind.livingNow().stream().map(KinKind::id), b))
                            .then(Commands.argument("lines", IntegerArgumentType.integer(0, 3)).executes(c -> {
                                ServerPlayer p = c.getSource().getPlayerOrException();
                                Kinship ks = Kinship.of(p);
                                ks.kind(KinKind.of(StringArgumentType.getString(c, "kind"))).watched = IntegerArgumentType.getInteger(c, "lines");
                                Kinship.save(p, ks);
                                return 1;
                            }))))
                    .then(Commands.literal("points").then(Commands.argument("kind", StringArgumentType.word())
                            .suggests((c, b) -> SharedSuggestionProvider.suggest(KinKind.livingNow().stream().map(KinKind::id), b))
                            .then(Commands.argument("n", IntegerArgumentType.integer(0, 1000)).executes(c -> {
                                ServerPlayer p = c.getSource().getPlayerOrException();
                                Kinship ks = Kinship.of(p);
                                ks.kind(KinKind.of(StringArgumentType.getString(c, "kind"))).points = IntegerArgumentType.getInteger(c, "n");
                                Kinship.save(p, ks);
                                return 1;
                            }))))
                    .then(Commands.literal("trust").executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        List<KinBeast> near = p.level().getEntitiesOfClass(KinBeast.class, new AABB(p.blockPosition()).inflate(16), b -> b.home == null);
                        if (near.isEmpty()) return 0;
                        KinBeast b = near.getFirst();
                        b.helps.put(p.getUUID(), 3);
                        b.trusted.add(p.getUUID());
                        KinHome.offer(b, p);
                        return 1;
                    }))
                    .then(Commands.literal("lesson").executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        Kinship ks = Kinship.of(p);
                        ks.lesson = 0;
                        Kinship.save(p, ks);
                        p.level().getServer().getCommands().performPrefixedCommand(p.level().getServer().createCommandSourceStack().withLevel(p.level()).withSuppressedOutput(), "time set 14000");
                        KinHome.lessonTick(p);
                        return 1;
                    }))
                    .then(Commands.literal("site").executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        ServerLevel level = p.level();
                        int cx = p.getBlockX() >> KinSpawner.CELL_SHIFT, cz = p.getBlockZ() >> KinSpawner.CELL_SHIFT;
                        for (int r = 0; r <= 2; r++)
                            for (int dx = -r; dx <= r; dx++)
                                for (int dz = -r; dz <= r; dz++) {
                                    KinSpawner.Site s = KinSpawner.site(level, cx + dx, cz + dz);
                                    if (s == null || !level.isLoaded(s.at())) continue;
                                    int n = KinSpawner.fill(level, s).size();
                                    c.getSource().sendSuccess(() -> Component.literal(n + " " + s.kind().id() + " at " + s.at().toShortString()), false);
                                    return n;
                                }
                        return 0;
                    }))
                    .then(Commands.literal("page").executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        KinNet.Page page = KinPage.build(p);
                        for (var row : page.home()) c.getSource().sendSuccess(() -> Component.literal("home: " + String.join(" | ", row)), false);
                        for (var row : page.beasts()) c.getSource().sendSuccess(() -> Component.literal("beast: " + String.join(" | ", row)), false);
                        for (var row : page.kin()) c.getSource().sendSuccess(() -> Component.literal("kin: " + String.join(" | ", row)), false);
                        KinNet.send(p);
                        return 1;
                    }))));
        });
    }

    /** One of a kind brought straight home to the player's Hearth (its nook, settled), as if it had followed them. */
    static boolean home(ServerPlayer p, KinKind k) {
        Optional<Hearth.HearthInfo> h = Hearth.home(p);
        if (h.isEmpty()) return false;
        ServerLevel level = p.level();
        BlockPos nook = KinHome.nookFor(level, h.get(), k);
        if (nook == null) nook = h.get().anchor().offset(3, 1, 3);
        KinBeast b = KinHome.spawn(level, k, nook, EntitySpawnReason.COMMAND);
        if (b == null) return false;
        Kinship.of(p).kind(k).met = true;
        KinHome.settle(level, b, h.get().anchor(), nook, p);
        return true;
    }
}
