package dev.awaken.core.audio.client.world;

import dev.awaken.core.audio.world.EventClock;
import dev.awaken.core.audio.world.Families;
import dev.awaken.core.audio.world.SoundscapeDef;
import dev.awaken.terrain.client.TerrainClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/**
 * The world sound Conductor (docs/sound/PLAN.md, SPEC §13 "Plan v2 · world sound"): every tick it reads the
 * {@link Listener}, works out which soundscapes play and how much, and drives the four layers. Soundscapes in six
 * slots: the Conductor's own nature (the shared {@code awaken:world} and the one or two families the ground around
 * you belongs to; only without AmbientSounds), the place you stand in ({@code <ns>:place/<biome path>}: Awaken's
 * dimensions, the Awakened lands), and up to two overlays another module asks for (the Veil). Layers: the air (one stream,
 * {@link AirBed}), the {@link Textures}, the one-shot events (placed on real blocks, at most {@value #MAX_EVENTS} at
 * once and {@value #MAX_NEW_PER_SECOND} new a second, within a budget of {@value #VOICE_BUDGET} voices shared with
 * other modules' sources), and the reactions (hushes, ducking under combat music, yielding inside the city).
 *
 * <p>Client thread only; nothing allocated per tick but the one-shot sound instances themselves (a few a minute).
 */
public final class Conductor {
    private Conductor() {}

    static final int VOICE_BUDGET = 16, MAX_EVENTS = 10, MAX_NEW_PER_SECOND = 4, MAX_HUSHES = 16, MAX_SCAPES = 6;
    private static final int PLACE = 3, OVERLAY = 4, MAX_OVERLAYS = 2, MAX_MUFFLES = 4;
    /** Beds under combat or Lord music (−4 dB), under a stage cue (−3 dB). */
    private static final float DUCK_COMBAT = 0.63F, DUCK_STAGE = 0.71F;

    private static final Listener AT = new Listener();
    private static final AirBed AIR = new AirBed();
    private static final Textures TEX = new Textures();
    private static final Scape[] SCAPES = new Scape[MAX_SCAPES];
    private static final SimpleSoundInstance[] EVENTS = new SimpleSoundInstance[MAX_EVENTS];
    private static final float[] KEEP = new float[SoundscapeDef.MAX_EVENTS];
    private static final List<Source> SOURCES = new ArrayList<>();
    // hushes: x, y, z, radius, depth (0 = silent, 1 = untouched), until (game time), owner
    private static final double[] HX = new double[MAX_HUSHES], HY = new double[MAX_HUSHES], HZ = new double[MAX_HUSHES];
    private static final float[] HR = new float[MAX_HUSHES], HD = new float[MAX_HUSHES];
    private static final long[] HUNTIL = new long[MAX_HUSHES];
    private static final Object[] HOWNER = new Object[MAX_HUSHES];
    // overlays (a module's soundscape over everything) and muffles (the air's filter and nature closed), each with an owner
    private static final Object[] OOWNER = new Object[MAX_OVERLAYS];
    private static final Identifier[] OID = new Identifier[MAX_OVERLAYS];
    private static final float[] OW = new float[MAX_OVERLAYS];
    private static final long[] OUNTIL = new long[MAX_OVERLAYS];
    private static final Object[] MOWNER = new Object[MAX_MUFFLES];
    private static final float[] MHZ = new float[MAX_MUFFLES], MNATURE = new float[MAX_MUFFLES];
    private static final long[] MUNTIL = new long[MAX_MUFFLES];
    // the place soundscape of each biome seen (null: none), cleared on a resource reload
    private static final java.util.Map<Holder<Biome>, java.util.Optional<SoundscapeDef>> PLACES = new java.util.HashMap<>();
    private static int placesFor = -1;

    private static IntSupplier musicLevel = () -> -1;
    private static BooleanSupplier stageCue = () -> false;
    private static Yield yield = (x, y, z) -> 0.0;
    private static int newThisSecond, secondTicks, ticks;
    private static float duckBeds = 1F, duckEvents = 1F, yieldNow, nature = 1F;
    private static long gameTime;
    private static boolean ambientSoundsLoaded, enabled = true;
    private static String lastEvent = "none yet";

