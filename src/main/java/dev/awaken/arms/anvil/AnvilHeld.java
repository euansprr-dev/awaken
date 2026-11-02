package dev.awaken.arms.anvil;

import dev.awaken.arms.ArmsArmoury;
import dev.awaken.arms.ArmsData;
import dev.awaken.arms.ArmsForge;
import dev.awaken.arms.ArmsItems;
import dev.awaken.arms.ArmsRaise;
import dev.awaken.arms.ArmsTrack;
import dev.awaken.arms.LordForged;
import dev.awaken.arms.Marks;
import dev.awaken.arms.net.ArmsPayloads.NeedRow;
import dev.awaken.core.AwakenNet;
import dev.awaken.core.Forces;
import dev.awaken.core.api.ForgeHooks;
import dev.awaken.core.api.HearthHooks;
import dev.awaken.hearth.Costs;
import dev.awaken.hearth.Upgrades;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A weapon held to the War Anvil (SPEC §46.8): the anvil's window for it (temper it, reforge it, attune it, each with what
 * it costs and one plain line for what's in the way), the start (the price and the weapon itself into the work, given back
 * if the anvil breaks), and what it leaves as. The song between is the anvil's own ({@link WarAnvil}): the heat, then a
 * strike a level (temper), the next tier's metal folded in (reforge) or three taps setting the stone (attune), the quench
 * for the first two.
 */
final class AnvilHeld {
    private AnvilHeld() {}

    static final String NO_FORGE = "Metal is heated in a Forge. Set your Hearth's Forge within six blocks of this anvil.";

    /** Use with one of Arms' weapons in hand and the anvil free: its window. False when it isn't such a weapon. */
    static boolean open(ServerPlayer p, ServerLevel level, WarAnvilBlockEntity be) {
        ItemStack weapon = p.getMainHandItem();
        if (ArmsItems.of(weapon.getItem()) == null) return false;
        AwakenNet.send(p, new AnvilPayloads.Held(be.getBlockPos(), options(p, level, be.getBlockPos(), weapon)));
        return true;
    }

    static List<AnvilPayloads.Option> options(ServerPlayer p, ServerLevel level, BlockPos anvil, ItemStack weapon) {
        BlockPos fire = WarAnvil.forge(level, anvil);
        return List.of(temperRow(p, weapon, fire), reforgeRow(p, weapon, fire, WarAnvil.build(level.getBlockState(anvil))), attuneRow(p, weapon, fire));
    }

    // ---- temper ------------------------------------------------------------------------------------------------------

    /** One heat as far as it goes: from its Temper to the target, each level's price summed; ready when it can begin. */
    record TemperPlan(int from, int target, int cap, List<Costs.Part> price, boolean ready, String line) {}

    static TemperPlan temperPlan(ServerPlayer p, ItemStack weapon, @Nullable BlockPos fire) {
        return temperPlan(p, weapon, fire, fire == null ? 1 : HearthHooks.temperCap(p, fire));
    }

    /** The same at a given limit (a Forge's own comes from its Hearth; the tests set it). */
    static TemperPlan temperPlan(ServerPlayer p, ItemStack weapon, @Nullable BlockPos fire, int cap) {
        int cur = Upgrades.temperOf(weapon);
        if (cur >= cap) {
            String why = cur >= Upgrades.MAX_TEMPER ? "It can't take any more."
                    : cap > 1 ? "It's at your Forge's limit. Upgrade the Forge to go further."
                    : "It's at your Forge's limit. Put an anvil and an armour stand in the Forge's room and it'll go further.";
            return new TemperPlan(cur, cur, cap, List.of(), false, why);
        }
        Rank rank = Progression.rank(p);
        // Oren's Mark: a weapon with no Temper starts at its level for that level's price alone, as at the Forge
        int first = cur == 0 ? Math.min(cap, 1 + (int) Progression.perk(p, "awaken:temper_start")) : cur + 1;
        List<Costs.Part> sum = new ArrayList<>(), ok = null;
        int target = first;
        for (int l = first; l <= cap; l++) {
            add(sum, price(l, rank));
            if (!Costs.canAfford(p, sum)) break;
            ok = List.copyOf(sum);
            target = l;
        }
        if (fire == null) return new TemperPlan(cur, target, cap, ok == null ? price(first, rank) : ok, false, NO_FORGE);
        if (ok == null) return new TemperPlan(cur, first, cap, price(first, rank), false, "Not enough for Temper " + Upgrades.roman(first) + " yet.");
        String line = target < cap ? "Your Forge can take it to " + Upgrades.roman(cap) + ". You have enough for " + Upgrades.roman(target) + "."
                : "One heat, a strike for each level.";
        return new TemperPlan(cur, target, cap, ok, true, line);
    }

