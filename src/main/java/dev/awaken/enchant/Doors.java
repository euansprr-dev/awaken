package dev.awaken.enchant;

import dev.awaken.core.api.CreatureTag;
import dev.awaken.core.api.DescentHooks;
import dev.awaken.delve.api.Bands;
import dev.awaken.delve.api.Lodes;
import dev.awaken.living.api.Hunts;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/**
 * Stage 3's doors to pages (SPEC §45.14), each paying its page the first time and a spare one time in five after, like
 * a gate's Lord: the hunts (a herd's or pack's leader brought down: Harvest; a named beast: Bane of Beasts) and the
 * Deep (an elite creature killed below the rock's zero or in a Descent: Bane of the Deep; a lode's heart broken: Fortune,
 * and the first broken in the Deep band: Silk Touch).
 */
final class Doors {
    private Doors() {}

    static final Identifier HARVEST = Identifier.parse("awaken:harvest"), BANE_OF_BEASTS = Identifier.parse("awaken:bane_of_beasts"),
            BANE_OF_THE_DEEP = Identifier.parse("awaken:bane_of_the_deep"), FORTUNE = Identifier.parse("minecraft:fortune"),
            SILK_TOUCH = Identifier.parse("minecraft:silk_touch");

    static void onDeath(LivingDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (!(dead.level() instanceof ServerLevel level) || !(e.getSource().getEntity() instanceof ServerPlayer p)) return;
        CreatureTag tag = CreatureTag.of(dead);
        if (tag.kind() == CreatureTag.Kind.BEAST) {
            String who = tag.title().isEmpty() ? "the beast" : tag.title();
            give(p, level, BANE_OF_BEASTS, "hunt:beast", "From " + who + ", brought down",
                    "A page of Bane of Beasts was caught in " + who + "'s hide.", at(dead));
        } else if (Hunts.alpha(dead)) {
            give(p, level, HARVEST, "hunt:leader", "From a herd's leader, brought down",
                    "A page of Harvest was caught in the leader's hide.", at(dead));
        } else if (tag.elite() && dead instanceof Enemy && !EnchantEffects.spared(dead) && deep(p, dead)) {
            give(p, level, BANE_OF_THE_DEEP, "deep:bane", "From an elite of the Deep",
                    "A page of Bane of the Deep fell from it as it died.", at(dead));
        }
    }

    /**
     * Before the heart goes (NORMAL, ahead of the delve's LOW listener that marks it broken): only a lode's own heart,
     * still whole, opens these doors, so a heart dug up and set down again pays nothing.
     */
    static void onBreak(BreakBlockEvent e) {
        if (!(e.getPlayer() instanceof ServerPlayer p) || !(e.getLevel() instanceof ServerLevel level) || p.isCreative()) return;
        if (!heartBlock(e.getState())) return;
        var heart = Lodes.heart(level, e.getPos());
        if (heart.isEmpty() || heart.get().heartBroken()) return;
        boolean deepBand = Bands.of(e.getPos().getY()) == Bands.Band.DEEP;
        if (deepBand && (!Known.of(p).gatePaid("deep:silk") || level.getRandom().nextBoolean()))
            give(p, level, SILK_TOUCH, "deep:silk", "From a lode's heart in the Deep",
                    "A page of Silk Touch was pressed into the lode's heart.", net.minecraft.world.phys.Vec3.atCenterOf(e.getPos()));
        else give(p, level, FORTUNE, "deep:fortune", "From a lode's heart",
                "A page of Fortune was pressed into the lode's heart.", net.minecraft.world.phys.Vec3.atCenterOf(e.getPos()));
    }

    /** The delve's lode heart blocks, one for each ore ({@code awaken:<ore>_lode_heart}). */
    static boolean heartBlock(net.minecraft.world.level.block.state.BlockState s) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(s.getBlock());
        return id.getNamespace().equals("awaken") && id.getPath().endsWith("_lode_heart");
    }

    /** Where the Deep is: under the rock's zero in the overworld, or anywhere in a Descent. */
    static boolean deep(ServerPlayer p, LivingEntity at) {
        return at.level().dimension() == Level.OVERWORLD && Bands.of(at.getBlockY()) == Bands.Band.DEEP || DescentHooks.inDescent(p);
    }

    /** The page, the first time this door opens for you, and a spare one time in five after. */
    static boolean give(ServerPlayer p, ServerLevel level, Identifier id, String door, String from, String said) {
        return give(p, level, id, door, from, said, null);
    }

    private static net.minecraft.world.phys.Vec3 at(LivingEntity dead) {
        return dead.position().add(0, dead.getBbHeight() * 0.5, 0);
    }

    /** {@code where}: the place the page flies from to the one it goes to (null: it only appears in the pack). */
    static boolean give(ServerPlayer p, ServerLevel level, Identifier id, String door, String from, String said,
                        net.minecraft.world.phys.@org.jspecify.annotations.Nullable Vec3 where) {
        Known k = Known.of(p);
        boolean first = !k.gatePaid(door);
        if (!first && level.getRandom().nextInt(Pages.SPARE_ONE_IN) != 0) return false;
        ItemStack page = Pages.page(level, id, from);
        if (page == null) return false;
        if (first) {
            k.payGate(door);
            Known.save(p, k);
        }
        if (!p.getInventory().add(page)) p.drop(page, false, net.minecraft.util.Prediction.SERVER_ONLY);
        Inscriptions.Entry entry = Inscriptions.of(id);
        Pages.door(p, "page given", entry);
        if (entry != null) {
            EnchantFx.page(p, entry, where);
            Pages.givenCard(p, entry);
        }
        p.sendSystemMessage(Component.literal(said + (entry == null ? " Use it to learn it." : " " + Pages.then(entry)))
                .withColor(entry == null ? 0xC9B8FF : entry.colour()), false);
        return true;
    }
}
