package dev.awaken.hearth;

import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.GateHooks;
import dev.awaken.core.api.Goals;
import dev.awaken.core.api.Goals.Goal;
import dev.awaken.core.api.Goals.Need;
import dev.awaken.core.api.Goals.Step;
import dev.awaken.hearth.guide.Guide;
import dev.awaken.hearth.guide.GuideData;
import dev.awaken.hearth.room.Furniture;
import dev.awaken.hearth.room.RoomType;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Hearth's steps on the Tasks page (SPEC §27.13): the Armoury, your first workshop, right after a bed; the Anchor
 * (a gate's Lord's Heart set on your fire); growing the Armoury into the Forge; the next tier with what it asks; and a
 * Sanctuary, marked optional. A room's pieces are ticked only where they stand together in one closed room, as the
 * guide ticks them (SPEC §27.12). Nothing here for a home that isn't yours yet: the fire's own line says how to start.
 */
final class HearthGoals {
    private HearthGoals() {}

    /** How far a gate counts as "your first gate" for the Anchor's step. */
    static final int GATE_REACH = 600;

    static void register() {
        Goals.register(HearthGoals::steps);
        Goals.registerFooter(HearthGoals::footers);
    }

    static List<Step> steps(ServerPlayer p) {
        if (!Progression.profile(p).awakened()) return List.of();
        var home = HearthService.homeOf(p);
        if (home.isEmpty()) return List.of();
        HearthRecord h = home.get();
        List<Step> out = new ArrayList<>();
        // what the home has had, not the last scan: a room built stays built for its steps (SPEC §27.14)
        if (h.hadRoom() && !h.had(RoomType.FITTING)) out.add(armoury(h));
        if (h.hearthfire()) out.add(anchor(p, h));
        if (h.had(RoomType.FITTING) && !h.had(RoomType.ARMORY)) out.add(forge(h));
        if (h.hearthfire() && h.had(RoomType.BEDROOM) && !h.had(RoomType.SANCTUARY)) out.add(sanctuary(h)); // with an Anchor it's one of the picks below
        Step forgeTier = forgeTier(p, h);
        if (forgeTier != null) out.add(forgeTier);
        Step tier = tier(p, h);
        if (tier != null) out.add(tier);
        Step add = add(p, h);
        if (add != null) out.add(add);
        return out;
    }

    /** One thing the home could have next, in the order they're offered (SPEC §53). {@code rank} is when it's told, not a lock: a room still counts whenever it's built. */
    private record Option(RoomType room, Rank rank, String title, String sub, String detail) {}

    private static final List<Option> OPTIONS = List.of(
            new Option(RoomType.STOREHOUSE, Rank.C, "Build a Storehouse", "One place to drop everything",
                    "You tip things into the Drop and your Totters shelve them in the room's chests and barrels, one bay for each sort of thing. "
                            + "The Ledger desk finds anything. The Drop needs a Granary Keystone. Every town's granary keeps one against a ground-floor wall."),
            new Option(RoomType.FOUNDRY, Rank.C, "Build a Foundry", "Where bells are cast", "A room with a Crucible, bell bronze banded round a Deepglass bowl, and a barrel."),
            new Option(RoomType.SANCTUARY, Rank.E, "Make your Bedroom a Sanctuary", "A bed and a Meditation Mat", "Sleeping there heals a Fracture and you wake with Regeneration."),
            new Option(RoomType.KITCHEN, Rank.E, "Make a Kitchen", "A stove, a crafting table and a barrel", "All three in one closed room. Meals made there last half again as long."),
            new Option(RoomType.STUDY, Rank.E, "Make a Study", "A lectern and six bookshelves", "Written there, enchantments can go deeper than II."),
            new Option(RoomType.STABLE, Rank.E, "Make a Stable", "Hay and a fence gate", "A home for your mount, and your whistle carries twice as far."),
            new Option(RoomType.GREENHOUSE, Rank.D, "Make a Greenhouse", "Soil or planted pots under a glass roof", "Herbs will grow faster there."),
            new Option(RoomType.BELL_TOWER, Rank.D, "Make a Bell tower", "A bell hung 8 or more above your fire", "It rings at dawn and dusk and warns of gate breaks two play-days early. Steading asks for one."),
            new Option(RoomType.MINE_HEAD, Rank.C, "Build an Ore Shed", "A lode heart and a barrel", "Hearts beat inside lodes, deep underground; break one free and bring it home."),
            new Option(RoomType.HALL, Rank.C, "Make a Hall", "Six seats and a table on a big floor", "A meal eaten in your hall feeds everyone in it."),
            new Option(RoomType.LOOKOUT, Rank.B, "Make a Lookout", "12 or more up, with glass on three sides", "From up there the Atlas fills in 256 blocks round home."),
            new Option(RoomType.SMELTERY, Rank.B, "Build a Smeltery", "Round a vent stone from a hot spring", "Hot springs have them."),
            new Option(RoomType.ROOKERY, Rank.B, "Build a Rookery", "Somewhere for Chimewrens to roost", "A Chime Perch is made round the clapper of a bell you've woken."));

