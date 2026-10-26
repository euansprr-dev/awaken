package dev.awaken.arms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.core.Forces;
import dev.awaken.core.api.WeaveHooks;
import dev.awaken.powers.Fx;
import dev.awaken.powers.PowerType;
import dev.awaken.powers.fx.FxKind;
import dev.awaken.powers.fx.FxStyle;
import dev.awaken.signal.Signal;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Weaves (SPEC §25 "Weaves", PLAN §4.4): for 12 ticks after an opening the blade earned, a power cast fuses with the
 * weapon. Four openings × nine power families = 36 Weaves, each named. The opening is the shape: a deflect bursts a
 * ring off the meeting of the blades; a perfect dodge leaves an echo where you stood that strikes a moment later; a
 * Break sends the power through the broken body; a finisher in full rhythm charges the next three blows with the
 * power's force. The family is the element and the look (its Force and its effects style). A Weave's power costs 30%
 * less. The first time each is woven the System names it ("WEAVE · Ember Riposte") and it is kept in the fighter's
 * memory ({@link ArmsMemory}, saved; the Codex reads it).
 */
public final class ArmsWeaves implements WeaveHooks.Weaver {
    static final ArmsWeaves INSTANCE = new ArmsWeaves();

    /** The beat after an opening in which a cast weaves (ticks), and the share of its energy it then costs. */
    public static final int WINDOW = 12;
    public static final float COST = 0.7F;

    /** The openings, in the order of {@link #NAMES}' rows. */
    public static final int DEFLECT = 1, DODGE = 2, BREAK = 3, RHYTHM = 4;

    /** The 36 names: by opening, then by family in {@link PowerType.Family}'s order. */
    private static final String[][] NAMES = {
            {"Iron Riposte", "Gale Riposte", "Weighted Riposte", "Ember Riposte", "Umbral Riposte", "Mirror Riposte", "Bulwark Riposte",
                    "Arc Riposte", "Soul Riposte"},
            {"Quake Step", "Slipstream", "Falling Star", "Flashpoint", "Afterimage", "Phantom Step", "Glass Wake", "Ghost Circuit", "Wisp Echo"},
            {"Sundering", "Whirlwind Break", "Collapse", "Meltdown", "Eclipse", "Shattered Will", "Keystone", "Overload", "Severance"},
            {"Hammer Rhythm", "Tempest Edge", "Heavy Hand", "Burning Edge", "Night Edge", "Thought Edge", "Mason's Edge", "Charged Edge", "Spirit Edge"},
    };

    /** The name of the Weave an opening makes with a power family. */
    public static String name(int opening, PowerType.Family family) {
        return NAMES[opening - 1][family.ordinal()];
    }

    /** Its id (the memory's key, the Codex's): the name in lower case, underscored. */
    public static String id(int opening, PowerType.Family family) {
        return name(opening, family).toLowerCase(Locale.ROOT).replace("'", "").replace(' ', '_');
    }

    /** An opening earned: the next cast within {@link #WINDOW} ticks weaves. */
    static void open(ServerPlayer p, ArmsState s, int kind, long now, int target) {
        s.opening = kind;
        s.openingAt = now;
        s.openingTarget = target;
    }

    static boolean inBeat(ArmsState s, long now) {
        return s.opening != 0 && now - s.openingAt <= WINDOW;
    }

    @Override
    public float costScale(ServerPlayer player) {
        ArmsState s = player.getExistingDataOrNull(ArmsContent.STATE);
        return s != null && inBeat(s, ArmsServer.now(player)) ? COST : 1F;
    }

    @Override
    public void cast(ServerPlayer p, String family, @Nullable String force, int level) {
        ArmsState s = p.getExistingDataOrNull(ArmsContent.STATE);
        long now = ArmsServer.now(p);
        if (s == null || !inBeat(s, now)) return;
        PowerType.Family fam;
        try {
            fam = PowerType.Family.valueOf(family);
        } catch (IllegalArgumentException e) {
            return;
        }
        int opening = s.opening;
        s.opening = 0;
        weave(p, s, opening, fam, force == null ? defaultForce(fam) : force, level, now);
    }

    /** A family without a Force of its own still has an element: its Weaves strike with this one (null: plain). */
    static @Nullable String defaultForce(PowerType.Family f) {
        return switch (f) {
            case ENERGY, TECH -> Forces.Force.CHARGE.name();
            case FLIGHT, ARCHITECT -> Forces.Force.GRAVITY.name();
            case SHADOW, MIND, SPIRIT -> Forces.Force.SPIRIT.name();
            default -> null;
        };
    }

