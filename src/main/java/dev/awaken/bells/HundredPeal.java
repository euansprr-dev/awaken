package dev.awaken.bells;

import dev.awaken.Awaken;
import dev.awaken.core.PlayDays;
import dev.awaken.core.api.BellHooks;
import dev.awaken.core.api.CreatureTag;
import dev.awaken.core.api.MobHooks;
import dev.awaken.core.api.MobRole;
import dev.awaken.fault.Fault;
import dev.awaken.fault.Faults;
import dev.awaken.hearth.HearthRecord;
import dev.awaken.hearth.HearthService;
import dev.awaken.kin.KinApi;
import dev.awaken.kin.KinHome;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The Hundred Peal and what follows it (SPEC §43.14; JOURNEY part 4). With all hundred bells in the Great Belfry, the
 * next dawn or dusk song is the Hundred Peal: the one big moment that doesn't glitch. Every player in the overworld hears
 * the whole song, any Fault on their screen mends, and the System's glass turns warm; every beast in hearing comes to the
 * tower; for everyone who lives there every kind they've met comes a step closer at once, and the Kinsong is theirs.
 * Thirty seconds later the Deep answers with a Great Fault, and from then on the world is the Deep's answer: gates break
 * sooner and harder, the Gnawed spread where the Kinsong doesn't reach, and the Sovereigns wake angrier.
 */
final class HundredPeal {
    private HundredPeal() {}

    static final int DEEP_DELAY = 20 * 30, LAND = 512;
    /** The Kinsong: how far it reaches, how long the land's beasts stay calm, and how long till the Deep's things come. */
    static final int KINSONG_REACH = 48, KINSONG_CALM = 20 * 60, KINSONG_HUNTED = 20 * 30, KINSONG_AGAIN = 20 * 60 * 5;
    static final String KINSONG = "kinsong", KINSONG_NAME = "The Kinsong",
            KINSONG_WORDS = "Calms the land's beasts round you: a stampede stops, a frightened herd settles, an angry beast lies down, and a Gnawed one is let go.";
    /** The Deep's own creatures, which come for whoever plays the Kinsong. */
    static final List<String> DEEP_THEMES = List.of("awaken:abyssal_shrine", "awaken:nightmare", "awaken:bell_crypt");

    /** A Deep answer on its way to one tower's hearers, or Deep things on their way to one Kinsong player. Not saved. */
    private record Answer(BlockPos at, long when, List<UUID> heard) {}
    private record Hunted(UUID player, long when) {}

    private static final List<Answer> ANSWERS = new ArrayList<>();
    private static final List<Hunted> HUNTED = new ArrayList<>();

    static void serverStopped() {
        ANSWERS.clear();
        HUNTED.clear();
    }

    /** Whether this home's next song is its Hundred Peal. */
    static boolean due(BellWorld.Home home) {
        return home.stage >= 3 && home.pealed < 0 && home.hung.size() >= 100;
    }

