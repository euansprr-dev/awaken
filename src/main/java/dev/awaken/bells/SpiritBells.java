package dev.awaken.bells;

import dev.awaken.core.api.Goals.Goal;
import dev.awaken.core.api.Goals.Step;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Veil's bells kept by spirits (SPEC §43.16 stage 5). The Spirit Bell comes from the first spirit you speak to, as
 * before. Each of the other five is held by a spirit you meet in spirit: the next spirit whose whole story you hear,
 * while one of the five waits for your rank, takes one to hold for you and says what it stayed for. When that's done
 * where it stands (a lantern lit, flowers planted, first light, a candle on a grave, and for the Veil Bell the other four
 * let go), it lets the bell go into your hands, whether you're there in spirit or in body.
 */
final class SpiritBells {
    private SpiritBells() {}

    /** The five spirits hold, in the order they're taken up. */
    static final List<String> HELD = List.of("ghost", "whisper", "wake", "mourner", "veil");
    /** How near the spot an errand is looked for, and the spot's own reach (a lantern within 3 of where it stands). */
    static final int NEAR = 16, REACH = 3, DAWN_NEAR = 8;

    /** A spirit finished telling its story (veil.Spirits, through BellHooks): it may take one of the five to hold for you. */
    static void told(ServerPlayer p, String spirit, String name, BlockPos at) {
        told(p, spirit, name, at, Progression.rank(p));
    }

    static void told(ServerPlayer p, String spirit, String name, BlockPos at, Rank rank) {
        BellWorld w = BellWorld.get(p.level().getServer());
        Map<Integer, BellWorld.Keeper> mine = w.keepers.computeIfAbsent(p.getUUID(), k -> new HashMap<>());
        for (Map.Entry<Integer, BellWorld.Keeper> e : mine.entrySet()) {
            BellWorld.Keeper k = e.getValue();
            if (!k.spirit.equals(spirit) || k.pos.distSqr(at) > 8 * 8) continue;
            if (!k.done) ask(p, BellCatalog.of(e.getKey()), k);
            return;
        }
        for (String id : HELD) {
            BellCatalog.Def d = BellCatalog.of(id);
            if (Bells.has(p, id) || mine.containsKey(d.no()) || rank.ordinal() < d.rank().ordinal()) continue;
            if (id.equals("veil") && !othersFree(p, mine)) continue;
            BellWorld.Keeper k = new BellWorld.Keeper(spirit, name, at.immutable());
            // a spirit doesn't stay for what's already there: a lantern lit beside it holds the next bell, not this one
            if (!id.equals("veil") && p.level() instanceof ServerLevel level && done(level, id, k, p, 0, mine)) continue;
            mine.put(d.no(), k);
            w.setDirty();
            ask(p, d, k);
            dev.awaken.Awaken.LOG.info("[bells] {} holds {} for {} at {}", name, d.id(), p.getName().getString(), at.toShortString());
            return;
        }
    }

    /** The other four were let go (or are held already). */
    private static boolean othersFree(ServerPlayer p, Map<Integer, BellWorld.Keeper> mine) {
        for (String id : HELD) {
            if (id.equals("veil") || Bells.has(p, id)) continue;
            return false;
        }
        return true;
    }

    /** What the spirit stayed for, in its own words. */
    static String errand(String id) {
        return switch (id) {
            case "ghost" -> "I've been holding a bell since the night the gate came. My lantern went out when I did, and I've stood here in the dark ever since. Light a lantern where I'm standing and I'll let the bell go.";
            case "whisper" -> "Nobody's spoken to me in eleven years but you. There used to be flowers here. Plant some where I'm standing, and the bell I'm holding is yours.";
            case "wake" -> "I stayed to see the sun come up one more time, and I never have. Be here with me at first light, and I'll let the bell go.";
            case "mourner" -> "She's buried right where I'm standing. Nobody's lit a candle for her since the gate came. Light one on her grave and I'll go, and leave you the bell.";
            default -> "I stayed until the others could go. When the four who hold bells have let them go, come back to me and I'll go after them.";
        };
    }

