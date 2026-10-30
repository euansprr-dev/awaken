package dev.awaken.arms;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import dev.awaken.core.Forces;
import dev.awaken.powers.Fx;
import dev.awaken.powers.Powers;
import dev.awaken.powers.fx.FxKind;
import dev.awaken.powers.fx.FxStyle;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Marks (SPEC §25 "Marks"): what a found weapon does beyond its moves. A Mark is a trigger (a hit, a finisher in full
 * rhythm, a deflect, a perfect dodge, a Break, a kill, a blow guarded, a perfect heavy) and an effect (a rider of a
 * Force, posture, a heal, power energy, Speed, Resistance); 48 of them in {@code data/awaken/arms/marks.json} (written by
 * tools/arms/marks.py), read once on both sides like the styles. A weapon carries its Marks, and its attunement, in its
 * {@code awaken:arms} component; a rider takes the weapon's attuned Force over its own.
 */
public final class Marks {
    private Marks() {}

    /** A Force by name; an unknown one fails the row (data errors fail fast). */
    static final Codec<Forces.Force> FORCE = Codec.STRING.comapFlatMap(n -> {
        try {
            return com.mojang.serialization.DataResult.success(Forces.Force.valueOf(n));
        } catch (IllegalArgumentException e) {
            return com.mojang.serialization.DataResult.error(() -> "unknown Force " + n);
        }
    }, Enum::name);

    public enum Trigger { HIT, FINISHER, DEFLECT, PERFECT_DODGE, BREAK, KILL, GUARD, PERFECT_HEAVY;
        static final Codec<Trigger> CODEC = Codec.STRING.xmap(s -> valueOf(s.toUpperCase(Locale.ROOT)), t -> t.name().toLowerCase(Locale.ROOT)); }

    public enum Effect { RIDER, POSTURE, HEAL, ENERGY, SWIFT, WARD;
        static final Codec<Effect> CODEC = Codec.STRING.xmap(s -> valueOf(s.toUpperCase(Locale.ROOT)), t -> t.name().toLowerCase(Locale.ROOT)); }

