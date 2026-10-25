package dev.awaken.arms.client;

import dev.awaken.arms.ArmsWeaves;
import dev.awaken.arms.net.ArmsPayloads;
import dev.awaken.powers.PowerType;
import dev.awaken.progression.client.CodexEntries;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Weaves on the Codex's board (SPEC §25 "Weaves"): a Weaves entry counting what you have found (N / ??, never naming
 * the rest), and one card for each Weave woven, threaded to it: its name, its shape and its element.
 */
final class ArmsCodex {
    private ArmsCodex() {}

    private static final Set<String> WOVEN = new TreeSet<>();
    private static final String CATEGORY = "Weaves", HUB = "arms_weaves";

    static void onWoven(ArmsPayloads.Woven msg) {
        WOVEN.clear();
        WOVEN.addAll(msg.weaves());
    }

    static void reset() {
        WOVEN.clear();
    }

    static List<CodexEntries.Entry> entries() {
        List<CodexEntries.Entry> out = new ArrayList<>();
        if (WOVEN.isEmpty()) return out;
        List<String> links = new ArrayList<>();
        for (String id : WOVEN) {
            int[] parts = ArmsWeaves.parts(id);
            if (parts == null) continue;
            PowerType.Family fam = PowerType.Family.values()[parts[1]];
            String key = "arms_weave_" + id;
            links.add(key);
            out.add(new CodexEntries.Entry(key, CATEGORY, ArmsWeaves.name(parts[0], fam), ArmsWeaves.describe(parts[0], fam), true, List.of(HUB)));
        }
        out.add(new CodexEntries.Entry(HUB, CATEGORY, "Weaves · " + WOVEN.size() + " / ??",
                "In the beat after the blade opens a way (a deflect, a perfect dodge, a broken stance, a chain in full rhythm), a power"
                        + " cast fuses with the weapon. Some Weaves you have found; others wait.", true, links));
        return out;
    }
}
