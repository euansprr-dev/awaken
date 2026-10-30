package dev.awaken.gates.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.progression.Rank;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Everything a gate's interior is built from (SPEC section 7). Immutable; the chunk generator rebuilds the same rooms
 * from the same plan on any thread. {@code annexTheme} is the theme of the hidden second dungeon (Double Gate) or the
 * small inner gate (Gate within a Gate); {@code annexRule} picks the Double Gate's law.
 */
public record GatePlan(long seed, int contentVersion, Rank rank, Theme theme, Layout layout, List<Modifier> modifiers,
                       Twist twist, int floors, String boss, Theme annexTheme, int annexRule, List<FloorWorld> worlds) {

    /**
     * Bump when generation changes: READY gates nobody entered are rebuilt on the new version. 2: the interiors' art
     * pass (set pieces, Lord arenas, taller rooms) and deeper floors that push back harder (docs/modules/gates.md).
     * 3: the prologue's ritual plan (boss {@link #RITUAL_BOSS}) is built as the Ritual of the Bell's single hall.
     * 4: the Gate Rig's rooms (SPEC §17): a Collapse Run, Mirror Hall or Gravity Wells on each ordinary floor of the
     * themes that have them; every other room is exactly what version 3 built. 5: the Gauntlet (SPEC §17.5) on the
     * ordinary floors of the Clockwork Foundry and the Frost Monastery, which had no rig room; every other theme builds
     * exactly as version 4. 6: Gate Worlds (SPEC §38): every floor its own plot in the cell, room for height, and the
     * world and mood picked per floor ({@link #worlds}); a floor with no world builds today's rooms inside its plot.
     * 7: depth in the worlds' places (SPEC §38.14): ground that rises and falls, walls and roofs that change with height,
     * growth that thickens away from the way; a version 6 floor carves exactly as before.
     * 8: room for the Lords (SPEC §38.15): every arena's cover and props stand a block clear of its Lord's sweep, and the
     * inner gate's keeper gets the full arena when its body is too big for the small one; a version 7 arena is unchanged.
     */
    public static final int CONTENT_VERSION = 8;

    /**
     * The version a new gate is made at: {@link #CONTENT_VERSION}, or the one before the depth (6, its arenas as they
     * were) while the server's {@code gateWorldsDepth} switch is off (SPEC §38.14).
     */
    public static int current() {
        try {
            return dev.awaken.core.AwakenConfig.GATE_WORLDS_DEPTH.get() ? CONTENT_VERSION : 6;
        } catch (IllegalStateException notLoaded) {
            return CONTENT_VERSION;
        }
    }

    /**
     * The boss id that marks the story's ritual Bell Crypt (docs/proposals/the-ritual.md). Nothing rolls it: the Director
     * and every other plan use a theme's own Lord. Old scripted Bell Crypts (the Bell Warden) keep their three floors.
     */
    public static final String RITUAL_BOSS = "awaken:the_seated_one";

    /**
     * The boss id that marks the Leaper's Gauntlet, an Ascension trial (SPEC §17.10): one Clockwork Foundry floor whose
     * hall is a Gauntlet ending in a chasm only the leap crosses. Nothing rolls it: the Ascent opens it for a player.
     */
    public static final String LEAPER_TRIAL = "awaken:leapers_gauntlet";

    /**
     * The boss id that marks the Sky Leviathan's trial, Skyborn's Ascension trial (SPEC §17.18): one Sky Archipelago
     * floor whose arena is open sky (4x4 slots, the full height), its Lord the theme's own Sky Leviathan. Nothing rolls
     * it: the Ascent opens it for a player.
     */
    public static final String SKYBORN_TRIAL = "awaken:sky_leviathans_trial";

    public static final Codec<GatePlan> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("seed").forGetter(GatePlan::seed),
            Codec.INT.fieldOf("content_version").forGetter(GatePlan::contentVersion),
            Rank.CODEC.fieldOf("rank").forGetter(GatePlan::rank),
            Theme.CODEC.fieldOf("theme").forGetter(GatePlan::theme),
            Layout.CODEC.fieldOf("layout").forGetter(GatePlan::layout),
            Modifier.CODEC.listOf().fieldOf("modifiers").forGetter(GatePlan::modifiers),
            Twist.CODEC.fieldOf("twist").forGetter(GatePlan::twist),
            Codec.INT.fieldOf("floors").forGetter(GatePlan::floors),
            Codec.STRING.fieldOf("boss").forGetter(GatePlan::boss),
            Theme.CODEC.fieldOf("annex_theme").forGetter(GatePlan::annexTheme),
            Codec.INT.optionalFieldOf("annex_rule", 0).forGetter(GatePlan::annexRule),
            FloorWorld.CODEC.listOf().optionalFieldOf("worlds", List.of()).forGetter(GatePlan::worlds)
    ).apply(i, GatePlan::new));

    public GatePlan {
        modifiers = List.copyOf(modifiers);
        floors = Math.max(1, Math.min(6, floors));
        worlds = List.copyOf(worlds);
    }

    /** A plan with no worlds picked (every floor today's rooms, or a version before 6). */
    public GatePlan(long seed, int contentVersion, Rank rank, Theme theme, Layout layout, List<Modifier> modifiers, Twist twist,
                    int floors, String boss, Theme annexTheme, int annexRule) {
        this(seed, contentVersion, rank, theme, layout, modifiers, twist, floors, boss, annexTheme, annexRule, List.of());
    }

    /** The world picked for a floor, or null (today's rooms). Versions before 6 never have one. */
    public FloorWorld worldFor(int area, int floor) {
        if (contentVersion < 6) return null;
        for (FloorWorld w : worlds) if (w.area() == area && w.floor() == floor) return w;
        return null;
    }

    /** Copy with the worlds picked for its floors. */
    public GatePlan withWorlds(List<FloorWorld> picked) {
        return new GatePlan(seed, contentVersion, rank, theme, layout, modifiers, twist, floors, boss, annexTheme, annexRule, picked);
    }

    /** Copy with another floor count (keeps the worlds; a floor past the end is never asked for). */
    public GatePlan withFloors(int n) {
        return new GatePlan(seed, contentVersion, rank, theme, layout, modifiers, twist, n, boss, annexTheme, annexRule, worlds);
    }

    /** The prologue's ritual gate: one floor, the antechamber and the hall of the Seated One. */
    public boolean ritual() {
        return contentVersion >= 3 && theme == Theme.BELL_CRYPT && RITUAL_BOSS.equals(boss);
    }

    /** The Leaper's Gauntlet: one floor, an antechamber, the course's hall and the room past it. */
    public boolean leaperTrial() {
        return contentVersion >= 5 && theme == Theme.CLOCKWORK_FOUNDRY && LEAPER_TRIAL.equals(boss);
    }

    /** The Sky Leviathan's trial: one floor, the antechamber and the open-sky arena of the Leviathan. */
    public boolean skybornTrial() {
        return contentVersion >= 5 && theme == Theme.SKY_ARCHIPELAGO && SKYBORN_TRIAL.equals(boss);
    }

    /** The Lord that holds the boss room (a trial's marker names the theme's own Lord). */
    public String lordId() {
        return skybornTrial() ? theme.bossId() : boss;
    }

    public boolean has(Modifier m) {
        return modifiers.contains(m);
    }

    public boolean is(Twist t) {
        return twist == t;
    }

    /** A gate with no combat needed: the Lantern Night Market theme or the Friendly twist. */
    public boolean friendly() {
        return theme.friendly || twist == Twist.FRIENDLY;
    }

    public boolean hasAnnex() {
        return twist == Twist.DOUBLE_GATE || twist == Twist.GATE_WITHIN;
    }

    /** The floor that holds the inner gate (Gate within a Gate). */
    public int innerGateFloor() {
        return Math.min(2, Math.max(1, floors - 1));
    }

    /** Copy with a new content version (used when an unentered gate is rebuilt after an update). */
    public GatePlan rebuilt(int version) {
        return new GatePlan(seed, version, rank, theme, layout, modifiers, twist, floors, boss, annexTheme, annexRule, worlds);
    }

    /** Force names in play, for the plan's combination lines ("Heat meets Spores ..."). */
    public Set<String> forceTags() {
        Set<String> s = new HashSet<>(theme.forces);
        for (Modifier m : modifiers) {
            s.add(m.displayName);
            if (!m.forceName().isEmpty()) s.add(m.forceName());
        }
        return s;
    }

    private static final String[][] COMBOS = {
            {"Heat", "Spores", "Heat meets Spores: one spark chains through the room"},
            {"Charge", "Water", "Charge meets Water: everything standing in water is stunned, you too"},
            {"Gravity", "Volatile", "Gravity meets Volatile: pull them together and they detonate each other"},
            {"Spirit", "Mirror", "Spirit meets Mirror: your spirit can walk through the mirrors"},
            {"Heat", "Frost", "Heat meets Frost: steam blinds them and hides you"},
            {"Lightless", "Silence", "Lightless with Silence: the Listeners hunt by sound in the dark"},
            {"Glass", "Mirror", "Glass with Mirror: your shadow hits as hard as you do"},
    };

    public List<String> combos() {
        Set<String> tags = forceTags();
        List<String> out = new ArrayList<>();
        for (String[] c : COMBOS) if (tags.contains(c[0]) && tags.contains(c[1])) out.add(c[2]);
        return out;
    }
}
