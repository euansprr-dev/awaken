package dev.awaken.armour;

import dev.awaken.core.test.AwakenTests;
import dev.awaken.gear.GearItems;
import dev.awaken.gear.HunterTier;
import dev.awaken.gear.Piece;
import dev.awaken.progression.Rank;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Armour GameTests (SPEC §44): the roster's shape, the Hunter items' looks, the Hunter sets' art shipped. */
final class ArmourTests {
    private ArmourTests() {}

    static void register() {
        AwakenTests.register("armour_roster_shape", 20, ArmourTests::roster);
        AwakenTests.register("armour_hunter_looks", 20, ArmourTests::looks);
        AwakenTests.register("armour_hunter_art_shipped", 20, ArmourTests::art);
    }

    private static final List<Rank> RANKS = List.of(Rank.E, Rank.D, Rank.C, Rank.B, Rank.A, Rank.S);

    /** Four sets at E (one per archetype), five at each rank D-S (one per archetype and a Lord-forged one); ids unique. */
    private static void roster(GameTestHelper h) {
        h.assertTrue(ArmourSet.values().length == 29, "29 sets, got " + ArmourSet.values().length);
        Set<String> ids = new HashSet<>();
        for (ArmourSet s : ArmourSet.values()) {
            h.assertTrue(ids.add(s.id), "set id unique: " + s.id);
            h.assertTrue(ArmourSet.byId(s.id) == s, "byId finds " + s.id);
            h.assertTrue(RANKS.contains(s.rank), s.id + " sits on a rank from E to S");
        }
        for (Rank r : RANKS) {
            EnumSet<Archetype> seen = EnumSet.noneOf(Archetype.class);
            int n = 0;
            for (ArmourSet s : ArmourSet.values()) {
                if (s.rank != r) continue;
                n++;
                h.assertTrue(seen.add(s.archetype), r.label + " has one " + s.archetype.label + " set");
            }
            EnumSet<Archetype> want = r == Rank.E ? EnumSet.complementOf(EnumSet.of(Archetype.LORD)) : EnumSet.allOf(Archetype.class);
            h.assertTrue(seen.equals(want) && n == want.size(), r.label + ": the archetypes " + want + ", got " + seen);
        }
        for (HunterTier tier : HunterTier.values()) {
            ArmourSet s = ArmourSet.of(tier);
            h.assertTrue(s.rank == tier.rank && s.id.equals(tier.id) && s.setName.equals(tier.setName),
                    tier + " is the set " + s.id + " at its own rank and name");
        }
        h.succeed();
    }

    /** Every Hunter armour piece is drawn as its tier's set and its own piece; blades, foci and vanilla armour are not. */
    private static void looks(GameTestHelper h) {
        for (HunterTier tier : HunterTier.values()) {
            for (Piece piece : Piece.values()) {
                ArmourLooks.Look look = ArmourLooks.of(GearItems.stack(tier, piece));
                if (piece.armorType == null) {
                    h.assertTrue(look == null, tier.itemId(piece) + " is not armour");
                    continue;
                }
                h.assertTrue(look != null && look.set() == ArmourSet.of(tier) && look.piece() == piece.armorType,
                        tier.itemId(piece) + " is drawn as " + ArmourSet.of(tier).id + " " + piece.armorType.getName());
            }
        }
        h.assertTrue(ArmourLooks.of(new ItemStack(Items.IRON_CHESTPLATE)) == null, "vanilla armour stays vanilla's");
        h.assertTrue(ArmourLooks.of(ItemStack.EMPTY) == null, "nothing worn, nothing drawn");
        h.succeed();
    }

    /** Each Hunter set ships a texture and a mesh for every piece on its main bone, so none falls back to the shell. */
    private static void art(GameTestHelper h) {
        String[][] main = {{"helmet", "head"}, {"chestplate", "body"}, {"leggings", "right_leg"}, {"boots", "right_leg"}};
        for (HunterTier tier : HunterTier.values()) {
            ArmourSet s = ArmourSet.of(tier);
            h.assertTrue(exists("/assets/awaken/textures/entity/armour/" + s.id + ".png"), s.id + " has its texture");
            for (String[] pb : main) {
                String file = "/assets/awaken/armour/" + s.id + "/" + pb[0] + "_" + pb[1] + ".mesh";
                h.assertTrue(exists(file), s.id + " ships " + file);
            }
            for (String[] pb : new String[][]{{"chestplate", "right_arm"}, {"chestplate", "left_arm"}, {"boots", "left_leg"}}) {
                h.assertTrue(exists("/assets/awaken/armour/" + s.id + "/" + pb[0] + "_" + pb[1] + ".mesh"), s.id + " dresses " + pb[1]);
            }
        }
        h.succeed();
    }

    private static boolean exists(String path) {
        try (InputStream in = ArmourTests.class.getResourceAsStream(path)) {
            return in != null && in.read() >= 0;
        } catch (IOException e) {
            return false;
        }
    }
}
