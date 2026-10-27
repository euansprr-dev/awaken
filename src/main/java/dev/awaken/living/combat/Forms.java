package dev.awaken.living.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.living.LivingContent;
import dev.awaken.living.region.RegionMap;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * The five forms (SPEC §21.11, decisions 16-22): in land of rank D or higher a common vanilla creature wears the form of its
 * land (rung 1 to 5 = ranks D to S), the one below it now and then, and rarely the one above (a taste of what is further on).
 * Rung 0 is the vanilla body. Which rung is a pure function of the land's level and a random roll, so it is rolled once, when the
 * creature spawns (or, for one from before this existed, when it first joins), and kept on the creature
 * ({@code awaken:wild_form}, saved, synced to everyone who sees it); the client draws it and the moves (Build 2) read it.
 */
public final class Forms {
    private Forms() {}

    /** Mark bit 1: the form's crown (an alpha, or an elite monster). */
    public static final int CROWN = 1;
    /** Mark bit 2: a revenant has already stood up once. */
    public static final int RISEN = 2;

    /** Biome families: the material a form is made of (moss and bark, sun-baked bone, rime). */
    public static final int GREEN = 0, SUN = 1, FROST = 2;

    /** What a creature wears. {@code rung} -1 = not rolled yet, 0 = vanilla. */
    public record Form(int rung, int family, int marks) {
        public static final Form UNSET = new Form(-1, 0, 0);
        public static final Form NONE = new Form(0, 0, 0);

