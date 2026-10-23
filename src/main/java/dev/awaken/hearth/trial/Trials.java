package dev.awaken.hearth.trial;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.awaken.Awaken;
import dev.awaken.core.AwakenConfig;
import dev.awaken.core.DirectorLog;
import dev.awaken.core.PlayDays;
import dev.awaken.core.api.GateHooks;
import dev.awaken.core.api.MobHooks;
import dev.awaken.core.api.MobRole;
import dev.awaken.hearth.HearthContent;
import dev.awaken.hearth.HearthService;
import dev.awaken.hearth.Offers;
import dev.awaken.hearth.player.HearthPlayer;
import dev.awaken.progression.Profile;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import dev.awaken.progression.content.Content;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Reassessment trials (rank-ups) and the Hardline Penalty Zone, fought in walled arenas in {@code awaken:trials}.
 * D: survive three waves. C: waves, then the Second Awakening. B: duel a Gate Lord. A: a timed descent through five
 * rooms. S: a Sovereign's echo. Trials never kill: falling ends the attempt and sends you home. One attempt per
 * play-day.
 */
public final class Trials {
    private Trials() {}

    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, Awaken.id("trials"));

    public enum Kind {
        WAVES("Survive three waves.", 3, 20 * 60 * 6),
        AWAKENING("Survive, then see what you are becoming.", 2, 20 * 60 * 6),
        DUEL("Face a Gate Lord alone.", 1, 20 * 60 * 5),
        DESCENT("Five rooms down. Four minutes.", 5, 20 * 60 * 4),
        SOVEREIGN("Face a Sovereign's echo.", 1, 20 * 60 * 6),
        PENALTY("Survive four minutes in the desert at night.", 99, 20 * 60 * 4);

        public final String text;
        public final int stages;
        public final int duration;

        Kind(String text, int stages, int duration) {
            this.text = text;
            this.stages = stages;
            this.duration = duration;
        }

        public static Kind forTarget(Rank target) {
            return switch (target) {
                case D -> WAVES;
                case C -> AWAKENING;
                case B -> DUEL;
                case A -> DESCENT;
                default -> SOVEREIGN;
            };
        }
    }

    public enum State { ACTIVE, PASSED, FAILED }

    /** One running trial. Package-visible fields; read through the accessors. */
    public static final class Session {
        final UUID player;
        final Kind kind;
        final Rank target;
        final ServerLevel level;
        final BlockPos centre;
        final boolean teleported;
        final boolean practice;
        final int cell;
        final long start;
        int duration;
        int stage;
        long nextStageAt;
        final List<UUID> mobs = new ArrayList<>();
        final List<BlockPos> placed = new ArrayList<>();
        @Nullable BlockPos hatch;
        long clearedAt;
        State state = State.ACTIVE;
        final ServerBossEvent bar;
        int spread;

        Session(UUID player, Kind kind, Rank target, ServerLevel level, BlockPos centre, boolean teleported, boolean practice, int cell) {
            this.player = player;
            this.kind = kind;
            this.target = target;
            this.level = level;
            this.centre = centre;
            this.teleported = teleported;
            this.practice = practice;
            this.cell = cell;
            this.start = level.getGameTime();
            this.duration = kind.duration;
            this.spread = teleported ? 10 : 3;
            this.bar = new ServerBossEvent(UUID.randomUUID(), Component.literal(kind == Kind.PENALTY ? "Penalty Zone" : target.label + "-rank Reassessment"),
                    kind == Kind.PENALTY ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS);
        }

        public Kind kind() { return kind; }
        public State state() { return state; }
        public int stage() { return stage; }
        public List<UUID> mobs() { return mobs; }
        public Rank target() { return target; }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    /** Ended sessions kept briefly so tests and commands can read the result. */
    private static final Map<UUID, State> LAST = new HashMap<>();

    public static void init() {
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(Trials::onTick);
        bus.addListener(EventPriority.HIGHEST, Trials::onDeath);
        bus.addListener(Trials::onLogout);
        bus.addListener(Trials::onLogin);
        bus.addListener((ServerStoppedEvent e) -> {
            SESSIONS.clear();
            LAST.clear();
        });
    }

    public static Optional<Session> session(ServerPlayer sp) {
        return Optional.ofNullable(SESSIONS.get(sp.getUUID()));
    }

    public static Optional<State> lastResult(UUID player) {
        return Optional.ofNullable(LAST.get(player));
    }

    public static boolean inTrial(ServerPlayer sp) {
        return SESSIONS.containsKey(sp.getUUID());
    }

    // ---- the Monolith -----------------------------------------------------------------------------------------------

    public static void monolith(ServerPlayer sp, ServerLevel level, BlockPos pos) {
        Profile p = Progression.profile(sp);
        if (!p.awakened()) {
            Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("The Monolith is silent"))
                    .body(Component.literal("It's waiting for someone the Signal has chosen.")).ttl(20 * 6).build());
            return;
        }
        if (inTrial(sp)) return;
        Rank next = p.rank().next();
        if (next == null || next == Rank.SOVEREIGN) {
            Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.REASSESSMENT, Component.literal(next == null ? "There is no rank above yours" : "Not here"))
                    .body(Component.literal(next == null ? "The Monolith hums your name and nothing else." : "Your last Reassessment waits at the Seed."))
                    .ttl(20 * 8).build());
            return;
        }
        if (!p.reassessmentReady()) {
            Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.REASSESSMENT, Component.literal("Not yet"))
                    .body(Component.literal("Next rank: " + next.label + ". Reassessment at Level " + next.levelRequired + ". You're Level " + p.level() + "."))
                    .ttl(20 * 8).build());
            return;
        }
        HearthPlayer hp = HearthService.player(sp);
        long today = PlayDays.current(level.getServer());
        if (hp.trialFailDay == today) {
            Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.REASSESSMENT, Component.literal("The Monolith is quiet"))
                    .body(Component.literal("You've been tested today. Rest. It will listen again next play-day.")).ttl(20 * 8).build());
            return;
        }
        Kind kind = Kind.forTarget(next);
        Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.REASSESSMENT, Component.literal(next.label + "-rank Reassessment"))
                .body(Component.literal(kind.text + " If you fall, you wake at home and can try again next play-day."))
                .row(Component.literal("Trial"), Component.literal(kind.name().charAt(0) + kind.name().substring(1).toLowerCase(java.util.Locale.ROOT)))
                .row(Component.literal("Time"), Component.literal(kind.duration / 1200 + " minutes"))
                .action(Offers.offer(sp, "trial", 20 * 30, pl -> start(pl, kind, next, false)))
                .footer(Component.literal("Begin?")).priority(6).ttl(20 * 30).build());
    }

    // ---- starting ---------------------------------------------------------------------------------------------------

    /** Sends the player to a fresh arena cell for this trial. */
    public static boolean start(ServerPlayer sp, Kind kind, Rank target, boolean practice) {
        if (inTrial(sp)) return false;
        MinecraftServer server = sp.level().getServer();
        ServerLevel trials = server.getLevel(DIMENSION);
        if (trials == null) {
            sp.sendOverlayMessage(Component.literal("The trial grounds are missing from this world."));
            return false;
        }
        if (GateHooks.inGate(sp)) {
            sp.sendOverlayMessage(Component.literal("Finish your gate first."));
            return false;
        }
        TrialData data = TrialData.get(server);
        int cell = data.allocate(kind == Kind.PENALTY);
        BlockPos centre = TrialChunkGenerator.centre(cell);
        trials.getChunk(centre.getX() >> 4, centre.getZ() >> 4);
        data.returns.put(sp.getUUID(), new TrialData.Return(sp.level().dimension().identifier().toString(), sp.getX(), sp.getY(), sp.getZ(), sp.getYRot()));
        data.setDirty();
        Session s = new Session(sp.getUUID(), kind, target, trials, centre, true, practice, cell);
        SESSIONS.put(sp.getUUID(), s);
        if (kind == Kind.DESCENT) buildDescent(s);
        BlockPos spawn = kind == Kind.DESCENT ? roomFloor(s, 0).above() : centre;
        ServerPlayer moved = sp.teleport(new TeleportTransition(trials, Vec3.atBottomCenterOf(spawn), Vec3.ZERO, 0f, 0f, TeleportTransition.PLAY_PORTAL_SOUND));
        if (moved == null) {
            abandon(s);
            return false;
        }
        begin(moved, s);
        return true;
    }

    /** Runs a trial right where {@code centre} is, without teleporting (GameTests, debugging). */
    public static Session startAt(ServerPlayer sp, Kind kind, Rank target, ServerLevel level, BlockPos centre, boolean practice, int duration) {
        Session s = new Session(sp.getUUID(), kind, target, level, centre, false, practice, -1);
        if (duration > 0) s.duration = duration;
        SESSIONS.put(sp.getUUID(), s);
        if (kind == Kind.DESCENT) buildDescent(s);
        begin(sp, s);
        return s;
    }

    private static void begin(ServerPlayer sp, Session s) {
        s.nextStageAt = s.level.getGameTime() + (s.teleported ? 60 : 5);
        s.bar.addPlayer(sp);
        s.bar.setProgress(1f);
        s.level.playSound(null, sp.blockPosition(), HearthContent.SND_TRIAL.get(), SoundSource.PLAYERS, 1f, 1f);
        String title = s.kind == Kind.PENALTY ? "Penalty Zone" : s.target.label + "-rank Reassessment";
        Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.REASSESSMENT, Component.literal(title))
                .body(Component.literal(s.kind.text)).ttl(20 * 5).priority(7).build());
        DirectorLog.log(sp, "trial", "start " + s.kind + " → " + s.target + (s.practice ? " (practice)" : ""));
    }

    // ---- ticking ----------------------------------------------------------------------------------------------------

    private static void onTick(ServerTickEvent.Post e) {
        if (SESSIONS.isEmpty()) return;
        MinecraftServer server = e.getServer();
        for (Session s : List.copyOf(SESSIONS.values())) {
            ServerPlayer sp = server.getPlayerList().getPlayer(s.player);
            if (sp == null) {
                end(server, s, false, "left");
                continue;
            }
            if (s.teleported && sp.level() != s.level) {
                end(server, s, false, "left");
                continue;
            }
            tick(sp, s);
        }
    }

    private static void tick(ServerPlayer sp, Session s) {
        long now = s.level.getGameTime();
        long elapsed = now - s.start;
        s.bar.setProgress(Math.max(0f, 1f - (float) elapsed / s.duration));
        if (s.kind == Kind.PENALTY) {
            if (elapsed >= s.duration) {
                end(sp.level().getServer(), s, true, "survived");
                return;
            }
            if (now >= s.nextStageAt) {
                s.stage++;
                spawnWave(s, sp, 2 + Math.min(6, s.stage), false);
                s.nextStageAt = now + Math.min(600, Math.max(40, s.duration / 8));
                sp.sendOverlayMessage(Component.literal("Wave " + s.stage + " · " + (s.duration - elapsed) / 20 + "s to survive"));
            }
            return;
        }
        if (elapsed >= s.duration) {
            end(sp.level().getServer(), s, false, "time");
            return;
        }
        if (now % 20 == 0 && s.kind == Kind.DESCENT) {
            sp.sendOverlayMessage(Component.literal("Room " + Math.max(1, s.stage) + "/5 · " + (s.duration - elapsed) / 20 + "s left"));
        }
        if (s.stage > 0 && now % 10 != 0) return;
        if (s.stage == 0) {
            if (now >= s.nextStageAt) nextStage(sp, s);
            return;
        }
        if (alive(s) > 0) return;
        if (s.stage >= s.kind.stages) {
            if (s.kind == Kind.AWAKENING) secondAwakening(sp);
            end(sp.level().getServer(), s, true, "passed");
            return;
        }
        if (s.clearedAt == 0) {
            s.clearedAt = now;
            if (s.kind == Kind.DESCENT) openHatch(sp, s);
        }
        boolean breathed = now - s.clearedAt >= 40;
        boolean dropped = s.kind != Kind.DESCENT || !s.teleported || sp.getY() < roomFloor(s, s.stage).getY() + 3;
        if (breathed && dropped) {
            s.clearedAt = 0;
            nextStage(sp, s);
        }
    }

    private static void nextStage(ServerPlayer sp, Session s) {
        s.stage++;
        s.hatch = null;
        int lv = s.target.levelRequired;
        switch (s.kind) {
            case WAVES -> {
                spawnWave(s, sp, 3 + s.stage, s.stage == 3);
                Signal.toast(sp, Component.literal("Wave " + s.stage + " of 3"));
            }
            case AWAKENING -> {
                spawnWave(s, sp, 4 + s.stage, s.stage == 2);
                Signal.toast(sp, Component.literal("Wave " + s.stage + " of 2"));
            }
            case DUEL -> {
                String boss = bossFor(sp.getUUID(), s.target);
                LivingEntity b = spawnBoss(s, boss, s.centre.north(Math.min(10, s.spread)), lv);
                if (b != null) Signal.toast(sp, Component.literal(b.getDisplayName().getString() + " answers."));
            }
            case SOVEREIGN -> {
                LivingEntity b = spawnBoss(s, "awaken:sovereign_mirrors", s.centre.north(Math.min(10, s.spread)), lv);
                if (b != null) {
                    var hp = b.getAttribute(Attributes.MAX_HEALTH);
                    if (hp != null) hp.addOrUpdateTransientModifier(new AttributeModifier(Awaken.id("trial_echo"), -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                    b.setHealth(b.getMaxHealth());
                    b.setCustomName(Component.literal("Echo of " + sovereignName()));
                    Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.REVELATION, Component.literal("Echo of " + sovereignName()))
                            .body(Component.literal("“" + sovereignIntro(sp.level().getServer()) + "”")).priority(8).ttl(20 * 8).build());
                }
            }
            case DESCENT -> {
                BlockPos floor = roomFloor(s, s.stage - 1);
                for (int i = 0; i < 2 + s.stage; i++) {
                    BlockPos at = floor.offset(-4 + (i * 3) % 9, 1, -4 + (i * 5) % 9);
                    track(s, MobHooks.spawnThemed(s.level, themeFor(sp.getUUID(), s.target), MobRole.Role.NORMAL, at, lv, Set.of(), "trial:" + s.player), sp);
                }
                if (s.stage == 5) track(s, MobHooks.spawnThemed(s.level, themeFor(sp.getUUID(), s.target), MobRole.Role.ELITE, floor.above(), lv, Set.of(), "trial:" + s.player), sp);
            }
            default -> {}
        }
    }

    private static int alive(Session s) {
        int n = 0;
        for (Iterator<UUID> it = s.mobs.iterator(); it.hasNext(); ) {
            Entity e = s.level.getEntity(it.next());
            if (e instanceof LivingEntity l && l.isAlive()) n++;
            else it.remove();
        }
        return n;
    }

    private static void spawnWave(Session s, ServerPlayer sp, int count, boolean elite) {
        int lv = s.kind == Kind.PENALTY ? Math.max(5, Progression.profile(sp).level()) : s.target.levelRequired;
        String theme = s.kind == Kind.PENALTY ? "awaken:glass_desert" : themeFor(sp.getUUID(), s.target);
        for (int i = 0; i < count; i++) {
            double a = (Math.PI * 2 * i) / count + s.stage;
            BlockPos at = s.centre.offset((int) Math.round(Math.cos(a) * s.spread), 0, (int) Math.round(Math.sin(a) * s.spread));
            track(s, MobHooks.spawnThemed(s.level, theme, MobRole.Role.NORMAL, at, lv, Set.of(), "trial:" + s.player), sp);
        }
        if (elite) track(s, MobHooks.spawnThemed(s.level, theme, MobRole.Role.ELITE, s.centre.south(Math.min(8, s.spread)), lv, Set.of(), "trial:" + s.player), sp);
    }

    private static @Nullable LivingEntity spawnBoss(Session s, String bossId, BlockPos at, int lv) {
        LivingEntity b = MobHooks.spawnBoss(s.level, bossId, at, lv, "trial:" + s.player);
        if (b == null) b = MobHooks.spawnThemed(s.level, "", MobRole.Role.BOSS, at, lv, Set.of(), "trial:" + s.player);
        if (b == null) {
            // Last resort: a vanilla stand-in so a trial can never soft-lock.
            Mob m = EntityTypes.ZOMBIE.create(s.level, EntitySpawnReason.MOB_SUMMONED);
            if (m != null) {
                m.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
                MobHooks.scale(m, lv, new MobRole(MobRole.Role.BOSS, lv, "", Optional.of(bossId), "trial:" + s.player));
                s.level.addFreshEntity(m);
                b = m;
            }
        }
        ServerPlayer sp = s.level.getServer().getPlayerList().getPlayer(s.player);
        track(s, b, sp);
        return b;
    }

    private static void track(Session s, @Nullable LivingEntity e, @Nullable ServerPlayer target) {
        if (e == null) return;
        s.mobs.add(e.getUUID());
        if (e instanceof Mob m) {
            m.setPersistenceRequired();
            if (target != null) m.setTarget(target);
        }
    }

    // ---- the Second Awakening ---------------------------------------------------------------------------------------

    static void secondAwakening(ServerPlayer sp) {
        var offers = Progression.offerPaths(sp);
        var w = SignalWindow.builder(SignalWindow.Kind.REVELATION, Component.literal("The Second Awakening"));
        if (offers.isEmpty()) {
            w.body(Component.literal("No Path has claimed you yet. Keep living the way you do; one will."));
        } else {
            w.body(Component.literal("The Signal read how you've lived. These Paths answered."));
            for (var o : offers) {
                var d = Content.PATHS.get(o.path());
                w.row(Component.literal(d == null ? o.path() : d.name()), Component.literal(o.evidence().isEmpty() ? "" : o.evidence().get(0)));
            }
            w.footer(Component.literal("Choose your Path · K · Truths"));
        }
        Signal.send(sp, w.priority(9).ttl(20 * 15).build());
    }

    // ---- A-rank: the descent -----------------------------------------------------------------------------------------

    /** Floor centre of descent room k (0..4), stacked under the arena. */
    static BlockPos roomFloor(Session s, int k) {
        return s.centre.below(13 + 12 * k);
    }

    private static void buildDescent(Session s) {
        BlockState wall = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
        BlockState floor = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        BlockState lamp = Blocks.SHROOMLIGHT.defaultBlockState();
        for (int k = 0; k < 5; k++) {
            BlockPos f = roomFloor(s, k);
            for (int dx = -7; dx <= 7; dx++) {
                for (int dz = -7; dz <= 7; dz++) {
                    boolean edge = Math.abs(dx) == 7 || Math.abs(dz) == 7;
                    place(s, f.offset(dx, 0, dz), (dx + dz) % 6 == 0 && !edge ? lamp : floor);
                    if (k == 0) place(s, f.offset(dx, 12, dz), wall);
                    // Walls reach the floor of the room above, so the only way on is down.
                    if (edge) for (int y = 1; y <= 11; y++) place(s, f.offset(dx, y, dz), y == 3 && (dx + dz) % 4 == 0 ? lamp : wall);
                }
            }
        }
    }

    private static void place(Session s, BlockPos p, BlockState st) {
        if (s.level.getBlockState(p).isAir()) {
            s.level.setBlock(p, st, Block.UPDATE_CLIENTS);
            s.placed.add(p);
        }
    }

    private static void openHatch(ServerPlayer sp, Session s) {
        BlockPos f = roomFloor(s, s.stage - 1);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) s.level.setBlock(f.offset(dx, 0, dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        s.hatch = f;
        sp.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 8, 0, false, false));
        s.level.playSound(null, f, net.minecraft.sounds.SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1f, 0.6f);
        Signal.toast(sp, Component.literal("The floor opens. Down."));
    }

    // ---- ending -----------------------------------------------------------------------------------------------------

    static void end(MinecraftServer server, Session s, boolean passed, String why) {
        SESSIONS.remove(s.player);
        s.state = passed ? State.PASSED : State.FAILED;
        LAST.put(s.player, s.state);
        s.bar.removeAllPlayers();
        for (UUID u : s.mobs) {
            Entity e = s.level.getEntity(u);
            if (e != null) e.discard();
        }
        for (BlockPos p : s.placed) s.level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        if (s.cell >= 0) TrialData.get(server).free(s.cell);
        ServerPlayer sp = server.getPlayerList().getPlayer(s.player);
        if (sp != null) {
            outcome(sp, s, passed, why);
            if (s.teleported) sendHome(sp);
        }
    }

    private static void abandon(Session s) {
        SESSIONS.remove(s.player);
        s.bar.removeAllPlayers();
        if (s.cell >= 0) TrialData.get(s.level.getServer()).free(s.cell);
    }

    private static void outcome(ServerPlayer sp, Session s, boolean passed, String why) {
        HearthPlayer hp = HearthService.player(sp);
        DirectorLog.log(sp, "trial", s.kind + " " + (passed ? "passed" : "failed") + " (" + why + ")");
        if (s.kind == Kind.PENALTY) {
            hp.penaltyPending = false;
            if (passed) {
                Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("You survived the Penalty Zone"))
                        .body(Component.literal("Hardline keeps its promises. So did you.")).priority(5).build());
            } else {
                Progression.fracture(sp, 20 * 60 * 8);
                Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.WARNING, Component.literal("The desert won"))
                        .body(Component.literal("Fractured. Rest at your Hearth.")).priority(5).build());
            }
            return;
        }
        if (passed) {
            boolean real = !s.practice && s.target == Progression.rank(sp).next() && Progression.profile(sp).reassessmentReady();
            if (real) {
                Progression.passReassessment(sp);
            } else {
                Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.REASSESSMENT, Component.literal("Trial passed"))
                        .body(Component.literal("A practice run. The Signal noticed anyway.")).build());
            }
            return;
        }
        if (!s.practice) hp.trialFailDay = PlayDays.current(sp.level().getServer());
        String reason = switch (why) {
            case "time" -> "Time ran out.";
            case "fell" -> "You fell. The trial caught you before the world did.";
            default -> "You left the trial.";
        };
        Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.REASSESSMENT, Component.literal("Reassessment failed"))
                .body(Component.literal(reason + " The Monolith will listen again next play-day."))
                .priority(6).build());
    }

    /** Back to where the player stood before the trial (or their Hearth, or the world spawn). */
    static void sendHome(ServerPlayer sp) {
        MinecraftServer server = sp.level().getServer();
        TrialData data = TrialData.get(server);
        TrialData.Return r = data.returns.remove(sp.getUUID());
        data.setDirty();
        ServerLevel dest = null;
        Vec3 pos = null;
        float yaw = sp.getYRot();
        if (r != null) {
            Identifier id = Identifier.tryParse(r.dimension());
            dest = id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
            pos = new Vec3(r.x(), r.y(), r.z());
            yaw = r.yaw();
        }
        if (dest == null || dest.dimension() == DIMENSION) {
            dest = server.overworld();
            pos = HearthService.hearthOf(sp).map(h -> HearthService.safeSpotNear(server.overworld(), h.pos))
                    .orElse(Vec3.atBottomCenterOf(server.overworld().getRespawnData().pos()));
        }
        sp.teleport(new TeleportTransition(dest, pos, Vec3.ZERO, yaw, 0f, TeleportTransition.PLAY_PORTAL_SOUND));
    }

    // ---- events -----------------------------------------------------------------------------------------------------

    /** Trials never kill. Falling ends the attempt. */
    private static void onDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        Session s = SESSIONS.get(sp.getUUID());
        if (s == null) return;
        e.setCanceled(true);
        sp.setHealth(Math.max(1f, sp.getMaxHealth() * 0.5f));
        sp.clearFire();
        sp.removeAllEffects();
        end(sp.level().getServer(), s, false, "fell");
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        Session s = SESSIONS.get(e.getEntity().getUUID());
        if (s != null && e.getEntity() instanceof ServerPlayer sp) {
            end(sp.level().getServer(), s, false, "left");
        }
    }

    /** Anyone who logs in inside the trial grounds without a running trial is sent home. */
    private static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        if (sp.level().dimension() == DIMENSION && !inTrial(sp)) sp.level().getServer().execute(() -> sendHome(sp));
        if (HearthService.player(sp).penaltyPending) PENDING_CHECK.add(sp.getUUID());
    }

    // ---- Hardline Penalty Zone --------------------------------------------------------------------------------------

    private static final java.util.Set<UUID> PENDING_CHECK = new java.util.HashSet<>();

    /** Installed as {@code Regimen.penaltyHandler}: a missed Regimen on Hardline opens the Penalty Zone. */
    public static void penalty(ServerPlayer sp) {
        HearthPlayer hp = HearthService.player(sp);
        hp.penaltyPending = true;
        if (!tryStartPenalty(sp)) PENDING_CHECK.add(sp.getUUID());
    }

    static boolean tryStartPenalty(ServerPlayer sp) {
        if (inTrial(sp) || sp.level().dimension() != Level.OVERWORLD || GateHooks.inGate(sp) || sp.isSleeping()) return false;
        Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.WARNING, Component.literal("Penalty Zone"))
                .body(Component.literal("You skipped yesterday's Regimen on Hardline. Survive four minutes.")).priority(8).ttl(20 * 6).build());
        return start(sp, Kind.PENALTY, Progression.rank(sp), true);
    }

    /** Checked from the hearth upkeep: owed Penalty Zones start once the player is free. */
    public static void checkPending(MinecraftServer server) {
        if (PENDING_CHECK.isEmpty()) return;
        for (Iterator<UUID> it = PENDING_CHECK.iterator(); it.hasNext(); ) {
            ServerPlayer sp = server.getPlayerList().getPlayer(it.next());
            if (sp == null || !HearthService.player(sp).penaltyPending || AwakenConfig.DIFFICULTY.get() != AwakenConfig.Difficulty.HARDLINE) {
                if (sp != null) HearthService.player(sp).penaltyPending = false;
                it.remove();
                continue;
            }
            if (sp.getLastHurtByMobTimestamp() > 0 && sp.tickCount - sp.getLastHurtByMobTimestamp() < 200) continue;
            if (tryStartPenalty(sp)) it.remove();
        }
    }

    // ---- content picks ----------------------------------------------------------------------------------------------

    /** Gate themes by the rank they first appear at (from the plan's gate generator). */
    private static final String[][] THEMES = {
            {"awaken:bell_crypt", "awaken:bell_warden", "0"}, {"awaken:fungal_hollow", "awaken:mother_of_spores", "0"},
            {"awaken:ember_mines", "awaken:ember_tyrant", "0"}, {"awaken:hive", "awaken:hive_regent", "0"},
            {"awaken:overgrown_temple", "awaken:root_saint", "0"}, {"awaken:drowned_archive", "awaken:drowned_archivist", "1"},
            {"awaken:clockwork_foundry", "awaken:gearwright_colossus", "1"}, {"awaken:glass_desert", "awaken:glass_serpent", "1"},
            {"awaken:frost_monastery", "awaken:abbot_of_frost", "2"}, {"awaken:mirror_palace", "awaken:mirror_knight", "2"},
            {"awaken:precursor_lab", "awaken:unit_zero", "2"}, {"awaken:sky_archipelago", "awaken:sky_leviathan", "3"},
            {"awaken:abyssal_shrine", "awaken:hollow_herald", "4"}};

    private static String[] themeRow(UUID player, Rank target) {
        List<String[]> fit = new ArrayList<>();
        for (String[] t : THEMES) if (Integer.parseInt(t[2]) <= target.ordinal()) fit.add(t);
        int i = Math.floorMod(player.hashCode() * 31 + target.ordinal(), fit.size());
        return fit.get(i);
    }

    public static String themeFor(UUID player, Rank target) {
        return themeRow(player, target)[0];
    }

    public static String bossFor(UUID player, Rank target) {
        return themeRow(player, target)[1];
    }

    private static String sovereignName() {
        return "the Reflection";
    }

    private static String sovereignIntro(MinecraftServer server) {
        var res = server.getResourceManager().getResource(Awaken.id("awaken/sovereign/sovereign_mirrors.json"));
        if (res.isPresent()) {
            try (Reader r = new InputStreamReader(res.get().open(), StandardCharsets.UTF_8)) {
                JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
                if (o.has("intro")) return o.get("intro").getAsString();
            } catch (Exception ignored) {
                // fall through to the default line
            }
        }
        return "I'm the part of you that stayed behind. Hello.";
    }

}
