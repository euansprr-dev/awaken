package dev.awaken.core.test;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Vanilla's attribute refresh ({@code LivingEntity.refreshDirtyAttributes}: every tick, and when an effect changes) walks
 * the body's dirty attributes and answers each one ({@code onAttributeUpdated}): max health clamps the health, max
 * absorption the absorption, scale refreshes the size ({@code EntityEvent.Size} and its listeners). Lithium swaps that set
 * for one sized to what is in it, so anything an answer does to the set wrecks the walk and the server dies ticking the
 * body: an attribute made or changed is added to it (a {@code getAttribute} the body never asked for makes one), an effect
 * added or removed refreshes inside the refresh and empties it (the Thousandfoot's carcass in a gate, 12 Nov; SPEC §13
 * "Mobs"). These replay the answers outside the walk and say what moved.
 */
public final class AttributeWalk {
    private AttributeWalk() {}

    /** The answers the next refresh will give, for what is dirty now: the first thing one moved, or null. */
    public static @Nullable String replay(LivingEntity e) {
        List<Holder<Attribute>> answers = new ArrayList<>();
        for (AttributeInstance a : List.copyOf(e.getAttributes().getAttributesToUpdate())) answers.add(a.getAttribute());
        return walk(e, answers, false);
    }

    /** Every answer, whatever is dirty, with the health set either way (a check across kinds): what moved, or null. */
    public static @Nullable String everyAnswer(LivingEntity e) {
        return walk(e, List.of(Attributes.MAX_HEALTH, Attributes.MAX_ABSORPTION, Attributes.SCALE), true);
    }

    private static @Nullable String walk(LivingEntity e, List<Holder<Attribute>> answers, boolean all) {
        // AttributeInstance keeps Object's equals, so these compare by identity, as the walk does
        Set<AttributeInstance> was = new HashSet<>(e.getAttributes().getAttributesToUpdate());
        Map<Holder<MobEffect>, MobEffectInstance> effects = new HashMap<>(e.getActiveEffectsMap());
        for (Holder<Attribute> a : answers) {
            answer(e, a, all);
            String moved = moved(e, was, effects);
            if (moved != null) return "answering " + a.getRegisteredName() + " " + moved;
        }
        return null;
    }

    /** What {@code onAttributeUpdated} does for {@code a}; with {@code all} the setters run even when nothing is over. */
    private static void answer(LivingEntity e, Holder<Attribute> a, boolean all) {
        if (a.is(Attributes.MAX_HEALTH)) {
            float hp = e.getHealth();
            if (hp > e.getMaxHealth()) e.setHealth(e.getMaxHealth());
            else if (all && hp > 1) {
                e.setHealth(hp - 1);
                e.setHealth(hp);
            }
        } else if (a.is(Attributes.MAX_ABSORPTION)) {
            if (all || e.getAbsorptionAmount() > e.getMaxAbsorption()) e.setAbsorptionAmount(Math.min(e.getAbsorptionAmount(), e.getMaxAbsorption()));
        } else if (a.is(Attributes.SCALE)) {
            e.refreshDimensions();
        }
    }

    private static @Nullable String moved(LivingEntity e, Set<AttributeInstance> was, Map<Holder<MobEffect>, MobEffectInstance> effects) {
        Set<AttributeInstance> now = e.getAttributes().getAttributesToUpdate();
        List<String> added = new ArrayList<>(), gone = new ArrayList<>();
        for (AttributeInstance a : now) if (!was.contains(a)) added.add(a.getAttribute().getRegisteredName());
        for (AttributeInstance a : was) if (!now.contains(a)) gone.add(a.getAttribute().getRegisteredName());
        if (!added.isEmpty() || !gone.isEmpty()) {
            return "changed the attributes being walked (added " + added + ", gone " + gone + ")";
        }
        if (!effects.equals(e.getActiveEffectsMap())) return "changed its effects (" + effects.keySet() + " to " + e.getActiveEffectsMap().keySet() + ")";
        return null;
    }
}