    /** Another module's ambient voices, counted in the Conductor's voice budget. */
    public interface Source {
        /** How many voices it holds right now. */
        int voices();

        /** 0..1: how strongly something of AWAKEN's is sounding near the listener (a rift, the Well); nature steps back. */
        default float emphasis() {
            return 0F;
        }
    }

    /** How much of the world bed to give way at a point: 0 none, 1 all (the city reports its walls). */
    @FunctionalInterface
    public interface Yield {
        double at(double x, double y, double z);
    }

    /** One playing soundscape: its definition, its weight now, and its event clock. */
    private static final class Scape {
        final EventClock clock;
        SoundscapeDef def;
        float weight;

        Scape(long seed) {
            clock = new EventClock(seed);
        }
    }

    static void init() {
        for (int i = 0; i < MAX_SCAPES; i++) SCAPES[i] = new Scape(0x5EED_A1E5L + 31L * i + System.nanoTime());
        ambientSoundsLoaded = ModList.get().isLoaded("ambientsounds");
    }

    /** Installed by the audio module: the layered music's level (-1 when it doesn't hold the channel) and a stage cue. */
    public static void music(IntSupplier level, BooleanSupplier stage) {
        musicLevel = level;
        stageCue = stage;
    }

    public static void register(Source source) {
        if (!SOURCES.contains(source)) SOURCES.add(source);
    }

    public static void yieldWeight(Yield weight) {
        yield = weight == null ? (x, y, z) -> 0.0 : weight;
    }

    /**
     * Quiets birds and insects (every event marked {@code hush}) within {@code radius} of a point: {@code depth} is what
     * is left (0 silent, 1 untouched) for {@code ticks}, easing back over the last fifth. A newer hush from the same
     * owner replaces its older one.
     */
    public static void hush(Object owner, Vec3 at, float radius, float depth, int ticks) {
        int slot = -1;
        for (int i = 0; i < MAX_HUSHES; i++) {
            if (HOWNER[i] == owner || (slot < 0 && HUNTIL[i] <= gameTime)) slot = i;
            if (HOWNER[i] == owner) break;
        }
        if (slot < 0) return;
        HX[slot] = at.x;
        HY[slot] = at.y;
        HZ[slot] = at.z;
        HR[slot] = radius;
        HD[slot] = Mth.clamp(depth, 0F, 1F);
        HUNTIL[slot] = gameTime + Math.max(1, ticks);
        HOWNER[slot] = owner;
    }