    private static void weave(ServerPlayer p, ArmsState s, int opening, PowerType.Family fam, @Nullable String force, int level, long now) {
        ServerLevel level0 = p.level();
        FxStyle style = FxStyle.of(fam);
        float power = (float) p.getAttributeValue(Attributes.ATTACK_DAMAGE) * (1F + 0.08F * Math.max(1, level));
        switch (opening) {
            case DEFLECT -> {
                // a ring bursts off the meeting of the blades
                burst(p, p.position(), 4.0, power * 1.2F, force, 0.6);
                Fx.at(level0, FxKind.NOVA, style, p.position().add(0, 1, 0)).caster(p).scale(4.0).send();
            }
            case DODGE -> {
                // an echo where you stood strikes a moment later (ArmsServer's tick lets it go)
                s.echoAt = now + 6;
                s.echoX = s.dodgeFromX;
                s.echoY = s.dodgeFromY;
                s.echoZ = s.dodgeFromZ;
                s.echoPower = power;
                s.echoForce = force;
                s.echoFamily = fam;
                Fx.at(level0, FxKind.RING, style, new Vec3(s.dodgeFromX, s.dodgeFromY + 0.1, s.dodgeFromZ)).caster(p).scale(1.5).ticks(6).send();
            }
            case BREAK -> {
                // the power goes through the broken body: a heavy blow of its force, and the Break held a second longer
                if (level0.getEntity(s.openingTarget) instanceof LivingEntity t && t.isAlive() && t.distanceToSqr(p) < 16 * 16) {
                    float cap = t.getMaxHealth() >= 150 ? t.getMaxHealth() * 0.03F : Float.MAX_VALUE;
                    t.hurtServer(level0, source(p, force), Math.min(cap, power * 1.6F));
                    ArmsServer.extendBreak(t, 20);
                    Fx.at(level0, FxKind.IMPACT, style, t.getBoundingBox().getCenter()).caster(p).target(t).scale(2.5).send();
                }
            }
            case RHYTHM -> {
                // the next three blows carry the power's force
                s.edgeHits = 3;
                s.edgeUntil = now + 100;
                s.edgeForce = force;
                s.edgeFamily = fam;
                Fx.at(level0, FxKind.CHARGE, style, p.getEyePosition()).caster(p).scale(0.6).ticks(10).send();
                // seen from outside too: the family's light rings the fighter's feet as the edge takes it
                Fx.at(level0, FxKind.RING, style, p.position().add(0, 0.1, 0)).caster(p).scale(2.2).ticks(8).send();
            }
            default -> { }
        }
        name(p, opening, fam);
    }

    /** The echo's strike, when its moment comes (ArmsServer's player tick). */
    static void tick(ServerPlayer p, ArmsState s, long now) {
        if (s.echoAt == 0 || now < s.echoAt) return;
        s.echoAt = 0;
        Vec3 at = new Vec3(s.echoX, s.echoY, s.echoZ);
        burst(p, at, 3.0, s.echoPower, s.echoForce, 0.4);
        if (s.echoFamily != null) Fx.at(p.level(), FxKind.NOVA, FxStyle.of(s.echoFamily), at.add(0, 1, 0)).caster(p).scale(3.0).send();
    }

    /** One of the next blows after a rhythm Weave: a share of it again as the power's force. */
    static void edge(ServerPlayer p, ArmsState s, LivingEntity t, long now, float dealt) {
        if (s.edgeHits <= 0 || now > s.edgeUntil) {
            s.edgeHits = 0;
            return;
        }
        s.edgeHits--;
        t.damageCooldownTime = 0;
        t.hurtServer(p.level(), source(p, s.edgeForce), Math.max(1F, dealt * 0.4F));
        if (s.edgeFamily != null) Fx.at(p.level(), FxKind.IMPACT, FxStyle.of(s.edgeFamily), t.getBoundingBox().getCenter()).caster(p).target(t).scale(1.2).send();
    }

