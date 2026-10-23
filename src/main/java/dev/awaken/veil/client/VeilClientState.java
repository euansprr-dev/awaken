package dev.awaken.veil.client;

import dev.awaken.progression.client.CodexEntries;
import dev.awaken.veil.net.VeilPayloads;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/** What the client knows about the Veil: the last state, meditation progress, sight and Codex cards. */
public final class VeilClientState {
    private VeilClientState() {}

    static VeilPayloads.State state = VeilPayloads.State.NONE;
    static int ticksLeft;
    static long age;
    static VeilPayloads.Meditation meditation = new VeilPayloads.Meditation(0, false, false, false);
    static int medTicks;
    static VeilPayloads.Sight sight = VeilPayloads.Sight.EMPTY;
    static final Int2IntOpenHashMap OUTLINES = new Int2IntOpenHashMap();
    static List<CodexEntries.Entry> codex = List.of();
    static int bodyHurtFlash;
    static int glimpse;
    static CameraType glimpseRestore;
    static int glimpseFlash;

    public static boolean spirit() {
        return state.spirit();
    }

    public static boolean possessing() {
        return state.spirit() && state.possessedId() >= 0;
    }

    static void onState(VeilPayloads.State s) {
        boolean was = state.spirit();
        state = s;
        ticksLeft = s.ticksLeft();
        if (s.bodyHurt()) bodyHurtFlash = 14;
        if (!s.spirit()) {
            sight = VeilPayloads.Sight.EMPTY;
            OUTLINES.clear();
            if (was) VeilInput.reset();
        }
    }

    static void onMeditation(VeilPayloads.Meditation m) {
        meditation = m;
        medTicks = m.ticks();
    }

    static void onSight(VeilPayloads.Sight s) {
        sight = s;
        OUTLINES.clear();
        for (int i = 0; i < s.mobIds().length && i < s.mobColours().length; i++) OUTLINES.put(s.mobIds()[i], s.mobColours()[i]);
    }

    static void onCodex(VeilPayloads.Codex c) {
        List<CodexEntries.Entry> out = new ArrayList<>();
        for (VeilPayloads.CodexCard card : c.cards()) out.add(new CodexEntries.Entry(card.id(), card.category(), card.title(), card.body(), card.found(), card.links()));
        codex = List.copyOf(out);
    }

    static void onGlimpse(VeilPayloads.Glimpse g) {
        Minecraft mc = Minecraft.getInstance();
        if (glimpse <= 0) glimpseRestore = mc.options.getCameraType();
        glimpse = g.ticks();
        glimpseFlash = 8;
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
    }

    static List<CodexEntries.Entry> codexEntries() {
        return codex;
    }

    /** Client tick: count down locally between server updates. */
    static void tick() {
        age++;
        if (state.spirit() && ticksLeft > 0) ticksLeft--;
        if (meditation.ticks() > 0 && medTicks > 0) medTicks++;
        if (bodyHurtFlash > 0) bodyHurtFlash--;
        if (glimpseFlash > 0) glimpseFlash--;
        if (glimpse > 0 && --glimpse == 0 && glimpseRestore != null) {
            Minecraft.getInstance().options.setCameraType(glimpseRestore);
            glimpseRestore = null;
        }
    }

    static void reset() {
        state = VeilPayloads.State.NONE;
        meditation = new VeilPayloads.Meditation(0, false, false, false);
        medTicks = 0;
        sight = VeilPayloads.Sight.EMPTY;
        OUTLINES.clear();
        codex = List.of();
        glimpse = 0;
        glimpseRestore = null;
    }
}
