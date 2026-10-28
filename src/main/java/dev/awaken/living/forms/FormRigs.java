package dev.awaken.living.forms;

import dev.awaken.living.combat.Forms.Species;
import dev.awaken.mobs.client.anim.HeroRig;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The form bodies (SPEC §21.13): one rig per body and rung from C (2) to S (5), {@code form_<body>_<rung>}, where the body is the
 * species' layout (a husk wears the zombie's, a stray the skeleton's, a cave spider the spider's at 0.7, a mooshroom the cow's) and its
 * own paint. Plain data, so the server and the GameTests can check every rig.
 */
public final class FormRigs {
    private FormRigs() {}

    public static final int FIRST = 2, LAST = 5;

    public static String id(String body, int rung) {
        return "form_" + body + "_" + rung;
    }

    public static String id(Species sp, int rung) {
        return id(sp.layout, rung);
    }

    private static @Nullable Map<String, HeroRig> all;

    public static synchronized Map<String, HeroRig> all() {
        if (all == null) {
            Map<String, HeroRig> m = new LinkedHashMap<>();
            for (String[] src : FormData.SOURCES) {
                HeroRig r = HeroRig.parse(src);
                m.put(r.id, r);
            }
            all = Collections.unmodifiableMap(m);
        }
        return all;
    }

    /** A species' rig at a rung, or null (D wears the vanilla body; a body not built yet falls back to it too). */
    public static @Nullable HeroRig get(Species sp, int rung) {
        return rung < FIRST ? null : all().get(id(sp, Math.min(rung, LAST)));
    }
}
