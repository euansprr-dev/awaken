package dev.awaken.gates.worlds;

import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.MobRole;
import dev.awaken.gates.FloorState;
import dev.awaken.gates.GateRecord;
import dev.awaken.gates.GateRegistry;
import dev.awaken.gates.GateService;
import dev.awaken.gates.GatesRegistry;
import dev.awaken.gates.block.ThemeBlocks;
import dev.awaken.gates.run.GateMobs;
import dev.awaken.gates.run.Rooms;
import dev.awaken.gates.run.Where;
import dev.awaken.gates.world.FloorLayout;
import dev.awaken.gates.world.Room;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The rules, events and moments of every theme after the Bell Crypt (SPEC §38.10 on). Each world's rule has a touchstone
 * (a puffball, a bellows, a wax drum, a brazier): touch one and the world shows you the way on in its own light, glints on
 * the secrets near you and does something to the monsters round it; touch too many on one floor and the place answers
 * with something big. Events run once a floor as you go deeper; a wonder world's moment is its signature.
 */
public final class WorldThemes {
    private WorldThemes() {}

    /** Touches on one floor before the place answers. */
    static final int TOO_MANY = 8;
    static final int COOLDOWN = 15;
    static final double REACH = 10;

    /**
     * A rule: the colour its way and secrets glint in, its sound, what it does to the monsters in reach, what it says when
     * you've touched too many, and the name of the one who comes.
     */
    record Rule(String id, int colour, String sound, Consumer<Mob> onMob, String tooMany, String comes) {}

