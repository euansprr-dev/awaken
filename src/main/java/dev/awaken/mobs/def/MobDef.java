package dev.awaken.mobs.def;

import dev.awaken.core.api.BodyMaterial;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One creature of the bestiary. Each def becomes its own entity type ({@code awaken:<id>}), spawn egg, loot table and
 * textures ({@code textures/entity/mobs/<id>.png} skin layer, {@code <id>_glow.png} emissive layer). Base stats are
 * before SPEC section 2 scaling.
 *
 * <p>Bestiary files declare defs as {@code m("id", "Name", Theme.X, Tier.Y, Model.Z, "signature")} on one line; the
 * content tools ({@code tools/content/mobs_bestiary.py}) read those lines to generate lang, loot tables, eggs and docs.
 */
public final class MobDef {
    public final String id;
    public final String name;
    public final Theme theme;
    public final Tier tier;
    public final Model model;
    /** One line: what makes it recognisable in a fight (docs, Codex). */
    public final String signature;

    public final double health, damage, speed, armor, knockbackResist, followRange;
    public final float scale, width, height;
    public final int tint;
    public final int glow;
    public final Set<Trait> traits;
    /** Phase 0 is the whole fight for non-bosses. */
    public final List<Phase> phases;
    public final Map<EquipmentSlot, Item> gear;
    public final List<Part> parts;
    public final BossEvent.BossBarColor barColor;
    public final int arena;
    public final @Nullable String intro;
    /** Explosion power when it dies volatile (FUSE mobs and the Volatile modifier). */
    public final float blast;
    public final int xp;
    /** Overworld creatures only (the Awakened layer): what it is for. Null for gate, stratum and dream creatures. */
    public final @Nullable FaunaRole fauna;
    /** Overrides the model's borrowed voice (overworld creatures only). */
    public final Model.@Nullable Voice voice;
    /** What an overworld creature does (NONE for everything else). */
    public final FaunaTalent talent;

    /** A boss phase: entered when health falls below {@code below} (phase 0 uses 1.0). */
    public record Phase(float below, String line, List<Ability> abilities, float speedBonus) {}

    /** A Monster Hunter style material this boss drops ({@code awaken:<id>} item, crafted by other modules). */
    public record Part(String id, String name, String ramp) {}

    private MobDef(B b) {
        this.id = b.id;
        this.name = b.name;
        this.theme = b.theme;
        this.tier = b.tier;
        this.model = b.model;
        this.signature = b.signature;
        this.health = b.health;
        this.damage = b.damage;
        this.speed = b.speed;
        this.armor = b.armor;
        this.knockbackResist = b.tier.isBoss() ? 1.0 : b.tier == Tier.WARDEN ? 0.8 : b.knockbackResist;
        this.followRange = b.followRange > 0 ? b.followRange : b.tier.isBoss() || b.tier == Tier.WARDEN ? 40 : 24;
        this.scale = b.scale;
        this.width = b.width > 0 ? b.width : b.model.width;
        this.height = b.height > 0 ? b.height : b.model.height;
        this.tint = b.tint != 0 ? b.tint : b.theme.tint;
        this.glow = b.glow != 0 ? b.glow : b.theme.glow;
        this.traits = b.traits.isEmpty() ? EnumSet.noneOf(Trait.class) : EnumSet.copyOf(b.traits);
        if (b.phases.isEmpty()) b.phases.add(new Phase(1.0F, "", List.of(), 0));
        this.phases = List.copyOf(b.phases);
        this.gear = Map.copyOf(b.gear);
        this.parts = List.copyOf(b.parts);
        this.barColor = b.barColor;
        this.arena = b.arena;
        this.intro = b.intro;
        this.blast = b.blast;
        this.xp = b.xp > 0 ? b.xp : switch (b.tier) {
            case NORMAL -> 5;
            case ELITE -> 15;
            case WARDEN -> 30;
            case NIGHTMARE -> 40;
            default -> 80;
        };
        this.fauna = b.fauna;
        this.voice = b.voice;
        this.talent = b.talent;
    }

    public boolean isFauna() {
        return fauna != null;
    }

    /** What a blade meets on this body (Arms' impacts): bone, golem iron and stone, creaking wood, a spirit's nothing. */
    public BodyMaterial material() {
        if (traits.contains(Trait.HAUNTED)) return BodyMaterial.SPIRIT;
        return switch (model) {
            case SKELETON, STRAY, WITHER_SKELETON, BOGGED, WITHER -> BodyMaterial.BONE;
            case IRON_GOLEM, BELL, TORTOISE -> BodyMaterial.STONE;
            case CREAKING -> BodyMaterial.WOOD;
            case SLIME, MAGMA -> BodyMaterial.SLIME;
            case VEX, BLAZE, BREEZE, ALLAY, SHADE -> BodyMaterial.SPIRIT;
            default -> BodyMaterial.FLESH;
        };
    }

