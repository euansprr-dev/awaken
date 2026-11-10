package dev.awaken.powers;

import dev.awaken.Awaken;
import dev.awaken.core.api.ForgeHooks;
import dev.awaken.core.api.Goals;
import dev.awaken.core.api.Goals.Goal;
import dev.awaken.core.api.Goals.Need;
import dev.awaken.core.api.NoticeEvents;
import dev.awaken.progression.Profile;
import dev.awaken.progression.Progression;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * How the earned powers come to you, and their steps on the Tasks page (SPEC §50, "Earned powers"; Euan, 9 Nov 2026: "easy
 * unlocks"; 2 Nov: every progression system puts its steps on Tasks). Each is given for something you were doing anyway, the
 * moment you have done enough of it and hold the rank it asks:
 * <ul>
 * <li>Brand: forging a weapon on the War Anvil.</li>
 * <li>Hoarfrost Lances: ten kills in snow country.</li>
 * <li>Updraft: six seconds off the ground in one go, on your own legs and powers.</li>
 * <li>Backdraft: setting off fifteen Brands.</li>
 * <li>Thornwake: ten kills on green ground.</li>
 * </ul>
 * Nothing announces them beforehand. Once you have made a start a line on Tasks says what kind of thing is waking and where,
 * never the number left to the block; once you have one, a single step says how it is used, until you have used it.
 * Everything here is the server's, counted in the profile's own counters, and safe to run for many players at once.
 */
public final class Earned {
    private Earned() {}

    enum Gift {
        BRAND("brand", "awaken:weapon_forged", 1, "awaken:brands_set_off", Goal.WEAPONS,
                "", "", "",
                "The anvil's heat stayed in your hand.",
                "Set off a Brand with your weapon", "Press Brand on an enemy, then hit it. The mark bursts over everything standing close.",
                "Brand an enemy and strike it"),
        LANCES("hoarfrost_lances", "awaken:earned/cold_kills", 10, "awaken:lances_loosed", Goal.WEAPONS,
                "Fight in snow country", "The cold has started to follow your swings. Keep fighting where it snows and it will stay with you.", "Kills where it snows",
                "The cold you fought in has stayed with you.",
                "Loose a Hoarfrost Lance", "Cast Hoarfrost Lances, then hit something with your weapon. Each blow sends a lance into what you hit.",
                "Land a blow while the lances hang"),
        UPDRAFT("updraft", "awaken:earned/air_best", 120, "awaken:updraft_rides", Goal.OUT,
                "Stay off the ground", "The air has started to hold you up. Stay off the ground for six seconds in one go, without wings, and it will lift you when you ask.",
                "Seconds in the air, in one go",
                "The air held you long enough to learn your weight.",
                "Ride your Updraft", "Cast Updraft and step into the column. It carries you fourteen blocks up, and anyone with you.",
                "Rise in your own column"),
        BACKDRAFT("backdraft", "awaken:brands_set_off", 15, "awaken:fires_breathed", Goal.WEAPONS,
                "Keep setting off Brands", "Every Brand you set off leaves a little of its fire in you. Enough of them and you'll be able to take fire in on purpose.",
                "Brands set off",
                "You have set off enough fire to take it back in.",
                "Breathe in a fire", "Backdraft is small on its own. Set something burning first, then cast it: every fire near you goes out and comes back as one breath.",
                "Cast Backdraft near something burning"),
        THORNWAKE("thornwake", "awaken:earned/green_kills", 10, "awaken:roots_woken", Goal.OUT,
                "Fight on green ground", "The ground has started to stir where you fight. Keep fighting among grass and leaves and the roots will come when you call.",
                "Kills on green ground",
                "The ground you fought on has learned you.",
                "Wake the roots", "Cast Thornwake with enemies near you. The more grass, flowers and leaves around, the more roots come up.",
                "Cast Thornwake somewhere green");

        final String power, counter, used, title, why, label, earnedLine, firstTitle, firstWhy, firstLabel;
        final int need;
        final Goal goal;

        Gift(String power, String counter, int need, String used, Goal goal, String title, String why, String label, String earnedLine,
             String firstTitle, String firstWhy, String firstLabel) {
            this.power = power;
            this.counter = counter;
            this.need = need;
            this.used = used;
            this.goal = goal;
            this.title = title;
            this.why = why;
            this.label = label;
            this.earnedLine = earnedLine;
            this.firstTitle = firstTitle;
            this.firstWhy = firstWhy;
            this.firstLabel = firstLabel;
        }

        String id() {
            return "awaken:" + power;
        }
    }

    private static final Gift[] GIFTS = Gift.values();

    static void init() {
        Goals.register(Earned::steps);
        // the anvil gives Brand the moment the weapon is done
        ForgeHooks.onForged((player, stack, quality, station) -> {
            if (station.equals("war_anvil")) check(player);
        });
    }

    // ---- counting ---------------------------------------------------------------------------------------------------

    /** A player killed something (PowerEvents): where it happened decides what the land gives. */
    static void killed(ServerPlayer p, LivingEntity dead) {
        if (!Combat.isFoeOrTarget(p, dead) && !(dead instanceof net.minecraft.world.entity.monster.Enemy)) return;
        BlockPos at = dead.blockPosition();
        var level = p.level();
        boolean changed = false;
        if (!Powers.owns(p, Gift.LANCES.id()) && level.getBiome(at).value().coldEnoughToSnow(at, level.getSeaLevel())) {
            Progression.count(p, Gift.LANCES.counter);
            changed = true;
        }
        if (!Powers.owns(p, Gift.THORNWAKE.id()) && (green(level.getBlockState(at)) || green(level.getBlockState(at.below())))) {
            Progression.count(p, Gift.THORNWAKE.counter);
            changed = true;
        }
        if (changed) check(p);
    }

    private static boolean green(BlockState s) {
        return s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.MOSS_BLOCK) || s.is(Blocks.PODZOL) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.LEAVES)
                || s.is(BlockTags.CROPS) || s.is(BlockTags.SAPLINGS) || s.is(BlockTags.REPLACEABLE_BY_TREES) && !s.liquid();
    }

    /**
     * Once a tick for each player (Casting): how long they have been off the ground on their own. Wings, a mount, water, a ladder,
     * creative flight and anything that carries you (levitation, Hover, Flight, an Updraft) don't count.
     */
    static void tick(ServerPlayer p, PowerState st) {
        boolean carried = p.onGround() || p.isInWater() || p.isPassenger() || p.onClimbable() || p.isFallFlying() || p.getAbilities().flying
                || p.isSpectator() || p.hasEffect(MobEffects.LEVITATION) || p.hasEffect(MobEffects.SLOW_FALLING);
        if (carried) {
            if (st.airTicks >= 40) {
                // a long time up, just ended: keep the best, and see whether the air has learned you
                if (Progression.record(p, Gift.UPDRAFT.counter, st.airTicks)) check(p);
            }
            st.airTicks = 0;
        } else if (++st.airTicks == Gift.UPDRAFT.need && !Powers.owns(p, Gift.UPDRAFT.id())) {
            // given in the air, while it is happening
            Progression.record(p, Gift.UPDRAFT.counter, st.airTicks);
            check(p);
        }
    }

    /** Gives every earned power this player has now done enough for (and holds the rank for). Cheap; called when a count moves. */
    static void check(ServerPlayer p) {
        Profile profile = Progression.profile(p);
        if (!profile.awakened()) return;
        for (Gift g : GIFTS) {
            if (Powers.owns(p, g.id()) || profile.counter(g.counter) < g.need) continue;
            PowerType type = PowerRegistry.get(g.id());
            if (type == null || Progression.rank(p).ordinal() < type.unlock.ordinal()) continue;
            if (!Powers.learn(p, type.id, 0)) continue;
            NoticeEvents.remember(p, new NoticeEvents.Event("earned/" + g.power, "Earned power", type.name, g.earnedLine,
                    List.of(type.description, g.firstWhy), NoticeEvents.Kind.PERMANENT, type.family.colour));
            Awaken.LOG.debug("{} earned {}", p.getScoreboardName(), g.id());
        }
    }

    // ---- the Tasks page ---------------------------------------------------------------------------------------------

    static List<Goals.Step> steps(ServerPlayer p) {
        Profile profile = Progression.profile(p);
        if (!profile.awakened()) return List.of();
        List<Goals.Step> out = null;
        for (Gift g : GIFTS) {
            boolean owned = Powers.owns(p, g.id());
            Goals.Step step = null;
            if (owned) {
                // how it's used, until it has been used once
                if (profile.counter(g.used) == 0) {
                    step = Goals.Step.of("powers/earned/" + g.power + "/first", g.goal, g.firstTitle).thread("powers.earned", 20).why(g.firstWhy)
                            .need(Need.of(g.firstLabel, false)).optional().priority(60).build();
                }
            } else if (!g.title.isEmpty()) {
                long have = profile.counter(g.counter);
                PowerType type = PowerRegistry.get(g.id());
                if (have > 0 && type != null && Progression.rank(p).ordinal() >= type.unlock.ordinal()) {
                    // Updraft counts ticks; the page shows seconds
                    int shown = g == Gift.UPDRAFT ? (int) (have / 20) : (int) have, of = g == Gift.UPDRAFT ? g.need / 20 : g.need;
                    step = Goals.Step.of("powers/earned/" + g.power, g.goal, g.title).thread("powers.earned", 10).lead().why(g.why)
                            .need(new Need("", g.label, Math.min(shown, of), of)).optional().priority(45).build();
                }
            }
            if (step == null) continue;
            if (out == null) out = new ArrayList<>(2);
            out.add(step);
        }
        return out == null ? List.of() : out;
    }
}