    static List<Costs.Part> price(int level, Rank rank) {
        Upgrades.Def def = Upgrades.temper(level, false);
        return Costs.price(def.base(), rank, def.tier());
    }

    private static void add(List<Costs.Part> sum, List<Costs.Part> parts) {
        for (Costs.Part part : parts) {
            int i = 0;
            while (i < sum.size() && sum.get(i).item() != part.item()) i++;
            if (i < sum.size()) sum.set(i, new Costs.Part(part.item(), sum.get(i).count() + part.count()));
            else sum.add(part);
        }
    }

    private static AnvilPayloads.Option temperRow(ServerPlayer p, ItemStack weapon, @Nullable BlockPos fire) {
        TemperPlan t = temperPlan(p, weapon, fire);
        List<AnvilPayloads.Cost> costs = new ArrayList<>();
        for (Costs.Part part : t.price()) costs.add(new AnvilPayloads.Cost(name(part.item()), Costs.countOf(p, part.item()), part.count()));
        String button = t.target() > t.from() ? "Temper it to " + Upgrades.roman(t.target()) : "Temper it";
        return new AnvilPayloads.Option(AnvilWork.TEMPER, button, t.ready(), t.line(), costs);
    }

    // ---- reforge -----------------------------------------------------------------------------------------------------

    private static AnvilPayloads.Option reforgeRow(ServerPlayer p, ItemStack weapon, @Nullable BlockPos fire, int build) {
        ArmsRaise.Plan plan = ArmsRaise.plan(p, weapon);
        if (plan == null) return new AnvilPayloads.Option(AnvilWork.REFORGE, "Reforge it", false, "There's no tier above it.", List.of());
        List<AnvilPayloads.Cost> costs = new ArrayList<>();
        for (NeedRow n : plan.needs()) costs.add(new AnvilPayloads.Cost(ArmsTrack.label(n), n.have(), n.need()));
        boolean afford = p.isCreative() || WarAnvil.afford(p, plan.needs());
        // reforged to a tier, it's that tier's work: the anvil must be built for it (§46.6)
        String unbuilt = WarAnvil.unbuilt(plan.next(), build);
        String line = !plan.reached() ? WarAnvil.tierName(plan.next()) + " weapons open at level " + ArmsArmoury.LEVEL[plan.next()] + "."
                : fire == null ? NO_FORGE
                : unbuilt != null ? unbuilt
                : !afford ? "You don't have everything it takes yet."
                : plan.lordForged() ? "It keeps its own form and Art, and gains a point of damage."
                : "It keeps its Marks, its name, its Temper and everything written on it.";
        return new AnvilPayloads.Option(AnvilWork.REFORGE, "Reforge it to " + ArmsArmoury.TIERS[plan.next()] + "-rank",
                plan.reached() && fire != null && unbuilt == null && afford, line, costs);
    }

    // ---- attune ------------------------------------------------------------------------------------------------------

    private static AnvilPayloads.Option attuneRow(ServerPlayer p, ItemStack weapon, @Nullable BlockPos fire) {
        Forces.Force f = ArmsForge.catalyst(p.getOffhandItem());
        if (f == null) return new AnvilPayloads.Option(AnvilWork.ATTUNE, "Attune it", false,
                "Hold a catalyst in your off hand: a Sunshard for Heat, Frostspar for Frost, a Fault Seam for Gravity, a Copper Ingot for "
                        + "Charge or a Deepglass Shard for Spirit.", List.of());
        List<AnvilPayloads.Cost> costs = new ArrayList<>();
        List<ArmsForge.Cost> price = ArmsForge.attunePrice(f);
        for (ArmsForge.Cost c : price) costs.add(new AnvilPayloads.Cost(c.label().getString(), ArmsForge.count(p, c.item()), c.n()));
        String name = ArmsForge.forceName(f);
        Forces.Force now = Marks.of(weapon).attunement().orElse(null);
        boolean afford = ArmsForge.affords(p, price);
        String line = now == f ? "It's already attuned to " + name + "."
                : fire == null ? NO_FORGE
                : !afford ? "Not enough to attune it to " + name + " yet."
                : now != null ? "Its " + ArmsForge.forceName(now) + " gives way to " + name + "."
                : "The stone goes into the guard while the steel is hot.";
        return new AnvilPayloads.Option(AnvilWork.ATTUNE, "Attune it to " + name, now != f && fire != null && afford, line, costs);
    }