    private static void ask(ServerPlayer p, BellCatalog.Def d, BellWorld.Keeper k) {
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(k.name)).body(Component.literal(errand(d.id())))
                .dialogue().ttl(20 * 14).priority(9).build());
    }

    /** Every 5 s per hunter with a spirit holding a bell: is what it stayed for done where it stands? */
    static void tick(MinecraftServer server) {
        if (server.getTickCount() % 100 != 40) return;
        BellWorld w = BellWorld.get(server);
        for (Map.Entry<UUID, Map<Integer, BellWorld.Keeper>> e : w.keepers.entrySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p != null) check(p, e.getValue());
        }
    }

    static void check(ServerPlayer p, Map<Integer, BellWorld.Keeper> mine) {
        if (!(p.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        for (Map.Entry<Integer, BellWorld.Keeper> e : new ArrayList<>(mine.entrySet())) {
            BellWorld.Keeper k = e.getValue();
            BellCatalog.Def d = BellCatalog.of(e.getKey());
            if (k.done || Bells.has(p, d.id())) continue;
            double dx = p.getX() - k.pos.getX() - 0.5, dz = p.getZ() - k.pos.getZ() - 0.5;
            if (dx * dx + dz * dz > NEAR * NEAR || !level.isLoaded(k.pos) || !done(level, d.id(), k, p, dx * dx + dz * dz, mine)) continue;
            k.done = true;
            BellWorld.get(level.getServer()).setDirty();
            level.playSound(null, k.pos, BellContent.note(d.no()), SoundSource.AMBIENT, 1.5F, 1.2F);
            Bells.say(p, "[" + k.name + " let the bell go. It came to you pale as frost, and cold in your hands.]");
            Bells.ring(p, d, "let go by " + k.name + ", who stayed for " + stayedFor(d.id()));
        }
    }

    private static String stayedFor(String id) {
        return switch (id) {
            case "ghost" -> "a lantern";
            case "whisper" -> "flowers";
            case "wake" -> "one more sunrise";
            case "mourner" -> "a candle on a grave";
            default -> "the others";
        };
    }

    /** Whether what the spirit holding bell {@code id} stayed for is done. */
    static boolean done(ServerLevel level, String id, BellWorld.Keeper k, ServerPlayer p, double flat2, Map<Integer, BellWorld.Keeper> mine) {
        return switch (id) {
            case "ghost" -> near(level, k.pos, s -> s.is(Blocks.LANTERN) || s.is(Blocks.SOUL_LANTERN));
            case "whisper" -> near(level, k.pos, s -> s.is(BlockTags.FLOWERS));
            case "mourner" -> near(level, k.pos, s -> s.is(BlockTags.CANDLES) && s.hasProperty(CandleBlock.LIT) && s.getValue(CandleBlock.LIT));
            case "wake" -> {
                long t = level.getOverworldClockTime() % 24000L;
                yield flat2 <= DAWN_NEAR * DAWN_NEAR && (t >= 22800 || t < 800);
            }
            default -> othersFree(p, mine);
        };
    }

    private static boolean near(ServerLevel level, BlockPos c, java.util.function.Predicate<BlockState> what) {
        for (BlockPos at : BlockPos.betweenClosed(c.offset(-REACH, -2, -REACH), c.offset(REACH, 3, REACH)))
            if (what.test(level.getBlockState(at))) return true;
        return false;
    }

    /** The Tasks page: each spirit still holding a bell for you, and what it stayed for. */
    static List<Step> steps(ServerPlayer p) {
        Map<Integer, BellWorld.Keeper> mine = BellWorld.get(p.level().getServer()).keepers.get(p.getUUID());
        if (mine == null) return List.of();
        List<Step> out = new ArrayList<>();
        for (Map.Entry<Integer, BellWorld.Keeper> e : mine.entrySet()) {
            BellWorld.Keeper k = e.getValue();
            BellCatalog.Def d = BellCatalog.of(e.getKey());
            if (k.done || Bells.has(p, d.id())) continue;
            String title = switch (d.id()) {
                case "ghost" -> "Light a lantern where " + k.name + " stands";
                case "whisper" -> "Plant flowers where " + k.name + " stands";
                case "wake" -> "Be where " + k.name + " stands at first light";
                case "mourner" -> "Light a candle on the grave " + k.name + " keeps";
                default -> othersFree(p, mine) ? "Go back to " + k.name : "Let the four spirits holding bells go, then go back to " + k.name;
            };
            out.add(Step.of("bells/spirit/" + d.no(), Goal.BELLS, title)
                    .why(k.name + " is holding " + d.name().replaceFirst("^The ", "the ") + " for you. You'll only see them in spirit, but what they stayed for can be done in body.")
                    .priority(35).at(k.pos).build());
        }
        return out;
    }
}