        public static final Codec<Form> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("rung").forGetter(Form::rung),
                Codec.INT.optionalFieldOf("family", 0).forGetter(Form::family),
                Codec.INT.optionalFieldOf("marks", 0).forGetter(Form::marks)
        ).apply(i, Form::new));

        public static final StreamCodec<ByteBuf, Form> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Form::rung,
                ByteBufCodecs.VAR_INT, Form::family,
                ByteBufCodecs.VAR_INT, Form::marks,
                Form::new);

        public boolean worn() {
            return rung > 0;
        }

        public boolean crowned() {
            return (marks & CROWN) != 0;
        }
    }

    /**
     * The creatures that wear forms. {@code layout} names the texture set (a husk wears the zombie's, a stray the skeleton's);
     * {@code wild} is true for the animals, which only ever wear one when they are wild kin.
     */
    public enum Species {
        PIG("pig", "pig", EntityTypes.PIG, true),
        COW("cow", "cow", EntityTypes.COW, true),
        MOOSHROOM("mooshroom", "cow", EntityTypes.MOOSHROOM, true),
        SHEEP("sheep", "sheep", EntityTypes.SHEEP, true),
        CHICKEN("chicken", "chicken", EntityTypes.CHICKEN, true),
        ZOMBIE("zombie", "zombie", EntityTypes.ZOMBIE, false),
        HUSK("husk", "zombie", EntityTypes.HUSK, false),
        SKELETON("skeleton", "skeleton", EntityTypes.SKELETON, false),
        STRAY("stray", "skeleton", EntityTypes.STRAY, false),
        SPIDER("spider", "spider", EntityTypes.SPIDER, false),
        CAVE_SPIDER("cave_spider", "spider", EntityTypes.CAVE_SPIDER, false),
        CREEPER("creeper", "creeper", EntityTypes.CREEPER, false);

        public final String id, layout;
        public final EntityType<? extends Mob> type;
        public final boolean animal;

        Species(String id, String layout, EntityType<? extends Mob> type, boolean animal) {
            this.id = id;
            this.layout = layout;
            this.type = type;
            this.animal = animal;
        }

        private static final Map<EntityType<?>, Species> BY_TYPE = new HashMap<>();

        static {
            for (Species s : values()) BY_TYPE.put(s.type, s);
        }

        public static @Nullable Species of(EntityType<?> type) {
            return BY_TYPE.get(type);
        }

        public static @Nullable Species byId(String id) {
            for (Species s : values()) if (s.id.equals(id)) return s;
            return null;
        }
    }

    /** Odds of the rung one above the land's (the foreshadow), of the one below, and of the one below in a rank's first three levels. */
    static final float ABOVE = 0.05F, BELOW = 0.25F, BELOW_EDGE = 0.50F;
    static final int EDGE_LEVELS = 3;

    /**
     * The rung a creature in land of this level wears: none in E land (levels 1-9, the first hour); in D land and up its land's rank
     * 70% of the time, one below 25% (a half in a rank's first three levels, since a border is a slope), one above 5%.
     */
    public static int rungFor(int landLevel, RandomSource rnd) {
        int rank = RegionMap.rankOf(landLevel);
        if (rank == 0) return 0;
        float u = rnd.nextFloat();
        if (u < ABOVE) return Math.min(5, rank + 1);
        float below = landLevel - RegionMap.RANK_FROM[rank] < EDGE_LEVELS ? BELOW_EDGE : BELOW;
        return u < ABOVE + below ? rank - 1 : rank;
    }

    /** The material of a biome: cold land wears rime, hot land sun-baked bone, the rest moss and bark. */
    public static int family(Biome biome) {
        float t = biome.getBaseTemperature();
        return t <= 0.2F ? FROST : t >= 1.0F ? SUN : GREEN;
    }

    /** Whether this world dresses its creatures in forms (the game rule {@code awaken:wild_forms}, on by default). */
    public static boolean enabled(ServerLevel level) {
        return level.getGameRules().get(LivingContent.WILD_FORMS.get());
    }

    /** As {@link #roll}, when the world has forms on; else the creature is rolled vanilla (so it is not asked again). */
    public static void rollIfOn(ServerLevel level, Mob mob, int landLevel, boolean alpha) {
        if (enabled(level)) roll(level, mob, landLevel, alpha);
        else mob.setData(LivingContent.FORM.get(), Form.NONE);
    }

    /** Rolls a creature's form, once. An alpha or an elite wears at least its land's rung, and the crown. */
    public static void roll(ServerLevel level, Mob mob, int landLevel, boolean alpha) {
        if (Species.of(mob.getType()) == null || mob.isBaby()) return;
        int rank = RegionMap.rankOf(landLevel);
        int rung = rungFor(landLevel, mob.getRandom());
        int marks = 0;
        if (alpha && rank > 0) {
            rung = Math.max(rung, rank);
            marks |= CROWN;
        }
        mob.setData(LivingContent.FORM.get(), new Form(rung, family(level.getBiome(mob.blockPosition()).value()), marks));
    }

    /** The names of the five forms of each layout (SPEC §21.11), D to S (what a first sight is called). */
    private static final java.util.Map<String, String[]> NAMES = java.util.Map.of(
            "pig", new String[]{"Tusker", "Rootbreaker", "Ember boar", "Boarlord", "Hollow tusk"},
            "cow", new String[]{"Bullock", "Aurochs", "Thunderhorn", "Stormbull", "Skyhorn"},
            "sheep", new String[]{"Bighorn", "Ironram", "Cinderfleece", "Stormfleece", "Moonram"},
            "chicken", new String[]{"Spurhen", "Cockerel", "Firecrest", "Roc-chick", "Sunfowl"},
            "zombie", new String[]{"Gnawer", "Ironhusk", "Reaver", "Revenant", "Hollow King"},
            "skeleton", new String[]{"Bonepicker", "Marksman", "Volley", "Wraith archer", "Bone warden"},
            "spider", new String[]{"Webspinner", "Ambusher", "Spitter", "Broodmother", "Void weaver"},
            "creeper", new String[]{"Hisser", "Stormbred", "Cinder", "Sapper", "Stormwalker"});

    public static String nameOf(Species sp, int rung) {
        String[] n = sp == Species.MOOSHROOM ? new String[]{"Sporecow", "Mycelid", "Bloom", "Blightbull", "Heartshroom"} : NAMES.get(sp.layout);
        return n == null || rung < 1 || rung > n.length ? sp.id : n[rung - 1];
    }

    /** Dev and tours: a creature standing still (no AI, silent, unhurtable) wearing a chosen form, facing {@code yaw} (degrees). */
    public static @Nullable Mob still(ServerLevel level, Species sp, int rung, int family, double x, double y, double z, float yaw) {
        Mob mob = sp.type.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        if (mob == null) return null;
        mob.snapTo(x, y, z, yaw, 0.0F);
        mob.setYHeadRot(yaw);
        mob.setYBodyRot(yaw);
        mob.setNoAi(true);
        mob.setSilent(true);
        mob.setPermanentlyInvulnerable(true);
        mob.setPersistenceRequired();
        level.addFreshEntity(mob);
        mob.setData(LivingContent.FORM.get(), new Form(rung, family, 0));
        return mob;
    }

    /** The form a creature wears (vanilla when it was never rolled). */
    public static Form of(net.minecraft.world.entity.Entity e) {
        Form f = e.getExistingData(LivingContent.FORM).orElse(Form.NONE);
        return f.rung() < 0 ? Form.NONE : f;
    }
}
