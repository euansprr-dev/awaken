package dev.awaken.bells;

import dev.awaken.bigday.BigDays;
import dev.awaken.hearth.HearthRecord;
import dev.awaken.hearth.HearthService;
import dev.awaken.kin.KinHome;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The handbell's peals (SPEC §43.7). A plain ring does nothing but ring (and out in the wild, sends the land's beasts
 * running for a minute). A peal is a power three bells make together: Homeward (five seconds standing still, two with a
 * Homeward bell hung, then you're home; every five minutes, overworld only), Gather (your beasts come to the tower and
 * wait a minute) and Stillness (a raid's creatures near you slow to a crawl for ten seconds, once a raid night).
 */
final class Peals {
    private Peals() {}

    static final int RING_COOLDOWN = 40, SCARE_REACH = 24, SCARE_TICKS = 20 * 60;
    static final int HOMEWARD_WAIT = 100, HOMEWARD_FAST = 40, HOMEWARD_AGAIN = 20 * 60 * 5, GATHER_TICKS = 20 * 60;

    /** A Homeward being rung: where the player stood and when it takes them. Not saved: a restart cancels it. */
    private record Pending(Vec3 from, long at, long started) {}

    private static final Map<UUID, Pending> HOMEWARD = new HashMap<>();

    /** The peals this player can ring now, in order, after the plain ring (""). */
    static List<String> ringable(ServerPlayer p) {
        BellBook b = Bells.book(p);
        List<String> out = new ArrayList<>();
        out.add("");
        for (BellCatalog.Peal pl : BellCatalog.PEALS) if (pl.complete(b::has)) out.add(pl.id());
        return out;
    }

    /** Sneak-use: the next peal. */
    static void cycle(ServerPlayer p) {
        List<String> can = ringable(p);
        BellBook b = Bells.book(p);
        int i = Math.max(0, can.indexOf(b.peal));
        b.peal = can.get((i + 1) % can.size());
        Bells.save(p, b);
        BellCatalog.Peal pl = BellCatalog.peal(b.peal);
        if (can.size() == 1) p.sendOverlayMessage(Component.literal("A plain ring. Your bells don't make a peal yet."));
        else p.sendOverlayMessage(Component.literal(pl == null ? "A plain ring" : "The peal " + pl.name() + ": " + pl.words()));
        p.level().playSound(null, p.blockPosition(), BellContent.HANDBELL_RING.get(), SoundSource.PLAYERS, 0.4F, 1.5F);
    }

    static void ring(ServerLevel level, ServerPlayer p, ItemStack stack) {
        if (p.getCooldowns().isOnCooldown(stack)) return;
        p.getCooldowns().addCooldown(stack, RING_COOLDOWN);
        level.playSound(null, p.blockPosition(), BellContent.HANDBELL_RING.get(), SoundSource.PLAYERS, 1.5F, 1F);
        Optional<HearthRecord> inside = level.dimension() == Level.OVERWORLD
                ? HearthService.data(level.getServer()).containing(p.blockPosition(), HearthService.OVERWORLD) : Optional.empty();
        if (inside.isEmpty()) KinHome.scare(level, p.blockPosition(), SCARE_REACH, SCARE_TICKS);
        BellBook b = Bells.book(p);
        BellCatalog.Peal pl = BellCatalog.peal(b.peal);
        if (pl == null) return;
        if (!pl.complete(b::has)) {
            b.peal = "";
            Bells.save(p, b);
            return;
        }
        switch (pl.id()) {
            case "homeward" -> homeward(level, p, b);
            case "gather" -> gather(level, p);
            case "stillness" -> stillness(level, p);
            default -> {}
        }
    }

    // ---- Homeward ---------------------------------------------------------------------------------------------------

    private static void homeward(ServerLevel level, ServerPlayer p, BellBook b) {
        Optional<HearthRecord> h = HearthService.data(level.getServer()).home(p.getUUID());
        if (h.isEmpty()) {
            p.sendOverlayMessage(Component.literal("Homeward needs a home to take you to."));
            return;
        }
        if (level.dimension() != Level.OVERWORLD) {
            p.sendOverlayMessage(Component.literal("Homeward only carries you across this world, not out of another."));
            return;
        }
        long now = level.getGameTime();
        if (b.homewardAt > now) {
            p.sendOverlayMessage(Component.literal("Homeward can ring again in " + secs(b.homewardAt - now) + "."));
            return;
        }
        int wait = Bells.voice(level, h.get().pos, BellCatalog.Voice.HOMEWARD) > 0 ? HOMEWARD_FAST : HOMEWARD_WAIT;
        HOMEWARD.put(p.getUUID(), new Pending(p.position(), now + wait, now));
        p.sendOverlayMessage(Component.literal("Homeward. Stand still."));
    }

    private static String secs(long ticks) {
        long s = (ticks + 19) / 20;
        return s >= 60 ? (s + 59) / 60 + (s >= 120 ? " minutes" : " minute") : s + " seconds";
    }

    static void tick(MinecraftServer server) {
        if (HOMEWARD.isEmpty()) return;
        HOMEWARD.entrySet().removeIf(e -> {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null || !p.isAlive() || !(p.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return true;
            Pending w = e.getValue();
            long now = level.getGameTime();
            if (p.position().distanceToSqr(w.from()) > 0.6 * 0.6 || p.hurtTime > 0) {
                p.sendOverlayMessage(Component.literal("Homeward broke off. Stand still and ring it again."));
                return true;
            }
            // a ring of light closes round the player as it gathers
            if ((now - w.started()) % 4 == 0) {
                double f = 1.0 - (double) (now - w.started()) / Math.max(1, w.at() - w.started());
                double r = 0.4 + 1.2 * f;
                for (int k = 0; k < 10; k++) {
                    double a = k * Math.PI / 5 + now * 0.15;
                    level.sendParticles(ParticleTypes.END_ROD, p.getX() + Math.cos(a) * r, p.getY() + 0.1 + (1 - f) * 1.6, p.getZ() + Math.sin(a) * r,
                            1, 0, 0, 0, 0);
                }
            }
            if (now < w.at()) return false;
            Optional<HearthRecord> h = HearthService.data(server).home(p.getUUID());
            if (h.isEmpty()) return true;
            BlockPos to = standing(level, h.get().pos);
            level.playSound(null, p.blockPosition(), BellContent.TOLL.get(), SoundSource.PLAYERS, 1F, 1.6F);
            p.teleportTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5);
            p.resetFallDistance();
            level.playSound(null, to, BellContent.TOLL.get(), SoundSource.PLAYERS, 1.5F, 1.2F);
            level.sendParticles(ParticleTypes.END_ROD, to.getX() + 0.5, to.getY() + 1, to.getZ() + 0.5, 30, 0.4, 0.8, 0.4, 0.02);
            BellBook b = Bells.book(p);
            b.homewardAt = now + HOMEWARD_AGAIN;
            Bells.save(p, b);
            return true;
        });
    }

    /** Somewhere to stand by the Anchor: open feet and head on something solid, nearest first; else the ground above it. */
    static BlockPos standing(ServerLevel level, BlockPos anchor) {
        for (int r = 1; r <= 4; r++)
            for (int dy = -1; dy <= 3; dy++)
                for (int dx = -r; dx <= r; dx++)
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                        BlockPos q = anchor.offset(dx, dy, dz);
                        if (level.getBlockState(q).getCollisionShape(level, q).isEmpty() && level.getBlockState(q.above()).getCollisionShape(level, q.above()).isEmpty()
                                && level.getBlockState(q.below()).isFaceSturdy(level, q.below(), net.minecraft.core.Direction.UP)
                                && level.getFluidState(q).isEmpty())
                            return q;
                    }
        return level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, anchor.offset(1, 0, 1));
    }

    // ---- Gather and Stillness ---------------------------------------------------------------------------------------

    private static void gather(ServerLevel level, ServerPlayer p) {
        Optional<HearthRecord> h = HearthService.data(level.getServer()).home(p.getUUID());
        if (h.isEmpty() || level.dimension() != Level.OVERWORLD) {
            p.sendOverlayMessage(Component.literal("Gather calls the beasts that live with you. Ring it at home."));
            return;
        }
        BellWorld.Home home = BellWorld.get(level.getServer()).peek(h.get().pos);
        @Nullable BlockPos frame = home == null ? null : home.frame;
        BlockPos tower = frame != null && level.getBlockState(frame).is(BellContent.BELL_FRAME.get()) ? frame : h.get().pos;
        int n = KinHome.gather(level, h.get().pos, tower, GATHER_TICKS);
        p.sendOverlayMessage(Component.literal(n == 0 ? "Nobody lives with you yet to come." : n == 1 ? "Your beast is coming to the yard."
                : "Your " + n + " beasts are coming to the yard."));
    }

    private static void stillness(ServerLevel level, ServerPlayer p) {
        Optional<HearthRecord> h = HearthService.data(level.getServer()).home(p.getUUID());
        if (h.isEmpty() || level.dimension() != Level.OVERWORLD || !BigDays.still(level, h.get().pos, p))
            p.sendOverlayMessage(Component.literal("Stillness is for a raid at your walls, once a night. There's nothing to still."));
        else p.sendOverlayMessage(Component.literal("The raid slows round you."));
    }

    static void forget(UUID id) {
        HOMEWARD.remove(id);
    }
}
