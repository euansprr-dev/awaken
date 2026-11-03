package dev.awaken.lucidium.dream;

import dev.awaken.Awaken;
import dev.awaken.core.AwakenNet;
import dev.awaken.core.DirectorLog;
import dev.awaken.core.PlayDays;
import dev.awaken.lucidium.LucidiumContent;
import dev.awaken.lucidium.data.DreamState;
import dev.awaken.lucidium.data.Dreamer;
import dev.awaken.lucidium.net.LucidiumNet;
import dev.awaken.lucidium.plan.DreamSign;
import dev.awaken.lucidium.world.DreamBlueprint;
import dev.awaken.lucidium.world.DreamChunkGenerator;
import dev.awaken.progression.Counters;
import dev.awaken.progression.Perks;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Stat;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
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

    /** WON: a trial's goal reached; SLIPPED: its clock ran out; CHOSE: the pause screen's Wake up (SPEC §9.3). */
    public enum Wake { TIME, DIED, FELL, BELL, COMMAND, LOGIN, LEFT, API, WON, SLIPPED, CHOSE }

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

    /**
     * Starts a new dream for {@code host} from a prepared plan. Returns the running dream, or null if it can't start. Only
     * a trial dreams (SPEC §9): an old island plan (version 1) is never entered.
     */
    public static @Nullable ActiveDream enter(ServerPlayer host, Planner.Prepared prepared, Entry entry) {
        MinecraftServer server = host.level().getServer();
        ServerLevel level = entry.target() != null ? entry.target() : dreamLevel(server);
        if (level == null || isDreaming(host) || prepared.blueprint().course == null) return null;
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
        // the recall bonus is a second for each dream finished, up to 10 (old saves keep what they wrote)
        st.lucidity = Lucidity.start(intention, entry.wbtb(), Trials.recall(d), Progression.perk(sp, Perks.LUCIDITY_START));
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
        sendHud(sp, st, ad, true);
        if (entry.fromSleep()) d.ledger.lastDreamDay = day;
        Trials.arrive(sp, st, ad, now);
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
        // one line a wake, so a tour's log says why a dream ended (the caller too when it came from outside the dream)
        Awaken.LOG.info("[lucidium] {} woke: {}{} after {} ticks{}", sp.getGameProfile().name(), why,
                st.trial.isEmpty() ? "" : " (trial " + st.trial + ", phase " + st.phase + ")", now - st.startTick,
                why == Wake.API || why == Wake.LEFT || why == Wake.DIED ? " from " + StackWalker.getInstance().walk(s -> s.skip(1).limit(4)
                        .map(f -> f.getClassName().substring(f.getClassName().lastIndexOf('.') + 1) + "." + f.getMethodName() + ":" + f.getLineNumber())
                        .collect(java.util.stream.Collectors.joining(" < "))) : "");

        if (ad != null) returnItems(sp, ad);

        if (why != Wake.LEFT) teleportBack(sp, st);
        restorePlayer(sp, st);
        clearEffects(sp);
        if (why == Wake.DIED) {
            sp.setHealth(Math.max(sp.getHealth(), sp.getMaxHealth() * 0.5f));
            sp.addEffect(new MobEffectInstance(LucidiumContent.SHAKEN, 20 * 40, 0));
        }

        List<SignalWindow.Row> rows = new ArrayList<>();
        // (no dream left running, as after a restart: just the waking)
        Trials.Waking trialWake = ad != null && Trials.is(ad) ? Trials.woke(sp, st, ad, why, d, PlayDays.current(server)) : null;
        if (trialWake != null) rows.addAll(trialWake.rows());
        d.lastWoke = now;
        d.lastWokeDream = st.dreamId;

        String title = trialWake != null ? trialWake.title() : switch (why) {
            case DIED -> "You died in the dream";
            case FELL -> "You fell, and jolted awake";
            case BELL -> "The Dream Bell woke you";
            default -> "You wake up";
        };
        if (why != Wake.LOGIN) {
            SignalWindow.Builder w = SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(title));
            rows.forEach(r -> w.row(r.label(), r.value()));
            if (trialWake != null) {
                if (!trialWake.body().isEmpty()) w.body(Component.literal(trialWake.body()));
                w.footer(Component.literal(trialWake.footer()).withStyle(ChatFormatting.GRAY));
            } else {
                if (why == Wake.DIED) w.body(Component.literal("Nothing lost. Just a little shaken."));
                if (why == Wake.BELL) w.body(Component.literal("Midnight. Sleep again within the hour and you'll dream clearer."));
            }
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
        // the cell is dreamed in again once its chunks are out of memory and erased (no restart needed)
        if (ad != null) dev.awaken.lucidium.world.Cells.release(ad.level.getServer(), cell);
    }

    private static void teleportBack(ServerPlayer sp, DreamState st) {
        MinecraftServer server = sp.level().getServer();
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, Identifier.tryParse(st.returnDim) != null
                ? Identifier.parse(st.returnDim) : Identifier.withDefaultNamespace("overworld"));
        ServerLevel found = server.getLevel(key);
        ServerLevel back = found == null || found.dimension() == LucidiumContent.LUCIDIUM ? server.overworld() : found;
        Vec3 lay = new Vec3(st.rx, st.ry, st.rz);
        float yaw = st.ryaw, pitch = st.rpitch;
        BlockPos bedAt = st.bed;
        // back once the land there is made (SPEC §28.9, "Arrivals"); the dream's state is read now, as it's reset after
        dev.awaken.core.api.Passage.go(sp, back, lay.x, lay.z, () -> {
            Vec3 pos = lay;
            if (bedAt != null) {
                back.getChunk(bedAt.getX() >> 4, bedAt.getZ() >> 4);
                BlockState bed = back.getBlockState(bedAt);
                if (bed.getBlock() instanceof AbstractBedBlock && bed.hasProperty(BedBlock.FACING)) {
                    Direction facing = bed.getValue(BedBlock.FACING);
                    Optional<Vec3> up = AbstractBedBlock.findStandUpPosition(EntityTypes.PLAYER, back, bedAt, facing, yaw);
                    if (up.isPresent()) pos = up.get();
                }
            }
            back.getChunk(Mth_floor(pos.x) >> 4, Mth_floor(pos.z) >> 4);
            pos = safeSpot(back, sp, pos);
            teleporting = true;
            try {
                sp.teleport(new TeleportTransition(back, pos, Vec3.ZERO, yaw, pitch, TeleportTransition.DO_NOTHING));
            } finally {
                teleporting = false;
            }
            sp.resetFallDistance();
            sp.setDeltaMovement(Vec3.ZERO);
        });
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
        var cfg = sp.getRespawnConfig();
        ServerLevel l = cfg == null ? null : server.getLevel(cfg.respawnData().dimension());
        boolean own = l != null && l.dimension() != LucidiumContent.LUCIDIUM;
        ServerLevel target = own ? l : server.overworld();
        BlockPos at = own ? cfg.respawnData().pos() : target.getRespawnData().pos();
        // there once its land is made (SPEC §28.9, "Arrivals")
        dev.awaken.core.api.Passage.go(sp, target, at.getX() + 0.5, at.getZ() + 0.5, () -> {
            Vec3 pos = own ? Vec3.atBottomCenterOf(at.above()) : new Vec3(at.getX() + 0.5,
                    target.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()), at.getZ() + 0.5);
            pos = safeSpot(target, sp, pos);
            teleporting = true;
            try {
                sp.teleport(new TeleportTransition(target, pos, Vec3.ZERO, sp.getYRot(), 0, TeleportTransition.DO_NOTHING));
            } finally {
                teleporting = false;
            }
            if (sp.gameMode() == GameType.ADVENTURE) sp.setGameMode(GameType.SURVIVAL);
            sp.resetFallDistance();
        });
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
            Trials.tickWorld(ad, players, now);
            for (ServerPlayer p : players) {
                if (!state(p).active) continue;
                tickPlayer(p, state(p), ad, now);
            }
        }
    }

    private static void tickPlayer(ServerPlayer sp, DreamState st, ActiveDream ad, long now) {
        if (sp.level() != ad.level) {
            wake(sp, Wake.LEFT);
            return;
        }
        if (!Trials.tick(sp, st, ad, now)) return;
        if (now % 10 == 0) viewChecks(sp, st, ad, now);
        if (now % 10 == 0) sendHud(sp, st, ad, now % 40 == 0);
    }

    static void moveWithin(ServerPlayer sp, Vec3 to, float yaw) {
        moveWithin(sp, to, yaw, sp.getXRot());
    }

    static void moveWithin(ServerPlayer sp, Vec3 to, float yaw, float pitch) {
        teleporting = true;
        try {
            sp.teleport(new TeleportTransition(sp.level(), to, Vec3.ZERO, yaw, pitch, TeleportTransition.DO_NOTHING));
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
        Dreamer d = dreamer(sp);
        boolean first = d.signsSeen.add(sign.name());
        playTo(sp, LucidiumContent.SIGN.get(), 0.9f, 1.2f);
        ActiveDream ad = active(sp);
        if (ad != null) Trials.signNoticed(sp, st, ad, now(sp.level().getServer()));
        if (first) Visions.sync(sp);
    }

    // ---- lucidity -------------------------------------------------------------------------------------------------------

    /** Lucidity in a trial is its starting seconds (SPEC §9.3): no flight, no shaping. */
    public static void gain(ServerPlayer sp, DreamState st, float amount) {
        st.lucidity = Lucidity.gain(st.lucidity, amount, Progression.stat(sp, Stat.SPI));
        st.peak = Math.max(st.peak, st.lucidity);
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

    // ---- the H key -------------------------------------------------------------------------------------------------------

    public static void realityCheck(ServerPlayer sp) {
        Progression.count(sp, Counters.REALITY_CHECKS);
        DreamState st = state(sp);
        if (!st.active) return; // awake: nothing visible happens
        long now = now(sp.level().getServer());
        ActiveDream ad = active(sp);
        if (ad == null) return;
        st.realityChecks++;
        Trials.press(sp, st, ad, now);
    }

    /** Lucidium.bellTaken: a trial's bell taken wins the dream. */
    public static void trialBellTaken(ServerPlayer sp) {
        Trials.bellTaken(sp);
    }

    /**
     * Where a trial's bell stands now (SPEC §9.5): the end of a Run's road, a Room's goal wherever its rule has it, or
     * null while the rule hides it or the Chase's fear carries it.
     */
    public static @Nullable BlockPos trialBellAt(ActiveDream ad) {
        if (ad.bp.course == null) return null;
        if (ad.bp.course.trial().run()) return ad.bp.course.bell();
        return ad.roomCarrier != null ? null : ad.roomGoal;
    }

    /** The Chase's fear carrying the bell, or null. */
    public static @Nullable UUID trialBellCarrier(ActiveDream ad) {
        return ad.roomCarrier;
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

    // ---- HUD ----------------------------------------------------------------------------------------------------------

    static void sendHud(ServerPlayer sp, DreamState st, ActiveDream ad, boolean force) {
        LucidiumNet.DreamHud hud = new LucidiumNet.DreamHud(true, 0, 0, 1, ad.signMask(), st.spotted,
                LucidiumNet.DreamHud.TRIAL | (ad.hasSign(DreamSign.RAIN_UP) ? LucidiumNet.DreamHud.RAIN_UP : 0), ad.shadowlessId());
        LucidiumNet.DreamHud last = LAST_HUD.get(sp.getUUID());
        if (!force && hud.equals(last)) return;
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