    public record Mark(String id, String name, Trigger trigger, Effect effect, float amount, Optional<Forces.Force> force) {
        static final Codec<Mark> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(Mark::id),
                Codec.STRING.fieldOf("name").forGetter(Mark::name),
                Trigger.CODEC.fieldOf("trigger").forGetter(Mark::trigger),
                Effect.CODEC.fieldOf("effect").forGetter(Mark::effect),
                Codec.FLOAT.fieldOf("amount").forGetter(Mark::amount),
                FORCE.optionalFieldOf("force").forGetter(Mark::force)
        ).apply(i, Mark::new));
    }

    /**
     * A weapon's own state (the {@code awaken:arms} component, moves with the item): its Marks (at most two) and the
     * Force it is attuned to at the Forge.
     */
    public record Instance(int schemaVersion, List<String> marks, Optional<Forces.Force> attunement) {
        public static final int SCHEMA = 1;
        public static final Instance EMPTY = new Instance(SCHEMA, List.of(), Optional.empty());
        public static final Codec<Instance> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("schemaVersion", SCHEMA).forGetter(Instance::schemaVersion),
                Codec.STRING.listOf().optionalFieldOf("marks", List.of()).forGetter(Instance::marks),
                FORCE.optionalFieldOf("attunement").forGetter(Instance::attunement)
        ).apply(i, (v, m, a) -> new Instance(SCHEMA, List.copyOf(m), a)));
        public static final StreamCodec<ByteBuf, Instance> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(4)), Instance::marks,
                ByteBufCodecs.VAR_INT, x -> x.attunement().map(f -> f.ordinal() + 1).orElse(0),
                (m, a) -> new Instance(SCHEMA, m, a == 0 ? Optional.empty() : Optional.of(Forces.Force.values()[a - 1])));

        public Instance withMark(String id) {
            if (marks.contains(id) || marks.size() >= MAX_MARKS) return this;
            List<String> m = new ArrayList<>(marks);
            m.add(id);
            return new Instance(SCHEMA, List.copyOf(m), attunement);
        }

        public Instance attuned(Forces.Force f) {
            return new Instance(SCHEMA, marks, Optional.of(f));
        }
    }

    public static final int MAX_MARKS = 2;
    static final String PATH = "/data/awaken/arms/marks.json";
    private static final Map<String, Mark> BY_ID = new LinkedHashMap<>();
    private static final Map<Trigger, List<Mark>> BY_TRIGGER = new EnumMap<>(Trigger.class);

    private record File(int schemaVersion, List<Mark> marks) {
        static final Codec<File> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("schemaVersion").forGetter(File::schemaVersion),
                Mark.CODEC.listOf().fieldOf("marks").forGetter(File::marks)
        ).apply(i, File::new));
    }

    static {
        try (InputStream in = Marks.class.getResourceAsStream(PATH)) {
            if (in == null) throw new IllegalStateException("missing " + PATH);
            JsonElement json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            File file = File.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(msg -> new IllegalStateException(PATH + ": " + msg));
            for (Mark m : file.marks()) {
                if (BY_ID.put(m.id(), m) != null) throw new IllegalStateException(PATH + ": two Marks named " + m.id());
                BY_TRIGGER.computeIfAbsent(m.trigger(), t -> new ArrayList<>()).add(m);
            }
            Awaken.LOG.info("[arms] {} Marks", BY_ID.size());
        } catch (java.io.IOException e) {
            throw new IllegalStateException(PATH, e);
        }
    }

    public static List<Mark> all() {
        return List.copyOf(BY_ID.values());
    }

    public static @Nullable Mark get(String id) {
        return BY_ID.get(id);
    }

    public static Instance of(ItemStack stack) {
        Instance i = stack.get(ArmsContent.INSTANCE.get());
        return i == null ? Instance.EMPTY : i;
    }

    /** A Mark at random (a found weapon's). */
    static Mark random(RandomSource r) {
        List<Mark> all = all();
        return all.get(r.nextInt(all.size()));
    }

    /** What a Mark does, in a line ("On a deflect: 10 posture to the attacker"). */
    public static String describe(Mark m) {
        return describe(m, Optional.empty());
    }

    /** The same on a weapon attuned to a Force (its riders take it). */
    public static String describe(Mark m, Optional<Forces.Force> attunement) {
        String when = switch (m.trigger()) {
            case HIT -> "On a hit";
            case FINISHER -> "On a finisher in full rhythm";
            case DEFLECT -> "On a deflect";
            case PERFECT_DODGE -> "On a perfect dodge";
            case BREAK -> "On a Break";
            case KILL -> "On a kill";
            case GUARD -> "On a blow guarded";
            case PERFECT_HEAVY -> "On a perfect heavy";
        };
        String who = switch (m.trigger()) {
            case DEFLECT, GUARD -> "the attacker";
            case KILL -> "the foes around";
            case PERFECT_DODGE -> "who you slipped";
            default -> "the struck";
        };
        String what = switch (m.effect()) {
            case RIDER -> String.format(java.util.Locale.ROOT, "%d%% of your attack as %s to %s", Math.round(m.amount() * 100),
                    attunement.or(m::force).map(f -> f.name().charAt(0) + f.name().substring(1).toLowerCase(java.util.Locale.ROOT)).orElse("a second blow"), who);
            case POSTURE -> m.trigger() == Trigger.BREAK ? "the Break held " + Math.round(m.amount() / 20F * 10) / 10F + " s longer"
                    : Math.round(m.amount()) + " posture to " + who;
            case HEAL -> String.format(java.util.Locale.ROOT, "heals %s%% of your health", trim(m.amount() * 100));
            case ENERGY -> Math.round(m.amount()) + " power energy";
            case SWIFT -> "Speed for " + trim(m.amount() / 20F) + " s";
            case WARD -> "Resistance for " + trim(m.amount() / 20F) + " s";
        };
        return when + ": " + what;
    }

    /** The same as a sentence you'd say out loud, for the Journal: "When you land a hit, the target takes an extra 20% ...". */
    public static String say(Mark m, Optional<Forces.Force> attunement) {
        String when = switch (m.trigger()) {
            case HIT -> "When you land a hit";
            case FINISHER -> "When you land a finisher in full rhythm";
            case DEFLECT -> "When you deflect a blow";
            case PERFECT_DODGE -> "When you dodge perfectly";
            case BREAK -> "When you Break an enemy";
            case KILL -> "When you kill an enemy";
            case GUARD -> "When you guard a blow";
            case PERFECT_HEAVY -> "When you land a perfect heavy";
        };
        String who = switch (m.trigger()) {
            case DEFLECT, GUARD -> "the attacker takes";
            case KILL -> "the enemies around it take";
            case PERFECT_DODGE -> "the one you slipped past takes";
            default -> "the target takes";
        };
        String what = switch (m.effect()) {
            case RIDER -> who + " an extra " + Math.round(m.amount() * 100) + "% of your attack" + attunement.or(m::force)
                    .map(f -> " as " + f.name().charAt(0) + f.name().substring(1).toLowerCase(Locale.ROOT) + " damage").orElse(" as a second blow");
            case POSTURE -> m.trigger() == Trigger.BREAK ? "the Break lasts " + seconds(m.amount() / 20F) + " longer" : who + " " + Math.round(m.amount()) + " posture damage";
            case HEAL -> "you heal " + trim(m.amount() * 100) + "% of your health";
            case ENERGY -> "you get " + Math.round(m.amount()) + " power energy back";
            case SWIFT -> "you get Speed for " + seconds(m.amount() / 20F);
            case WARD -> "you get Resistance for " + seconds(m.amount() / 20F);
        };
        return when + ", " + what + ".";
    }

    private static String seconds(float v) {
        return trim(v) + (v == 1 ? " second" : " seconds");
    }

    private static String trim(float v) {
        return v == Math.rint(v) ? String.valueOf((int) v) : String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    // ---- firing -------------------------------------------------------------------------------------------------------

    /**
     * The fighter's weapon met a trigger: each of its Marks on it acts. {@code target} is the body it concerns (the
     * struck, the attacker answered, the one broken or killed); a kill's rider and posture go to the foes around it.
     */
    static void fire(ServerPlayer p, Trigger trigger, @Nullable LivingEntity target) {
        // every blade moment, marked or not, for armour's set abilities (SPEC §44.5)
        dev.awaken.core.api.FightMoments.report(p, dev.awaken.core.api.FightMoments.Moment.valueOf(trigger.name()), target);
        Instance inst = of(p.getMainHandItem());
        if (inst.marks().isEmpty()) return;
        for (String id : inst.marks()) {
            Mark m = BY_ID.get(id);
            if (m != null && m.trigger() == trigger) apply(p, m, inst, target);
        }
    }

    static void apply(ServerPlayer p, Mark m, Instance inst, @Nullable LivingEntity target) {
        ServerLevel level = p.level();
        boolean around = m.trigger() == Trigger.KILL || m.trigger() == Trigger.PERFECT_DODGE && target == null;
        switch (m.effect()) {
            case RIDER -> {
                Forces.@Nullable Force f = inst.attunement().orElse(m.force().orElse(null));
                float dmg = (float) p.getAttributeValue(Attributes.ATTACK_DAMAGE) * m.amount();
                DamageSource src = f == null ? p.damageSources().indirectMagic(p, p) : level.damageSources().source(Forces.key(f), p);
                for (LivingEntity t : around ? near(p, target, 4) : one(target)) {
                    t.damageCooldownTime = 0;
                    t.hurtServer(level, src, t.getMaxHealth() >= 150 ? Math.min(dmg, t.getMaxHealth() * 0.02F) : dmg);
                    Fx.at(level, FxKind.IMPACT, f == null ? FxStyle.NEUTRAL : look(f),
                            t.position().add(0, t.getBbHeight() * 0.6, 0)).scale(1.0).send();
                }
            }
            case POSTURE -> {
                if (m.trigger() == Trigger.BREAK) {
                    if (target != null) ArmsServer.extendBreak(target, (int) m.amount());
                } else {
                    for (LivingEntity t : around ? near(p, target, 4) : one(target)) ArmsServer.addPosture(t, (int) m.amount(), p);
                }
            }
            case HEAL -> p.heal(p.getMaxHealth() * m.amount());
            case ENERGY -> Powers.restoreEnergy(p, m.amount());
            case SWIFT -> p.addEffect(new MobEffectInstance(MobEffects.SPEED, (int) m.amount(), 0, false, false, true));
            case WARD -> p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, (int) m.amount(), 0, false, false, true));
        }
    }

    /** A Force's own look on the effects engine. */
    static FxStyle look(Forces.Force f) {
        return switch (f) {
            case HEAT -> FxStyle.HEAT;
            case CHARGE -> FxStyle.CHARGE;
            case GRAVITY -> FxStyle.GRAVITY;
            case SPIRIT -> FxStyle.SOUL;
            case FROST -> FxStyle.FROST;
        };
    }

    private static List<LivingEntity> one(@Nullable LivingEntity t) {
        return t == null || !t.isAlive() ? List.of() : List.of(t);
    }

    /** The foes within r of a body (or of the fighter), the body itself left out. */
    private static List<LivingEntity> near(ServerPlayer p, @Nullable LivingEntity around, double r) {
        var c = around == null ? p.position() : around.position();
        return p.level().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r),
                t -> t != around && t != p && t.isAlive() && ArmsServer.foe(p, t) && t.distanceToSqr(c) <= r * r);
    }
}