    /** The Hundred Peal, rung at {@code at} (the Great Belfry's bells) in place of a song. */
    static void ring(ServerLevel level, HearthRecord h, BellWorld.Home home, BlockPos at) {
        MinecraftServer server = level.getServer();
        long day = PlayDays.current(server), now = level.getGameTime();
        BellWorld w = BellWorld.get(server);
        home.pealed = day;
        if (w.deepAnswered < 0) w.deepAnswered = day;
        w.setDirty();
        // the whole song, heard everywhere in the world
        long lo = 0, hi = 0;
        for (int no : home.hung) {
            if (no < 64) lo |= 1L << no;
            else hi |= 1L << (no - 64);
        }
        BellNet.Song s = new BellNet.Song(at, lo, hi);
        List<UUID> heard = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            PacketDistributor.sendToPlayer(p, s);
            Faults.mend(p);
            heard.add(p.getUUID());
            boolean ours = h.belongs(p.getUUID());
            Signal.send(p, SignalWindow.builder(SignalWindow.Kind.REVELATION, Component.literal("The Hundred Peal"))
                    .body(Component.literal(ours ? "All hundred of your bells are ringing together. Everything in hearing has stopped to listen."
                            : "Somewhere far off, someone's bells are ringing all hundred together. For a while the whole land is quiet."))
                    .priority(10).ttl(20 * 14).build());
        }
        long until = now + 100L * BellCatalog.STEP + 200;
        KinHome.song(level, h.pos, at, 100, until);
        int came = KinApi.peal(level, at, BellTower.HEAR, until);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!h.belongs(p.getUUID())) continue;
            pealed(p);
            int kinds = KinApi.hundredPeal(p);
            Progression.bestMoment(p, "Rang the Hundred Peal");
            Bells.say(p, "[" + (kinds == 0 ? "The land's beasts heard it." : kinds == 1 ? "One kind you've met came a step closer to you at once."
                    : kinds + " kinds you've met came a step closer to you at once.") + (came > 0 ? " The wild ones in hearing are coming to the tower." : "")
                    + " You have the Kinsong now: ring it from your handbell.]");
        }
        level.sendParticles(ParticleTypes.END_ROD, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 120, 4, 3, 4, 0.03);
        ANSWERS.add(new Answer(at.immutable(), now + DEEP_DELAY, heard));
        Awaken.LOG.info("[bells] the Hundred Peal rang at {} on play-day {} ({} heard, {} wild kin came)", at.toShortString(), day, heard.size(), came);
    }

    /** A member of a home that rang the Hundred Peal: the Kinsong is theirs, and the Wake is open to them. */
    static void pealed(ServerPlayer p) {
        BellBook b = Bells.book(p);
        if (b.pealed) return;
        b.pealed = true;
        Bells.save(p, b);
    }

    static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        long now = level.getGameTime();
        ANSWERS.removeIf(a -> {
            if (now < a.when()) return false;
            answer(level, a);
            return true;
        });
        HUNTED.removeIf(hu -> {
            if (now < hu.when()) return false;
            ServerPlayer p = server.getPlayerList().getPlayer(hu.player());
            if (p != null && p.isAlive() && p.level() == level) hunt(level, p);
            return true;
        });
    }

    /** The Deep answers the song: a Great Fault for everyone who heard it, cut to the tower. */
    private static void answer(ServerLevel level, Answer a) {
        Vec3 tower = Vec3.atCenterOf(a.at());
        Fault f = new Fault(Fault.Size.GREAT, "deep_answer",
                List.of("[ERROR] … SIGNAL HEARD BELOW", "100 / 100 · ANSWERING ▮▮▮▮▮"), "[ERROR] it knows where the song came from",
                "The Deep heard your bells. Gates will break sooner now, and harder, and the Gnawed will spread wherever the Kinsong doesn't reach. "
                        + "It's pushing back from the very bottom, from the Seed. That's where this ends.",
                tower.add(26, 10, 26), tower, 90, false);
        for (UUID id : a.heard())
            if (level.getServer().getPlayerList().getPlayer(id) instanceof ServerPlayer p && p.level() == level) Faults.play(p, f);
        Awaken.LOG.info("[bells] the Deep answered the Hundred Peal at {}", a.at().toShortString());
    }

    // ---- the Kinsong --------------------------------------------------------------------------------------------------

    /** The Kinsong rung from a handbell: the land's beasts round you calm, the Gnawed let go, and the Deep takes note. */
    static void kinsong(ServerLevel level, ServerPlayer p) {
        BlockPos at = p.blockPosition();
        long now = level.getGameTime();
        int kin = KinApi.kinsong(level, p, at, KINSONG_REACH, KINSONG_CALM);
        int calmed = 0;
        for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(at).inflate(KINSONG_REACH), Mob::isAlive)) {
            CreatureTag.Kind k = CreatureTag.of(m).kind();
            // the land's own only: the Deep's (monsters, gate things, wardens, bosses) and camps don't hear it as a song
            if (m instanceof Enemy || m instanceof dev.awaken.kin.KinBeast || k == CreatureTag.Kind.MONSTER || k == CreatureTag.Kind.GATE
                    || k == CreatureTag.Kind.WARDEN || k == CreatureTag.Kind.BOSS || k == CreatureTag.Kind.CAMP) continue;
            boolean riled = m.getTarget() != null || m.getLastHurtByMob() != null || m.isAggressive();
            m.setTarget(null);
            m.setLastHurtByMob(null);
            m.setAggressive(false);
            m.getNavigation().stop();
            if (riled) {
                m.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20 * 15, 3));
                calmed++;
            }
        }
        int stampedes = dev.awaken.core.api.LivingHooks.calm(level, at, KINSONG_REACH + 32);
        level.sendParticles(ParticleTypes.NOTE, p.getX(), p.getY() + 2, p.getZ(), 24, 3, 1, 3, 1);
        level.playSound(null, at, BellContent.TOLL.get(), SoundSource.PLAYERS, 3F, 1.2F);
        if (level.dimension() == Level.OVERWORLD) {
            BellWorld w = BellWorld.get(level.getServer());
            long day = PlayDays.current(level.getServer());
            w.kinsongs.removeIf(s -> s.day() < day - 8);
            w.kinsongs.add(new BellWorld.Song(at.getX(), at.getZ(), day));
            while (w.kinsongs.size() > BellWorld.KINSONGS) w.kinsongs.removeFirst();
            w.setDirty();
        }
        StringBuilder said = new StringBuilder("The Kinsong. ");
        if (stampedes > 0) said.append("The stampede slows and stops. ");
        if (calmed > 0) said.append(calmed == 1 ? "An angry beast settles. " : calmed + " beasts settle. ");
        if (kin > 0) said.append(kin == 1 ? "A beast of the land comes to listen." : kin + " beasts of the land come to listen.");
        if (stampedes == 0 && calmed == 0 && kin == 0) said.append("Everything round you is already calm.");
        p.sendOverlayMessage(Component.literal(said.toString().trim()));
        // the Deep's things hate it, and come for whoever plays it (once in five minutes, after the Deep has answered)
        BellBook b = Bells.book(p);
        if (BellHooks.deepAnswered(level.getServer()) && level.dimension() == Level.OVERWORLD && now >= b.kinsongAt && !p.isCreative() && !p.isSpectator()) {
            b.kinsongAt = now + KINSONG_AGAIN;
            Bells.save(p, b);
            HUNTED.add(new Hunted(p.getUUID(), now + KINSONG_HUNTED));
        }
    }

    /** Two to four of the Deep's creatures at the player's rank, come up out of the ground 18 to 26 blocks off. */
    private static void hunt(ServerLevel level, ServerPlayer p) {
        var r = level.getRandom();
        Rank rank = Progression.rank(p);
        int lv = Progression.contentLevel(rank), n = 2 + Math.min(2, rank.ordinal() / 2);
        String theme = DEEP_THEMES.get(r.nextInt(DEEP_THEMES.size()));
        int made = 0;
        for (int i = 0; i < n; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = 18 + r.nextInt(9);
            BlockPos col = BlockPos.containing(p.getX() + Math.cos(a) * d, p.getY(), p.getZ() + Math.sin(a) * d);
            if (!level.isLoaded(col)) continue;
            BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, col);
            LivingEntity e = MobHooks.spawnThemed(level, theme, i == 0 ? MobRole.Role.ELITE : MobRole.Role.NORMAL, at, lv, Set.of(), "");
            if (e instanceof Mob m) {
                m.setTarget(p);
                level.sendParticles(ParticleTypes.SQUID_INK, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 20, 0.5, 0.6, 0.5, 0.02);
                made++;
            }
        }
        if (made > 0) Bells.say(p, "[Something heard the Kinsong that hates it. It's coming up out of the ground.]");
    }

    // ---- the hooks other modules read ---------------------------------------------------------------------------------

    static final class Access implements BellHooks.Access {
        @Override
        public boolean deepAnswered(MinecraftServer server) {
            return BellWorld.get(server).deepAnswered >= 0;
        }

        @Override
        public boolean pealed(ServerPlayer player) {
            if (Bells.book(player).pealed) return true;
            Optional<HearthRecord> h = HearthService.data(player.level().getServer()).home(player.getUUID());
            BellWorld.Home home = h.map(r -> BellWorld.get(player.level().getServer()).peek(r.pos)).orElse(null);
            return home != null && home.pealed >= 0;
        }

        @Override
        public String belfry(ServerPlayer player) {
            return GreatBelfry.line(player);
        }

        @Override
        public void told(ServerPlayer player) {
            Bells.told(player);
        }

        @Override
        public boolean quiet(ServerLevel level, BlockPos at) {
            return Bells.quiet(level, at);
        }

        @Override
        public boolean kinsongNear(ServerLevel level, BlockPos at, int reach, int days) {
            if (level.dimension() != Level.OVERWORLD) return false;
            BellWorld w = BellWorld.get(level.getServer());
            long today = PlayDays.current(level.getServer());
            long r2 = (long) reach * reach;
            for (BellWorld.Song s : w.kinsongs) {
                if (s.day() < today - days) continue;
                long dx = s.x() - at.getX(), dz = s.z() - at.getZ();
                if (dx * dx + dz * dz <= r2) return true;
            }
            return false;
        }
    }
}
