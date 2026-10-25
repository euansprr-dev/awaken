package dev.awaken.living.craft;

import dev.awaken.core.test.AwakenTests;
import dev.awaken.progression.Progression;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

import java.util.ArrayList;
import java.util.List;

/** The crafts' GameTest (SPEC §21.9): the curve, budgets that level a grind off, perks opening, herbs, a perk, a meal. */
public final class CraftTests {
    private CraftTests() {}

    public static void register() {
        AwakenTests.register("living_crafts", 200, h -> {
            // the curve: 40·c^1.3 + 40
            h.assertTrue(Craft.toNext(1) == 80 && Craft.levelFor(79) == 1 && Craft.levelFor(80) == 2, "level 2 at 80 craft XP");
            double all = 0;
            for (int c = 1; c < Craft.MAX_LEVEL; c++) all += Craft.toNext(c);
            h.assertTrue(Craft.levelFor(all) == 50 && Craft.levelFor(all * 10) == 50, "fifty at most");
            // herbs: about 3% of plants ripe on a day, 6% with Root Sense, the same answer every time
            int ripe = 0, root = 0;
            for (int i = 0; i < 20000; i++) {
                BlockPos p = new BlockPos(i * 7 % 997, 64, i / 7);
                if (Herbs.ripe(p, 12, false)) ripe++;
                if (Herbs.ripe(p, 12, true)) root++;
            }
            h.assertTrue(ripe > 400 && ripe < 800, "about 3% ripe: " + ripe);
            h.assertTrue(root > 900 && root < 1500, "about 6% with Root Sense: " + root);
            h.assertTrue(Herbs.ripe(new BlockPos(5, 64, 9), 12, false) == Herbs.ripe(new BlockPos(5, 64, 9), 12, false), "a pure function");

            ServerPlayer p = h.makeMockServerPlayerInLevel();
            Progression.awaken(p);
            // a grind levels off: the hundredth herb pays far less craft XP than the first, and the Hunter level too
            double first = Crafts.gain(p, Craft.FORAGING, 12, 1), last = 0;
            for (int i = 0; i < 99; i++) last = Crafts.gain(p, Craft.FORAGING, 12, 1);
            h.assertTrue(first == 12, "the first herb pays in full: " + first);
            h.assertTrue(last < first * 0.25, "the hundredth much less: " + last);
            // a perk opens at its level
            Crafts.setLevel(p, Craft.FORAGING, 4);
            h.assertTrue(!Crafts.has(p, Craft.FORAGING, "herb_glint"), "no Herb Glint at 4");
            Crafts.setLevel(p, Craft.FORAGING, 5);
            h.assertTrue(Crafts.has(p, Craft.FORAGING, "herb_glint") && !Crafts.has(p, Craft.FORAGING, "wild_larder"), "Herb Glint at 5, not yet Wild Larder");
            // Clean Strike: ores sometimes one more
            Crafts.setLevel(p, Craft.MINING, 5);
            BlockPos at = h.absolutePos(new BlockPos(0, 1, 0));
            int extra = 0;
            for (int i = 0; i < 400; i++) {
                List<ItemEntity> drops = new ArrayList<>(List.of(new ItemEntity(h.getLevel(), at.getX(), at.getY(), at.getZ(), new ItemStack(Items.RAW_IRON))));
                CraftEvents.onBlockDrops(new BlockDropsEvent(h.getLevel(), at, Blocks.IRON_ORE.defaultBlockState(), null, drops, p, ItemStack.EMPTY));
                if (drops.size() > 1) extra++;
            }
            h.assertTrue(extra > 30 && extra < 100, "Clean Strike about 15%: " + extra + " / 400");
            // a meal's strength, half again as long for a cook with Hunter's Stew
            ItemStack stew = new ItemStack(CraftItems.HUNTERS_STEW.get());
            CraftItems.HUNTERS_STEW.get().finishUsingItem(stew, h.getLevel(), p);
            int plain = p.getEffect(MobEffects.STRENGTH).getDuration();
            p.removeAllEffects();
            Crafts.setLevel(p, Craft.COOKING, 20);
            CraftItems.HUNTERS_STEW.get().finishUsingItem(new ItemStack(CraftItems.HUNTERS_STEW.get()), h.getLevel(), p);
            int cooked = p.getEffect(MobEffects.STRENGTH).getDuration();
            h.assertTrue(cooked >= plain * 1.4, "Hunter's Stew lasts longer: " + plain + " → " + cooked);
            h.succeed();
        });
    }
}
