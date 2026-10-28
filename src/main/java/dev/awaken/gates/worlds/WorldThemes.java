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
                    "That's a lot of wishes. The Collector is on his way round to see who's paying for them all.", "The Collector")),
            // the C themes (38.12)
            Map.entry("prayer", new Rule("prayer", 0xb8e0ff, "wheel", m -> {
                m.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 3));
                m.setTicksFrozen(Math.max(m.getTicksFrozen(), 160));
            }, "The prayer wheels have been turning too long. The Frost Warden is coming down to see who's praying so hard.", "The Frost Warden")),
            Map.entry("reflections", new Rule("reflections", 0xe8e8ff, "mirror", m -> {
                m.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
                m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0));
            }, "Too many mirrors looked into. The Mirror Warden is coming to see who's been staring.", "The Mirror Warden")),
            Map.entry("power", new Rule("power", 0x60f0ff, "console", m -> {
                m.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 2));
                m.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 1));
            }, "Too many consoles woken up. Security is coming to see who's logged in.", "The Security Unit")),
            Map.entry("lullaby", new Rule("lullaby", 0xc8a0ff, "musicbox", m -> {
                m.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 4));
                m.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 1));
            }, "The music box has played too often. Something that hates that tune is coming to stop it.", "The Sandman")),
            // the B themes (38.13)
            Map.entry("winds", new Rule("winds", 0xc8f0ff, "skychime", m -> {
                m.push(0, 0.7, 0);
                m.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 0));
                m.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));
            }, "The chimes have rung too often. The Wind Warden is coming to see who's calling the wind.", "The Wind Warden")),
            Map.entry("vigil", new Rule("vigil", 0xb888f4, "offering", m -> {
                m.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2));
                m.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 1));
                m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0));
            }, "Too many offerings left in the dark. The Keeper of the Dark is coming up to see who's been giving them.", "The Keeper of the Dark")));

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

    /** The secrets an event gives everyone there that aren't named after it (38.13): the giant, the swimmer, the wish. */
    public static final Map<String, String> GIVES = Map.of("the_giant_looks", "the_giant", "the_ice_sings", "the_swimmer", "shooting_star", "the_wish");

    /** The room on the way (index in the path) whose arrival runs a theme event, or -1 for one this class doesn't run. */
    static int trigger(String ev, int n) {
        return switch (ev) {
            case "spore_burst", "the_hum", "queens_choir", "page_storm", "overdrive", "the_mirage", "fireworks",
                 "the_bells", "doubled", "breach", "lullaby", "sunburst", "the_hush", "starfall",
                 "shooting_star", "the_ink_runs", "the_giant_looks", "the_ice_sings", "the_aurora", "the_doors_open", "the_echoes" -> n / 2;
            case "tremor", "the_rains", "the_tide", "the_power_cut", "sandstorm", "lantern_release",
                 "blizzard", "shatter", "lockdown", "the_alarm", "gale", "hunger", "the_downpour" -> Math.max(1, n / 3);
            case "the_wakening", "the_bloom", "slag_wyrm", "root_saint", "bookworm", "clock_keeper", "sand_walker", "the_festival",
                 "the_still", "the_whale", "the_crash", "the_sleeper_stirs", "the_great_wind", "the_knock", "the_heartbeat"
                    -> Math.min(n - 1, (2 * n) / 3);
            default -> -1;
        };
    }

    /** What a world says as it wakes and its lights come on, in its own theme's words. */
    static String wakening(GateWorld w) {
        if (w != null && w.tier() == GateWorld.Tier.RARE) return "This place wakes. Its lights come on, one after another, in colours you don't remember.";
        return switch (w == null ? "" : w.theme().id) {
            case "fungal_hollow" -> "The hollow wakes. Its glowcaps light up, one after another.";
            case "ember_mines" -> "The mine wakes. Its old furnaces catch, one after another.";
            case "hive" -> "The hive wakes. The comb starts to glow, cell after cell.";
            case "overgrown_temple" -> "The temple wakes. Its fires light themselves, one after another.";
            case "drowned_archive" -> "The archive wakes. Its reading lamps come on, one after another.";
            case "clockwork_foundry" -> "The foundry wakes. Its furnaces roar back into life, one after another.";
            case "glass_desert" -> "The city wakes. Lamps come on in its windows, one after another.";
            case "frost_monastery" -> "The monastery wakes. Its butter lamps light themselves, one after another.";
            case "mirror_palace" -> "The palace wakes. Its candles light themselves, and every mirror lights with them.";
            case "precursor_lab" -> "The lab wakes. Its panels light up, one after another, and something starts counting.";
            case "nightmare" -> "The house wakes. Its lamps come on, one after another, in rooms nobody's in.";
            case "sky_archipelago" -> "The islands wake. Their wind lanterns light themselves, one after another.";
            case "abyssal_shrine" -> "The shrine wakes. Its candles light themselves, one after another, all the way down.";
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
            case "blizzard" -> {
                say(on, "A blizzard blows in. You can hardly see your hand in front of you.");
                play(level, pos, "blizzard", 3F, 1F);
                for (int i = 0; i < 10; i++) WorldLive.later(i * 12, () -> cloud(level, on, 0xf0f8ff, 70, 8));
                for (ServerPlayer q : on) q.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 200, 0));
                for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(40), e -> e.isAlive() && GateMobs.keyOf(e) != null))
                    m.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 200, 1));
            }
            case "the_bells" -> {
                // somewhere above, the monastery rings for prayers, and the sound shows the way on
                say(on, "Somewhere above you, the monastery's bells start ringing.");
                play(level, pos, "bells", 3F, 1F);
                WorldRules.show(level, g, f, r, pos, on, GatesRegistry.trail(0xb8e0ff));
            }
            case "shatter" -> {
                say(on, "Every mirror in the palace cracks at once.");
                play(level, pos, "shatter", 3F, 1F);
                crack(level, pos);
                for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(30), e -> e.isAlive() && GateMobs.keyOf(e) != null))
                    m.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
            }
            case "doubled" -> {
                // your reflections step out of the glass, and they're not on your side
                say(on, "Your reflection steps out of the mirror. It isn't copying you any more.");
                play(level, pos, "doubled", 2.5F, 1F);
                for (int i = 0; i < Math.min(2, on.size() + 1); i++) {
                    int k = i;
                    WorldLive.later(30 + i * 15, () -> {
                        LivingEntity e = Rooms.spawn(level, g, f, -1, MobRole.Role.NORMAL, spot(level, g, f, r, pos, 6 + k * 3));
                        if (e == null) return;
                        e.setCustomName(Component.literal("Your Reflection"));
                        e.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0));
                        level.sendParticles(GatesRegistry.trail(0xe8e8ff), e.getX(), e.getY() + 1, e.getZ(), 24, 0.4, 0.8, 0.4, 0.02);
                    });
                }
            }
            case "lockdown" -> {
                say(on, "An alarm goes off. The lab is locking itself down, and the lights go red.");
                play(level, pos, "lockdown", 3F, 1F);
                for (ServerPlayer q : on) q.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0));
                for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(40), e -> e.isAlive() && GateMobs.keyOf(e) != null))
                    m.addEffect(new MobEffectInstance(MobEffects.SPEED, 200, 0));
                WorldLive.later(120, () -> say(on, "The alarm stops. The lights come back, white."));
            }
            case "breach" -> {
                say(on, "Glass breaks somewhere ahead. Something has got out of its tank.");
                play(level, pos, "breach", 3F, 1F);
                cloud(level, on, 0x80ff90, 30, 5);
                for (int i = 0; i < 2; i++) {
                    int k = i;
                    WorldLive.later(40 + i * 20, () -> Rooms.spawn(level, g, f, -1, MobRole.Role.NORMAL, spot(level, g, f, r, pos, 10 + k * 4)));
                }
            }
            case "lullaby" -> {
                say(on, "A music box starts playing somewhere in the house. Everything in it slows down to listen.");
                play(level, pos, "lullaby", 3F, 1F);
                for (int i = 0; i < 5; i++) WorldLive.later(i * 20, () -> cloud(level, on, 0xc8a0ff, 24, 5));
                for (ServerPlayer q : on) q.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 0));
                for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(40), e -> e.isAlive() && GateMobs.keyOf(e) != null))
                    m.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 200, 3));
            }
            case "the_alarm" -> {
                say(on, "An alarm clock goes off, somewhere, very loud. The whole house jumps.");
                play(level, pos, "alarm", 3F, 1F);
                for (ServerPlayer q : on) q.addEffect(new MobEffectInstance(MobEffects.SPEED, 200, 0));
                for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(40), e -> e.isAlive() && GateMobs.keyOf(e) != null))
                    m.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 0));
            }
            case "gale" -> {
                // the wind comes across the islands from one side: it lifts you gently, and it shoves the monsters
                say(on, "A gale blows across the islands. Hold on to something.");
                play(level, pos, "gale", 3F, 1F);
                double a = level.getRandom().nextDouble() * Math.PI * 2, dx = Math.cos(a), dz = Math.sin(a);
                for (int i = 0; i < 8; i++) WorldLive.later(i * 12, () -> cloud(level, on, 0xf4f8ff, 50, 8));
                for (ServerPlayer q : on) q.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 240, 0));
                for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(40), e -> e.isAlive() && GateMobs.keyOf(e) != null))
                    m.push(dx * 1.1, 0.35, dz * 1.1);
            }
            case "sunburst" -> {
                say(on, "The clouds part, and the sun breaks through. Everything shines, and you can see the way on.");
                play(level, pos, "sunburst", 3F, 1F);
                cloud(level, on, 0xfff0b0, 40, 8);
                for (ServerPlayer q : on) q.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 120, 0));
                WorldRules.show(level, g, f, r, pos, on, GatesRegistry.trail(0xfff0b0));
            }
            case "the_great_wind" -> greatWind(level, on, g, f, w, r, pos);
            case "the_hush" -> {
                // the dark swallows every sound: the monsters lose track of you a while
                say(on, "Everything goes quiet. Even your own footsteps. Nothing down here can hear you now.");
                play(level, pos, "hush", 3F, 1F);
                for (ServerPlayer q : on) q.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0));
                for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(40), e -> e.isAlive() && GateMobs.keyOf(e) != null)) {
                    m.setTarget(null);
                    m.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 200, 1));
                }
            }
            case "hunger" -> {
                say(on, "Something far below the shrine is hungry. You can feel it pulling at you.");
                play(level, pos, "hunger", 3F, 1F);
                for (ServerPlayer q : on) q.addEffect(new MobEffectInstance(MobEffects.HUNGER, 300, 1));
                cloud(level, on, 0x6a3eaa, 30, 6);
                for (int i = 0; i < 2; i++) {
                    int k = i;
                    WorldLive.later(50 + i * 20, () -> Rooms.spawn(level, g, f, -1, MobRole.Role.NORMAL, spot(level, g, f, r, pos, 9 + k * 4)));
                }
            }
            case "starfall" -> {
                // stars come down out of the dark round you, and where they land the secrets near you glint
                say(on, "Stars are falling out of the dark. Where they land, things that were hidden shine.");
                play(level, pos, "starfall", 3F, 1F);
                for (int i = 0; i < 16; i++) {
                    int k = i;
                    WorldLive.later(i * 6, () -> {
                        for (ServerPlayer q : on) {
                            double a = k * 2.4, rad = 6 + (k % 5) * 3;
                            level.sendParticles(q, GatesRegistry.trail(0xe0e8ff), true, false, q.getX() + Math.cos(a) * rad, q.getY() + 14 - (k % 4) * 3,
                                    q.getZ() + Math.sin(a) * rad, 12, 0.1, 2.5, 0.1, 0.05);
                        }
                    });
                }
                for (ServerPlayer q : on) WorldRules.glintSecrets(level, g, f, world.isEmpty() ? f.world : world, q.blockPosition(), q);
                for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(40), e -> e.isAlive() && GateMobs.keyOf(e) != null))
                    m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 300, 0));
            }
            case "the_knock" -> knock(level, on, g, f, w, r, pos);
            // the rare worlds' own (38.13)
            case "shooting_star" -> {
                say(on, "A shooting star goes over, slow and bright. Make a wish.");
                play(level, pos, "starfall", 1.5F, 1.2F);
                for (int i = 0; i < 20; i++) {
                    int k = i;
                    WorldLive.later(i * 2, () -> {
                        for (ServerPlayer q : on)
                            level.sendParticles(q, GatesRegistry.trail(0xfff8e0), true, false, q.getX() - 30 + k * 3, q.getY() + 30 - k * 0.6, q.getZ() + 20,
                                    6, 0.2, 0.2, 0.2, 0.0);
                    });
                }
                for (ServerPlayer q : on) {
                    q.addEffect(new MobEffectInstance(MobEffects.LUCK, 20 * 600, 0));
                    WorldLive.found(q, world.isEmpty() ? f.world : world, GIVES.get(ev));
                }
            }
            case "the_ink_runs" -> {
                say(on, "The ink on the map runs, and the way on draws itself in front of you.");
                play(level, pos, "draw", 2F, 0.8F);
                WorldRules.show(level, g, f, r, pos, on, GatesRegistry.trail(0x2a3a6a));
                for (ServerPlayer q : on) WorldRules.glintSecrets(level, g, f, world.isEmpty() ? f.world : world, q.blockPosition(), q);
            }
            case "the_downpour" -> {
                say(on, "The rain gets heavier, and all of it is falling up. It's lifting the monsters off their feet.");
                play(level, pos, "gale", 2F, 1.3F);
                rise(level, on, 80, 0xa8d0f0);
                for (ServerPlayer q : on) q.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 300, 0));
                for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(40), e -> e.isAlive() && GateMobs.keyOf(e) != null))
                    m.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 50, 0));
            }
            case "the_giant_looks" -> {
                say(on, "Something enormous leans down and looks in through the front of the house. Then it goes away again.");
                play(level, pos, "watcher", 3F, 1.2F);
                for (ServerPlayer q : on) q.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0));
                for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(40), e -> e.isAlive() && GateMobs.keyOf(e) != null))
                    m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 300, 0));
                for (ServerPlayer q : on) WorldLive.found(q, world.isEmpty() ? f.world : world, GIVES.get(ev));
            }
            case "the_ice_sings" -> {
                say(on, "The ice sings under your feet. Something very big is swimming slowly underneath it.");
                play(level, pos, "icecrack", 2.5F, 0.8F);
                WorldLive.later(40, () -> play(level, pos, "whale", 3F, 1.1F));
                for (ServerPlayer q : on) WorldLive.found(q, world.isEmpty() ? f.world : world, GIVES.get(ev));
            }
            case "the_aurora" -> {
                say(on, "The aurora comes down so low you could almost touch it. Standing under it, you feel better.");
                play(level, pos, "sunburst", 2F, 0.7F);
                for (int i = 0; i < 24; i++) {
                    int k = i;
                    WorldLive.later(i * 4, () -> {
                        for (ServerPlayer q : on)
                            level.sendParticles(q, GatesRegistry.drift(k % 3 == 0 ? 0xa080ff : 0x80ffc0), true, false, q.getX() + Math.sin(k * 0.5) * 14,
                                    q.getY() + 12, q.getZ() - 14 + k * 1.2, 20, 1.5, 0.6, 1.5, 0.0);
                    });
                }
                for (ServerPlayer q : on) q.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0));
            }
            case "the_doors_open" -> {
                say(on, "Every door in the library swings open at once, and a draught blows through all of them toward the way on.");
                play(level, pos, "doorway", 3F, 0.9F);
                WorldRules.show(level, g, f, r, pos, on, GatesRegistry.trail(0xfff0c8));
            }
            case "the_echoes" -> {
                say(on, "The figures round the fire all start talking at once, as if they've been waiting for you to sit down.");
                play(level, pos, "echoes", 3F, 1F);
                for (ServerPlayer q : on) q.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0));
            }
            case "the_heartbeat" -> heartbeat(level, on, g, f, w, r, pos);
            case "the_still" -> still(level, on, g, f, w, r, pos);
            case "the_whale" -> whale(level, on, g, f, w, r, pos);
            case "the_crash" -> crash(level, on, g, f, w, r, pos);
            case "the_sleeper_stirs" -> stirs(level, on, g, f, w, r, pos);
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

    /**
     * The Still Snow's moment (SPEC §38.12): the wind drops, the snow hangs where it is, the prayer flags light one after
     * another along the way on, and the Snow Monk stands up out of the drift.
     */
    static void still(ServerLevel level, List<ServerPlayer> on, GateRecord g, FloorLayout f, GateWorld w, Room r, BlockPos pos) {
        say(on, "The wind stops. The snow hangs in the air, and the prayer flags start to glow, one after another.");
        play(level, pos, "still", 3F, 1F);
        for (int i = 0; i < 12; i++) WorldLive.later(i * 5, () -> {
            for (ServerPlayer q : on) {
                Vec3 v = q.position();
                level.sendParticles(q, GatesRegistry.drift(0xffffff), true, false, v.x, v.y + 2, v.z, 40, 9, 3, 9, 0.0);
            }
        });
        WorldLive.later(30, () -> WorldRules.show(level, g, f, r, pos, on, GatesRegistry.trail(0xffe0a0)));
        for (ServerPlayer q : on) WorldLive.found(q, f.world, "the_still");
        WorldLive.later(140, () -> signature(level, on, g, f, w, r, pos, "snow_monk"));
    }

    /**
     * The White's moment: far off, the whale opens its eye and swims away into the white; then someone walks out of the
     * white toward you, and it's you.
     */
    static void whale(ServerLevel level, List<ServerPlayer> on, GateRecord g, FloorLayout f, GateWorld w, Room r, BlockPos pos) {
        say(on, "Far off, the whale opens its eye. It turns, slowly, and swims away into the white.");
        play(level, pos, "whale", 4F, 1F);
        for (int i = 0; i < 20; i++) {
            int k = i;
            WorldLive.later(i * 4, () -> {
                for (ServerPlayer q : on) {
                    Vec3 v = q.position(), look = q.getLookAngle();
                    level.sendParticles(q, GatesRegistry.drift(0xd8e0f0), true, false, v.x + look.x * 40 + k, v.y + 18, v.z + look.z * 40,
                            30, 6, 2, 6, 0.01);
                }
            });
        }
        for (ServerPlayer q : on) WorldLive.found(q, f.world, "the_whale");
        WorldLive.later(120, () -> signature(level, on, g, f, w, r, pos, "the_reflection"));
    }

    /** The Simulation's moment: it stutters, everything you've drawn is wiped back to lines, and the Glitch comes. */
    static void crash(ServerLevel level, List<ServerPlayer> on, GateRecord g, FloorLayout f, GateWorld w, Room r, BlockPos pos) {
        say(on, "The simulation stutters. Everything you've drawn is wiped away.");
        play(level, pos, "glitch", 3F, 1F);
        for (ServerPlayer q : on) q.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0));
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -24; dx <= 24; dx++) {
            for (int dz = -24; dz <= 24; dz++) {
                for (int dy = -4; dy <= 10; dy++) {
                    m.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    BlockState st = level.getBlockState(m);
                    if (st.getBlock() instanceof ThemeBlocks.Folding && st.getValue(ThemeBlocks.Folding.OPEN))
                        level.setBlock(m, st.setValue(ThemeBlocks.Folding.OPEN, false), 2);
                }
            }
        }
        for (ServerPlayer q : on) WorldLive.found(q, f.world, "the_crash");
        WorldLive.later(60, () -> signature(level, on, g, f, w, r, pos, "the_glitch"));
    }

    /** The Sleeper's Dream's moment: the whole dream lurches as the Sleeper turns over, and a sleepwalker comes for you. */
    static void stirs(ServerLevel level, List<ServerPlayer> on, GateRecord g, FloorLayout f, GateWorld w, Room r, BlockPos pos) {
        say(on, "The whole dream lurches. Somewhere far below, the Sleeper turns over in its sleep.");
        play(level, pos, "stirs", 4F, 1F);
        for (ServerPlayer q : on) q.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 100, 0));
        for (int i = 0; i < 8; i++) WorldLive.later(i * 10, () -> cloud(level, on, 0x9a70e0, 40, 9));
        for (ServerPlayer q : on) WorldLive.found(q, f.world, "the_sleeper_stirs");
        WorldLive.later(80, () -> signature(level, on, g, f, w, r, pos, "sleepwalker"));
    }

    /**
     * Above the Clouds' moment (SPEC §38.13): a great wind rises from under the islands and lifts you on it, then sets you
     * down gently; and out of the storm it brings, the Storm Roc.
     */
    static void greatWind(ServerLevel level, List<ServerPlayer> on, GateRecord g, FloorLayout f, GateWorld w, Room r, BlockPos pos) {
        say(on, "A great wind rises from below the islands. It lifts you right off your feet, and sets you down gently.");
        play(level, pos, "great_wind", 4F, 1F);
        for (ServerPlayer q : on) {
            q.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 40, 1));
            q.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 400, 0));
        }
        rise(level, on, 60, 0xf4f8ff);
        for (ServerPlayer q : on) WorldLive.found(q, f.world, "the_great_wind");
        WorldLive.later(120, () -> signature(level, on, g, f, w, r, pos, "storm_roc"));
    }

    /** The Last Room's moment: someone knocks on the door of the room, three times and once more, and then it opens. */
    static void knock(ServerLevel level, List<ServerPlayer> on, GateRecord g, FloorLayout f, GateWorld w, Room r, BlockPos pos) {
        say(on, "Someone knocks on the door of the room. Three times. Then nothing. Then once more.");
        play(level, pos, "knock", 3F, 1F);
        for (ServerPlayer q : on) WorldLive.found(q, f.world, "the_knock");
        WorldLive.later(130, () -> signature(level, on, g, f, w, r, pos, "the_watcher"));
    }

    /**
     * The Gates' Heart's moment: the heart at the middle of everything beats once, and you feel every gate you've opened
     * beat with it.
     */
    static void heartbeat(ServerLevel level, List<ServerPlayer> on, GateRecord g, FloorLayout f, GateWorld w, Room r, BlockPos pos) {
        say(on, "The heart of the gates beats once. You feel every gate you've ever opened beat with it.");
        play(level, pos, "heartbeat", 4F, 1F);
        for (int i = 0; i < 3; i++) WorldLive.later(i * 24, () -> {
            play(level, pos, "heartbeat", 3F, 1F);
            cloud(level, on, 0xff5060, 40, 8);
        });
        for (ServerPlayer q : on) {
            q.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 300, 1));
            q.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 20 * 120, 1));
            WorldLive.found(q, f.world, "the_heartbeat");
        }
    }

    /** Every mirror within reach of a position cracks (the Shattered event; loaded chunks only). */
    static void crack(ServerLevel level, BlockPos pos) {
        BlockState cracked = GatesRegistry.theme("palace_mirror_cracked").defaultBlockState();
        net.minecraft.world.level.block.Block mirror = GatesRegistry.theme("palace_mirror");
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -20; dx <= 20; dx++) {
            for (int dz = -20; dz <= 20; dz++) {
                for (int dy = -2; dy <= 8; dy++) {
                    m.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    if (level.isLoaded(m) && level.getBlockState(m).is(mirror)) level.setBlock(m, cracked, 2);
                }
            }
        }
    }

    /** Paper lanterns going up into the night round everyone on the floor for {@code ticks}. */
    static void rise(ServerLevel level, List<ServerPlayer> on, int ticks) {
        rise(level, on, ticks, 0);
    }

    /** Lights going up round everyone on the floor for {@code ticks}: lanterns (colour 0), or rain, or wind, in a colour. */
    static void rise(ServerLevel level, List<ServerPlayer> on, int ticks, int colour) {
        for (int i = 0; i < ticks; i += 5) {
            int k = i;
            WorldLive.later(i, () -> {
                for (ServerPlayer q : on) {
                    Vec3 v = q.position();
                    int c = colour != 0 ? colour : k % 10 == 0 ? 0xffb060 : 0xff8040;
                    level.sendParticles(q, GatesRegistry.drift(c), true, false, v.x, v.y + 2 + k * 0.15, v.z,
                            14, 10, 2, 10, 0.02);
                }
            });
        }
    }

    /** The names a wonder's signature creature has until the Creature Kit gives it a body of its own. */
    static final Map<String, String> NAMES = Map.ofEntries(Map.entry("slag_wyrm", "The Slag Wyrm"), Map.entry("queens_choir", "The Queen's Choir"),
            Map.entry("root_saint", "The Root Saint"), Map.entry("bookworm", "The Bookworm"), Map.entry("clock_keeper", "The Clock-Keeper"),
            Map.entry("sand_walker", "The Sand Walker"), Map.entry("lantern_spirits", "The Lantern Spirits"), Map.entry("snow_monk", "The Snow Monk"),
            Map.entry("the_reflection", "Your Reflection"), Map.entry("the_glitch", "The Glitch"), Map.entry("sleepwalker", "The Sleepwalker"),
            Map.entry("storm_roc", "The Storm Roc"), Map.entry("the_watcher", "The Watcher"));

    /** A wonder world's signature creature comes (SPEC §38.10): its own body when the mobs module has one, else a named elite. */
    static void signature(ServerLevel level, List<ServerPlayer> on, GateRecord g, FloorLayout f, GateWorld w, Room r, BlockPos pos, String id) {
        String line = switch (id) {
            case "slag_wyrm" -> "The magma heaves. Something long is coming up out of the sea.";
            case "queens_choir" -> "The hum becomes a chord. The Queen's Choir is gathering round you.";
            case "bookworm" -> "The ink heaves. Something long that's been eating through the stacks is coming up out of it.";
            case "clock_keeper" -> "Every clock in the hall starts again at once. The Clock-Keeper has come to see who stopped them.";
            case "sand_walker" -> "The sand stands up in the shape of a man and walks towards you.";
            case "snow_monk" -> "A monk made of snow stands up out of the drift. He bows to you, and then he comes for you.";
            case "the_reflection" -> "Someone walks out of the white towards you. It's you, and it's smiling.";
            case "the_glitch" -> "Something the simulation can't draw is coming. It flickers every time you look at it.";
            case "sleepwalker" -> "Something is walking towards you in its sleep, and it's much bigger than you.";
            case "storm_roc" -> "Something huge is circling in the storm the wind brought. The Storm Roc is coming down for you.";
            case "the_watcher" -> "The door opens on its own. Something is standing in the doorway, and it's been watching you all along.";
            default -> "The great tree groans. Its roots are pulling free of the floor.";
        };
        say(on, line);
        String snd = switch (id) {
            case "slag_wyrm" -> "surface";
            case "queens_choir" -> "choir";
            case "bookworm" -> "bookworm";
            case "clock_keeper" -> "clockwork";
            case "sand_walker" -> "walker";
            case "snow_monk" -> "monk";
            case "the_reflection" -> "reflection";
            case "the_glitch" -> "glitch";
            case "sleepwalker" -> "sleepwalker";
            case "storm_roc" -> "roc";
            case "the_watcher" -> "watcher";
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
