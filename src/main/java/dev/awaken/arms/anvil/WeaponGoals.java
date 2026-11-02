package dev.awaken.arms.anvil;

import dev.awaken.Awaken;
import dev.awaken.arms.Archetypes;
import dev.awaken.arms.ArmsArmoury;
import dev.awaken.arms.ArmsData;
import dev.awaken.arms.ArmsItems;
import dev.awaken.arms.ArmsRaise;
import dev.awaken.arms.LordForged;
import dev.awaken.arms.Marks;
import dev.awaken.arms.net.ArmsPayloads.NeedRow;
import dev.awaken.core.api.ArmsApi;
import dev.awaken.core.api.Goals;
import dev.awaken.core.api.Goals.Goal;
import dev.awaken.core.api.Goals.Need;
import dev.awaken.core.api.HearthHooks;
import dev.awaken.hearth.Upgrades;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The War Anvil on the Weapons tab (SPEC §46.4, §46.7, §27.13): from your awakening the page says weapons are forged, and
 * what it takes. "Set up a War Anvil" (its recipe) until you've forged on one; "Forge your first weapon" until you have,
 * its choices the styles you know at the best tier you've reached, each with what it still takes; after that, "Make your
 * D-rank weapon" whenever you've reached a tier above the best you've forged. The first two are on the main path from
 * D-rank, after the Forge. A step that's done never comes back. Two optional steps at your anvil (§46.8): "Temper your
 * Beastbound Longsword" while the best weapon you carry is below your Forge's limit, and "Reforge your ..." while you carry
 * a found one (Marked or Lord-forged) below the best tier you've reached.
 */
final class WeaponGoals {
    private WeaponGoals() {}

    static final String ANVIL = "arms/war_anvil", FIRST = "arms/first_weapon", RANK = "arms/rank_weapon",
            TEMPER = "arms/temper", REFORGE = "arms/reforge";
    /** How many weapons this player has forged at a War Anvil (a progression counter). */
    static final String FORGED = "awaken:weapon_forged";

    static void register() {
        Goals.register(WeaponGoals::steps);
    }

    static List<Goals.Step> steps(ServerPlayer p) {
        if (!Progression.profile(p).awakened()) return List.of();
        SmithMemory mem = SmithMemory.of(p);
        List<Goals.Step> out = new ArrayList<>(forging(p, mem));
        held(p, mem, out);
        return out;
    }

    private static List<Goals.Step> forging(ServerPlayer p, SmithMemory mem) {
        boolean d = Progression.rank(p).ordinal() >= Rank.D.ordinal();
        int tier = reached(p);
        if (Progression.profile(p).counter(FORGED) > 0) {
            if (mem.best() < 0 || tier <= mem.best()) return List.of();
            BlockPos anvil = anvil(p, mem);
            Goals.Builder b = Goals.Step.of(RANK, Goal.WEAPONS, "Make your " + ArmsArmoury.TIERS[tier] + "-rank weapon")
                    .why("You've reached " + WarAnvil.tierName(tier) + " weapons, stronger than anything you've forged. Make one at your War Anvil.")
                    .need(Need.of("A War Anvil by the Forge", anvil != null && forgeNear(p, mem, anvil)))
                    .priority(40).covers("arms:forge/");
            choices(p, b, tier);
            if (anvil != null) b.at(anvil);
            return List.of(b.build());
        }
        BlockPos anvil = anvil(p, mem);
        List<Goals.Step> out = new ArrayList<>(2);
        if (anvil == null) {
            Goals.Builder b = Goals.Step.of(ANVIL, Goal.WEAPONS, "Set up a War Anvil")
                    .why("It makes weapons stronger than anything from a crafting table, by hand, Plain to Flawless. Set it down within 6 blocks of the Hearth's Forge.")
                    .path(d ? Goals.PATH_WAR_ANVIL : Goals.NO_PATH).priority(44);
            if (Goals.count(p.getInventory(), AnvilContent.ANVIL_ITEM.get()) > 0) b.need(Need.item(p, AnvilContent.ANVIL_ITEM.get(), 1));
            else for (NeedRow r : ArmsArmoury.needsOf(p, "war_anvil")) b.need(need(r));
            out.add(b.build());
        }
        Goals.Builder b = Goals.Step.of(FIRST, Goal.WEAPONS, "Forge your first weapon")
                .why(tier == 0 ? "At your War Anvil, by hand: knap a bone or flint edge and bind its grip. How well you do decides its quality."
                        : "At your War Anvil: heat it in the Forge, draw it out on the beat, quench it and fit its hilt. How well you do decides its quality and Temper.")
                .need(Need.of(tier == 0 ? "A War Anvil" : "A War Anvil by the Forge", anvil != null && (tier == 0 || forgeNear(p, mem, anvil))))
                .path(d ? Goals.PATH_FIRST_WEAPON : Goals.NO_PATH).priority(43).covers("arms:forge/");
        choices(p, b, tier);
        if (anvil != null) b.at(anvil);
        out.add(b.build());
        return out;
    }

