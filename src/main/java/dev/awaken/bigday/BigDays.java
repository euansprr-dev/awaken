package dev.awaken.bigday;

import dev.awaken.Awaken;
import dev.awaken.bigday.BigDayData.Broken;
import dev.awaken.bigday.BigDayData.Home;
import dev.awaken.bigday.BigDayData.Raid;
import dev.awaken.core.PlayDays;
import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.Destinations;
import dev.awaken.core.api.GateHooks;
import dev.awaken.core.api.MobHooks;
import dev.awaken.core.api.MobRole;
import dev.awaken.fault.Fault;
import dev.awaken.fault.Faults;
import dev.awaken.hearth.HearthRecord;
import dev.awaken.hearth.HearthService;
import dev.awaken.hearth.trial.Trials;
import dev.awaken.kin.KinHome;
import dev.awaken.kin.KinWorld;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Big Days (SPEC §43.8; JOURNEY parts 5 and 6). Stage 3 brings the raid: from the first Great Bell (D rank until the
 * Ringer hands over the Tenor), never in a world's first two hours, at most one Big Day every five play-days, a play-day
 * or two after it's due and never said when. The day before, the beasts are frightened of something in one direction
 * and wild ones run past the other way. At dusk a gate breaks: a Great Fault the first time (a Fault after), then waves
 * up the land to the walls and the raid's Lord last. Dawn mends it: the beasts come out and check on you. If nobody was
 * home, the raid still comes: it breaks a few doors and windows on its side (put back over the next day) and carries
 * off a chest's worth of food, dropped in a chest along its trail, never a beast and never the Forge's racks.
 */
public final class BigDays {
    private BigDays() {}

    /** A world's first two hours of play have no Big Days; then at most one every five play-days. */
    static final long FIRST = 2 * 60 * 60 * 20L;
    static final int GAP = 5;
    /** Dusk and dawn (ticks into the day): the raid's night. */
    static final int DUSK = 12200, DAWN = 23200;
    /** Within this of the Hearth counts as home: the Fault's reach, and who the raid comes for. */
    static final int REACH = 160;
    /** Normals and elites per wave, first to last; then the Lord. */
    static final int[] WAVES = {4, 6, 8};
    static final int[] ELITES = {0, 1, 2};
    /** Waves come out of the dark this far along the breach's bearing. */
    static final int COME_FROM = 52;
    /** The first raid is tuned for a level-12 player in D gear (SPEC §43.8). */
    static final int FIRST_LEVEL = 12;
    /** An away raid breaks at most this many blocks, and carries at most this many stacks. */
    static final int MAX_BROKEN = 10, CARRIED = 9;
    /** One broken block is put back this often. */
    static final int MEND_EVERY = 300;
    static final String TAG = "awaken_raid", LORD = "awaken_raid_lord";

    // ---- what other modules ask -----------------------------------------------------------------------------------

    /** A raid is coming to this home tomorrow or tonight: its beasts are uneasy. */
    public static boolean uneasy(ServerLevel level, @Nullable BlockPos anchor) {
        if (anchor == null || level.dimension() != Level.OVERWORLD) return false;
        Home h = BigDayData.get(level.getServer()).peek(anchor);
        return h != null && h.raid != null && (h.raid.phase == Raid.WARNED || h.raid.phase == Raid.NIGHT);
    }

    /** A raid is being fought at this home right now. */
    public static boolean raiding(ServerLevel level, @Nullable BlockPos anchor) {
        if (anchor == null) return false;
        Home h = BigDayData.get(level.getServer()).peek(anchor);
        return h != null && h.raid != null && h.raid.phase == Raid.NIGHT;
    }

    // ---- the clock ------------------------------------------------------------------------------------------------

