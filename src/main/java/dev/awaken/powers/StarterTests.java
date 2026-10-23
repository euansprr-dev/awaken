package dev.awaken.powers;

import dev.awaken.core.test.AwakenTests;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Requirements;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** GameTests for the first-power choice and the power requirements Directives use. */
final class StarterTests {
    private StarterTests() {}

    static void register() {
        AwakenTests.register("powers_starter_cards_from_three_families", 20, h -> {
            for (long seed = 0; seed < 40; seed++) {
                List<String> cards = Starter.roll(RandomSource.create(seed));
                h.assertTrue(cards.size() == 3, "three cards, got " + cards);
                Set<PowerType.Family> families = new HashSet<>();
                for (String id : cards) {
                    PowerType t = PowerRegistry.get(id);
                    h.assertTrue(t != null && Starter.POOL.contains(id), "a real pool power: " + id);
                    h.assertTrue(!t.hidden && t.slot == PowerType.Slot.ACTIVE, "never hidden, always an active key: " + id);
                    h.assertTrue(Starter.BLURB.containsKey(id), "each card has its one line: " + id);
                    families.add(t.family);
                }
                h.assertTrue(families.size() == 3, "three different families: " + cards);
            }
            h.succeed();
        });

        AwakenTests.register("powers_starter_chosen_once_and_equipped", 40, h -> {
            ServerPlayer p = h.makeMockServerPlayerInLevel();
            h.assertFalse(Starter.pending(p), "nothing to choose before awakening");
            Progression.awaken(p);
            h.assertTrue(Starter.pending(p), "awakened: the choice is waiting");
            Starter.offer(p);
            List<String> cards = Starter.state(p).offer();
            h.assertTrue(cards.size() == 3, "three cards offered");
            Starter.offer(p);
            h.assertTrue(Starter.state(p).offer().equals(cards), "a re-offer shows the same cards");
            h.assertFalse(Starter.choose(p, "awaken:flight"), "a power that isn't on a card is refused");
            String pick = cards.get(1);
            h.assertTrue(Starter.choose(p, pick), "choosing a card works");
            Loadout l = Powers.loadout(p);
            h.assertTrue(l.owns(pick), "the power is learned");
            h.assertTrue(l.slotOf(pick) >= 0 && l.slotOf(pick) < 4, "and on a power key, slot " + l.slotOf(pick));
            h.assertFalse(Starter.pending(p), "nothing waiting any more");
            h.assertFalse(Starter.choose(p, cards.get(0)), "never a second card");
            h.assertFalse(Powers.loadout(p).owns(cards.get(0)), "the other cards stay closed");
            // saved and loaded (a relog): still chosen, so never offered again
            var tag = Starter.State.CODEC.encodeStart(NbtOps.INSTANCE, Starter.state(p)).getOrThrow();
            Starter.State back = Starter.State.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
            h.assertTrue(back.chosen().equals(pick) && back.offer().equals(cards), "the choice survives a save");
            p.setData(Starter.STATE.get(), back);
            Starter.migrate(p);
            h.assertFalse(Starter.pending(p), "after a relog it is still made");
            h.succeed();
        });

        AwakenTests.register("powers_starter_old_saves", 40, h -> {
            ServerPlayer veteran = h.makeMockServerPlayerInLevel();
            Progression.awaken(veteran);
            Powers.learn(veteran, PowerRegistry.get("awaken:nova").id, 0);
            Starter.migrate(veteran);
            h.assertFalse(Starter.pending(veteran), "a v1 player who already has an attack power isn't offered a free one");

            ServerPlayer stuck = h.makeMockServerPlayerInLevel();
            Progression.awaken(stuck);
            Powers.learn(stuck, PowerRegistry.get("awaken:dash").id, 0);
            Starter.migrate(stuck);
            h.assertTrue(Starter.pending(stuck), "a v1 player with no attack power (only Dash) gets the choice");
            h.succeed();
        });

        AwakenTests.register("powers_requirement_tokens", 40, h -> {
            ServerPlayer p = h.makeMockServerPlayerInLevel();
            Progression.awaken(p);
            for (String t : List.of("dash", "movement", "active", "forces", "family:velocity", "power:awaken:dash")) {
                h.assertFalse(Requirements.met(p, t), "no powers yet: " + t + " unmet");
            }
            Powers.learn(p, PowerRegistry.get("awaken:dash").id, 0);
            h.assertTrue(Requirements.met(p, "dash") && Requirements.met(p, "movement"), "Dash is a dash and a movement power");
            h.assertTrue(Requirements.met(p, "power:awaken:dash") && Requirements.met(p, "power:dash"), "by id, with or without namespace");
            h.assertTrue(Requirements.met(p, "family:velocity") && !Requirements.met(p, "family:energy"), "by family");
            h.assertFalse(Requirements.met(p, "forces"), "Dash carries no force");
            Powers.learn(p, PowerRegistry.get("awaken:beam").id, 0);
            h.assertTrue(Requirements.met(p, "forces") && Requirements.met(p, "family:energy"), "Beam burns: a force, Energy");
            h.assertTrue(Requirements.met(p, List.of("dash", "active", "family:energy")), "lists need all of them");
            h.succeed();
        });
    }
}