    private static final String ADD = "hearth/add";
    private static final int ADD_SHOWN = 3;

    /** "Add to your home": one row, up to three things to build; a room you've started comes first, and what you picked stays on offer. */
    private static @Nullable Step add(ServerPlayer p, HearthRecord h) {
        if (h.hearthfire()) return null;
        Rank rank = Progression.rank(p);
        String picked = Goals.chosen(p, ADD);
        List<Option> open = new ArrayList<>();
        for (Option o : OPTIONS) {
            if (h.had(o.room())) continue;
            if (rank.ordinal() >= o.rank().ordinal() || HearthNow.started(o.room(), h) || id(o).equals(picked)) open.add(o);
        }
        open.sort(java.util.Comparator.comparing((Option o) -> !id(o).equals(picked)).thenComparing(o -> !HearthNow.started(o.room(), h)));
        if (open.size() > ADD_SHOWN) open = open.subList(0, ADD_SHOWN);
        if (open.isEmpty()) return null;
        Goals.Builder b = Step.of(ADD, Goal.HEARTH, "Add to your home").thread("hearth.build", 40).priority(33).at(h.pos)
                .why(switch (open.size()) {
                    case 1 -> "One more thing your home could have. Pick it and this row becomes it. You can change your mind.";
                    case 2 -> "Two things your home could have next. Pick one and this row becomes it. You can change your mind.";
                    default -> "Three things your home could have next. Pick one and this row becomes it. You can change your mind.";
                });
        for (Option o : open) {
            List<Need> needs = new ArrayList<>(pieces(o.room(), h));
            if (o.room() == RoomType.STOREHOUSE && p.level() instanceof ServerLevel level) needs.add(Need.of("A Totter", dev.awaken.kin.KinApi.livingWith(level, p, dev.awaken.kin.KinKind.TOTTER) > 0));
            List<String> show = new ArrayList<>();
            for (Need n : needs) if (!n.item().isEmpty() && show.size() < 4) show.add(n.item());
            b.choice(new Goals.Choice(id(o), o.title(), o.sub(), show, needs, o.detail()));
            String name = o.room().name().toLowerCase(java.util.Locale.ROOT);
            b.covers("hearth:room/" + name).teaches("room:" + name);
        }
        return b.build();
    }

    private static String id(Option o) {
        return o.room().name().toLowerCase(java.util.Locale.ROOT);
    }