    static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 7) return;
        ServerLevel level = server.overworld();
        BigDayData d = BigDayData.get(server);
        long today = PlayDays.current(server), play = PlayDays.playTicks(server);
        long tod = level.getOverworldClockTime() % PlayDays.DAY;
        List<Long> seen = new ArrayList<>();
        for (HearthRecord h : HearthService.data(server).all()) {
            if (!HearthService.OVERWORLD.equals(h.dimension)) continue;
            seen.add(h.pos.asLong());
            Home home = d.home(h.pos);
            step(server, level, d, h, home, today, play, tod);
        }
        // a Hearth taken up: its Big Days go with it
        if (d.homes.keySet().removeIf(k -> !seen.contains(k))) d.setDirty();
    }

    private static void step(MinecraftServer server, ServerLevel level, BigDayData d, HearthRecord h, Home home, long today, long play, long tod) {
        ServerPlayer owner = server.getPlayerList().getPlayer(h.owner);
        if (!home.ready && owner != null && Progression.profile(owner).rank().ordinal() >= Rank.D.ordinal()) {
            home.ready = true;
            d.setDirty();
        }
        mend(level, d, home);
        Raid r = home.raid;
        if (r == null) {
            if (!home.ready || level.getDifficulty() == Difficulty.PEACEFUL || play < FIRST || today - home.last < GAP) return;
            if (home.next < 0) {
                // due: a play-day or two from now, never said when
                home.next = today + 1 + level.getRandom().nextInt(home.raids == 0 ? 2 : 3);
                d.setDirty();
            }
            if (home.next == today + 1 && tod >= PlayDays.MORNING && tod < DUSK) warn(level, d, h, home, today + 1);
            else if (home.next <= today) home.next = today + 1; // missed its warning (nobody's clock ran): warn tomorrow
            return;
        }
        switch (r.phase) {
            case Raid.WARNED -> {
                if (today > r.day) {
                    // the night came and went with the server down, or slept through: it came while nobody was home
                    r.phase = Raid.AWAY;
                    away(level, d, h, home, r);
                } else if (today == r.day && tod >= DUSK && tod < DAWN) dusk(level, d, h, home, r);
                streamDone(level, r);
            }
            case Raid.NIGHT -> night(level, d, h, home, r, today, tod);
            case Raid.AWAY -> {
                if (today > r.day || tod >= DAWN) away(level, d, h, home, r);
            }
            default -> home.raid = null;
        }
    }

    /** The day before: the warning (a note, never an order), and wild beasts running past the other way. */
    private static void warn(ServerLevel level, BigDayData d, HearthRecord h, Home home, long day) {
        boolean first = home.raids == 0;
        BlockPos breach = breach(level, h, home.raids);
        String theme = theme(level, h, breach);
        int lv = FIRST_LEVEL;
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(h.owner);
        if (!first && owner != null) lv = Math.max(FIRST_LEVEL, Progression.profile(owner).level());
        Raid r = new Raid(h.owner, day, first, breach, theme, lv, Raid.WARNED, 0, false, -1);
        home.raid = r;
        d.setDirty();
        String dir = compass(h.pos, breach);
        boolean beasts = !KinWorld.get(level.getServer()).at(h.pos).isEmpty();
        for (ServerPlayer p : household(level, h, Integer.MAX_VALUE)) {
            Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Something to the " + dir))
                    .body(Component.literal(beasts ? "Your beasts are frightened of something to the " + dir + "."
                            : "The wild things are all moving away from the " + dir + ". Something's wrong out there."))
                    .priority(5).ttl(20 * 10).build());
        }
        // the wild runs the other way, past the house
        if (!household(level, h, REACH).isEmpty()) {
            Vec3 b = bearing(h.pos, breach);
            BlockPos from = h.pos.offset((int) (b.x * 44), 0, (int) (b.z * 44)), to = h.pos.offset((int) (-b.x * 90), 0, (int) (-b.z * 90));
            if (level.isLoaded(from)) r.stream.addAll(KinHome.stream(level, from, to, 4));
        }
        Awaken.LOG.info("[bigday] raid warned at {} for play-day {} from {} ({})", h.pos.toShortString(), day, breach.toShortString(), theme);
    }

    /** Dusk: the gate breaks. Somebody home: the Fault, then the night. Nobody: it comes anyway. */
    private static void dusk(ServerLevel level, BigDayData d, HearthRecord h, Home home, Raid r) {
        d.setDirty();
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            // nothing hostile walks in Peaceful: the gate settles again
            home.raid = null;
            home.next = -1;
            home.last = r.day;
            return;
        }
        List<ServerPlayer> here = household(level, h, REACH);
        if (here.isEmpty()) {
            r.phase = Raid.AWAY;
            return;
        }
        r.phase = Raid.NIGHT;
        r.faultAt = level.getGameTime();
        Vec3 b = bearing(h.pos, r.breach);
        int dist = (int) Math.round(Math.sqrt(h.pos.distSqr(r.breach)) / 10.0) * 10;
        String dir = compass(h.pos, r.breach);
        Vec3 home3 = Vec3.atCenterOf(h.pos);
        Fault f = new Fault(r.first ? Fault.Size.GREAT : Fault.Size.FAULT, r.first ? "first_raid" : "",
                List.of("[ERROR] … BREACH", dist + " BLOCKS " + dir.toUpperCase(java.util.Locale.ROOT) + " · COUNT ▮▮▮"),
                r.first ? "[ERROR] not this one again" : "",
                "A gate broke to the " + dir + ". They're coming here.",
                home3.add(-b.x * 8, 20, -b.z * 8), home3.add(b.x * 120, 6, b.z * 120), 90, true);
        for (ServerPlayer p : household(level, h, REACH)) Faults.play(p, f);
        // the beasts run for their nooks and stay there till first light
        KinHome.hide(level, h.pos, level.getGameTime() + (PlayDays.DAY - level.getOverworldClockTime() % PlayDays.DAY) + 200);
        Awaken.LOG.info("[bigday] raid at {}: the gate broke {} blocks {} ({} home)", h.pos.toShortString(), dist, dir, here.size());
    }

    /** When the held cut looks out: the gate's light bursting on the land, and the first of them coming. */
    static int burstAt(boolean first) {
        Fault.Size s = first ? Fault.Size.GREAT : Fault.Size.FAULT;
        return s.tell + s.tear + Math.max(s.black, 60);
    }

    private static void night(ServerLevel level, BigDayData d, HearthRecord h, Home home, Raid r, long today, long tod) {
        long now = level.getGameTime();
        boolean dawn = today > r.day || tod >= DAWN || tod < DUSK - 400;
        List<Mob> mobs = mobs(level, h.pos);
        if (dawn) {
            dawn(level, d, h, home, r, mobs);
            return;
        }
        Vec3 b = bearing(h.pos, r.breach);
        if (!r.burst && now >= r.faultAt + burstAt(r.first)) {
            r.burst = true;
            BlockPos light = ground(level, h.pos.offset((int) (b.x * 110), 0, (int) (b.z * 110)));
            if (light != null) {
                LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.EVENT);
                if (bolt != null) {
                    bolt.setVisualOnly(true);
                    bolt.snapTo(light.getX() + 0.5, light.getY(), light.getZ() + 0.5);
                    level.addFreshEntity(bolt);
                }
                level.sendParticles(ParticleTypes.END_ROD, light.getX() + 0.5, light.getY() + 12, light.getZ() + 0.5, 120, 1.5, 12, 1.5, 0.02);
            }
        }
        if (r.burst && r.wave < WAVES.length && (r.wave == 0 || mobs.size() <= 2 || now >= r.nextWaveAt)) {
            wave(level, h, r, b, WAVES[r.wave], ELITES[r.wave]);
            r.wave++;
            r.nextWaveAt = now + 20 * 60;
            d.setDirty();
        } else if (r.wave >= WAVES.length && !r.lordOut && (mobs.size() <= 1 || now >= r.nextWaveAt)) {
            lord(level, h, r, b);
            r.lordOut = true;
            r.wave = WAVES.length + 1;
            d.setDirty();
        }
        // they come for whoever holds the home, else for the home itself
        List<ServerPlayer> here = household(level, h, REACH);
        if (now % 40 == 7) for (Mob m : mobs) {
            if (m.getTarget() != null && m.getTarget().isAlive()) continue;
            ServerPlayer near = null;
            for (ServerPlayer p : here) if (p.distanceToSqr(m) < 48 * 48 && (near == null || p.distanceToSqr(m) < near.distanceToSqr(m))) near = p;
            if (near != null) m.setTarget(near);
            else m.getNavigation().moveTo(h.pos.getX() + 0.5, h.pos.getY(), h.pos.getZ() + 0.5, 1.0);
        }
    }

    private static void wave(ServerLevel level, HearthRecord h, Raid r, Vec3 b, int normals, int elites) {
        for (int i = 0; i < normals + elites; i++) {
            double side = (i - (normals + elites) / 2.0) * 4.5;
            BlockPos at = ground(level, h.pos.offset((int) (b.x * COME_FROM - b.z * side), 0, (int) (b.z * COME_FROM + b.x * side)));
            if (at == null) continue;
            LivingEntity e = MobHooks.spawnThemed(level, r.theme, i < normals ? MobRole.Role.NORMAL : MobRole.Role.ELITE, at, r.level, Set.of(), "raid:" + h.owner);
            mark(e, TAG);
        }
    }

    private static void lord(ServerLevel level, HearthRecord h, Raid r, Vec3 b) {
        BlockPos at = ground(level, h.pos.offset((int) (b.x * COME_FROM), 0, (int) (b.z * COME_FROM)));
        if (at == null) return;
        LivingEntity e = MobHooks.spawnThemed(level, r.theme, MobRole.Role.BREAK, at, r.level, Set.of(), "raid:" + h.owner);
        mark(e, TAG);
        mark(e, LORD);
        for (ServerPlayer p : household(level, h, REACH))
            Signal.send(p, SignalWindow.builder(SignalWindow.Kind.WARNING, Component.literal("The raid's Lord")).body(Component.literal("It came out last. It's coming up the land now.")).priority(6).ttl(20 * 6).build());
    }

    private static void mark(@Nullable LivingEntity e, String tag) {
        if (e == null) return;
        e.addTag(tag);
        if (e instanceof Mob m) m.setPersistenceRequired();
    }

    /** The Lord falls: its heart, and the raid is as good as held. */
    static void died(LivingDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (!(dead.level() instanceof ServerLevel level) || !dead.entityTags().contains(LORD)) return;
        dead.spawnAtLocation(level, new ItemStack(CoreApi.LORD_HEART.get()));
        BigDayData d = BigDayData.get(level.getServer());
        for (Home home : d.homes.values())
            if (home.raid != null && home.raid.phase == Raid.NIGHT) {
                home.raid.lordDown = true;
                d.setDirty();
            }
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, dead.getBoundingBox().inflate(96)))
            Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("The Lord is down")).body(Component.literal("Hold till first light.")).priority(6).ttl(20 * 6).build());
    }

    /** First light: what's left melts back into the dark, the mend, and the beasts come out. */
    private static void dawn(ServerLevel level, BigDayData d, HearthRecord h, Home home, Raid r, List<Mob> mobs) {
        for (Mob m : mobs) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, m.getX(), m.getY() + 0.8, m.getZ(), 12, 0.3, 0.5, 0.3, 0.01);
            m.discard();
        }
        List<ServerPlayer> here = household(level, h, REACH);
        Faults.mendNear(level, h.pos, REACH);
        if (r.lordDown) {
            KinHome.checkOn(level, h.pos, here);
            for (ServerPlayer p : here) {
                Progression.count(p, "awaken:raids_held");
                Progression.bestMoment(p, "Held my home through its first raid");
            }
        } else KinHome.hide(level, h.pos, 0);
        for (ServerPlayer p : here)
            Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(r.lordDown ? "The raid is held" : "First light"))
                    .body(Component.literal(r.lordDown ? "They're gone. Your home stood." : "What was left of them went back into the dark."))
                    .priority(6).ttl(20 * 8).build());
        finish(d, home, r);
    }

    /** Nobody home: it breaks what it can reach on its side and carries off what it can, both put right. */
    private static void away(ServerLevel level, BigDayData d, HearthRecord h, Home home, Raid r) {
        Vec3 b = bearing(h.pos, r.breach);
        int radius = Math.min(h.radius(), 48);
        List<Broken> broke = new ArrayList<>();
        // nearest the breach first: the side they came from
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos q : BlockPos.betweenClosed(h.pos.offset(-radius, -4, -radius), h.pos.offset(radius, 8, radius))) {
            if (!level.isLoaded(q)) continue;
            double dot = (q.getX() - h.pos.getX()) * b.x + (q.getZ() - h.pos.getZ()) * b.z;
            if (dot <= 0) continue;
            if (breakable(level.getBlockState(q))) candidates.add(q.immutable());
        }
        candidates.sort((p1, p2) -> Double.compare(-dotOf(h.pos, p1, b), -dotOf(h.pos, p2, b)));
        for (BlockPos q : candidates) {
            if (broke.size() >= MAX_BROKEN) break;
            BlockState s = level.getBlockState(q);
            if (!breakable(s)) continue;
            broke.add(new Broken(q, s));
            level.setBlock(q, Blocks.AIR.defaultBlockState(), 2 | 16);
        }
        home.mending.addAll(broke);
        // a chest's worth of food, dropped in a chest along their trail
        List<ItemStack> carried = carry(level, h, radius);
        BlockPos cache = null;
        if (!carried.isEmpty()) {
            BlockPos at = ground(level, h.pos.offset((int) (b.x * 70), 0, (int) (b.z * 70)));
            if (at != null) {
                level.setBlock(at, Blocks.CHEST.defaultBlockState(), 3);
                if (level.getBlockEntity(at) instanceof ChestBlockEntity c) {
                    for (int i = 0; i < carried.size() && i < c.getContainerSize(); i++) c.setItem(i, carried.get(i));
                    cache = at;
                }
            }
        }
        for (ServerPlayer p : household(level, h, Integer.MAX_VALUE)) {
            String what = broke.isEmpty() ? "" : "They broke " + broke.size() + (broke.size() == 1 ? " door or window" : " doors and windows") + " on the " + compass(h.pos, r.breach) + " side. It'll be put right over the day. ";
            String took = cache == null ? "" : "They carried off food from your larder and dropped it in a chest along their trail. It's on your card.";
            if (cache != null) Destinations.set(p, new Destinations.Target("What the raid carried off", Level.OVERWORLD.identifier(), cache, "raid"));
            Signal.send(p, SignalWindow.builder(SignalWindow.Kind.WARNING, Component.literal("A raid came while you were away"))
                    .body(Component.literal((what + took).isEmpty() ? "Your home held. Nothing was taken." : what + took)).priority(6).ttl(20 * 12).build());
        }
        Awaken.LOG.info("[bigday] away raid at {}: {} broken, {} stacks carried", h.pos.toShortString(), broke.size(), carried.size());
        finish(d, home, r);
    }

    private static double dotOf(BlockPos o, BlockPos q, Vec3 b) {
        return (q.getX() - o.getX()) * b.x + (q.getZ() - o.getZ()) * b.z;
    }

    /** Doors, windows and fences: what a raid breaks. Never a chest, a rack, a bed or the fire. */
    static boolean breakable(BlockState s) {
        return s.is(BlockTags.DOORS) || s.is(BlockTags.TRAPDOORS) || s.is(BlockTags.FENCE_GATES) || s.is(BlockTags.WOODEN_FENCES)
                || s.is(Tags.BlockItems.GLASS_BLOCKS.block()) || s.is(Tags.BlockItems.GLASS_PANES.block());
    }

    /** The larder: the chest at home with the most food in it; at most {@link #CARRIED} stacks of food out of it. */
    static List<ItemStack> carry(ServerLevel level, HearthRecord h, int radius) {
        ChestBlockEntity best = null;
        int most = 0;
        for (BlockPos q : BlockPos.betweenClosed(h.pos.offset(-radius, -6, -radius), h.pos.offset(radius, 10, radius))) {
            if (!level.isLoaded(q) || !(level.getBlockEntity(q) instanceof ChestBlockEntity c)) continue;
            int food = 0;
            for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).has(DataComponents.FOOD)) food++;
            if (food > most) {
                most = food;
                best = c;
            }
        }
        List<ItemStack> out = new ArrayList<>();
        if (best == null) return out;
        for (int i = 0; i < best.getContainerSize() && out.size() < CARRIED; i++) {
            ItemStack s = best.getItem(i);
            if (!s.has(DataComponents.FOOD)) continue;
            out.add(s.copy());
            best.setItem(i, ItemStack.EMPTY);
        }
        best.setChanged();
        return out;
    }

    private static void finish(BigDayData d, Home home, Raid r) {
        home.last = r.day;
        home.next = -1;
        home.raids++;
        home.raid = null;
        d.setDirty();
    }

    /** What a raid broke comes back, a block at a time, where nothing has been put since. */
    private static void mend(ServerLevel level, BigDayData d, Home home) {
        if (home.mending.isEmpty() || level.getGameTime() % MEND_EVERY >= 20) return;
        Broken b = home.mending.getFirst();
        if (!level.isLoaded(b.pos())) return;
        home.mending.removeFirst();
        if (level.getBlockState(b.pos()).isAir()) {
            level.setBlock(b.pos(), b.state(), 2 | 16);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, b.pos().getX() + 0.5, b.pos().getY() + 0.5, b.pos().getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0);
        }
        d.setDirty();
    }

    /** Wild beasts that ran past are gone once they've run their course. */
    private static void streamDone(ServerLevel level, Raid r) {
        if (r.stream.isEmpty()) return;
        r.stream.removeIf(id -> {
            Entity e = level.getEntity(id);
            if (e == null) return true;
            if (e instanceof dev.awaken.kin.KinBeast k && k.hideUntil < level.getGameTime()) {
                e.discard();
                return true;
            }
            return false;
        });
    }

    // ---- where ----------------------------------------------------------------------------------------------------

    /** The gate it comes from: the nearest standing one within 700 blocks, else one 400 blocks off on a bearing of its own. */
    static BlockPos breach(ServerLevel level, HearthRecord h, int salt) {
        GateHooks.GateView best = null;
        for (GateHooks.GateView g : GateHooks.near(level, h.pos, 700))
            if (best == null || g.pos().distSqr(h.pos) < best.pos().distSqr(h.pos)) best = g;
        if (best != null && best.pos().distSqr(h.pos) > 120 * 120) return best.pos();
        double a = Math.floorMod(h.pos.asLong() * 31 + salt, 8) * Math.PI / 4;
        return h.pos.offset((int) (Math.cos(a) * 400), 0, (int) (Math.sin(a) * 400));
    }

    private static String theme(ServerLevel level, HearthRecord h, BlockPos breach) {
        for (GateHooks.GateView g : GateHooks.near(level, breach, 8)) if (!g.theme().isEmpty()) return g.theme();
        return Trials.themeFor(h.owner, Rank.D);
    }

    static Vec3 bearing(BlockPos from, BlockPos to) {
        Vec3 v = new Vec3(to.getX() - from.getX(), 0, to.getZ() - from.getZ());
        return v.lengthSqr() < 1 ? new Vec3(1, 0, 0) : v.normalize();
    }

    static String compass(BlockPos from, BlockPos to) {
        double a = Math.atan2(to.getX() - from.getX(), -(to.getZ() - from.getZ()));
        int i = Math.floorMod((int) Math.round(a / (Math.PI / 4)), 8);
        return new String[]{"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"}[i];
    }

    private static @Nullable BlockPos ground(ServerLevel level, BlockPos near) {
        if (!level.isLoaded(near)) return null;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, near.getX(), near.getZ());
        return y <= level.getMinY() ? null : new BlockPos(near.getX(), y, near.getZ());
    }

    private static List<Mob> mobs(ServerLevel level, BlockPos anchor) {
        return level.getEntitiesOfClass(Mob.class, new AABB(anchor).inflate(REACH), m -> m.isAlive() && m.entityTags().contains(TAG));
    }

    /** The household (owner and members) online, in the overworld, within {@code reach} of the Hearth. */
    static List<ServerPlayer> household(ServerLevel level, HearthRecord h, int reach) {
        List<ServerPlayer> out = new ArrayList<>();
        double r2 = reach == Integer.MAX_VALUE ? Double.MAX_VALUE : (double) reach * reach;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            if (!p.getUUID().equals(h.owner) && !h.members.containsKey(p.getUUID())) continue;
            if (p.blockPosition().distSqr(h.pos) <= r2) out.add(p);
        }
        return out;
    }

    // ---- admin and tests -------------------------------------------------------------------------------------------

    /** Sets this home's raid for tonight (warned now), or tomorrow if it's past dusk. */
    static boolean force(ServerPlayer p, boolean first) {
        ServerLevel level = p.level();
        Optional<HearthRecord> rec = HearthService.homeOf(p);
        if (rec.isEmpty() || level.dimension() != Level.OVERWORLD) return false;
        HearthRecord h = rec.get();
        BigDayData d = BigDayData.get(level.getServer());
        Home home = d.home(h.pos);
        if (first) home.raids = 0;
        home.ready = true;
        long today = PlayDays.current(level.getServer());
        warn(level, d, h, home, today);
        return true;
    }

    /** Ends tonight's raid as if dawn came. */
    static boolean dawnNow(ServerPlayer p) {
        ServerLevel level = p.level();
        Optional<HearthRecord> rec = HearthService.homeOf(p);
        if (rec.isEmpty()) return false;
        BigDayData d = BigDayData.get(level.getServer());
        Home home = d.peek(rec.get().pos);
        if (home == null || home.raid == null) return false;
        if (home.raid.phase == Raid.NIGHT) dawn(level, d, rec.get(), home, home.raid, mobs(level, rec.get().pos));
        else away(level, d, rec.get(), home, home.raid);
        return true;
    }
}