    /** What is left of a hushable sound at a point (1 = untouched): full depth within half the radius, easing out to its edge. */
    static float hushAt(double x, double y, double z) {
        float left = 1F;
        for (int i = 0; i < MAX_HUSHES; i++) {
            long rest = HUNTIL[i] - gameTime;
            if (rest <= 0) continue;
            double dx = x - HX[i], dy = y - HY[i], dz = z - HZ[i];
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d >= HR[i]) continue;
            float near = Mth.clamp((float) ((HR[i] - d) / (0.5 * HR[i])), 0F, 1F);
            float k = Mth.clamp(rest / 40F, 0F, 1F); // the last two seconds ease back
            left = Math.min(left, 1F - (1F - HD[i]) * k * near * near * (3F - 2F * near));
        }
        return left;
    }

    /**
     * Lays the soundscape {@code id} over everything at {@code weight} for {@code ticks} (renew it while it holds): the
     * Veil's choir while you project. A newer call from the same owner replaces its older one. At most two at once.
     */
    public static void overlay(Object owner, Identifier id, float weight, int ticks) {
        int slot = slot(OOWNER, OUNTIL, owner);
        if (slot < 0) return;
        OOWNER[slot] = owner;
        OID[slot] = id;
        OW[slot] = Mth.clamp(weight, 0F, 1F);
        OUNTIL[slot] = gameTime + Math.max(1, ticks);
    }

    /**
     * Closes the air's filter to {@code cutoffHz} and lets only {@code natureLeft} of the nature through for
     * {@code ticks} (renew it while it holds): the Veil (the world through glass), a dream at low lucidity.
     */
    public static void muffle(Object owner, float cutoffHz, float natureLeft, int ticks) {
        int slot = slot(MOWNER, MUNTIL, owner);
        if (slot < 0) return;
        MOWNER[slot] = owner;
        MHZ[slot] = Mth.clamp(cutoffHz, 200F, 20000F);
        MNATURE[slot] = Mth.clamp(natureLeft, 0F, 1F);
        MUNTIL[slot] = gameTime + Math.max(1, ticks);
    }

    /** The owner's slot, else a free (expired) one, else -1. */
    private static int slot(Object[] owners, long[] until, Object owner) {
        int free = -1;
        for (int i = 0; i < owners.length; i++) {
            if (owners[i] == owner) return i;
            if (free < 0 && until[i] <= gameTime) free = i;
        }
        return free;
    }

    /** Dev switch ({@code /awaken audio world off|on}): the whole Conductor, for frame-time comparisons. */
    static void enabled(boolean on) {
        enabled = on;
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || !enabled) {
            clear();
            return;
        }
        ticks++;
        gameTime = level.getGameTime();
        AT.update(mc, level);
        int music = musicLevel.getAsInt();
        float bedGoal = stageCue.getAsBoolean() ? DUCK_STAGE : music >= 2 ? DUCK_COMBAT : 1F;
        duckBeds += (bedGoal - duckBeds) * 0.03F;
        duckEvents = music >= 2 ? 0.5F : 1F;
        yieldNow = (float) Mth.clamp(yield.at(AT.x, AT.y, AT.z), 0.0, 1.0);
        float world = duckBeds * (1F - yieldNow);

        pickScapes();
        float natureGoal = natureGoal(music);
        nature += (natureGoal - nature) * (natureGoal < nature ? 0.05F : 0.01F);
        float rainMix = AT.rain;
        // the air: one stream; indoors and underwater close its filter
        for (Scape s : SCAPES) {
            if (s.def == null || s.weight <= 0.001F) continue;
            for (SoundscapeDef.Air a : s.def.air()) {
                float g = a.gain() * s.weight * a.when().at(AT.dayTime) * rain(a.rain(), rainMix) * Textures.drive(a.drive(), AT) * world;
                AIR.want(a.file(), g);
            }
        }
        float indoor = Mth.clamp((1F - AT.open) / 0.85F, 0F, 1F);
        float cutoff = Mth.lerp(AT.underwater, Mth.lerp(indoor * indoor, 20000F, 1400F), 520F);
        for (int i = 0; i < MAX_MUFFLES; i++) if (MUNTIL[i] > gameTime) cutoff = Math.min(cutoff, MHZ[i]);
        AIR.cutoff(cutoff);
        AIR.commit(mc);

        // textures, placed on the ring
        boolean leaves = TerrainClientConfig.leafRustle();
        for (Scape s : SCAPES) {
            if (s.def == null) continue;
            List<SoundscapeDef.Texture> list = s.def.textures();
            for (int i = 0; i < list.size(); i++) {
                SoundscapeDef.Texture t = list.get(i);
                boolean windy = t.drive() == SoundscapeDef.Drive.RUSTLE || t.drive() == SoundscapeDef.Drive.HIGH_WIND;
                float goal = windy && !leaves ? 0F : t.gain() * s.weight * t.when().at(AT.dayTime) * rain(t.rain(), rainMix)
                        * Textures.drive(t.drive(), AT) * world;
                float pitch = windy ? 0.9F + 0.2F * AT.strength : 1F;
                boolean placed = Spots.texture(AT, t.anchor(), SPOT);
                TEX.drive(mc, t, t.sound(), goal, pitch, SPOT[0], SPOT[1], SPOT[2], placed);
            }
        }
        TEX.endTick();

        // events, a second's worth of clock every tick it advances
        if (++secondTicks >= 20) {
            secondTicks = 0;
            newThisSecond = 0;
        }
        for (int i = 0; i < MAX_EVENTS; i++) {
            if (EVENTS[i] != null && !mc.getSoundManager().isActive(EVENTS[i]) && (ticks & 7) == 0) EVENTS[i] = null;
        }
        for (Scape s : SCAPES) {
            if (s.def == null || s.def.events().isEmpty()) continue;
            List<SoundscapeDef.Event> evs = s.def.events();
            for (int i = 0; i < evs.size(); i++) {
                SoundscapeDef.Event e = evs.get(i);
                KEEP[i] = Mth.clamp(s.weight * e.when().at(AT.dayTime) * rain(e.rain(), rainMix) * duckEvents * (1F - yieldNow), 0F, 1F);
            }
            current = s;
            s.clock.advance(0.05, KEEP, Conductor::fire);
        }
    }

    private static final double[] SPOT = new double[3];
    private static Scape current;

    /** The clock's sink: places the event on a real spot, checks hushes and the budget, and plays it. */
    private static void fire(int index, boolean answer) {
        Scape s = current;
        SoundscapeDef.Event e = s.def.events().get(index);
        Minecraft mc = Minecraft.getInstance();
        if (newThisSecond >= MAX_NEW_PER_SECOND) return;
        int free = -1, used = 0;
        for (int i = 0; i < MAX_EVENTS; i++) {
            if (EVENTS[i] == null) free = i;
            else used++;
        }
        int others = TEX.voices();
        for (Source src : SOURCES) others += src.voices();
        if (free < 0 || used + others >= VOICE_BUDGET) return;
        float dist = s.clock.uniform(e.distMin(), e.distMax());
        if (!Spots.event(AT, e.anchor(), dist, s.clock, SPOT)) return;
        if (e.hush() && s.clock.uniform(0F, 1F) > hushAt(SPOT[0], SPOT[1], SPOT[2])) return;
        double dx = SPOT[0] - AT.x, dy = SPOT[1] - AT.y, dz = SPOT[2] - AT.z;
        float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float vol = s.clock.uniform(e.volMin(), e.volMax()) * 12F / (12F + d) * duckBeds;
        float pitch = s.clock.uniform(e.pitchMin(), e.pitchMax());
        SimpleSoundInstance snd = new SimpleSoundInstance(e.sound(), SoundSource.AMBIENT, vol, pitch,
                SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, SPOT[0], SPOT[1], SPOT[2], false);
        mc.getSoundManager().play(snd);
        EVENTS[free] = snd;
        newThisSecond++;
        lastEvent = String.format(Locale.ROOT, "%s%s at %.0f blocks", e.sound(), answer ? " (answer)" : "", d);
    }

    /**
     * How much of the world's nature ambience to let through (1 all, 0 none), eased: down in about a second, back up
     * over about five. AWAKEN's moments take the stage: a hush where you stand (an open gate, a Break), layered combat
     * or Lord music, a stage cue, a rift or the Well sounding near. Read by compat's AmbientSounds bridge; the
     * Conductor's own nature (played only without AmbientSounds) follows the same hush through its events.
     */
    public static float natureLevel() {
        return enabled ? nature : 1F;
    }

    private static float natureGoal(int music) {
        float goal = hushAt(AT.x, AT.y, AT.z);
        for (int i = 0; i < MAX_MUFFLES; i++) if (MUNTIL[i] > gameTime) goal = Math.min(goal, MNATURE[i]);
        if (music >= 2) goal = Math.min(goal, 0.55F);
        if (stageCue.getAsBoolean()) goal = Math.min(goal, 0.5F);
        float emphasis = 0F;
        for (Source src : SOURCES) emphasis = Math.max(emphasis, src.emphasis());
        return goal * (1F - 0.6F * Mth.clamp(emphasis, 0F, 1F));
    }

    /**
     * Slots 0-2: the shared world soundscape at weight 1 and the two strongest families: the Conductor's own nature,
     * played only when AmbientSounds isn't installed (with it in the pack, it plays the nature; Euan, 2026-10-25).
     * Slot 3: the place. Slots 4-5: overlays.
     */
    private static void pickScapes() {
        if (placesFor != Soundscapes.generation()) {
            PLACES.clear();
            placesFor = Soundscapes.generation();
        }
        SoundscapeDef placeDef = AT.biome == null ? null : PLACES.computeIfAbsent(AT.biome, Conductor::placeOf).orElse(null);
        place(PLACE, placeDef, 1F);
        for (int i = 0; i < MAX_OVERLAYS; i++) {
            boolean on = OUNTIL[i] > gameTime && OID[i] != null;
            place(OVERLAY + i, on ? Soundscapes.get(OID[i]) : null, on ? OW[i] : 0F);
        }
        if (ambientSoundsLoaded) {
            for (int i = 0; i < PLACE; i++) place(i, null, 0F);
            return;
        }
        int first = -1, second = -1;
        float[] w = AT.familyWeight;
        for (int f = 0; f < Families.COUNT; f++) {
            if (Soundscapes.get(Families.soundscape(f)) == null || w[f] < 0.02F) continue;
            if (first < 0 || w[f] > w[first]) {
                second = first;
                first = f;
            } else if (second < 0 || w[f] > w[second]) {
                second = f;
            }
        }
        place(0, Soundscapes.get(Families.WORLD), 1F);
        place(1, first < 0 ? null : Soundscapes.get(Families.soundscape(first)), first < 0 ? 0F : w[first]);
        place(2, second < 0 ? null : Soundscapes.get(Families.soundscape(second)), second < 0 ? 0F : w[second]);
    }

    /** Puts a soundscape in a slot: a new one restarts the slot's clock; one already elsewhere swaps in with its clock. */
    private static void place(int slot, SoundscapeDef def, float weight) {
        Scape s = SCAPES[slot];
        if (def == null) {
            s.def = null;
            s.weight = 0F;
            return;
        }
        if (s.def != def) {
            for (int o = 0; o < MAX_SCAPES; o++) {
                if (o != slot && SCAPES[o].def == def) { // the two families changed places: keep their clocks
                    SCAPES[slot] = SCAPES[o];
                    SCAPES[o] = s;
                    s = SCAPES[slot];
                    break;
                }
            }
            if (s.def != def) s.clock.start(def);
            s.def = def;
        }
        s.weight = weight;
    }

    /** A biome's place soundscape: {@code <ns>:place/<biome path>}, if one exists. */
    private static java.util.Optional<SoundscapeDef> placeOf(Holder<Biome> biome) {
        return biome.unwrapKey().map(k -> Soundscapes.get(Identifier.fromNamespaceAndPath(k.identifier().getNamespace(),
                "place/" + k.identifier().getPath())));
    }

    private static float rain(float inRain, float rainLevel) {
        return 1F + (inRain - 1F) * rainLevel;
    }

    static void clear() {
        AIR.stop();
        TEX.stopAll();
        TEX.endTick();
        for (int i = 0; i < MAX_EVENTS; i++) EVENTS[i] = null;
        for (Scape s : SCAPES) if (s != null) {
            s.def = null;
            s.weight = 0F;
        }
        java.util.Arrays.fill(HUNTIL, 0L);
        java.util.Arrays.fill(HOWNER, null);
        java.util.Arrays.fill(OUNTIL, 0L);
        java.util.Arrays.fill(OOWNER, null);
        java.util.Arrays.fill(MUNTIL, 0L);
        java.util.Arrays.fill(MOWNER, null);
        nature = 1F;
        AT.reset();
    }

    static String status() {
        if (!enabled) return "World sound: off (/awaken audio world on)";
        if (!AT.valid) return "World sound: no world";
        StringBuilder sb = new StringBuilder("World sound: ");
        for (Scape s : SCAPES) {
            if (s.def != null) sb.append(String.format(Locale.ROOT, "%s %.2f%s; ", s.def.id(), s.weight, s.clock.lull() ? " (lull)" : ""));
        }
        int events = 0;
        for (SimpleSoundInstance e : EVENTS) if (e != null) events++;
        int others = 0;
        for (Source src : SOURCES) others += src.voices();
        sb.append(String.format(Locale.ROOT, "canopy %.2f, open %.2f, underwater %.2f, wind %.2f, height %.2f, rain %.2f; ",
                AT.canopy, AT.open, AT.underwater, AT.strength, AT.height, AT.rain));
        sb.append(AIR.status()).append("; ").append(TEX.status()).append("; ");
        sb.append(String.format(Locale.ROOT, "voices: %d textures, %d events, %d other (budget %d); duck %.2f, yield %.2f; last event %s",
                TEX.voices(), events, others, VOICE_BUDGET, duckBeds, yieldNow, lastEvent));
        sb.append(String.format(Locale.ROOT, "; nature %.2f", nature));
        if (ambientSoundsLoaded) sb.append(" (AmbientSounds plays the nature; ours is off)");
        if (Soundscapes.count() == 0) sb.append("; no soundscapes loaded");
        return sb.toString();
    }
}
