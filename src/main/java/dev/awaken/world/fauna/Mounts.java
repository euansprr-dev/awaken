package dev.awaken.world.fauna;

import dev.awaken.Awaken;
import dev.awaken.core.DirectorLog;
import dev.awaken.core.Playtest;
import dev.awaken.core.api.HearthHooks;
import dev.awaken.core.api.NowHooks;
import dev.awaken.mobs.def.MobDef;
import dev.awaken.mobs.entity.Fauna3Kinds;
import dev.awaken.mobs.entity.FaunaMob;
import dev.awaken.mobs.entity.MountHooks;
import dev.awaken.mobs.entity.WaterFauna;
import dev.awaken.mobs.entity.WildMount;
import dev.awaken.progression.Profile;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Mounts (plan v2 section 4.4, levels 11-20): the world side of the trust ritual, the bridle and the whistle. From level
 * 11 a Hunter can win a Frost Stag's, a Dune Ray's or a Tidesteed's trust (sneak and feed it its favourite food a few
 * times); it then follows them, takes a Hunter's Bridle, can be ridden, never despawns, rests where it is told (and
 * heals at a Hearth) and answers its owner's whistle within {@value #RANGE} blocks, or the whistle says why not. The
 * player's side of the bond is the {@code awaken:fauna_mount} attachment ({@link MountBond}); the Now tracker points at
 * a wild mount nearby, at the bridle, and at a mount left far behind.
 */
public final class Mounts {
    private Mounts() {}

    /** Mounts trust Hunters from this level (the 11-20 band). */
    public static final int MIN_LEVEL = 11;
    /** The whistle reaches this far (blocks). */
    public static final int RANGE = 160;
    /** Ticks before the whistle can be blown again: after it was answered, and after it wasn't. */
    public static final int COOLDOWN = 200, FAIL_COOLDOWN = 40;
    /** A mount further than this comes the quick way (it appears beside you); nearer, it runs to you. */
    static final int RUN_TO = 20;

    public static final Supplier<AttachmentType<MountBond>> BOND = FaunaModule.ATTACHMENTS.register("fauna_mount",
            () -> AttachmentType.builder(() -> MountBond.EMPTY).serialize(MountBond.MAP_CODEC).copyOnDeath().build());

