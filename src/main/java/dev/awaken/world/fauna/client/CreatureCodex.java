package dev.awaken.world.fauna.client;

import dev.awaken.mobs.def.Bestiary;
import dev.awaken.mobs.def.FaunaRole;
import dev.awaken.mobs.def.MobDef;
import dev.awaken.mobs.def.Theme;
import dev.awaken.progression.Profile;
import dev.awaken.progression.Progression;
import dev.awaken.progression.client.CodexEntries;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * The Codex's Creatures column: every overworld creature this player has seen (the profile's discovery counters,
 * synced to the client), what it is for and where it lives. A named beast is threaded to the creatures of its family.
 */
public final class CreatureCodex {
    private CreatureCodex() {}

    static final String CATEGORY = "Creatures";

    static List<CodexEntries.Entry> entries() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return List.of();
        Profile p = Progression.profile(mc.player);
        List<CodexEntries.Entry> out = new ArrayList<>();
        for (MobDef d : Bestiary.fauna()) {
            if (!seen(p, d)) continue;
            StringBuilder body = new StringBuilder(d.signature).append(".\n").append(role(d.fauna)).append("\nLives in ").append(where(d.theme)).append('.');
            if (p.counter("awaken:discovered/beast_felled/" + d.id) > 0) body.append("\nYou have felled it.");
            var mount = dev.awaken.mobs.entity.Fauna3Kinds.mount(d.id);
            if (mount != null) {
                String food = new net.minecraft.world.item.ItemStack(mount.favouriteItem()).getHoverName().getString().toLowerCase(java.util.Locale.ROOT);
                body.append("\nA mount from level 11: sneak and feed it ").append(food).append(" until it trusts you, then put a Hunter's Bridle on it.");
            }
            if (d.fauna == FaunaRole.BEAST) {
                if (p.counter("awaken:discovered/lair/" + d.id) > 0) body.append("\nYou have found its lair.");
                else if (p.counter("awaken:lair_heard/" + d.id) > 0) body.append("\nYou have heard where its lair is (see the tracker).");
            }
            List<String> links = new ArrayList<>();
            if (d.fauna == FaunaRole.BEAST) {
                for (MobDef o : Bestiary.fauna()) if (o != d && o.theme == d.theme && seen(p, o)) links.add(id(o));
            }
            out.add(new CodexEntries.Entry(id(d), CATEGORY, d.name, body.toString(), true, List.copyOf(links)));
        }
        return out;
    }

    private static boolean seen(Profile p, MobDef d) {
        return p.counter("awaken:discovered/creature/" + d.id) > 0;
    }

    private static String id(MobDef d) {
        return "creature/" + d.id;
    }

    static String role(FaunaRole r) {
        return switch (r) {
            case AMBIENT -> "Ambient: never hostile.";
            case NEUTRAL -> "Neutral: fights back when struck, then calms down.";
            case HUNTER -> "Night hunter: hostile only in darkness. Torchlight and daylight are safe.";
            case BEAST -> "Named beast: holds a territory and warns before it fights. Essence and a good chance of a Power Core.";
        };
    }

    public static String where(Theme t) {
        return switch (t) {
            case GREENWILD -> "the Greenwild: forests, plains, meadows";
            case SUNSCORCH -> "the Sunscorch: deserts, savannas, badlands";
            case FROSTWILD -> "the Frostwild: taigas, snowy plains, groves";
            case MIRE -> "the Mire: swamps and mangrove swamps";
            case CANOPY -> "the Canopy: jungles and bamboo jungles";
            case DUSKWOOD -> "Duskwood: dark forests and the pale garden";
            case BLOSSOM -> "Blossomwood: cherry groves, flower and birch forests";
            case CRAG -> "the Crags: peaks, windswept hills, stony shores";
            case SPORE -> "Sporewild: the mushroom fields";
            case TIDEWATER -> "the Tidewater: rivers, lakes and the sea";
            case HOLLOWS -> "the Hollows: lush caves, dripstone caves, the deep dark";
            default -> t.display;
        };
    }
}
