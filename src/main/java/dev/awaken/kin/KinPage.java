package dev.awaken.kin;

import dev.awaken.core.api.Destinations;
import dev.awaken.hearth.Hearth;
import dev.awaken.hearth.HearthRecord;
import dev.awaken.hearth.HearthService;
import dev.awaken.hearth.TierRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The Hearth page (SPEC §43.6; JOURNEY part 3, "The Hearth page"), built on the server for one player: Home (what the
 * Hearth can't do yet, each beast need with a rumour of where to look; the next tier; the Great Belfry pinned and dim),
 * Your beasts (who lives with you, what each is doing, how it's feeling, what it made today) and Kin (every kind met or
 * heard of). Nothing on it expires or renews daily. There's always something on it: the Kin list always holds at least
 * one rumour not yet followed.
 */
public final class KinPage {
    private KinPage() {}

    /** The beast needs in the order a home meets them (the Ember's light and field first). */
    static final KinKind[] NEEDS = {KinKind.LAMPWING, KinKind.BURROWBUN, KinKind.MOSSBACK, KinKind.PEBBLEPUP, KinKind.THATCHBACK, KinKind.EMBERKIT,
            KinKind.DUMPLIN, KinKind.PUFFWOOL, KinKind.TOTTER, KinKind.PONDLING, KinKind.SNUFFLET, KinKind.CHIMEWREN, KinKind.BUMBLEPUFF, KinKind.LOOMWORM,
            KinKind.BOULDERBACK, KinKind.CLOUDCALF};
    /** The page shows this many beast needs at once (the earliest unmet); the next shows once one is met. */
    static final int NEEDS_SHOWN = 4;

    static KinNet.Page build(ServerPlayer p) {
        ServerLevel level = p.level();
        Optional<Hearth.HearthInfo> h = Hearth.home(p);
        BlockPos from = h.map(Hearth.HearthInfo::anchor).orElse(p.blockPosition());
        List<List<String>> home = new ArrayList<>(), beasts = new ArrayList<>(), kin = new ArrayList<>();
        Map<KinKind, Integer> living = new EnumMap<>(KinKind.class);
        if (h.isEmpty()) {
            home.add(List.of("You have no home yet. Out past the ridge, set a Hearthfire down where you want to live; beasts will come to it.", "", "", "need"));
        } else {
            Hearth.HearthInfo info = h.get();
            KinWorld w = KinWorld.get(level.getServer());
            List<KinWorld.Homed> homed = w.at(info.anchor());
            for (KinWorld.Homed m : homed) living.merge(m.kind(), 1, Integer::sum);
            int nooks = KinHome.nooks(level, info);
            home.add(List.of(TierRules.name(info.tier()) + " · " + homed.size() + " of " + nooks + " nooks taken", "", "", "head"));
            boolean full = homed.size() >= nooks;
            int shown = 0;
            for (KinKind k : NEEDS) {
                if (living.containsKey(k) || shown >= NEEDS_SHOWN) continue;
                String need = need(level, info, k);
                if (need == null) continue;
                home.add(List.of(need, full ? "" : rumour(p, k, from, true), full ? "" : k.id(), "need"));
                shown++;
            }
            for (String line : dev.awaken.kin.works.WorkPlans.lines(level, info.anchor(), Math.max(info.radius(), 32))) home.add(List.of(line, "", "", "need"));
            for (KinBeast t : KinApi.loaded(level, info.anchor(), KinKind.TOTTER))
                if (t.taskUntil > level.getGameTime() && t.task == KinWorks.NONE) {
                    home.add(List.of("The Storehouse is full. It needs another bay: one more chest or barrel in the hall.", "", "", "need"));
                    break;
                }
            if (full) home.add(List.of("Every nook is taken. A bigger Hearth has more, and the Ore Shed has two of its own.", "", "", "need"));
            Optional<HearthRecord> rec = HearthService.homeOf(p);
            if (rec.isPresent() && info.tier() < 5) {
                List<TierRules.Need> next = TierRules.needs(rec.get(), info.tier() + 1);
                if (!next.isEmpty()) {
                    home.add(List.of("To grow into a " + TierRules.name(info.tier() + 1) + ":", "", "", "tier"));
                    for (TierRules.Need n : next) home.add(List.of(n.text(), "", "", n.met() ? "met" : "need"));
                }
            }
            String belfry = dev.awaken.core.api.BellHooks.belfry(p);
            home.add(belfry.isEmpty() ? List.of("The Great Belfry. It holds all hundred bells. What it needs isn't known yet.", "", "", "dim")
                    : List.of(belfry, "", "", belfry.startsWith("Your Great Belfry stands") ? "met" : "need"));
            for (KinWorld.Homed m : homed) beasts.add(beast(level, m, living.getOrDefault(m.kind(), 1)));
        }
        Kinship ks = Kinship.of(p);
        boolean openRumour = false;
        for (KinKind k : KinKind.livingNow()) {
            Kinship.Kind s = ks.kinds.get(k);
            if (s == null || (!s.met && !s.heard)) continue;
            if (!s.met) openRumour = true;
            kin.add(card(p, k, s, from, h.isPresent()));
        }
        if (!openRumour) {
            // always one rumour not yet followed, tied to the home's next need
            for (KinKind k : NEEDS) {
                Kinship.Kind s = ks.kinds.get(k);
                if ((s != null && s.met) || living.containsKey(k)) continue;
                if (KinSpawner.rumourFor(p, k, from) == null) continue;
                kin.add(card(p, k, Kinship.of(p).kind(k), from, h.isPresent()));
                break;
            }
        }
        return new KinNet.Page(home, beasts, kin);
    }

    /** What the home can't do yet without this kind, in the System's words; null when it isn't a need yet. */
    static @Nullable String need(ServerLevel level, Hearth.HearthInfo h, KinKind k) {
        return switch (k) {
            case LAMPWING -> "Nothing lights your yard at night.";
            case BURROWBUN -> KinHome.nookFor(level, h, k) != null ? "Nobody's minding the fields."
                    : "Nobody's minding the fields, and there aren't any yet. Tilled ground at home would be a start.";
            case MOSSBACK -> "Nothing makes your crops grow any faster.";
            case PEBBLEPUP -> KinWorld.get(level.getServer()).oreHeart(h.anchor()) == null
                    ? "The Ore Shed needs a lode heart. Hearts beat inside lodes, deep underground; break one free and bring it home."
                    : "Nobody works the Ore Shed.";
            case THATCHBACK -> "Nobody builds your works for you. A Work Plan pegged out at home shows a Thatchback what to build.";
            case EMBERKIT -> KinHome.nookFor(level, h, k) != null ? "Nobody smelts your ore for you."
                    : "Nobody smelts your ore for you. An Emberkit needs somewhere warm: a lit campfire, or a Smeltery round a vent stone from a hot spring.";
            case DUMPLIN -> KinHome.nookFor(level, h, k) != null ? "Nobody leaves you eggs." : "Nobody leaves you eggs. A Dumplin flock nests on a bale of hay.";
            case PUFFWOOL -> KinHome.nookFor(level, h, k) != null ? "Nothing at home gives you wool." : "Nothing at home gives you wool. A Puffwool lies by a bale of hay.";
            case TOTTER -> KinHome.nookFor(level, h, k) != null ? "Nobody carries things between your works."
                    : "Nobody carries things between your works. A Totter looks after a chest or barrel in one of your rooms.";
            case PONDLING -> KinHome.nookFor(level, h, k) != null ? "Nothing fishes your water." : "Nothing fishes your water. A Pondling needs a pond open to the sky.";
            case SNUFFLET -> "Nobody forages round your home.";
            case CHIMEWREN -> KinHome.nookFor(level, h, k) != null ? "Nothing brings you word of sleeping bells."
                    : "Nothing brings you word of sleeping bells. Chimewrens roost in a Rookery: a room with a Chime Perch, made round the clapper of a bell you've woken.";
            case BUMBLEPUFF -> KinHome.nookFor(level, h, k) != null ? "Nothing at home makes honey or wax."
                    : "Nothing at home makes honey or wax. A Bumblepuff lives in a bee nest or a beehive.";
            case LOOMWORM -> KinHome.nookFor(level, h, k) != null ? "Nobody spins you thread or cloth."
                    : "Nobody spins you thread or cloth. A Loomworm wants leaves at home it can reach from the ground: a low tree, or a hedge.";
            case BOULDERBACK -> KinHome.nookFor(level, h, k) != null ? "Nobody carries stone for your works."
                    : "Nobody carries stone for your works. Whatever does would want somewhere dark at home: a room with no light and no sky.";
            case CLOUDCALF -> KinHome.nookFor(level, h, k) != null ? "Nothing waters your fields."
                    : "Nothing waters your fields, and there aren't any open to the sky.";
            default -> null;
        };
    }

    static String rumour(ServerPlayer p, KinKind k, BlockPos from, boolean home) {
        Kinship.Kind s = Kinship.of(p).kind(k);
        if (k == KinKind.LAMPWING && Kinship.of(p).lesson == 1) return "The one that's been watching your fire, from the trees nearby.";
        if (s.met) return "You've met one: " + k.where + ".";
        BlockPos at = KinSpawner.rumourFor(p, k, from);
        if (at == null) return "None lives within a long walk of here.";
        return KinSpawner.rumourLine(p, k, at, from, home);
    }

    private static List<String> beast(ServerLevel level, KinWorld.Homed m, int ofKind) {
        String doing = "at home, too far off to see";
        String made = "";
        if (level.getEntity(m.id()) instanceof KinBeast b) {
            boolean today = b.madeDay == level.getOverworldClockTime() / 24000L;
            doing = switch (b.act()) {
                case KinBeast.SLEEP -> "asleep in its nook";
                case KinBeast.HIDE -> "hiding, hurt. It will come back when it's well";
                case KinBeast.WORK -> switch (m.kind()) {
                    case LAMPWING -> "lighting the yard";
                    case BURROWBUN -> b.carry.isEmpty() ? "tending the fields" : "carrying the harvest to the chest";
                    case MOSSBACK -> "sitting in the wet, the crops round it growing";
                    case PEBBLEPUP -> "sorting the Ore Shed's barrels";
                    case TOTTER -> "putting things in their places";
                    case THATCHBACK -> "building from a plan";
                    case EMBERKIT -> "keeping the fires hot";
                    case PUFFWOOL -> "grazing by the hay";
                    case DUMPLIN -> "scratching about the yard";
                    case PONDLING -> "fishing";
                    case SNUFFLET -> "foraging";
                    case CHIMEWREN -> "singing on its perch";
                    case BUMBLEPUFF -> "drifting round the hive";
                    case LOOMWORM -> "spinning";
                    case CLOUDCALF -> "raining on the field";
                    case BOULDERBACK -> "sitting in the dark";
                    default -> "working";
                };
                case KinBeast.CARRY -> b.task == KinWorks.FETCH_TAKE || b.task == KinWorks.FETCH_PUT ? "fetching something to the Ledger" : "carrying things where they go";
                case KinBeast.SNIFF -> "snuffling for something";
                default -> "about the yard";
            };
            if (today && b.made > 0) made = switch (m.kind()) {
                case BURROWBUN -> b.made + " harvested today";
                case MOSSBACK -> b.made + " crops grown on today";
                case PEBBLEPUP -> b.made + " sorted today";
                case TOTTER -> b.made + " put away today";
                case THATCHBACK -> b.made + " blocks laid today";
                case EMBERKIT -> b.made + " smelted today";
                case PUFFWOOL -> b.made + " wool today";
                case DUMPLIN -> b.made + (b.made == 1 ? " egg today" : " eggs today");
                case PONDLING -> b.made + " fish today";
                case SNUFFLET -> b.made + " things foraged today";
                case CHIMEWREN -> "sang you a note today";
                case BUMBLEPUFF -> "left you honey today";
                case LOOMWORM -> "spun you thread today";
                case CLOUDCALF -> b.made + " crops rained on today";
                case BOULDERBACK -> "brought you stone today";
                default -> "";
            };
        }
        String feeling = "settled in its nook" + (ofKind > 1 ? ", glad of its own kind's company" : "");
        return List.of(m.kind().id(), m.kind().title, doing, feeling, made);
    }

    private static List<String> card(ServerPlayer p, KinKind k, Kinship.Kind s, BlockPos from, boolean home) {
        String where = s.met ? "Met on day " + (s.metDay + 1) + ", near " + s.metX + ", " + s.metZ + "." : "Heard of, not met.";
        List<String> wants = new ArrayList<>();
        for (int i = 0; i < Math.min(s.watched, k.watch.size()); i++) wants.add(k.watch.get(i));
        String step = s.step().label + (s.step() == Kinship.Step.KIN ? " · it calls you " + k.callsYou : "");
        String rumour = s.met ? "" : rumour(p, k, from, home);
        return List.of(k.id(), k.title, s.met ? "1" : "0", where, "Could live with you. " + k.job, wants.isEmpty() ? "?" : String.join("\n", wants), step,
                s.lastLine, rumour, Integer.toHexString(k.colour));
    }

    /** Track: a rough bearing to where the kind lives (never the spot). */
    static void track(ServerPlayer p, KinKind k) {
        if (!k.built) return;
        BlockPos from = Hearth.home(p).map(Hearth.HearthInfo::anchor).orElse(p.blockPosition());
        Kinship.Kind s = Kinship.of(p).kind(k);
        BlockPos at = s.met && !s.rumour ? new BlockPos(s.metX, 64, s.metZ) : KinSpawner.rumourFor(p, k, from);
        if (at == null) return;
        Destinations.set(p, new Destinations.Target("Where " + plural(k) + " live (roughly)", Level.OVERWORLD.identifier(), KinSpawner.rough(p, k, at), "rumour"));
        KinWatch.say(p, "[Tracking roughly where " + plural(k) + " live. The card points the way, never the spot.]", 6);
    }

    static String plural(KinKind k) {
        return k.title + "s";
    }
}