    // ---- the start ---------------------------------------------------------------------------------------------------

    /** The window's button: the price and the weapon go into the work and the scene opens. False if it can't begin (the window is sent again). */
    static boolean begin(ServerPlayer p, ServerLevel level, WarAnvilBlockEntity be, int kind) {
        ItemStack weapon = p.getMainHandItem();
        ArmsData.Weapon data = ArmsItems.of(weapon.getItem());
        if (data == null || be.work() != null || !be.slot().isEmpty() || p.distanceToSqr(Vec3.atCenterOf(be.getBlockPos())) > 10 * 10) return false;
        BlockPos fire = WarAnvil.forge(level, be.getBlockPos());
        if (fire == null) return false;
        boolean free = p.isCreative();
        String drawn = data.id();
        int target;
        Runnable pay;
        List<ItemStack> taken = new ArrayList<>();
        switch (kind) {
            case AnvilWork.TEMPER -> {
                TemperPlan t = temperPlan(p, weapon, fire);
                if (!t.ready()) return false;
                target = t.target();
                pay = () -> {
                    for (Costs.Part part : t.price()) takeItem(p, part.item(), part.count(), taken);
                };
            }
            case AnvilWork.REFORGE -> {
                ArmsRaise.Plan plan = ArmsRaise.plan(p, weapon);
                if (plan == null || !plan.reached() || WarAnvil.unbuilt(plan.next(), WarAnvil.build(be.getBlockState())) != null
                        || !free && !WarAnvil.afford(p, plan.needs())) return false;
                target = plan.next();
                // a forged weapon is drawn as the next tier's of its style; a Lord-forged keeps its own body
                if (!plan.lordForged()) drawn = ArmsArmoury.PREFIX[plan.next()] + "_" + data.style();
                pay = () -> taken.addAll(WarAnvil.take(p, plan.needs()));
            }
            case AnvilWork.ATTUNE -> {
                Forces.Force f = ArmsForge.catalyst(p.getOffhandItem());
                if (f == null || Marks.of(weapon).attunement().orElse(null) == f) return false;
                List<ArmsForge.Cost> price = ArmsForge.attunePrice(f);
                if (!ArmsForge.affords(p, price)) return false;
                target = f.ordinal();
                pay = () -> {
                    for (ArmsForge.Cost c : price) takeItem(p, c.item(), c.n(), taken);
                };
            }
            default -> {
                return false;
            }
        }
        // the weapon leaves the hand first, so nothing it is can be taken as a part
        ItemStack piece = weapon.copy();
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        if (!free) pay.run();
        AnvilWork w = AnvilWork.worked(drawn, level.getGameTime(), taken, piece, p.getUUID(), fire, kind, target);
        WarAnvil.arrive(p.getUUID(), be.getBlockPos());
        SmithMemory.of(p).at(level.dimension(), be.getBlockPos());
        be.setWork(w);
        level.playSound(null, fire, AnvilContent.BELLOWS.get(), SoundSource.BLOCKS, 0.9F, 1F);
        AwakenNet.send(p, new AnvilPayloads.Open(be.getBlockPos()));
        return true;
    }

    /** Takes so many of an item, the off hand first (a catalyst is held there), into {@code into}. */
    private static void takeItem(ServerPlayer p, Item item, int n, List<ItemStack> into) {
        ItemStack off = p.getOffhandItem();
        if (off.is(item) && n > 0) {
            into.add(off.split(Math.min(n, off.getCount())));
            n -= into.getLast().getCount();
        }
        for (ItemStack s : p.getInventory().getNonEquipmentItems()) {
            if (n <= 0) break;
            if (s.isEmpty() || !s.is(item)) continue;
            into.add(s.split(Math.min(n, s.getCount())));
            n -= into.getLast().getCount();
        }
    }