    static final Map<String, Rule> RULES = Map.ofEntries(
            Map.entry("spores", new Rule("spores", 0x8ff0d8, "puff", m -> {
                m.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
                m.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));
            }, "The air is thick with spores now. Something in the hollow has woken up to them.", "The Spore Warden")),
            Map.entry("embers", new Rule("embers", 0xff9a40, "bellows", m -> {
                m.igniteForSeconds(3);
                m.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 0));
            }, "The mine's too hot. The Foreman's coming down to see who's working the bellows.", "The Foreman")),
            Map.entry("hum", new Rule("hum", 0xffd060, "drum", m -> {
                m.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 50, 2));
                m.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 50, 1));
            }, "You've drummed too long. The hive's guards are coming to stop the noise.", "The Hive Guard")),
            Map.entry("offerings", new Rule("offerings", 0xd8f080, "brazier", m -> m.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 4)),
                    "Too many fires lit. The temple's keeper is coming to see who's making offerings.", "The Temple Keeper")),
            // the D themes (38.11)
            Map.entry("reading", new Rule("reading", 0x7fe0d0, "page", m -> {
                m.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
                m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0));
            }, "Somebody's been reading a lot of books. The archive's Clerk is coming to see who.", "The Clerk")),
            Map.entry("steam", new Rule("steam", 0xffc070, "valve", m -> {
                m.push(0, 0.6, 0);
                m.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1));
                m.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0));
            }, "There's too much pressure in the pipes. The Pressure Warden is coming to see who's been at the valves.", "The Pressure Warden")),
            Map.entry("sun", new Rule("sun", 0xffe0a0, "chime", m -> {
                m.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
                m.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 0));
            }, "You've been flashing the sun about too much. The Sun Warden is coming to see who's playing with mirrors.", "The Sun Warden")),
            Map.entry("wishes", new Rule("wishes", 0xffb060, "wish", m -> m.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 3)),
                    "That's a lot of wishes. The Collector is on his way round to see who's paying for them all.", "The Collector")));

    private static final Map<UUID, Long> LAST = new HashMap<>();

    static void clear() {
        LAST.clear();
    }

    public static boolean has(String rule) {
        return RULES.containsKey(rule);
    }

    /** A touchstone was hit or used. True when a world's rule answered. */
    public static boolean touch(ServerPlayer p, BlockPos pos) {
        ServerLevel level = (ServerLevel) p.level();
        Where w = Where.at(level.getServer(), pos);
        if (w == null || w.floor() == null) return false;
        FloorLayout f = w.floor();
        String world = w.room() == null ? f.world : f.worldOf(w.room());
        GateWorld gw = GateWorlds.get(world);
        Rule rule = gw == null ? null : RULES.get(gw.dressing().rule());
        if (rule == null) return false;
        long t = level.getServer().getTickCount();
        Long last = LAST.get(p.getUUID());
        if (last != null && t - last < COOLDOWN) return true;
        LAST.put(p.getUUID(), t);
        GateRecord g = w.gate();
        SoundEvent snd = GatesRegistry.worldSound(rule.sound());
        if (snd != null) level.playSound(null, pos, snd, SoundSource.BLOCKS, 1.4F, 0.92F + p.getRandom().nextFloat() * 0.16F);
        level.sendParticles(GatesRegistry.drift(rule.colour()), pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 24, 0.5, 0.4, 0.5, 0.02);
        List<ServerPlayer> near = WorldLive.near(level, g, f, pos, WorldRules.SHOW_R);
        if (!near.contains(p)) near.add(p);
        WorldRules.show(level, g, f, w.room(), pos, near, GatesRegistry.trail(rule.colour()));
        WorldRules.glintSecrets(level, g, f, world, pos, p);
        for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(REACH), e -> e.isAlive() && GateMobs.keyOf(e) != null)) {
            MobRole role = m.getData(CoreApi.MOB_ROLE.get());
            if (role.role() == MobRole.Role.BOSS) continue;
            rule.onMob().accept(m);
            level.sendParticles(GatesRegistry.trail(rule.colour()), m.getX(), m.getY() + m.getBbHeight() + 0.2, m.getZ(), 5, 0.25, 0.1, 0.25, 0.01);
        }
        int yRel = pos.getY() - f.baseY;
        FloorLayout.Mark secret = WorldRules.markAt(f, FloorLayout.Mark.SECRET, w.x(), yRel, w.z(), 0);
        if (secret != null) {
            for (ServerPlayer q : WorldLive.near(level, g, f, pos, WorldRules.SECRET_R)) WorldLive.found(q, world, secret.id());
            WorldLive.found(p, world, secret.id());
        }
        touched(level, p, g, f, w.room(), pos, rule, gw.theme().friendly);
        return true;
    }

    /**
     * Counts a floor's touches; at {@link #TOO_MANY} the place answers once: a named elite of the theme and two more. In a
     * friendly gate (the Night Market) nobody comes to fight: the line is all.
     */
    static void touched(ServerLevel level, ServerPlayer p, GateRecord g, FloorLayout f, Room r, BlockPos pos, Rule rule, boolean friendly) {
        WorldLive.Live live = WorldLive.live(g, f);
        live.strikes++;
        FloorState fs = g.floor(f.area, f.floor);
        if (live.strikes < TOO_MANY || !fs.world.add("too_many")) return;
        GateRegistry.get(level.getServer()).setDirty();
        for (ServerPlayer q : WorldLive.near(level, g, f, pos, 48)) q.sendOverlayMessage(Component.literal(rule.tooMany()));
        if (friendly) return;
        Room in = r;
        WorldLive.later(60, () -> {
            BlockPos at = spot(level, g, f, in, pos, 8);
            LivingEntity e = Rooms.spawn(level, g, f, -1, MobRole.Role.ELITE, at);
            if (e != null) {
                e.setCustomName(Component.literal(rule.comes()));
                e.setCustomNameVisible(true);
                level.sendParticles(GatesRegistry.trail(rule.colour()), e.getX(), e.getY() + 1, e.getZ(), 30, 0.6, 1, 0.6, 0.03);
            }
            for (int i = 0; i < 2; i++) Rooms.spawn(level, g, f, -1, MobRole.Role.NORMAL, spot(level, g, f, in, pos, 6 + i * 3));
        });
    }

    /** A room spot about {@code dist} from a position (the nearest to that distance), else the position. */
    static BlockPos spot(ServerLevel level, GateRecord g, FloorLayout f, Room in, BlockPos pos, double dist) {
        List<BlockPos> spots = in == null ? List.of() : Rooms.spots(level, g, f, in);
        BlockPos best = null;
        for (BlockPos s : spots) if (best == null || Math.abs(s.distSqr(pos) - dist * dist) < Math.abs(best.distSqr(pos) - dist * dist)) best = s;
        return best == null ? pos : best;
    }

    // ---- events and moments ----------------------------------------------------------------------------------------

    /** The room on the way (index in the path) whose arrival runs a theme event, or -1 for one this class doesn't run. */
    static int trigger(String ev, int n) {
        return switch (ev) {
            case "spore_burst", "the_hum", "queens_choir", "page_storm", "overdrive", "the_mirage", "fireworks" -> n / 2;
            case "tremor", "the_rains", "the_tide", "the_power_cut", "sandstorm", "lantern_release" -> Math.max(1, n / 3);
            case "the_wakening", "the_bloom", "slag_wyrm", "root_saint", "bookworm", "clock_keeper", "sand_walker", "the_festival" ->
                    Math.min(n - 1, (2 * n) / 3);
            default -> -1;
        };
    }

    /** What a world says as it wakes and its lights come on, in its own theme's words. */
    static String wakening(GateWorld w) {
        return switch (w == null ? "" : w.theme().id) {
            case "fungal_hollow" -> "The hollow wakes. Its glowcaps light up, one after another.";
            case "ember_mines" -> "The mine wakes. Its old furnaces catch, one after another.";
            case "hive" -> "The hive wakes. The comb starts to glow, cell after cell.";
            case "overgrown_temple" -> "The temple wakes. Its fires light themselves, one after another.";
            case "drowned_archive" -> "The archive wakes. Its reading lamps come on, one after another.";
            case "clockwork_foundry" -> "The foundry wakes. Its furnaces roar back into life, one after another.";
            case "glass_desert" -> "The city wakes. Lamps come on in its windows, one after another.";
            default -> "Something down here wakes. The lights come on, one after another.";
        };
    }

    /** Runs a theme event once for a floor, from where the player who brought it on stands. */
    static void run(String ev, ServerLevel level, ServerPlayer p, GateRecord g, FloorLayout f, Room r) {
        String world = f.worldOf(r);
        GateWorld w = GateWorlds.get(world.isEmpty() ? f.world : world);
        BlockPos pos = p.blockPosition();
        List<ServerPlayer> on = WorldLive.on(level, g, f);
        switch (ev) {
            case "spore_burst" -> {
                // the floor breathes out: a cloud of spores rolls past and the monsters in it glow
                say(on, "The hollow breathes out. Spores roll through the caves, and everything in them glows.");
                play(level, pos, "puff", 2.5F, 0.6F);
                cloud(level, on, 0x9cf0d8, 40, 6);
                for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(40), e -> e.isAlive() && GateMobs.keyOf(e) != null))
                    m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 400, 0));
            }
            case "tremor" -> {
                say(on, "The mine shudders. Dust and ash come down from the roof.");
                play(level, pos, "tremor", 3F, 0.9F);
                cloud(level, on, 0x6a625a, 30, 5);
                for (ServerPlayer q : on) q.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 0));
            }
            case "the_hum" -> {
                say(on, "The hum rises. The hive has noticed you, and its drones are coming.");
                play(level, pos, "swarm", 3F, 1F);
                cloud(level, on, 0xffd060, 20, 4);
                for (int i = 0; i < 2; i++) {
                    int k = i;
                    WorldLive.later(40 + i * 20, () -> Rooms.spawn(level, g, f, -1, MobRole.Role.NORMAL, spot(level, g, f, r, pos, 10 + k * 4)));
                }
            }
            case "the_rains" -> {
                say(on, "The rains come. Water runs off the canopy and down every stair.");
                play(level, pos, "rains", 2.5F, 1F);
                for (int i = 0; i < 6; i++) WorldLive.later(i * 20, () -> cloud(level, on, 0xa8c8e0, 30, 5));
            }
            case "the_wakening" -> {
                say(on, wakening(w));
                play(level, pos, "wakening", 3F, 1F);
                light(level, g, f, pos);
            }
            case "page_storm" -> {
                // a wind out of nowhere lifts every loose page; the monsters in it can't see where they're going
                say(on, "A wind comes through the stacks out of nowhere, and every loose page goes up with it.");
                play(level, pos, "pages", 2.5F, 1F);
                for (int i = 0; i < 4; i++) WorldLive.later(i * 15, () -> cloud(level, on, 0xefe8d2, 40, 6));
                for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(30), e -> e.isAlive() && GateMobs.keyOf(e) != null))
                    m.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 1));
            }
            case "the_tide" -> {
                say(on, "The tide comes in through the stacks. For a while the water carries you.");
                play(level, pos, "tide", 2.5F, 1F);
                cloud(level, on, 0x5fd0c0, 40, 6);
                for (ServerPlayer q : on) {
                    q.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 600, 0));
                    q.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 600, 0));
                }
            }
            case "overdrive" -> {
                // the machines run three times as fast for half a minute, everyone quicker with them
                say(on, "The machines start running faster and faster. The whole floor is shaking.");
                play(level, pos, "overdrive", 3F, 1F);
                WorldProps.overdrive(g, f, 600);
                for (int i = 0; i < 5; i++) WorldLive.later(i * 20, () -> cloud(level, on, 0xdce0e4, 30, 5));
                for (ServerPlayer q : on) q.addEffect(new MobEffectInstance(MobEffects.SPEED, 400, 0));
                for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(40), e -> e.isAlive() && GateMobs.keyOf(e) != null))
                    m.addEffect(new MobEffectInstance(MobEffects.SPEED, 400, 0));
            }
            case "the_power_cut" -> {
                say(on, "The power cuts out. Everything goes dark.");
                play(level, pos, "powercut", 3F, 1F);
                for (ServerPlayer q : on) q.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 180, 0));
                WorldLive.later(160, () -> say(on, "The lamps flicker, and come back on."));
            }
            case "sandstorm" -> {
                say(on, "A sandstorm is blowing in across the land. Keep your head down.");
                play(level, pos, "sandstorm", 3F, 1F);
                for (int i = 0; i < 8; i++) WorldLive.later(i * 15, () -> cloud(level, on, 0xd8b080, 60, 8));
                for (ServerPlayer q : on) q.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 160, 0));
                for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(40), e -> e.isAlive() && GateMobs.keyOf(e) != null))
                    m.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 160, 1));
            }
            case "the_mirage" -> {
                say(on, "The air shimmers. For a moment there's a palace on the horizon, and then there isn't.");
                play(level, pos, "shimmer", 2.5F, 1F);
                for (ServerPlayer q : on) {
                    Vec3 v = q.position(), look = q.getLookAngle();
                    level.sendParticles(q, GatesRegistry.drift(0xffe0a0), true, false, v.x + look.x * 30, v.y + 8, v.z + look.z * 30, 80, 8, 4, 8, 0.01);
                }
            }
            case "fireworks" -> {
                say(on, "Fireworks go up over the market.");
                for (int i = 0; i < 8; i++) {
                    int k = i;
                    WorldLive.later(i * 12, () -> {
                        play(level, pos, "fireworks", 3F, 0.9F + (k % 3) * 0.1F);
                        for (ServerPlayer q : on) {
                            Vec3 v = q.position();
                            double a = k * 1.9;
                            level.sendParticles(q, ParticleTypes.FIREWORK, true, false, v.x + Math.cos(a) * 14, v.y + 18 + k % 3 * 3,
                                    v.z + Math.sin(a) * 14, 60, 2.5, 2.5, 2.5, 0.12);
                        }
                    });
                }
            }
            case "lantern_release" -> {
                say(on, "Everyone in the market lets a lantern go. Hundreds of them drift up into the night.");
                play(level, pos, "release", 2.5F, 1F);
                rise(level, on, 60);
            }
            case "the_festival" -> festival(level, on, g, f, w, r, pos);
            case "the_bloom" -> bloom(level, on, g, f, w, r, pos);
            case "slag_wyrm", "queens_choir", "root_saint", "bookworm", "clock_keeper", "sand_walker" -> signature(level, on, g, f, w, r, pos, ev);
            default -> {}
        }
    }

    /** Inside the Bloom's moment: the gills open over the great cavern and the Spore Mother's moths rise in a spiral. */
    static void bloom(ServerLevel level, List<ServerPlayer> on, GateRecord g, FloorLayout f, GateWorld w, Room r, BlockPos pos) {
        say(on, "The Bloom opens. Its moths rise out of the gills in their thousands.");
        play(level, pos, "bloom", 4F, 1F);
        for (int i = 0; i < 40; i++) {
            int k = i;
            WorldLive.later(i * 3, () -> {
                for (ServerPlayer q : on) {
                    double a = k * 0.55, rad = 3 + k * 0.25;
                    for (int j = 0; j < 4; j++) {
                        double aa = a + j * Math.PI / 2;
                        level.sendParticles(q, GatesRegistry.trail(j % 2 == 0 ? 0xffd8f0 : 0x9cf0d8), true, false, pos.getX() + Math.cos(aa) * rad,
                                pos.getY() + 1 + k * 0.4, pos.getZ() + Math.sin(aa) * rad, 3, 0.3, 0.3, 0.3, 0.01);
                    }
                }
            });
        }
        if (w != null) for (ServerPlayer q : on) WorldLive.found(q, f.world, "the_bloom");
        if (w != null && !w.dressing().signature().isEmpty()) for (ServerPlayer q : on) {
            GateAtlas atlas = q.getData(GatesRegistry.ATLAS.get());
            if (atlas.met(w.dressing().signature())) {
                q.setData(GatesRegistry.ATLAS.get(), atlas);
                WorldsLive.sync(q);
            }
        }
    }

    /**
     * The Festival's moment (SPEC §38.11): lanterns go up from every square, then turn round and come back down as the
     * Lantern Spirits, who only want to see the festival. Nobody fights in the market: meeting them is the signature, and
     * the last lantern the secret that gives its keepsake.
     */
    static void festival(ServerLevel level, List<ServerPlayer> on, GateRecord g, FloorLayout f, GateWorld w, Room r, BlockPos pos) {
        say(on, "The festival begins. Lanterns go up from every square, and the music gets louder.");
        play(level, pos, "festival", 4F, 1F);
        rise(level, on, 100);
        WorldLive.later(110, () -> {
            say(on, "The lanterns stop rising and turn round. The Lantern Spirits have come down to see the festival.");
            play(level, pos, "spirits", 4F, 1F);
            for (int i = 0; i < 30; i++) {
                int k = i;
                WorldLive.later(i * 4, () -> {
                    for (ServerPlayer q : on) {
                        double a = k * 0.6, rad = 4 + Math.sin(k * 0.3) * 1.5;
                        level.sendParticles(q, GatesRegistry.trail(k % 2 == 0 ? 0xffd890 : 0xff9060), true, false, q.getX() + Math.cos(a) * rad,
                                q.getY() + 1.5 + Math.sin(k * 0.4), q.getZ() + Math.sin(a) * rad, 4, 0.2, 0.2, 0.2, 0.01);
                    }
                });
            }
            String sig = w == null ? "" : w.dressing().signature();
            for (ServerPlayer q : on) {
                if (!sig.isEmpty()) {
                    GateAtlas atlas = q.getData(GatesRegistry.ATLAS.get());
                    if (atlas.met(sig)) {
                        q.setData(GatesRegistry.ATLAS.get(), atlas);
                        WorldsLive.sync(q);
                    }
                }
                WorldLive.found(q, f.world, "the_festival");
            }
        });
    }

    /** Paper lanterns going up into the night round everyone on the floor for {@code ticks}. */
    static void rise(ServerLevel level, List<ServerPlayer> on, int ticks) {
        for (int i = 0; i < ticks; i += 5) {
            int k = i;
            WorldLive.later(i, () -> {
                for (ServerPlayer q : on) {
                    Vec3 v = q.position();
                    level.sendParticles(q, GatesRegistry.drift(k % 10 == 0 ? 0xffb060 : 0xff8040), true, false, v.x, v.y + 2 + k * 0.15, v.z,
                            14, 10, 2, 10, 0.02);
                }
            });
        }
    }

    /** The names a wonder's signature creature has until the Creature Kit gives it a body of its own. */
    static final Map<String, String> NAMES = Map.of("slag_wyrm", "The Slag Wyrm", "queens_choir", "The Queen's Choir", "root_saint", "The Root Saint",
            "bookworm", "The Bookworm", "clock_keeper", "The Clock-Keeper", "sand_walker", "The Sand Walker", "lantern_spirits", "The Lantern Spirits");

    /** A wonder world's signature creature comes (SPEC §38.10): its own body when the mobs module has one, else a named elite. */
    static void signature(ServerLevel level, List<ServerPlayer> on, GateRecord g, FloorLayout f, GateWorld w, Room r, BlockPos pos, String id) {
        String line = switch (id) {
            case "slag_wyrm" -> "The magma heaves. Something long is coming up out of the sea.";
            case "queens_choir" -> "The hum becomes a chord. The Queen's Choir is gathering round you.";
            case "bookworm" -> "The ink heaves. Something long that's been eating through the stacks is coming up out of it.";
            case "clock_keeper" -> "Every clock in the hall starts again at once. The Clock-Keeper has come to see who stopped them.";
            case "sand_walker" -> "The sand stands up in the shape of a man and walks towards you.";
            default -> "The great tree groans. Its roots are pulling free of the floor.";
        };
        say(on, line);
        String snd = switch (id) {
            case "slag_wyrm" -> "surface";
            case "queens_choir" -> "choir";
            case "bookworm" -> "bookworm";
            case "clock_keeper" -> "clockwork";
            case "sand_walker" -> "walker";
            default -> "saint";
        };
        play(level, pos, snd, 4F, 1F);
        // on the Lord's floor of a world whose signature is the theme's own Lord (the Great Tree's Root Saint), the Lord
        // is the one that comes: no second one
        if (f.bossFloor && id.equals(w.theme().bossKey)) return;
        WorldLive.later(60, () -> {
            BlockPos at = spot(level, g, f, r, pos, 9);
            LivingEntity e = Rooms.signature(level, g, f, -1, WorldLook.id(id), at);
            if (e == null) {
                e = Rooms.spawn(level, g, f, -1, MobRole.Role.ELITE, at);
                if (e == null) return;
                e.setCustomName(Component.literal(NAMES.getOrDefault(id, WorldsLive.pretty(id))));
                e.setCustomNameVisible(true);
                AttributeInstance hp = e.getAttribute(Attributes.MAX_HEALTH);
                if (hp != null) {
                    hp.setBaseValue(hp.getBaseValue() * 2);
                    e.setHealth(e.getMaxHealth());
                }
                if (id.equals("slag_wyrm")) e.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 600, 0));
            }
            String world = f.worldOf(r).isEmpty() ? f.world : f.worldOf(r);
            e.getPersistentData().putString(WorldLive.RINGER_TAG, world);
            WorldsLive.dressSignature(e, world, id);
            level.sendParticles(id.equals("slag_wyrm") ? ParticleTypes.LAVA : ParticleTypes.GLOW, e.getX(), e.getY() + 1, e.getZ(), 40, 0.8, 1, 0.8, 0.05);
            for (ServerPlayer q : WorldLive.near(level, g, f, at, 64)) {
                GateAtlas atlas = q.getData(GatesRegistry.ATLAS.get());
                if (atlas.met(id)) {
                    q.setData(GatesRegistry.ATLAS.get(), atlas);
                    WorldsLive.sync(q);
                }
            }
        });
    }

    /**
     * The Mycelium Mind hears you (SPEC §38.10): say anything in it and its threads light up from where you stand out to
     * everyone near, and the first time it shares a thought of its own (the secret "the_thought").
     */
    public static void chat(net.neoforged.neoforge.event.ServerChatEvent e) {
        ServerPlayer p = e.getPlayer();
        if (!(p.level() instanceof ServerLevel level)) return;
        BlockPos pos = p.blockPosition();
        Where w = Where.at(level.getServer(), pos);
        if (w == null || w.floor() == null) return;
        FloorLayout f = w.floor();
        String world = w.room() == null ? f.world : f.worldOf(w.room());
        if (!world.endsWith("/mycelium_mind")) return;
        List<ServerPlayer> near = WorldLive.near(level, w.gate(), f, pos, WorldRules.SHOW_R);
        if (!near.contains(p)) near.add(p);
        WorldRules.show(level, w.gate(), f, w.room(), pos, near, GatesRegistry.trail(0x8ff0d8));
        level.sendParticles(GatesRegistry.drift(0xc8fff0), p.getX(), p.getY() + 1.6, p.getZ(), 30, 1.2, 0.6, 1.2, 0.02);
        play(level, pos, "puff", 1F, 1.3F);
        GateAtlas.Entry seen = p.getData(GatesRegistry.ATLAS.get()).entry(world);
        if (seen != null && seen.secrets.contains("the_thought")) return;
        p.sendSystemMessage(Component.literal("The threads answer all at once, without words: yours is the first voice down here in a very long time."));
        WorldLive.found(p, world, "the_thought");
    }

    /** Lights every brazier within 48 of a position, one after another (loaded chunks only). */
    static void light(ServerLevel level, GateRecord g, FloorLayout f, BlockPos pos) {
        int i = 0;
        for (FloorLayout.Mark m : WorldLive.nearest(g, f, FloorLayout.Mark.TOUCH, pos, 48, 24)) {
            BlockPos at = WorldLive.at(g, f, m);
            WorldLive.later(i++ * 6, () -> {
                if (!level.isLoaded(at)) return;
                BlockState s = level.getBlockState(at);
                if (!(s.getBlock() instanceof ThemeBlocks.Touchstone) || s.getValue(ThemeBlocks.Touchstone.LIT)) return;
                level.setBlock(at, s.setValue(ThemeBlocks.Touchstone.LIT, true), 3);
                level.sendParticles(ParticleTypes.FLAME, at.getX() + 0.5, at.getY() + 0.9, at.getZ() + 0.5, 8, 0.2, 0.1, 0.2, 0.02);
                play(level, at, "brazier", 1F, 1F);
            });
        }
    }

    // ---- small helpers -----------------------------------------------------------------------------------------------

    static void say(List<ServerPlayer> to, String line) {
        for (ServerPlayer q : to) q.sendOverlayMessage(Component.literal(line));
    }

    static void play(ServerLevel level, BlockPos pos, String sound, float vol, float pitch) {
        SoundEvent s = GatesRegistry.worldSound(sound);
        if (s != null) level.playSound(null, pos, s, SoundSource.AMBIENT, vol, pitch);
    }

    /** A drift of light flakes round each player, {@code count} of them in a box {@code r} across. */
    static void cloud(ServerLevel level, List<ServerPlayer> to, int colour, int count, double r) {
        for (ServerPlayer q : to) {
            Vec3 v = q.position();
            level.sendParticles(q, GatesRegistry.drift(colour), true, false, v.x, v.y + 3, v.z, count, r, 2.5, r, 0.01);
        }
    }

    /** The world position of a floor-local block, for code that has one. */
    static BlockPos world(GateRecord g, FloorLayout f, int x, int y, int z) {
        return GateService.world(g, f, x, y, z);
    }
}