    /** The Forge's next tier, once your rank opens it (before that the foot of the tab says when). */
    private static @Nullable Step forgeTier(ServerPlayer p, HearthRecord h) {
        if (!h.had(RoomType.ARMORY) || h.forgeTier >= HearthRecord.MAX_FORGE_TIER) return null;
        int target = h.forgeTier + 1;
        Upgrades.Def next = Upgrades.forgeTier(target);
        Rank rank = Progression.rank(p);
        if (rank.ordinal() < next.tier().ordinal()) return null;
        List<Need> needs = new ArrayList<>();
        for (Costs.Part part : Costs.price(next.base(), rank, next.tier())) needs.add(Need.item(p, part.item(), part.count()));
        return Step.of("hearth/forge_tier/" + target, Goal.HEARTH, "Raise your Forge to Tier " + Upgrades.roman(target)).thread("hearth.home", 55)
                .why("Your Forge tempers two levels higher for every tier. Sneak and use the Forge with an empty hand when you have all of this.")
                .needs(needs).at(h.pos).priority(36).teaches("forge_tier:" + target).build();
    }

    /** What the next rank opens for your home, at the foot of the Hearth tab. */
    private static List<Goals.Footer> footers(ServerPlayer p) {
        if (!Progression.profile(p).awakened()) return List.of();
        var home = HearthService.homeOf(p);
        if (home.isEmpty()) return List.of();
        HearthRecord h = home.get();
        Rank rank = Progression.rank(p);
        for (int r = rank.ordinal() + 1; r < Rank.values().length; r++) {
            Rank next = Rank.values()[r];
            int forge = 0, tier = 0; // the first tier this rank opens: a lower one you haven't taken yet is already on the list
            for (int t = h.forgeTier + 1; t <= HearthRecord.MAX_FORGE_TIER && forge == 0; t++) if (Upgrades.forgeTier(t).tier() == next) forge = t;
            for (int t = Math.max(h.tier + 1, 2); t <= HearthRecord.MAX_TIER && tier == 0; t++) if (Upgrades.anchorTier(t).tier() == next) tier = t;
            boolean forges = forge > 0, grows = tier > 0;
            if (!forges && !grows) continue;
            String at = "At " + next.label + "-rank (level " + next.levelRequired + ") ";
            String f = "your Forge can go to Tier " + Upgrades.roman(forge);
            return List.of(new Goals.Footer(Goal.HEARTH, forges && grows ? at + f + " and your home to " + TierRules.name(tier) + "."
                    : forges ? at + f + "." : at + "your home can grow to " + TierRules.name(tier) + "."));
        }
        return h.tier < HearthRecord.MAX_TIER ? List.of() : List.of(new Goals.Footer(Goal.HEARTH, TierRules.name(HearthRecord.MAX_TIER) + " is the last size your home can reach."));
    }

    /** A room kind's pieces, each ticked where it stands in the room that has most of them (the guide's rule). */
    private static List<Need> pieces(RoomType t, HearthRecord h) {
        GuideData.RoomCard c = Guide.card(t, h);
        List<Need> out = new ArrayList<>();
        for (Furniture f : t.furniture) {
            Item item = f.icon().getItem();
            String label = Character.toUpperCase(f.label.charAt(0)) + f.label.substring(1);
            out.add(new Need(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString(), label, (c.have() & f.bit()) != 0 ? 1 : 0, 1));
        }
        out.add(Need.of(c.room().isEmpty() ? "In one closed room" : "In " + c.room(), c.closed()));
        return out;
    }

    static Step armoury(HearthRecord h) {
        return Step.of("hearth/armoury", Goal.HEARTH, "Build your Armoury").thread("hearth.build", 10)
                .why("A Fitting Stand and a crafting table in one closed room. Your first armour is made there, before your first gate.")
                .needs(pieces(RoomType.FITTING, h)).at(h.pos).path(Goals.PATH_ARMOURY).priority(60)
                .covers("hearth:room/fitting", "hearth:why/fitting").build();
    }