    /** The optional steps for a weapon you carry, at your anvil with the Forge in reach (SPEC §46.8). */
    private static void held(ServerPlayer p, SmithMemory mem, List<Goals.Step> out) {
        BlockPos anvil = anvil(p, mem);
        if (anvil == null || !forgeNear(p, mem, anvil)) return;
        int reached = reached(p), bestTier = -1, bestTemper = 0;
        ItemStack best = ItemStack.EMPTY, found = ItemStack.EMPTY;
        for (ItemStack s : p.getInventory().getNonEquipmentItems()) {
            ArmsData.Weapon d = s.isEmpty() ? null : ArmsItems.of(s.getItem());
            if (d == null) continue;
            boolean lord = LordForged.byWeapon(d.id()) != null;
            int tier = ArmsArmoury.tierIndex(d.tier()) + (lord ? ArmsRaise.raised(s) : 0), temper = Upgrades.temperOf(s);
            // the best you carry: the highest tier, then the lowest Temper
            if (tier > bestTier || tier == bestTier && temper < bestTemper) {
                best = s;
                bestTier = tier;
                bestTemper = temper;
            }
            if (found.isEmpty() && tier >= 0 && tier < reached && (lord || !Marks.of(s).marks().isEmpty())) found = s;
        }
        if (!best.isEmpty() && bestTemper < mem.forgeCap) {
            // its next level, as the anvil reckons it (Oren's Mark starts a fresh one higher)
            int next = bestTemper == 0 ? Math.min(mem.forgeCap, 1 + (int) Progression.perk(p, "awaken:temper_start")) : bestTemper + 1;
            String name = best.getHoverName().getString();
            Goals.Builder b = Goals.Step.of(TEMPER, Goal.WEAPONS, "Temper your " + name)
                    .why((bestTemper == 0 ? "It has no Temper yet" : "It's at Temper " + Upgrades.roman(bestTemper)) + ", and the Forge by your War Anvil can take it to "
                            + Upgrades.roman(mem.forgeCap) + ". Hold it and use the anvil: one heat, a strike for each level.")
                    .optional().priority(30).at(anvil);
            for (var part : AnvilHeld.price(next, Progression.rank(p))) b.need(Need.item(p, part.item(), part.count()));
            out.add(b.build());
        }
        ArmsRaise.Plan plan = found.isEmpty() ? null : ArmsRaise.plan(p, found);
        if (plan != null && plan.reached()) {
            Goals.Builder b = Goals.Step.of(REFORGE, Goal.WEAPONS, "Reforge your " + found.getHoverName().getString())
                    .why("You've reached " + WarAnvil.tierName(plan.next()) + " weapons. At your War Anvil it's reforged as one, "
                            + (plan.lordForged() ? "keeping its own form and Art and gaining a point of damage." : "keeping its Marks, its name and its Temper."))
                    .optional().priority(29).at(anvil);
            for (NeedRow r : plan.needs()) b.need(need(r));
            out.add(b.build());
        }
    }

    /** The highest tier whose level the player has reached (0 E to 5 S). */
    static int reached(ServerPlayer p) {
        int level = Progression.profile(p).level(), best = 0;
        for (int i = 0; i < ArmsArmoury.LEVEL.length; i++) if (level >= ArmsArmoury.LEVEL[i]) best = i;
        return best;
    }

    /** Each style the player knows at this tier, with what it still takes (a carried blade standing in). */
    private static void choices(ServerPlayer p, Goals.Builder b, int tier) {
        for (Archetypes.Loaded st : Archetypes.all()) {
            if (!ArmsApi.knows(p, st.id())) continue;
            String id = ArmsArmoury.PREFIX[tier] + "_" + st.id();
            if (AnvilCosts.of(id) == null) continue;
            List<Need> needs = new ArrayList<>();
            for (NeedRow r : AnvilCosts.needs(p, id)) needs.add(need(r));
            b.choice(new Goals.Choice(id, new ItemStack(WarAnvil.item(id)).getHoverName().getString(),
                    st.def().name() + (tier == 0 ? " · by hand at the anvil" : " · in the Forge's heat"), List.of(Awaken.MODID + ":" + id), needs));
        }
    }

    /** Your anvil, if it still stands (one in a chunk that isn't loaded is taken as standing). */
    private static @org.jspecify.annotations.Nullable BlockPos anvil(ServerPlayer p, SmithMemory mem) {
        if (mem.anvil == null) return null;
        var level = p.level().getServer().getLevel(mem.anvil.dimension());
        if (level == null) return null;
        BlockPos pos = mem.anvil.pos();
        if (level.isLoaded(pos) && !level.getBlockState(pos).is(AnvilContent.ANVIL.get())) {
            mem.anvil = null;
            return null;
        }
        return pos;
    }

    /** Whether the Forge is in reach of your anvil: read at most every two seconds (a small cube of blocks). */
    private static boolean forgeNear(ServerPlayer p, SmithMemory mem, BlockPos anvil) {
        long now = p.level().getGameTime();
        if (now - mem.forgeAt < 40) return mem.forgeNear;
        mem.forgeAt = now;
        var level = p.level().getServer().getLevel(mem.anvil.dimension());
        mem.forgePos = level != null && level.isLoaded(anvil) ? WarAnvil.forge(level, anvil) : null;
        mem.forgeNear = mem.forgePos != null;
        mem.forgeCap = mem.forgePos == null ? 1 : HearthHooks.temperCap(p, mem.forgePos);
        return mem.forgeNear;
    }

    private static Need need(NeedRow r) {
        if (r.kind().equals("tag")) return new Need("#" + r.id(), tagLabel(r.id()), r.have(), r.need());
        var item = BuiltInRegistries.ITEM.getValue(Identifier.parse(r.id()));
        return new Need(r.id(), item.getName(new ItemStack(item)).getString(), r.have(), r.need());
    }

    /** What a cost's tag is called on the page ("Beast core", "Metal", "Stone"). */
    static String tagLabel(String tag) {
        String path = Identifier.parse(tag).getPath();
        if (path.contains("core")) return "Beast core";
        if (path.contains("metal")) return "Metal";
        if (path.contains("bind")) return "Binding";
        if (path.equals("logs")) return "Logs";
        if (path.equals("stone_crafting_materials")) return "Cobblestone";
        return Character.toUpperCase(path.charAt(0)) + path.substring(1).replace('_', ' ');
    }
}