    /** The voice this creature speaks with (its own override, else its model's). */
    public Model.Voice voice() {
        return voice != null ? voice : model.voice;
    }

    public boolean has(Trait t) {
        return traits.contains(t);
    }

    public boolean flies() {
        return model.move == Model.Move.FLY;
    }

    public String bossId() {
        return "awaken:" + id;
    }

    public static B m(String id, String name, Theme theme, Tier tier, Model model, String signature) {
        return new B(id, name, theme, tier, model, signature);
    }

    public static Part P(String id, String name, String ramp) {
        return new Part(id, name, ramp);
    }

    /** Fluent builder used by the bestiary files. */
    public static final class B {
        final String id, name, signature;
        final Theme theme;
        final Tier tier;
        final Model model;
        double health = 20, damage = 3, speed = 0.25, armor = 0, knockbackResist = 0, followRange = 0;
        float scale = 1.0F, width = 0, height = 0, blast = 2.0F;
        int tint, glow, arena = 22, xp;
        final EnumSet<Trait> traits = EnumSet.noneOf(Trait.class);
        final List<Phase> phases = new ArrayList<>();
        final Map<EquipmentSlot, Item> gear = new EnumMap<>(EquipmentSlot.class);
        final List<Part> parts = new ArrayList<>();
        BossEvent.BossBarColor barColor = BossEvent.BossBarColor.PURPLE;
        String intro;
        FaunaRole fauna;
        Model.Voice voice;
        FaunaTalent talent = FaunaTalent.NONE;

        B(String id, String name, Theme theme, Tier tier, Model model, String signature) {
            this.id = id;
            this.name = name;
            this.theme = theme;
            this.tier = tier;
            this.model = model;
            this.signature = signature;
            if (tier == Tier.WARDEN) barColor = BossEvent.BossBarColor.RED;
        }

        public B hp(double v) { health = v; return this; }
        public B dmg(double v) { damage = v; return this; }
        public B spd(double v) { speed = v; return this; }
        public B armor(double v) { armor = v; return this; }
        public B kb(double v) { knockbackResist = v; return this; }
        public B follow(double v) { followRange = v; return this; }
        public B scale(float v) { scale = v; return this; }
        public B size(float w, float h) { width = w; height = h; return this; }
        public B tint(int rgb) { tint = rgb; return this; }
        public B glow(int rgb) { glow = rgb; return this; }
        public B blast(float power) { blast = power; return this; }
        public B xp(int v) { xp = v; return this; }
        public B traits(Trait... t) { traits.addAll(List.of(t)); return this; }
        public B hold(Item item) { gear.put(EquipmentSlot.MAINHAND, item); return this; }
        public B offhand(Item item) { gear.put(EquipmentSlot.OFFHAND, item); return this; }
        public B wear(EquipmentSlot slot, Item item) { gear.put(slot, item); return this; }
        public B bar(BossEvent.BossBarColor c) { barColor = c; return this; }
        public B arena(int radius) { arena = radius; return this; }
        public B intro(String line) { intro = line; return this; }
        public B parts(Part... p) { parts.addAll(List.of(p)); return this; }
        /** Marks an overworld creature (spawned by the world module's fauna layer, not by gates). */
        public B fauna(FaunaRole role) { fauna = role; return this; }
        public B voice(Model.Voice v) { voice = v; return this; }
        public B talent(FaunaTalent t) { talent = t; return this; }

        /** Abilities for a non-boss (or a boss's opening phase). */
        public B ab(Ability.Builder... abilities) {
            return phase(1.0F, "", 0, abilities);
        }

        /** A boss phase starting when health drops below {@code below}. */
        public B phase(float below, String line, Ability.Builder... abilities) {
            return phase(below, line, phases.isEmpty() ? 0 : 0.1F * phases.size(), abilities);
        }

        public B phase(float below, String line, float speedBonus, Ability.Builder... abilities) {
            List<Ability> list = new ArrayList<>();
            for (Ability.Builder a : abilities) list.add(a.build());
            phases.add(new Phase(below, line, List.copyOf(list), speedBonus));
            return this;
        }

        public MobDef build() {
            return new MobDef(this);
        }
    }
}