    /** At the Hearthfire: a gate's Lord's Heart raises the Anchor. With a Heart in the pack, set it on the fire. */
    static Step anchor(ServerPlayer p, HearthRecord h) {
        if (HearthNow.holds(p, CoreApi.LORD_HEART.get())) {
            return Step.of("hearth/anchor", Goal.HEARTH, "Set the Lord's Heart on your fire").thread("hearth.home", 50)
                    .why("Hold it and right-click your campfire: the fire takes it and stands as your Anchor. More rooms, more nooks, your home's real start.")
                    .need(Need.item(p, CoreApi.LORD_HEART.get(), 1)).at(h.pos).path(Goals.PATH_ANCHOR).priority(64)
                    .covers("hearth:heart").build();
        }
        Goals.Builder b = Step.of("hearth/anchor", Goal.RANK, "Clear your first gate").thread("rank.next", 0)
                .why("Its Lord drops a Lord's Heart. Set it on your fire to raise the Anchor: more rooms, more nooks, your home's real start.")
                .need(Need.item(p, CoreApi.LORD_HEART.get(), 1)).path(Goals.PATH_FIRST_GATE).priority(55).covers("gate:near:").covers("progression:directive/"); // the Registry's first floor is part of it
        BlockPos gate = nearestGate(p);
        if (gate != null) b.at(gate);
        return b.build();
    }

    private static @Nullable BlockPos nearestGate(ServerPlayer p) {
        if (p.level().dimension() != Level.OVERWORLD || !(p.level() instanceof ServerLevel level)) return null;
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (GateHooks.GateView g : GateHooks.near(level, p.blockPosition(), GATE_REACH)) {
            if (!g.state().equals("READY") && !g.state().equals("ACTIVE") && !g.state().equals("OPEN")) continue; // enterable, not cleared
            double d = g.pos().distSqr(p.blockPosition());
            if (d < bestD) {
                bestD = d;
                best = g.mouth();
            }
        }
        return best;
    }

    static Step forge(HearthRecord h) {
        return Step.of("hearth/forge", Goal.HEARTH, "Grow your Armoury into a Forge").thread("hearth.build", 30)
                .why("Set an anvil and the Forge in the room with your Fitting Stand: D-rank armour, weapon raises and tempering. The anvil and the Forge take about 38 iron.")
                .needs(pieces(RoomType.ARMORY, h)).at(h.pos).path(Goals.PATH_FORGE).priority(45)
                .covers("hearth:room/armory", "hearth:why/armory").build();
    }

    static Step sanctuary(HearthRecord h) {
        return Step.of("hearth/sanctuary", Goal.HEARTH, "Make your Bedroom a Sanctuary").thread("hearth.build", 20)
                .why("Add a Meditation Mat by the bed: sleeping there heals a Fracture and you wake with Regeneration.")
                .needs(pieces(RoomType.SANCTUARY, h)).at(h.pos).optional().priority(20)
                .covers("hearth:room/sanctuary", "hearth:why/sanctuary").build();
    }

    /** The next tier: what the home must have grown, and its price (the rank it waits on, said plainly). */
    static @Nullable Step tier(ServerPlayer p, HearthRecord h) {
        if (h.hearthfire() || h.tier >= HearthRecord.MAX_TIER) return null;
        int target = h.tier + 1;
        Upgrades.Def next = Upgrades.anchorTier(target);
        List<Need> needs = new ArrayList<>();
        for (TierRules.Need n : TierRules.needs(h, target)) needs.add(Need.of(Character.toUpperCase(n.text().charAt(0)) + n.text().substring(1), n.met()));
        var rank = Progression.rank(p);
        if (rank.ordinal() < next.tier().ordinal()) return null; // the foot of the tab says when: nothing on the list is out of reach

        for (Costs.Part part : Costs.price(next.base(), rank, next.tier())) needs.add(Need.item(p, part.item(), part.count()));
        return Step.of("hearth/tier/" + target, Goal.HEARTH, "Grow your home to " + TierRules.name(target)).thread("hearth.home", 60)
                .why("A bigger radius, more nooks for beasts, and the Forge tempers higher. Right-click the Anchor to raise it when all of this holds.")
                .needs(needs).at(h.pos).priority(35).covers("hearth:upgrade").build();
    }
}