    private static void burst(ServerPlayer p, Vec3 at, double radius, float damage, @Nullable String force, double knock) {
        List<LivingEntity> near = p.level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius, 2, radius),
                t -> t != p && t.isAlive() && ArmsServer.foe(p, t) && t.distanceToSqr(at) <= radius * radius);
        DamageSource src = source(p, force);
        for (LivingEntity t : near) {
            t.damageCooldownTime = 0;
            t.hurtServer(p.level(), src, damage);
            Vec3 away = t.position().subtract(at);
            double d = Math.max(1e-3, away.horizontalDistance());
            double k = knock * (1 - t.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
            t.setDeltaMovement(t.getDeltaMovement().add(away.x / d * k, 0.2 * k, away.z / d * k));
            t.needsSync = true;
        }
    }

    private static DamageSource source(ServerPlayer p, @Nullable String force) {
        if (force != null) {
            try {
                return p.level().damageSources().source(Forces.key(Forces.Force.valueOf(force)), p);
            } catch (IllegalArgumentException ignored) {
                // an unknown force: a plain blow
            }
        }
        return p.damageSources().playerAttack(p);
    }

    /** The first time a Weave happens, the System names it and the fighter remembers it. */
    private static void name(ServerPlayer p, int opening, PowerType.Family fam) {
        ArmsMemory m = p.getData(ArmsContent.MEMORY);
        String id = id(opening, fam);
        boolean first = m.weaves().add(id);
        if (first) {
            p.setData(ArmsContent.MEMORY, m);
            sync(p);
        }
        Component text = Component.literal("WEAVE · ").withStyle(Style.EMPTY.withColor(0xE2B450))
                .append(Component.literal(name(opening, fam)).withStyle(Style.EMPTY.withColor(fam.colour)));
        if (first) Signal.toast(p, text);
        else p.sendOverlayMessage(text);
    }

    /**
     * What Arms remembers of a fighter (saved, kept through death): the Weaves woven (the Codex's Weaves page counts
     * them, never naming the rest), and how many Lords have fallen since the last Lord-forged weapon.
     */
    public record ArmsMemory(int schemaVersion, Set<String> weaves, int lordsWithout) {
        public static final int SCHEMA = 2; // 2: lordsWithout (the Lord-forged pity)
        static final MapCodec<ArmsMemory> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.optionalFieldOf("schemaVersion", SCHEMA).forGetter(ArmsMemory::schemaVersion),
                Codec.STRING.listOf().xmap(l -> (Set<String>) new HashSet<>(l), st -> List.copyOf(st)).optionalFieldOf("weaves", Set.of())
                        .forGetter(ArmsMemory::weaves),
                Codec.INT.optionalFieldOf("lordsWithout", 0).forGetter(ArmsMemory::lordsWithout)
        ).apply(i, (v, w, l) -> new ArmsMemory(SCHEMA, new HashSet<>(w), l)));

        static ArmsMemory fresh() {
            return new ArmsMemory(SCHEMA, new HashSet<>(), 0);
        }

        ArmsMemory withLordsWithout(int n) {
            return new ArmsMemory(SCHEMA, weaves, n);
        }
    }

    /** Tells the fighter's client every Weave they have woven (on joining, after a new one). */
    static void sync(ServerPlayer p) {
        dev.awaken.core.AwakenNet.send(p, new dev.awaken.arms.net.ArmsPayloads.Woven(List.copyOf(p.getData(ArmsContent.MEMORY).weaves())));
    }

    /** The opening and family a Weave's id names, as {opening, family ordinal}, or null for an unknown id. */
    public static int @Nullable [] parts(String id) {
        for (int o = DEFLECT; o <= RHYTHM; o++) {
            for (PowerType.Family f : PowerType.Family.values()) if (id(o, f).equals(id)) return new int[]{o, f.ordinal()};
        }
        return null;
    }

    /** What a Weave does, in a line (the Codex): its opening's shape and its family's element. */
    public static String describe(int opening, PowerType.Family family) {
        String shape = switch (opening) {
            case DEFLECT -> "Deflect, then cast: a ring bursts off the meeting of the blades.";
            case DODGE -> "Dodge perfectly, then cast: an echo strikes where you stood.";
            case BREAK -> "Break a stance, then cast: the power goes through the broken body and holds it down.";
            default -> "Finish a chain in full rhythm, then cast: your next three blows carry the power.";
        };
        return shape + " " + family.label + " is its element. A Weave's power costs 30% less.";
    }

    /** How many Weaves this fighter has found (the Codex: N / ??). */
    public static int found(ServerPlayer p) {
        return p.getData(ArmsContent.MEMORY).weaves().size();
    }
}