    // ---- what it leaves as -------------------------------------------------------------------------------------------

    /** The work is done: the weapon tempered, attuned or reforged, standing in the slot to be drawn, and the card sent. */
    static void finish(ServerLevel level, WarAnvilBlockEntity be, AnvilWork w, @Nullable ServerPlayer smith) {
        ItemStack out = w.piece.copy();
        WeaponForged story = out.get(AnvilContent.FORGED.get());
        int quality = story == null ? 0 : story.quality(), flaw = 0, flags;
        switch (w.kind) {
            case AnvilWork.TEMPER -> {
                Upgrades.applyTemper(out, w.target);
                flags = WarAnvil.TEMPERED;
                if (smith != null) {
                    Progression.count(smith, "awaken:gear_tempered");
                    dev.awaken.living.api.Quests.advance(smith, Identifier.parse("awaken:use/temper"));
                }
            }
            case AnvilWork.ATTUNE -> {
                Forces.Force f = Forces.Force.values()[Math.floorMod(w.target, Forces.Force.values().length)];
                ArmsForge.attune(out, f);
                flags = WarAnvil.ATTUNED;
                if (smith != null) ArmsForge.attuned(smith, out, f);
            }
            default -> {
                ArmsData.Weapon data = ArmsItems.of(out.getItem());
                boolean lord = data != null && LordForged.byWeapon(data.id()) != null;
                ItemStack lifted = data == null ? null
                        : ArmsRaise.lift(out, new ArmsRaise.Plan(data, w.target, lord, ArmsRaise.raised(out), List.of(), true));
                if (lifted != null) out = lifted;
                boolean twin = w.help >= AnvilSong.TWIN_STRIKES;
                float heat = Math.max(0F, w.heat);
                flaw = AnvilSong.burnt(heat) ? AnvilSong.BURNT_TIP : w.held >= 0F && AnvilSong.brittle(w.held) ? AnvilSong.BRITTLE : AnvilSong.NO_FLAW;
                AnvilSong.Quality q = AnvilSong.quality(AnvilSong.helped(AnvilSong.reforgeScore(heat, AnvilWork.mean(w.scores),
                        Math.max(0F, w.quench)), w.help), flaw != AnvilSong.NO_FLAW);
                quality = q.ordinal();
                // the steel keeps what its first forging's acts left in it (the reforging has no acts of its own)
                out.set(AnvilContent.FORGED.get(), new WeaponForged(WeaponForged.SCHEMA, quality, flaw,
                        smith != null ? smith.getGameProfile().name() : "", twin, false, true, story == null ? 0 : story.acts()));
                // its Temper: what it had, or the reforging's own if that's higher (held to the Forge's limit, no Mark: it isn't new)
                int temper = AnvilSong.temper(q, 0, smith != null ? HearthHooks.temperCap(smith, w.forge) : 1);
                if (temper > Upgrades.temperOf(out)) Upgrades.applyTemper(out, temper);
                boolean armoury = out.isDamageableItem() && HearthHooks.armoury(level, be.getBlockPos());
                if (armoury) out.set(DataComponents.MAX_DAMAGE, Math.round(out.getMaxDamage() * WarAnvil.ARMOURY_WEAR));
                flags = WarAnvil.REFORGED | (twin ? WarAnvil.TWIN : 0) | (armoury ? WarAnvil.ARMOURY : 0);
                if (smith != null) {
                    ArmsRaise.counted(smith);
                    ForgeHooks.forged(smith, out, quality, "war_anvil");
                }
            }
        }
        be.setWork(null);
        be.setSlot(out);
        level.playSound(null, be.getBlockPos(), AnvilContent.FLIP.get(), SoundSource.BLOCKS, 1F, 0.7F);
        if (smith != null) AwakenNet.send(smith, new AnvilPayloads.Done(be.getBlockPos(), quality, flaw, Upgrades.temperOf(out), flags));
    }

    private static String name(Item item) {
        return item.getName(new ItemStack(item)).getString();
    }
}