    static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Awaken.MODID);
    public static final DeferredHolder<SoundEvent, SoundEvent> WHISTLE_SOUND = SOUNDS.register("fauna.mount_whistle",
            () -> SoundEvent.createVariableRangeEvent(Awaken.id("fauna.mount_whistle")));

    static void init(IEventBus modBus) {
        SOUNDS.register(modBus);
        MountHooks.install(new Hooks());
        NowHooks.register(Mounts::suggest);
    }

    // ---- the mount layer's hooks --------------------------------------------------------------------------------------

    static final class Hooks implements MountHooks.Server {
        @Override
        public @Nullable String tameRefusal(ServerPlayer p, WildMount m) {
            Profile prof = Progression.profile(p);
            if (!prof.awakened()) return "It shies away from you.";
            if (prof.level() < MIN_LEVEL) return "It shies away. Mounts trust Hunters of level " + MIN_LEVEL + " (you are " + prof.level() + ").";
            return null;
        }

        @Override
        public void trust(WildMount m, ServerPlayer p, int trust, int need) {
            Progression.setCounter(p, "awaken:mount_met", 1);
            if (trust < need) p.sendOverlayMessage(Component.literal(m.def().name + " trusts you a little more (" + trust + " of " + need + ")").withColor(m.def().glow));
        }

        @Override
        public void tamed(WildMount m, ServerPlayer p) {
            Progression.count(p, "awaken:mounts_tamed");
            Progression.setCounter(p, "awaken:mount_met", 1);
            bond(p, m);
            Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Tamed · " + m.def().name).withColor(m.def().glow))
                    .body(Component.literal(m.bridled() ? "Bridled. Right-click to ride" : "It follows you. A Hunter's Bridle lets you ride it"))
                    .presentation(SignalWindow.Presentation.TOAST).ttl(20 * 8).build());
            Playtest.log(p, "mount", "tamed", m.def().id);
            DirectorLog.log(p, "fauna", "tamed a " + m.def().id);
        }

        @Override
        public void bridled(WildMount m, ServerPlayer p) {
            Progression.count(p, "awaken:mounts_bridled");
            bond(p, m);
            Fauna3Kinds.Kind k = m.kind();
            String how = k == null ? "" : switch (k.style()) {
                case GROUND -> "Right-click to ride; hold jump to leap";
                case GLIDE -> "Right-click to ride; jump soars, then it glides";
                case SWIM -> "Right-click to ride; it swims where you look";
            };
            Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Bridled · " + m.def().name).withColor(m.def().glow))
                    .body(Component.literal(how)).presentation(SignalWindow.Presentation.TOAST).ttl(20 * 8).build());
            DirectorLog.log(p, "fauna", "bridled a " + m.def().id);
        }

        @Override
        public void mounted(WildMount m, ServerPlayer p) {
            Progression.count(p, "awaken:mount_rides");
            bond(p, m);
        }

        @Override
        public void seen(WildMount m) {
            if (!(m.level() instanceof ServerLevel level) || m.owner() == null) return;
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(m.owner());
            if (p == null) return;
            MountBond b = p.getData(BOND.get());
            if (!b.has() && m.trusted()) bond(p, m);
            else if (b.is(m.getUUID())) p.setData(BOND.get(), b.seen(m.blockPosition(), level.dimension().identifier().toString(), m.resting()));
        }

        @Override
        public void gone(WildMount m, boolean died) {
            if (!(m.level() instanceof ServerLevel level) || m.owner() == null) return;
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(m.owner());
            if (p == null || !p.getData(BOND.get()).is(m.getUUID())) return;
            p.setData(BOND.get(), MountBond.EMPTY);
            if (died) {
                Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Your " + m.def().name + " has fallen").withColor(0xFF8A7A))
                        .body(Component.literal("Another can learn to trust you")).presentation(SignalWindow.Presentation.TOAST).ttl(20 * 8).build());
            }
        }
    }

    /** This mount answers this player's whistle now. */
    static void bond(ServerPlayer p, WildMount m) {
        p.setData(BOND.get(), MountBond.of(m.getUUID(), m.def().id, m.blockPosition(), m.level().dimension().identifier().toString(), m.resting()));
    }

    // ---- the whistle --------------------------------------------------------------------------------------------------

    /** What the whistle got: an answer and the line that says it. */
    public enum Answer { COMING, SENT_HOME, NONE, OTHER_WORLD, OUT_OF_EARSHOT, TOO_FAR, NO_ROOM, NO_HEARTH, HEARTH_FAR, RIDING }

    public record Result(Answer answer, String line) {
        public boolean ok() {
            return answer == Answer.COMING || answer == Answer.SENT_HOME;
        }
    }

    /** Blows the whistle: the mount comes (or, with {@code home}, goes to rest at the Hearth), or the line says why not. */
    public static Result whistle(ServerPlayer p, boolean home) {
        ServerLevel here = p.level();
        here.playSound(null, p.getX(), p.getY(), p.getZ(), WHISTLE_SOUND.get(), SoundSource.PLAYERS, 1.0F, home ? 0.85F : 1.0F);
        MountBond b = p.getData(BOND.get());
        if (!b.has()) return new Result(Answer.NONE, "No mount answers. Win one's trust first: sneak and feed a Frost Stag, Dune Ray or Tidesteed.");
        String name = name(b);
        UUID id = b.mount().get();
        Entity e = here.getEntity(id);
        if (e == null) {
            for (ServerLevel l : here.getServer().getAllLevels()) {
                if (l != here && l.getEntity(id) != null) return new Result(Answer.OTHER_WORLD, "Your " + name + " is in another world.");
            }
            if (!b.dimension().isEmpty() && !b.dimension().equals(here.dimension().identifier().toString())) {
                return new Result(Answer.OTHER_WORLD, "Your " + name + " is in another world.");
            }
            String where = where(p, b.last());
            if (b.resting() && HearthHooks.inside(here, b.last())) return new Result(Answer.OUT_OF_EARSHOT, "Your " + name + " rests at your Hearth · " + where + ".");
            return new Result(Answer.OUT_OF_EARSHOT, "Out of earshot. Your " + name + " was last seen " + where + ".");
        }
        if (!(e instanceof WildMount m) || !m.isAlive() || !m.ownedBy(p)) {
            p.setData(BOND.get(), MountBond.EMPTY);
            return new Result(Answer.NONE, "No mount answers. Win one's trust first: sneak and feed a Frost Stag, Dune Ray or Tidesteed.");
        }
        if (p.getVehicle() == m) return new Result(Answer.RIDING, "You are riding it.");
        double d = m.distanceTo(p);
        // a Stable at home (or Pell's Spurs) carries the whistle further: perk awaken:whistle_range, in blocks
        if (d > RANGE + Progression.perk(p, "awaken:whistle_range")) return new Result(Answer.TOO_FAR, "Too far to hear you: your " + name + " is " + where(p, m.blockPosition()) + ".");
        if (home) return sendHome(p, m, name);
        ServerLevel level = (ServerLevel) m.level();
        if (d > RUN_TO) {
            Vec3 spot = spotNear(level, m, p.blockPosition());
            if (spot == null) return new Result(Answer.NO_ROOM, "No room for your " + name + " here" + (swims(m) ? ": it needs water nearby." : "."));
            level.sendParticles(ParticleTypes.CLOUD, m.getX(), m.getY() + 0.5, m.getZ(), 10, 0.4, 0.4, 0.4, 0.02);
            m.teleportTo(spot.x, spot.y, spot.z);
            level.sendParticles(ParticleTypes.CLOUD, spot.x, spot.y + 0.5, spot.z, 10, 0.4, 0.4, 0.4, 0.02);
        }
        m.wake();
        m.getNavigation().moveTo(p, 1.3);
        SoundEvent call = dev.awaken.mobs.MobSounds.fauna(m.def().id, "ambient");
        if (call != null) level.playSound(null, m.getX(), m.getY(), m.getZ(), call, SoundSource.NEUTRAL, 1.0F, 1.0F);
        p.setData(BOND.get(), p.getData(BOND.get()).seen(m.blockPosition(), level.dimension().identifier().toString(), false));
        DirectorLog.log(p, "fauna", "whistled: " + m.def().id + " came from " + Math.round(d) + " blocks");
        return new Result(Answer.COMING, "Your " + name + " is coming.");
    }

    /** Sneak-whistle: it goes home to rest beside the Hearth (and heals there). */
    static Result sendHome(ServerPlayer p, WildMount m, String name) {
        Optional<BlockPos> hearth = HearthHooks.hearthOf(p);
        if (hearth.isEmpty()) return new Result(Answer.NO_HEARTH, "You have no Hearth yet. Sneak and use your " + name + " with an empty hand: it rests where it stands.");
        ServerLevel overworld = p.level().getServer().overworld();
        BlockPos h = hearth.get();
        if (m.level() != overworld || !overworld.isLoaded(h) || !overworld.isPositionEntityTicking(h)) {
            return new Result(Answer.HEARTH_FAR, "Your Hearth is too far for it to find alone. Ride it home, then sneak and use it with an empty hand.");
        }
        Vec3 spot = spotNear(overworld, m, h);
        if (spot == null) return new Result(Answer.NO_ROOM, "There is no room for your " + name + " at your Hearth.");
        m.teleportTo(spot.x, spot.y, spot.z);
        m.rest(BlockPos.containing(spot));
        p.setData(BOND.get(), p.getData(BOND.get()).seen(m.blockPosition(), overworld.dimension().identifier().toString(), true));
        return new Result(Answer.SENT_HOME, "Your " + name + " goes home to rest at your Hearth.");
    }

    static boolean swims(WildMount m) {
        Fauna3Kinds.Kind k = m.kind();
        return k != null && k.style() == Fauna3Kinds.Style.SWIM;
    }

    /** A spot near {@code at} where this mount fits: water for a swimmer, standing room for the rest (a glider hovers). */
    static @Nullable Vec3 spotNear(ServerLevel level, WildMount m, BlockPos at) {
        if (swims(m)) {
            BlockPos w = WaterFauna.waterNear(level, at, 8);
            if (w == null) return null;
            Vec3 v = Vec3.atBottomCenterOf(w);
            return fits(level, m, v) ? v : null;
        }
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        for (int r = 2; r <= 5; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    for (int dy = 2; dy >= -3; dy--) {
                        q.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz);
                        if (!level.getBlockState(q.below()).isFaceSturdy(level, q.below(), Direction.UP) || !level.getFluidState(q).isEmpty()) continue;
                        Vec3 v = Vec3.atBottomCenterOf(q);
                        if (m.def().flies()) v = v.add(0, 1.0, 0);
                        if (fits(level, m, v)) return v;
                    }
                }
            }
        }
        return null;
    }

    private static boolean fits(ServerLevel level, WildMount m, Vec3 at) {
        AABB box = m.getType().getSpawnAABB(at.x, at.y, at.z).deflate(1.0E-4);
        return level.noCollision(m, box);
    }

    static String name(MountBond b) {
        MobDef d = dev.awaken.mobs.def.Bestiary.get(b.kind());
        return d == null ? "mount" : d.name;
    }

    static String where(ServerPlayer p, BlockPos at) {
        double dx = at.getX() + 0.5 - p.getX(), dz = at.getZ() + 0.5 - p.getZ();
        return Lairs.distance(Math.sqrt(dx * dx + dz * dz)) + " " + FaunaNow.compass(dx, dz);
    }

    // ---- the Now tracker ----------------------------------------------------------------------------------------------

    /** A wild mount within reach (level 11+), the bridle for a trusting one, or a mount left far behind. */
    static List<NowHooks.Suggestion> suggest(ServerPlayer p) {
        Profile prof = Progression.profile(p);
        if (!prof.awakened() || p.level().dimension() != Level.OVERWORLD) return List.of();
        List<NowHooks.Suggestion> out = new ArrayList<>(2);
        MountBond b = p.getData(BOND.get());
        if (b.has()) {
            Entity e = p.level().getEntity(b.mount().get());
            if (e instanceof WildMount m && m.ownedBy(p)) {
                if (m.trusted() && !m.bridled()) {
                    out.add(NowHooks.Suggestion.at("fauna:mount/bridle", 44, "Bridle your " + m.def().name + " · Hunter's Bridle", m.blockPosition()));
                } else if (p.getVehicle() != m && m.distanceTo(p) > 48) {
                    out.add(NowHooks.Suggestion.at("fauna:mount/far", 18, "Your " + m.def().name + " · " + where(p, m.blockPosition()) + " · whistle", m.blockPosition()));
                }
            } else if (e == null && b.dimension().equals(p.level().dimension().identifier().toString()) && !b.resting()) {
                out.add(NowHooks.Suggestion.at("fauna:mount/far", 16, "Your " + name(b) + " · " + where(p, b.last()), b.last()));
            }
            return out;
        }
        if (prof.level() < MIN_LEVEL) return out;
        WildMount near = nearestWild(p, 24.0);
        if (near != null) {
            if (prof.counter("awaken:mount_met") == 0) Progression.setCounter(p, "awaken:mount_met", 1);
            Fauna3Kinds.Kind k = near.kind();
            String food = k == null ? "its food" : new net.minecraft.world.item.ItemStack(k.favouriteItem()).getHoverName().getString().toLowerCase(java.util.Locale.ROOT);
            out.add(NowHooks.Suggestion.at("fauna:mount/tame", 46, near.def().name + " " + where(p, near.blockPosition()) + " · sneak, feed " + food, near.blockPosition()));
        }
        return out;
    }

    /** The nearest untamed mount within {@code range} (from the census: no entity scan). */
    static @Nullable WildMount nearestWild(ServerPlayer p, double range) {
        WildMount best = null;
        double bd = range * range;
        for (FaunaMob f : FaunaCensus.in(p.level())) {
            if (!(f instanceof WildMount m) || m.kind() == null || m.trusted() || !m.isAlive()) continue;
            double d = m.distanceToSqr(p);
            if (d < bd) {
                bd = d;
                best = m;
            }
        }
        return best;
    }
}
