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
    }

    static List<Step> steps(ServerPlayer p) {
        if (!Progression.profile(p).awakened()) return List.of();
        var home = HearthService.homeOf(p);
        if (home.isEmpty()) return List.of();
        HearthRecord h = home.get();
        List<Step> out = new ArrayList<>();
        if (!h.rooms.isEmpty() && !h.hasRoom(RoomType.FITTING)) out.add(armoury(h));
        if (h.hearthfire()) out.add(anchor(p, h));
        if (h.hasRoom(RoomType.FITTING) && !h.hasRoom(RoomType.ARMORY)) out.add(forge(h));
        if (h.hasRoom(RoomType.BEDROOM) && !h.hasRoom(RoomType.SANCTUARY)) out.add(sanctuary(h));
        Step tier = tier(p, h);
        if (tier != null) out.add(tier);
        return out;
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
        return Step.of("hearth/armoury", Goal.HEARTH, "Build your Armoury")
                .why("A Fitting Stand and a crafting table in one closed room. Your first armour is made there, before your first gate.")
                .needs(pieces(RoomType.FITTING, h)).at(h.pos).path(Goals.PATH_ARMOURY).priority(60)
                .covers("hearth:room/fitting", "hearth:why/fitting").build();
    }

    /** At the Hearthfire: a gate's Lord's Heart raises the Anchor. With a Heart in the pack, set it on the fire. */
    static Step anchor(ServerPlayer p, HearthRecord h) {
        if (HearthNow.holds(p, CoreApi.LORD_HEART.get())) {
            return Step.of("hearth/anchor", Goal.HEARTH, "Set the Lord's Heart on your fire")
                    .why("Hold it and right-click your campfire: the fire takes it and stands as your Anchor. More rooms, more nooks, your home's real start.")
                    .need(Need.item(p, CoreApi.LORD_HEART.get(), 1)).at(h.pos).path(Goals.PATH_ANCHOR).priority(64)
                    .covers("hearth:heart").build();
        }
        Goals.Builder b = Step.of("hearth/anchor", Goal.RANK, "Clear your first gate")
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
        return Step.of("hearth/forge", Goal.HEARTH, "Grow your Armoury into a Forge")
                .why("An anvil, the Forge and an armour stand in the same room: D-rank armour, weapon raises and tempering. The anvil and the Forge take about 38 iron.")
                .needs(pieces(RoomType.ARMORY, h)).at(h.pos).path(Goals.PATH_FORGE).priority(45)
                .covers("hearth:room/armory", "hearth:why/armory").build();
    }

    static Step sanctuary(HearthRecord h) {
        return Step.of("hearth/sanctuary", Goal.HEARTH, "Make your Bedroom a Sanctuary")
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
        boolean ranked = rank.ordinal() >= next.tier().ordinal();
        needs.add(Need.of(next.tier().label + "-rank", ranked));
        for (Costs.Part part : Costs.price(next.base(), rank, next.tier())) needs.add(Need.item(p, part.item(), part.count()));
        return Step.of("hearth/tier/" + target, Goal.HEARTH, "Grow your home to " + TierRules.name(target))
                .why("A bigger radius, more nooks for beasts, and the Forge tempers higher. Right-click the Anchor to raise it when all of this holds.")
                .needs(needs).at(h.pos).priority(35).covers("hearth:upgrade").build();
    }
}
