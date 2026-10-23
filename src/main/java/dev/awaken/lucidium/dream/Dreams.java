package dev.awaken.lucidium.dream;

import dev.awaken.Awaken;
import dev.awaken.core.AwakenNet;
import dev.awaken.core.DirectorLog;
import dev.awaken.core.PlayDays;
import dev.awaken.core.api.MobHooks;
import dev.awaken.core.api.MobRole;
import dev.awaken.lucidium.LucidiumContent;
import dev.awaken.lucidium.data.DreamRecord;
import dev.awaken.lucidium.data.DreamState;
import dev.awaken.lucidium.data.Dreamer;
import dev.awaken.lucidium.net.LucidiumNet;
import dev.awaken.lucidium.plan.DreamPlan;
import dev.awaken.lucidium.plan.DreamPlanner;
import dev.awaken.lucidium.plan.DreamSign;
import dev.awaken.lucidium.plan.Nightmares;
import dev.awaken.lucidium.world.DreamBlueprint;
import dev.awaken.lucidium.world.DreamChunkGenerator;
import dev.awaken.progression.Counters;
import dev.awaken.progression.Perks;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Stat;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import dev.awaken.core.Forces;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Entering, running and waking from dreams. The server is authoritative; the client only draws the HUD and the dream
 * shader. Every way out of a dream (time, death, falling, the bell, a command, logout, a crash) ends in
 * {@link #wake}, which puts the player back where they lay down and undoes everything the dream changed.
 */
public final class Dreams {
    private Dreams() {}

    public static final Identifier FX = Awaken.id("dream");
    public static final Identifier FX_LUCID = Awaken.id("dream_lucid");
    public static final String MANIFEST_WOKE = "awaken:latent/manifest_woke";
    public static final String DREAM_MANIFESTS = "awaken:dream_manifests";

    public enum Wake { TIME, DIED, FELL, BELL, COMMAND, LOGIN, LEFT, API }

    static final Map<Integer, ActiveDream> ACTIVE = new HashMap<>();
    private static final Map<UUID, LucidiumNet.DreamHud> LAST_HUD = new HashMap<>();
    private static final Map<UUID, Integer> LOOKING_UP = new HashMap<>();
    private static boolean teleporting;

    // ---- queries -----------------------------------------------------------------------------------------------------

    public static DreamState state(Player player) {
        return player.getData(LucidiumContent.DREAM);
    }

    public static Dreamer dreamer(Player player) {
        return player.getData(LucidiumContent.DREAMER);
    }

    public static boolean isDreaming(Player player) {
        return state(player).active;
    }

    public static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    public static @Nullable ActiveDream active(ServerPlayer player) {
        DreamState st = state(player);
        return st.active ? ACTIVE.get(st.cell) : null;
    }

    public static @Nullable ServerLevel dreamLevel(MinecraftServer server) {
        return server.getLevel(LucidiumContent.LUCIDIUM);
    }

    // ---- entering ----------------------------------------------------------------------------------------------------

    /** How the dream starts. {@code target} overrides the Lucidium level (GameTests run without it). */
    public record Entry(boolean fromSleep, @Nullable BlockPos bed, @Nullable ServerLevel target, boolean wbtb) {
        public static Entry awake() {
            return new Entry(false, null, null, false);
        }
    }

    /** Starts a new dream for {@code host} from a prepared plan. Returns the running dream, or null if it can't start. */
    public static @Nullable ActiveDream enter(ServerPlayer host, Planner.Prepared prepared, Entry entry) {
        MinecraftServer server = host.level().getServer();
        ServerLevel level = entry.target() != null ? entry.target() : dreamLevel(server);
        if (level == null || isDreaming(host)) return null;
        DreamChunkGenerator.register(prepared.blueprint());
        ActiveDream ad = new ActiveDream(prepared.blueprint(), level, host.getUUID(), now(server));
        ACTIVE.put(prepared.blueprint().cell, ad);
        if (!place(host, ad, entry, true)) {
            ACTIVE.remove(prepared.blueprint().cell);
            DreamChunkGenerator.unregister(prepared.blueprint().cell);
            return null;
        }
        return ad;
    }

    /** A sleeper sharing someone else's dream (same room). */
    public static boolean join(ServerPlayer guest, ActiveDream ad, Entry entry) {
        if (isDreaming(guest)) return false;
        return place(guest, ad, entry, false);
    }

    private static boolean place(ServerPlayer sp, ActiveDream ad, Entry entry, boolean host) {
        MinecraftServer server = sp.level().getServer();
        long now = now(server);
        DreamState st = state(sp);
        Dreamer d = dreamer(sp);
        long day = PlayDays.current(server);
        DreamBlueprint bp = ad.bp;

        st.reset();
        st.active = true;
        st.dreamId = bp.plan.seed() ^ (now << 1) ^ sp.getUUID().getLeastSignificantBits();
        st.cell = bp.cell;
        st.host = ad.host.toString();
        st.startTick = now;
        st.endTick = ad.end;
        st.returnDim = sp.level().dimension().identifier().toString();
        st.rx = sp.getX();
        st.ry = sp.getY();
        st.rz = sp.getZ();
        st.ryaw = sp.getYRot();
        st.rpitch = sp.getXRot();
        st.bed = entry.bed();
        st.fromSleep = entry.fromSleep();
        boolean intention = !d.intention(day).isEmpty();
        st.lucidity = Lucidity.start(intention, entry.wbtb(), d.recall, Progression.perk(sp, Perks.LUCIDITY_START));
        st.peak = st.lucidity;
        if (entry.wbtb()) d.wbtbUntil = 0;
        st.powerBaseline = powerUses(sp);

        if (sp.gameMode() == GameType.SURVIVAL) {
            st.prevMode = "survival";
            sp.setGameMode(GameType.ADVENTURE);
        }
        st.prevMayfly = sp.getAbilities().mayfly;

        Vec3 spawn = bp.spawn;
        teleporting = true;
        try {
            ServerPlayer moved = sp.teleport(new TeleportTransition(ad.level, spawn, Vec3.ZERO, bp.spawnYaw, 0f, TeleportTransition.DO_NOTHING));
            if (moved == null) {
                restorePlayer(sp, st);
                st.reset();
                return false;
            }
        } finally {
            teleporting = false;
        }
        sp.resetFallDistance();
        sp.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0, false, false));
        sp.addPostEffect(FX);
        ad.participants.add(sp.getUUID());

        d.dreams++;
        Progression.count(sp, Counters.DREAMS);
        playTo(sp, LucidiumContent.ENTER.get(), 0.8f, 0.8f);
        DirectorLog.log(sp, "dream", (host ? "entered" : "joined") + " cell " + bp.cell + ": " + bp.plan.summary()
                + String.format(" (lucidity %.0f)", st.lucidity));
        if (st.lucidity >= Lucidity.LUCID) onLucid(sp, st);
        sendHud(sp, st, ad, true);
        return true;
    }

    // ---- waking ------------------------------------------------------------------------------------------------------

    /** Ends the player's dream and puts them back in their bed (or wherever they came from). Safe to call anytime. */
    public static void wake(ServerPlayer sp, Wake why) {
        MinecraftServer server = sp.level().getServer();
        DreamState st = state(sp);
        if (!st.active) {
            if (sp.level().dimension() == LucidiumContent.LUCIDIUM) rescue(sp);
            clearEffects(sp);
            return;
        }
        long now = now(server);
        ActiveDream ad = ACTIVE.get(st.cell);
        Dreamer d = dreamer(sp);

        if (now - st.lastManifest <= 200) Progression.count(sp, MANIFEST_WOKE, 1);
        if (ad != null) returnItems(sp, ad);

        if (why != Wake.LEFT) teleportBack(sp, st);
        restorePlayer(sp, st);
        clearEffects(sp);
        if (why == Wake.DIED) {
            sp.setHealth(Math.max(sp.getHealth(), sp.getMaxHealth() * 0.5f));
            sp.addEffect(new MobEffectInstance(LucidiumContent.SHAKEN, 20 * 40, 0));
        }

        DreamPlan plan = ad != null ? ad.bp.plan : null;
        List<SignalWindow.Row> rows = new ArrayList<>();
        if (plan != null) {
            boolean lucid = st.lucidCounted;
            String summary = summarize(plan, st);
            d.addHistory(new DreamRecord(st.dreamId, PlayDays.current(server), summary, Math.round(st.peak), lucid, false, false, plan));
            rows.add(new SignalWindow.Row(Component.literal("Lucidity"), Component.literal(Math.round(st.peak) + " at its highest")));
            int signs = Integer.bitCount(st.spotted);
            if (signs > 0) rows.add(new SignalWindow.Row(Component.literal("Dream signs"), Component.literal(signs + " noticed")));
            plan.vision().flatMap(d::vision).ifPresent(v -> {
                Visions.reveal(sp, v, plan.day());
                rows.add(new SignalWindow.Row(Component.literal("You saw"), Component.literal(v.label())));
            });
            if (ad.nightmareState == 2) rows.add(new SignalWindow.Row(Component.literal("Nightmare"), Component.literal("faced")));
        }
        d.lastWoke = now;
        d.lastWokeDream = st.dreamId;

        String title = switch (why) {
            case DIED -> "You died in the dream";
            case FELL -> "You fell, and jolted awake";
            case BELL -> "The Dream Bell woke you";
            default -> "You wake up";
        };
        if (why != Wake.LOGIN) {
            SignalWindow.Builder w = SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(title));
            rows.forEach(r -> w.row(r.label(), r.value()));
            if (why == Wake.DIED) w.body(Component.literal("Nothing lost. Just a little shaken."));
            if (why == Wake.BELL) w.body(Component.literal("Midnight. Sleep again within the hour and you'll dream clearer."));
            w.footer(Component.literal("Write it down in your Dream Journal while you remember").withStyle(ChatFormatting.GRAY));
            Signal.send(sp, w.ttl(20 * 9).build());
        }
        playTo(sp, LucidiumContent.WAKE.get(), 0.7f, 1.0f);

        if (d.dreams == 1 && !hasJournal(sp)) {
            ItemStack journal = LucidiumContent.DREAM_JOURNAL.get().getDefaultInstance();
            if (!sp.addItem(journal)) sp.drop(journal, false, net.minecraft.util.Prediction.SERVER_ONLY);
            Signal.toast(sp, Component.literal("You wake holding a journal you don't remember owning."));
        }

        boolean fromSleep = st.fromSleep;
        BlockPos bed = st.bed;
        int cell = st.cell;
        st.reset();
        LAST_HUD.remove(sp.getUUID());
        AwakenNet.send(sp, LucidiumNet.DreamHud.AWAKE);
        if (ad != null) {
            ad.participants.remove(sp.getUUID());
            if (ad.participants.isEmpty()) finish(cell);
        }
        DirectorLog.log(sp, "dream", "woke (" + why.name().toLowerCase() + ")");
        if (why == Wake.BELL) d.wbtbUntil = now + 1000;
        else if (fromSleep && why != Wake.LOGIN) Sleep.afterDream(sp, bed);
    }

    private static void finish(int cell) {
        ActiveDream ad = ACTIVE.remove(cell);
        if (ad != null) ad.cleanup();
        DreamChunkGenerator.unregister(cell);
    }

    private static String summarize(DreamPlan plan, DreamState st) {
        List<String> places = new ArrayList<>();
        for (DreamPlan.Node n : plan.nodes()) if (DreamPlan.MEMORY.equals(n.kind()) && places.size() < 3) places.add(n.label());
        String s = places.isEmpty() ? "A dream of places you have never been" : "A dream of " + String.join(", ", places);
        if (st.lucidCounted) s += ". You knew you were dreaming";
        return s + ".";
    }

    private static void teleportBack(ServerPlayer sp, DreamState st) {
        MinecraftServer server = sp.level().getServer();
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, Identifier.tryParse(st.returnDim) != null
                ? Identifier.parse(st.returnDim) : Identifier.withDefaultNamespace("overworld"));
        ServerLevel back = server.getLevel(key);
        if (back == null || back.dimension() == LucidiumContent.LUCIDIUM) back = server.overworld();
        Vec3 pos = new Vec3(st.rx, st.ry, st.rz);
        float yaw = st.ryaw;
        if (st.bed != null) {
            back.getChunk(st.bed.getX() >> 4, st.bed.getZ() >> 4);
            BlockState bed = back.getBlockState(st.bed);
            if (bed.getBlock() instanceof AbstractBedBlock && bed.hasProperty(BedBlock.FACING)) {
                Direction facing = bed.getValue(BedBlock.FACING);
                Optional<Vec3> up = AbstractBedBlock.findStandUpPosition(EntityTypes.PLAYER, back, st.bed, facing, yaw);
                if (up.isPresent()) pos = up.get();
            }
        }
        back.getChunk(Mth_floor(pos.x) >> 4, Mth_floor(pos.z) >> 4);
        pos = safeSpot(back, sp, pos);
        teleporting = true;
        try {
            sp.teleport(new TeleportTransition(back, pos, Vec3.ZERO, yaw, st.rpitch, TeleportTransition.DO_NOTHING));
        } finally {
            teleporting = false;
        }
        sp.resetFallDistance();
        sp.setDeltaMovement(Vec3.ZERO);
    }

    private static int Mth_floor(double v) {
        return net.minecraft.util.Mth.floor(v);
    }

    /** Moves the position up until the player fits (blocks may have been placed on the bed while they slept). */
    static Vec3 safeSpot(ServerLevel level, ServerPlayer sp, Vec3 pos) {
        for (int up = 0; up < 12; up++) {
            Vec3 p = pos.add(0, up, 0);
            AABB box = sp.getDimensions(Pose.STANDING).makeBoundingBox(p);
            if (level.noCollision(sp, box)) return p;
        }
        return pos;
    }

    /** Someone in the Lucidium with no dream (a crash, an old save): send them to their spawn point. */
    static void rescue(ServerPlayer sp) {
        MinecraftServer server = sp.level().getServer();
        ServerLevel target = server.overworld();
        Vec3 pos = null;
        var cfg = sp.getRespawnConfig();
        if (cfg != null) {
            ServerLevel l = server.getLevel(cfg.respawnData().dimension());
            if (l != null && l.dimension() != LucidiumContent.LUCIDIUM) {
                target = l;
                BlockPos bp = cfg.respawnData().pos();
                target.getChunk(bp.getX() >> 4, bp.getZ() >> 4);
                pos = Vec3.atBottomCenterOf(bp.above());
            }
        }
        if (pos == null) {
            BlockPos s = target.getRespawnData().pos();
            target.getChunk(s.getX() >> 4, s.getZ() >> 4);
            int y = target.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, s.getX(), s.getZ());
            pos = new Vec3(s.getX() + 0.5, y, s.getZ() + 0.5);
        }
        pos = safeSpot(target, sp, pos);
        teleporting = true;
        try {
            sp.teleport(new TeleportTransition(target, pos, Vec3.ZERO, sp.getYRot(), 0, TeleportTransition.DO_NOTHING));
        } finally {
            teleporting = false;
        }
        if (sp.gameMode() == GameType.ADVENTURE) sp.setGameMode(GameType.SURVIVAL);
        sp.resetFallDistance();
    }

    private static void restorePlayer(ServerPlayer sp, DreamState st) {
        if ("survival".equals(st.prevMode) && sp.gameMode() == GameType.ADVENTURE) sp.setGameMode(GameType.SURVIVAL);
        if (st.grantedFly && !sp.isCreative() && !sp.isSpectator()) {
            sp.getAbilities().mayfly = st.prevMayfly;
            if (!st.prevMayfly) sp.getAbilities().flying = false;
            sp.onUpdateAbilities();
        }
    }

    static void clearEffects(ServerPlayer sp) {
        sp.removePostEffect(FX);
        sp.removePostEffect(FX_LUCID);
    }

    private static void returnItems(ServerPlayer sp, ActiveDream ad) {
        for (ItemEntity item : ad.level.getEntitiesOfClass(ItemEntity.class, sp.getBoundingBox().inflate(96),
                e -> e.getOwner() == sp || sp.getUUID().equals(ownerOf(e)))) {
            ItemStack stack = item.getItem().copy();
            item.discard();
            if (!sp.getInventory().add(stack)) sp.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
    }

    private static @Nullable UUID ownerOf(ItemEntity e) {
        Entity owner = e.getOwner();
        return owner == null ? null : owner.getUUID();
    }

    private static boolean hasJournal(ServerPlayer sp) {
        var inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(LucidiumContent.DREAM_JOURNAL.get())) return true;
        return false;
    }

    // ---- running ---------------------------------------------------------------------------------------------------

    static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;
        long now = now(server);
        for (ActiveDream ad : List.copyOf(ACTIVE.values())) {
            List<ServerPlayer> players = new ArrayList<>();
            for (UUID id : List.copyOf(ad.participants)) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p == null || !state(p).active || state(p).cell != ad.bp.cell) {
                    ad.participants.remove(id);
                    continue;
                }
                players.add(p);
            }
            if (players.isEmpty()) {
                finish(ad.bp.cell);
                continue;
            }
            ad.tickWorld(now, players);
            for (ServerPlayer p : players) {
                if (!state(p).active) continue;
                tickPlayer(p, state(p), ad, now);
            }
            if (now % 10 == 3) tickEncounters(ad, players, now);
        }
    }

    private static void tickPlayer(ServerPlayer sp, DreamState st, ActiveDream ad, long now) {
        if (sp.level() != ad.level) {
            wake(sp, Wake.LEFT);
            return;
        }
        if (now >= st.endTick) {
            wake(sp, Wake.TIME);
            return;
        }
        // falling dreams: the first fall puts you back where you began, the second wakes you
        if (sp.getY() < ad.level.getMinY() + 40) {
            if (st.falls++ == 0) {
                moveWithin(sp, ad.bp.spawn, ad.bp.spawnYaw);
                gain(sp, st, Lucidity.FIRST_FALL);
                Signal.toast(sp, Component.literal("You fall... and land where you began."));
            } else {
                wake(sp, Wake.FELL);
                return;
            }
        }
        // doors that lead somewhere else
        if (now - st.lastDoor > 30) {
            AABB box = sp.getBoundingBox();
            for (DreamBlueprint.Door door : ad.bp.doors) {
                if (!box.intersects(door.trigger())) continue;
                st.lastDoor = now;
                moveWithin(sp, door.exit(), door.exitYaw());
                playTo(sp, LucidiumContent.DOOR.get(), 0.8f, door.special() ? 1.4f : 0.9f);
                if (door.special() && !st.doorFound) {
                    st.doorFound = true;
                    gain(sp, st, Lucidity.DOOR_FOUND);
                    Signal.toast(sp, Component.literal("You found the door you were looking for."));
                    dropResidue(ad.level, sp.position(), 2);
                }
                break;
            }
        }
        if (now % 10 == 0) viewChecks(sp, st, ad, now);
        if (now % 20 == 7) trainingXp(sp, st);
        if (now % 10 == 0) sendHud(sp, st, ad, now % 40 == 0);
    }

    private static void moveWithin(ServerPlayer sp, Vec3 to, float yaw) {
        teleporting = true;
        try {
            sp.teleport(new TeleportTransition(sp.level(), to, Vec3.ZERO, yaw, sp.getXRot(), TeleportTransition.DO_NOTHING));
        } finally {
            teleporting = false;
        }
        sp.resetFallDistance();
        sp.setDeltaMovement(Vec3.ZERO);
    }

    // ---- perception ----------------------------------------------------------------------------------------------

    static boolean looksAt(ServerPlayer sp, Vec3 target, double degrees, double maxDist) {
        Vec3 eye = sp.getEyePosition();
        Vec3 to = target.subtract(eye);
        double dist = to.length();
        if (dist > maxDist || dist < 0.5) return dist < 0.5;
        return sp.getLookAngle().dot(to.scale(1 / dist)) >= Math.cos(Math.toRadians(degrees));
    }

    private static boolean lineOfSight(ServerPlayer sp, Vec3 target) {
        HitResult hit = sp.level().clip(new ClipContext(sp.getEyePosition(), target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, sp));
        return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(target) < 2.5;
    }

    private static void viewChecks(ServerPlayer sp, DreamState st, ActiveDream ad, long now) {
        for (DreamBlueprint.Node n : ad.bp.nodes) {
            if (looksAt(sp, n.centre(), 58, 176) || sp.blockPosition().distSqr(n.feet()) < 26 * 26) ad.markSeen(n.index(), now);
        }
        for (DreamSign sign : ad.bp.signs.keySet()) {
            if ((st.spotted & sign.bit()) != 0) continue;
            boolean seen = switch (sign) {
                case RAIN_UP -> {
                    int up = sp.getXRot() < -45 ? LOOKING_UP.merge(sp.getUUID(), 1, Integer::sum) : 0;
                    if (up == 0) LOOKING_UP.remove(sp.getUUID());
                    yield up >= 2;
                }
                case NO_SHADOW -> {
                    Vec3 p = ad.shadowlessPos();
                    yield p != null && looksAt(sp, p, 9, 40) && lineOfSight(sp, p);
                }
                case SCRAMBLED_SIGNS -> {
                    boolean any = false;
                    for (BlockPos p : ad.signsPlaced()) {
                        Vec3 c = Vec3.atCenterOf(p);
                        if (looksAt(sp, c, 10, 12) && lineOfSight(sp, c)) any = true;
                    }
                    yield any;
                }
                default -> {
                    DreamBlueprint.Sign s = ad.bp.signs.get(sign);
                    Vec3 c = Vec3.atCenterOf(s.positions().getFirst());
                    yield looksAt(sp, c, 9, 48) && lineOfSight(sp, c);
                }
            };
            if (seen) spotSign(sp, st, sign);
        }
    }

    private static void spotSign(ServerPlayer sp, DreamState st, DreamSign sign) {
        st.spotted |= sign.bit();
        gain(sp, st, Lucidity.DREAM_SIGN);
        Dreamer d = dreamer(sp);
        boolean first = d.signsSeen.add(sign.name());
        playTo(sp, LucidiumContent.SIGN.get(), 0.9f, 1.2f);
        Signal.toast(sp, Component.literal("That's not right. ").append(Component.literal(sign.title).withStyle(ChatFormatting.LIGHT_PURPLE)));
        if (first) Visions.sync(sp);
    }

    // ---- lucidity -------------------------------------------------------------------------------------------------------

    public static void gain(ServerPlayer sp, DreamState st, float amount) {
        float before = st.lucidity;
        st.lucidity = Lucidity.gain(before, amount, Progression.stat(sp, Stat.SPI));
        st.peak = Math.max(st.peak, st.lucidity);
        if (before < Lucidity.LUCID && st.lucidity >= Lucidity.LUCID) onLucid(sp, st);
        boolean flyIntent = DreamPlanner.FLY.equals(dreamer(sp).intention(PlayDays.current(sp.level().getServer())));
        if (!st.grantedFly && Lucidity.canFly(st.lucidity, flyIntent) && !sp.getAbilities().mayfly) {
            st.grantedFly = true;
            sp.getAbilities().mayfly = true;
            sp.onUpdateAbilities();
            Signal.toast(sp, Component.literal("You can fly. Double-tap jump."));
        } else if (!st.grantedFly && Lucidity.canFly(st.lucidity, flyIntent)) {
            st.grantedFly = true;
        }
        if (!st.masteryShown && st.lucidity >= Lucidity.MASTERY) {
            st.masteryShown = true;
            Signal.toast(sp, Component.literal("You are the dreamer. Sneak and hold H to call on a memory."));
        }
        ActiveDream ad = active(sp);
        if (ad != null) sendHud(sp, st, ad, false);
    }

    /** Admin: set lucidity exactly (thresholds fire as if it was earned). */
    public static void setLucidity(ServerPlayer sp, float value) {
        DreamState st = state(sp);
        if (!st.active) return;
        st.lucidity = Math.min(st.lucidity, Lucidity.clamp(value));
        float missing = Lucidity.clamp(value) - st.lucidity;
        gain(sp, st, missing / Lucidity.gainMultiplier(Progression.stat(sp, Stat.SPI)));
        st.lucidity = Lucidity.clamp(value);
    }

    private static void onLucid(ServerPlayer sp, DreamState st) {
        if (st.lucidCounted) return;
        st.lucidCounted = true;
        Progression.count(sp, Counters.LUCID_DREAMS);
        Dreamer d = dreamer(sp);
        sp.removePostEffect(FX);
        sp.addPostEffect(FX_LUCID);
        playTo(sp, LucidiumContent.LUCID.get(), 0.8f, 1.0f);
        Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.REVELATION, Component.literal("You are dreaming."))
                .body(Component.literal("You know it now, and the dream knows you know. Fly. Shape it. Remember it."))
                .footer(Component.literal("Hold H on a wall, a floor or the open air to shape the dream"))
                .ttl(20 * 8).priority(4).build());
        if (!d.lucidEver) {
            d.lucidEver = true;
            Progression.markNewThing(sp, "lucid dream");
            Progression.bestMoment(sp, "You woke up inside a dream");
        }
    }

    // ---- the H key -------------------------------------------------------------------------------------------------------

    public static void realityCheck(ServerPlayer sp) {
        Progression.count(sp, Counters.REALITY_CHECKS);
        DreamState st = state(sp);
        if (!st.active) return; // awake: nothing visible happens
        long now = now(sp.level().getServer());
        if (now - st.lastRealityCheck < 60) return;
        st.lastRealityCheck = now;
        float amount = Lucidity.realityCheckAmount(st.realityChecks++);
        gain(sp, st, amount);
        playTo(sp, LucidiumContent.REALITY_CHECK.get(), 0.7f, 0.9f + sp.getRandom().nextFloat() * 0.3f);
        if (st.realityChecks == 1) Signal.toast(sp, Component.literal("Your hand flickers. Something is wrong with this place."));
    }

    public static void manifest(ServerPlayer sp, boolean summon) {
        DreamState st = state(sp);
        ActiveDream ad = active(sp);
        if (ad == null) return;
        long now = now(sp.level().getServer());
        if (now - st.lastManifest < 60) return;
        if (summon) {
            if (st.lucidity < Lucidity.MASTERY || st.summoned) return;
            if (Manifest.summon(sp, ad)) {
                st.summoned = true;
                st.lastManifest = now;
            }
            return;
        }
        if (st.lucidity < Lucidity.LUCID) {
            sp.sendOverlayMessage(Component.literal("Not yet. You don't quite believe it's a dream.").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (Manifest.shape(sp, ad)) {
            st.lastManifest = now;
            Progression.count(sp, DREAM_MANIFESTS);
            playTo(sp, LucidiumContent.MANIFEST.get(), 0.9f, 1.0f);
        }
    }

    // ---- nightmares and the mentor ---------------------------------------------------------------------------------

    private static void tickEncounters(ActiveDream ad, List<ServerPlayer> players, long now) {
        if (ad.nightmareState == 0) {
            DreamBlueprint.Node n = ad.bp.nodes.get(ad.nightmareNode);
            for (ServerPlayer p : players) {
                if (horizontal(p, n.feet()) < 14 && Math.abs(p.getY() - n.feet().getY()) < 12) {
                    startNightmare(ad, n, p);
                    break;
                }
            }
        } else if (ad.nightmareState == 1 && now % 20 == 3) {
            boolean anyAlive = false;
            for (UUID id : ad.nightmareMobs) {
                Entity e = ad.level.getEntity(id);
                if (e instanceof LivingEntity le && le.isAlive()) anyAlive = true;
            }
            if (!anyAlive) beatNightmare(ad, players);
        }
        if (ad.mentorState == 0) {
            DreamBlueprint.Node n = ad.bp.nodes.get(ad.mentorNode);
            for (ServerPlayer p : players) {
                if (horizontal(p, n.feet()) < 9 && Math.abs(p.getY() - n.feet().getY()) < 6 && Progression.profile(p).path().isPresent()) {
                    Mentors.start(ad, n, p);
                    break;
                }
            }
        }
    }

    static double horizontal(Entity e, BlockPos p) {
        double dx = e.getX() - (p.getX() + 0.5), dz = e.getZ() - (p.getZ() + 0.5);
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static void startNightmare(ActiveDream ad, DreamBlueprint.Node n, ServerPlayer trigger) {
        ad.nightmareState = 1;
        Nightmares.Family f = ad.family;
        ServerPlayer host = trigger.level().getServer().getPlayerList().getPlayer(ad.host);
        ServerPlayer ref = host != null ? host : trigger;
        int rank = Progression.rank(ref).ordinal();
        int lv = Math.max(1, (int) (Progression.contentLevel(Progression.rank(ref)) * 0.8));
        int count = Math.min(6, 3 + rank / 2);
        java.util.Set<Forces.Tag> tags = switch (f) {
            case DROWNING -> java.util.Set.of(Forces.Tag.WATER);
            case EXPLOSION -> java.util.Set.of(Forces.Tag.VOLATILE);
            case UNDEAD -> java.util.Set.of(Forces.Tag.HAUNTED);
            default -> java.util.Set.of();
        };
        int ring = f == Nightmares.Family.FALLING ? 1 : 5; // the falling arena is mostly sky
        for (int k = 0; k < count; k++) {
            double a = k * (Math.PI * 2 / count) + 0.4;
            BlockPos at = ad.standingSpot(n.feet().offset((int) Math.round(Math.cos(a) * ring), 1, (int) Math.round(Math.sin(a) * ring)));
            if (at == null) at = n.feet().above();
            LivingEntity mob = MobHooks.spawnThemed(ad.level, f.theme(), MobRole.Role.NIGHTMARE, at, lv, tags, "");
            if (mob != null) {
                ad.nightmareMobs.add(mob.getUUID());
                if (mob instanceof net.minecraft.world.entity.Mob m) m.setTarget(trigger);
            }
        }
        for (UUID id : ad.participants) {
            ServerPlayer p = trigger.level().getServer().getPlayerList().getPlayer(id);
            if (p == null) continue;
            playTo(p, LucidiumContent.NIGHTMARE.get(), 1.0f, 0.8f);
            Signal.send(p, SignalWindow.builder(SignalWindow.Kind.WARNING, Component.literal("A nightmare"))
                    .body(Component.literal(f.warning)).footer(Component.literal("Beat it and this fear won't come back this week"))
                    .ttl(20 * 6).priority(6).build());
        }
        DirectorLog.log(ref, "dream", "nightmare " + f.id + ": " + ad.nightmareMobs.size() + " fresh from the " + f.theme() + " template");
    }

    private static void beatNightmare(ActiveDream ad, List<ServerPlayer> players) {
        ad.nightmareState = 2;
        DreamBlueprint.Node n = ad.bp.nodes.get(ad.nightmareNode);
        dropResidue(ad.level, Vec3.atCenterOf(n.feet()), 3);
        for (ServerPlayer p : players) {
            if (horizontal(p, n.feet()) > 40) continue;
            Progression.count(p, Counters.NIGHTMARES_BEATEN);
            var profile = Progression.profile(p);
            for (String counter : Nightmares.weeklyCountersOf(ad.family, Map.copyOf(profile.counters()))) Progression.setCounter(p, counter, 0);
            dreamer(p).clearFamily(ad.family);
            Planner.invalidate(p);
            gain(p, state(p), Lucidity.NIGHTMARE_BEATEN);
            Progression.grantLevels(p, 0.10, Progression.XpSource.DREAM);
            Progression.bestMoment(p, "You faced " + ad.family.fear + " and it let go");
            Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Fear faced"))
                    .body(Component.literal("You stood in " + ad.family.fear + " and didn't run. It won't follow you this week."))
                    .ttl(20 * 7).build());
        }
    }

    static void dropResidue(ServerLevel level, Vec3 at, int count) {
        ItemEntity item = new ItemEntity(level, at.x, at.y + 0.5, at.z, new ItemStack(LucidiumContent.DREAM_RESIDUE.get(), count));
        item.setDefaultPickUpDelay();
        level.addFreshEntity(item);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 0.8, at.z, 12, 0.4, 0.4, 0.4, 0.02);
    }

    // ---- dream training ---------------------------------------------------------------------------------------------

    static long powerUses(ServerPlayer sp) {
        long sum = 0;
        for (var e : Progression.profile(sp).counters().entrySet()) if (e.getKey().startsWith("awaken:power_uses/")) sum += e.getValue();
        return sum;
    }

    /** Powers practised in a dream train you: a small XP grant per use (capped per dream). */
    private static void trainingXp(ServerPlayer sp, DreamState st) {
        long uses = powerUses(sp);
        if (st.powerBaseline < 0) st.powerBaseline = uses;
        long delta = uses - st.powerBaseline;
        st.powerBaseline = uses;
        if (delta <= 0 || st.xpGranted >= 0.15) return;
        double grant = Math.min(0.15 - st.xpGranted, delta * 0.004);
        st.xpGranted += grant;
        Progression.grantLevels(sp, grant, Progression.XpSource.DREAM);
    }

    // ---- HUD ----------------------------------------------------------------------------------------------------------

    static void sendHud(ServerPlayer sp, DreamState st, ActiveDream ad, boolean force) {
        long now = now(sp.level().getServer());
        int flags = 0;
        boolean flyIntent = DreamPlanner.FLY.equals(dreamer(sp).intention(PlayDays.current(sp.level().getServer())));
        if (Lucidity.canFly(st.lucidity, flyIntent)) flags |= LucidiumNet.DreamHud.FLY;
        if (st.lucidity >= Lucidity.LUCID) flags |= LucidiumNet.DreamHud.SHAPE | LucidiumNet.DreamHud.LUCID;
        if (st.lucidity >= Lucidity.MASTERY && !st.summoned) flags |= LucidiumNet.DreamHud.SUMMON;
        if (ad.nightmareState == 1) flags |= LucidiumNet.DreamHud.NIGHTMARE;
        if (ad.hasSign(DreamSign.RAIN_UP)) flags |= LucidiumNet.DreamHud.RAIN_UP;
        LucidiumNet.DreamHud hud = new LucidiumNet.DreamHud(true, Math.round(st.lucidity * 2) / 2f,
                (int) Math.max(0, st.endTick - now), (int) Math.max(1, st.endTick - st.startTick), ad.signMask(), st.spotted, flags,
                ad.shadowlessId());
        LucidiumNet.DreamHud last = LAST_HUD.get(sp.getUUID());
        if (!force && last != null && last.lucidity() == hud.lucidity() && last.flags() == hud.flags()
                && last.spotted() == hud.spotted() && last.shadowless() == hud.shadowless()) return;
        LAST_HUD.put(sp.getUUID(), hud);
        AwakenNet.send(sp, hud);
    }

    static void playTo(ServerPlayer sp, SoundEvent sound, float volume, float pitch) {
        sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    static boolean teleporting() {
        return teleporting;
    }

    /** Server stopping: wake everyone so the world saves with nobody inside a dream. */
    static void wakeAll(MinecraftServer server) {
        for (ServerPlayer p : List.copyOf(server.getPlayerList().getPlayers())) {
            if (isDreaming(p)) wake(p, Wake.API);
        }
        for (int cell : List.copyOf(ACTIVE.keySet())) finish(cell);
        LAST_HUD.clear();
    }

    static void forget(ServerPlayer sp) {
        LAST_HUD.remove(sp.getUUID());
        LOOKING_UP.remove(sp.getUUID());
    }

    static boolean isMentor(Entity e) {
        for (ActiveDream ad : ACTIVE.values()) if (e.getUUID().equals(ad.mentor)) return true;
        return false;
    }

    static @Nullable ActiveDream dreamOf(Entity e) {
        for (ActiveDream ad : ACTIVE.values()) if (ad.isDreamEntity(e)) return ad;
        return null;
    }
}
