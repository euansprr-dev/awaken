package dev.awaken.living.client;

import dev.awaken.Awaken;
import dev.awaken.living.pulse.StormCell;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * The Rolling Storm on this client (SPEC §21.6): a real storm, local to its cell. The cell is drawn by Lens on Cinematic
 * and Ultra (a cumulonimbus in its cloud volume, handed over by {@link #lensCells}) and otherwise by
 * {@link StormFallback}. Near it the world storms: this client's rain and thunder levels rise as you near its rain core
 * (the game's own rain falls on you and drums on what's near, the sky and the light darken, terrain's wind picks up),
 * and hand back to the server's weather as you leave. Its strikes (the server's visual bolts) flash inside the cloud and
 * are heard as they would be: a crack and its thunder when close, a long low roll when far, late by their distance.
 * Between strikes, lightning flickers inside the cloud and rumbles far off.
 */
public final class StormSky {
    private StormSky() {}

    /** The most cells Lens is handed (frame texels 42-47). */
    public static final int MAX = 2;

    /** A storm cell as drawn, from its tell. */
    static final class Cell {
        final int id;
        final float bornAt, seedTime;
        final double baseY;
        // at the last {@link #place}: where it is, the ground under it, the wind, how fully it's drawn
        double x, z, ground;
        float windX = 1F, windZ, strength;
        // the latest flash (a strike, or lightning inside the cloud): when it began (client ticks), where, how bright,
        // and when its restrikes come (seconds in; negative: none)
        float flashAt = -1000F, flashAmp, re1 = -1F, re2 = -1F;
        double fx, fy, fz;
        float nextFlicker;

        Cell(int id, float now, double groundY) {
            this.id = id;
            this.bornAt = now;
            this.baseY = StormCell.baseY(groundY);
            this.seedTime = Math.floorMod(id * 397, 600);
            this.nextFlicker = now + 60;
        }
    }

    static final List<Cell> CELLS = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();

    /** From the client entry: its strikes, their thunder and the fallback's render pipeline. */
    public static void init(net.neoforged.bus.api.IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class, StormSky::onJoin);
        NeoForge.EVENT_BUS.addListener(PlaySoundEvent.class, StormSky::onPlaySound);
        StormFallback.init(modBus);
    }

    /** Where the cell is at {@code now} (client ticks with the partial tick), eased as its tell is. */
    static boolean place(Cell c, float now) {
        PulseFx.Shown s = PulseFx.SHOWN.get(c.id);
        if (s == null) return false;
        float k = Mth.clamp((now - s.since) / PulseFx.LERP, 0F, 1F);
        c.x = Mth.lerp(k, s.px, s.x);
        c.z = Mth.lerp(k, s.pz, s.z);
        c.ground = Mth.lerp(k, s.py, s.y);
        if (s.dx != 0F || s.dz != 0F) {
            c.windX = s.dx;
            c.windZ = s.dz;
        }
        float progress = Math.min(1F, s.progress + (now - s.since) / 20F / StormCell.DURATION_S);
        c.strength = StormCell.strength((now - c.bornAt) / 20F, progress, StormCell.DURATION_S);
        return true;
    }

    /** The cell's flash at {@code now}, 0..1: a burst and its restrikes, each gone in a fraction of a second. */
    static float flash(Cell c, float now) {
        float s = (now - c.flashAt) / 20F;
        if (s < 0F || s > 1.2F) return 0F;
        return c.flashAmp * Math.min(1F, pulse(s, 0F) + 0.7F * pulse(s, c.re1) + 0.45F * pulse(s, c.re2));
    }

    private static float pulse(float s, float at) {
        return at < 0F || s < at ? 0F : (float) Math.exp(-(s - at) / 0.07F);
    }

    /**
     * Lens 2 (SPEC §22 "Storm cells"): the cells to draw this frame, {@link StormCell#FLOATS} floats each into
     * {@code out} (camera-relative, see {@link StormCell#pack}); returns how many (at most {@link #MAX}).
     */
    public static int lensCells(float[] out, double camX, double camY, double camZ) {
        if (CELLS.isEmpty() || !here()) return 0;
        float now = LivingClient.ticks + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        int n = 0;
        for (int i = 0; i < CELLS.size() && n < MAX; i++) {
            Cell c = CELLS.get(i);
            if (!place(c, now) || c.strength <= 0.001F) continue;
            StormCell.pack(out, n * StormCell.FLOATS, c.x, c.z, c.baseY, c.strength, c.windX, c.windZ, c.ground,
                    c.seedTime + (now - c.bornAt) / 20F, c.fx, c.fy, c.fz, flash(c, now), camX, camY, camZ);
            n++;
        }
        return n;
    }

    /** How much rain the storm adds to this client's weather now (Lens leaves its cloud cover alone for that part). */
    public static float localRain() {
        return setRain < 0F ? 0F : Math.max(0F, setRain - serverRain);
    }

    /** Storms show only in the overworld, while the Living Wilds are shown. */
    private static boolean here() {
        Minecraft mc = Minecraft.getInstance();
        return LivingClient.shown && mc.level != null && mc.level.dimension() == Level.OVERWORLD;
    }

    // ---- every tick -------------------------------------------------------------------------------------------------

    static void tick(Minecraft mc) {
        float now = LivingClient.ticks;
        // the cells follow the storm tells
        CELLS.removeIf(c -> !PulseFx.SHOWN.containsKey(c.id) || !"storm".equals(PulseFx.SHOWN.get(c.id).tell));
        for (var e : PulseFx.SHOWN.entrySet()) {
            if (!"storm".equals(e.getValue().tell)) continue;
            boolean known = false;
            for (Cell c : CELLS) known |= c.id == e.getKey();
            if (!known) CELLS.add(new Cell(e.getKey(), now, e.getValue().y));
        }
        Vec3 cam = mc.gameRenderer.mainCamera().position();
        float want = 0F;
        boolean here = here();
        for (Cell c : CELLS) {
            if (!place(c, now)) continue;
            double cx = c.x + c.windX * StormCell.RAIN_AHEAD, cz = c.z + c.windZ * StormCell.RAIN_AHEAD;
            if (here) want = Math.max(want, StormCell.weatherAt(Math.sqrt((cam.x - cx) * (cam.x - cx) + (cam.z - cz) * (cam.z - cz))) * c.strength);
            // lightning inside the cloud between strikes, and its far rumble
            if (here && now >= c.nextFlicker && c.strength > 0.4F && now - c.flashAt > 25F) {
                double a = RANDOM.nextDouble() * Math.PI * 2, r = RANDOM.nextDouble() * 55;
                flashAt(c, now, c.x + Math.cos(a) * r + c.windX * 20, c.baseY + 50 + RANDOM.nextDouble() * 160,
                        c.z + Math.sin(a) * r + c.windZ * 20, 0.45F + 0.25F * RANDOM.nextFloat());
                hear(now, c.fx, c.fy, c.fz, cam, false);
                c.nextFlicker = now + 50 + RANDOM.nextInt(90);
            }
        }
        if (mc.level != null) weather(mc.level, want);
        sounds(mc, now);
        RECENT.removeIf(b -> now - b[3] > 10);
    }

    private static void flashAt(Cell c, float now, double x, double y, double z, float amp) {
        c.flashAt = now;
        c.flashAmp = amp;
        c.fx = x;
        c.fy = y;
        c.fz = z;
        c.re1 = RANDOM.nextFloat() < 0.8F ? 0.1F + 0.12F * RANDOM.nextFloat() : -1F;
        c.re2 = RANDOM.nextFloat() < 0.5F ? 0.28F + 0.22F * RANDOM.nextFloat() : -1F;
    }

    // ---- the local weather --------------------------------------------------------------------------------------------

    // the server's own weather (what to hand back), and what this client last set over it (-1: not overriding)
    private static float serverRain, serverThunder, setRain = -1F, setThunder;
    private static ClientLevel weatherLevel;

    /** Rain and thunder at least {@code want} here, never less than the server's own (followed if it changes). */
    private static void weather(ClientLevel level, float want) {
        if (level != weatherLevel) {
            weatherLevel = level;
            setRain = -1F;
        }
        if (setRain < 0F) {
            if (want <= 0.001F) return;
            serverRain = level.getRainLevel(1F);
            serverThunder = serverRain > 1.0e-4F ? level.getThunderLevel(1F) / serverRain : 0F;
        } else {
            // the server changed its weather under ours: those are the values to hand back
            float r = level.getRainLevel(1F), t = level.getThunderLevel(1F);
            if (Math.abs(r - setRain) > 1.0e-4F) serverRain = r;
            if (r > 1.0e-4F && Math.abs(t - setThunder * r) > 1.0e-4F) serverThunder = t / r;
        }
        if (want <= 0.001F) {
            level.setRainLevel(serverRain);
            level.setThunderLevel(serverThunder);
            setRain = -1F;
            return;
        }
        setRain = Math.max(serverRain, want);
        setThunder = Math.max(serverThunder, want);
        level.setRainLevel(setRain);
        level.setThunderLevel(setThunder);
    }

    static void reset() {
        CELLS.clear();
        PENDING.clear();
        RECENT.clear();
        Minecraft mc = Minecraft.getInstance();
        if (setRain >= 0F && mc.level == weatherLevel && mc.level != null) {
            mc.level.setRainLevel(serverRain);
            mc.level.setThunderLevel(serverThunder);
        }
        setRain = -1F;
        weatherLevel = null;
    }

    // ---- strikes and their thunder ------------------------------------------------------------------------------------

    private static final Identifier THUNDER = SoundEvents.LIGHTNING_BOLT_THUNDER.location(), CRACK = SoundEvents.LIGHTNING_BOLT_IMPACT.location(),
            ROLL = Awaken.id("living.storm.roll");
    /** The storm's strikes of the last half second (x, y, z, client tick): their own thunder is silenced, ours comes late. */
    private static final List<double[]> RECENT = new ArrayList<>();
    /** Thunder on its way: (client tick it arrives, x, y, z, distance, 1 a strike / 0 lightning inside the cloud). */
    private static final List<double[]> PENDING = new ArrayList<>();

    private static void onJoin(EntityJoinLevelEvent e) {
        if (!e.getLevel().isClientSide() || !(e.getEntity() instanceof LightningBolt bolt) || !here()) return;
        Cell best = null;
        double bestD = 90 * 90;
        for (Cell c : CELLS) {
            double d = (bolt.getX() - c.x) * (bolt.getX() - c.x) + (bolt.getZ() - c.z) * (bolt.getZ() - c.z);
            if (d < bestD) {
                bestD = d;
                best = c;
            }
        }
        if (best == null) return;
        float now = LivingClient.ticks;
        RECENT.add(new double[]{bolt.getX(), bolt.getY(), bolt.getZ(), now});
        // the strike lights the cloud from inside, just over where it came down
        flashAt(best, now, bolt.getX(), best.baseY + 25, bolt.getZ(), 1F);
        hear(now, bolt.getX(), bolt.getY(), bolt.getZ(), Minecraft.getInstance().gameRenderer.mainCamera().position(), true);
    }

    private static void hear(float now, double x, double y, double z, Vec3 cam, boolean strike) {
        double d = Math.sqrt(cam.distanceToSqr(x, y, z));
        PENDING.add(new double[]{now + StormCell.thunderDelay(d) * 20F, x, y, z, d, strike ? 1 : 0});
    }

    /** A storm bolt's own thunder (the vanilla bolt plays it at once, everywhere at full volume) is ours to play late. */
    private static void onPlaySound(PlaySoundEvent e) {
        SoundInstance s = e.getSound();
        if (s == null || s instanceof Voice || RECENT.isEmpty()) return;
        Identifier id = s.getIdentifier();
        if (!THUNDER.equals(id) && !CRACK.equals(id)) return;
        for (double[] b : RECENT) {
            if (Math.abs(s.getX() - b[0]) < 2 && Math.abs(s.getZ() - b[2]) < 2) {
                e.setSound(null);
                return;
            }
        }
    }

    private static void sounds(Minecraft mc, float now) {
        for (int i = 0; i < PENDING.size(); i++) {
            double[] p = PENDING.get(i);
            if (now < p[0]) continue;
            PENDING.remove(i--);
            double d = p[4];
            if (p[5] > 0) {
                if (d < StormCell.CRACK) {
                    // close: the crack, white and hard, and the game's thunder right on it
                    play(mc, CRACK, p, 1F, 0.55F + 0.15F * RANDOM.nextFloat());
                    play(mc, THUNDER, p, 1F, 0.85F + 0.15F * RANDOM.nextFloat());
                } else {
                    // far: no crack; the thunder lower and softer the farther, and a long roll after it
                    play(mc, THUNDER, p, (float) Mth.clamp(1.15 - d / 520, 0.3, 1), (float) Mth.clamp(0.95 - d / 1400, 0.62, 0.95));
                    play(mc, ROLL, p, (float) Mth.clamp((d - 40) / 300, 0.2, 0.85), 0.9F + 0.15F * RANDOM.nextFloat());
                }
            } else {
                play(mc, ROLL, p, (float) Mth.clamp(0.7 - d / 900, 0.15, 0.6), 0.85F + 0.25F * RANDOM.nextFloat());
            }
        }
    }

    private static void play(Minecraft mc, Identifier sound, double[] at, float volume, float pitch) {
        mc.getSoundManager().play(new Voice(sound, volume, pitch, at[1], at[2], at[3]));
    }

    /** The storm's own thunder: placed at the strike (heard from its side), its level set by distance here. */
    private static final class Voice extends SimpleSoundInstance {
        Voice(Identifier sound, float volume, float pitch, double x, double y, double z) {
            super(sound, SoundSource.WEATHER, volume, pitch, RANDOM, false, 0, SoundInstance.Attenuation.NONE, x, y, z, false);
        }
    }
}
