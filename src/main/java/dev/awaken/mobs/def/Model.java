package dev.awaken.mobs.def;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * The body a creature wears. Most are vanilla models re-tinted with our own skin and glow layers on top (the vanilla
 * texture is referenced, never copied); the last few are our own LayerDefinitions with generated textures. The client
 * maps each value to a renderer in {@code mobs.client.AwRenderers}; this common enum only holds what the server needs.
 */
public enum Model {
    // humanoids
    ZOMBIE("zombie/zombie", Move.GROUND, 0.6F, 1.95F, Voice.ZOMBIE),
    HUSK("zombie/husk", Move.GROUND, 0.6F, 1.95F, Voice.HUSK),
    DROWNED("zombie/drowned", Move.GROUND, 0.6F, 1.95F, Voice.DROWNED),
    SKELETON("skeleton/skeleton", Move.GROUND, 0.6F, 1.99F, Voice.SKELETON),
    STRAY("skeleton/stray", Move.GROUND, 0.6F, 1.99F, Voice.STRAY),
    WITHER_SKELETON("skeleton/wither_skeleton", Move.GROUND, 0.7F, 2.4F, Voice.WITHER_SKELETON),
    BOGGED("skeleton/bogged", Move.GROUND, 0.6F, 1.99F, Voice.BOGGED),
    PIGLIN("piglin/piglin", Move.GROUND, 0.6F, 1.95F, Voice.PIGLIN),
    PIGLIN_BRUTE("piglin/piglin_brute", Move.GROUND, 0.6F, 1.95F, Voice.PIGLIN),
    /** Player model wearing the skin of the player it copies (Mirror, Hollow Court). */
    MIMIC("player/wide/steve", Move.GROUND, 0.6F, 1.8F, Voice.PLAYERLIKE),
    /** Player model with our own generated texture (Unit Zero, the Signal's echoes). */
    HUMAN("", Move.GROUND, 0.6F, 1.8F, Voice.PLAYERLIKE),
    ENDERMAN("enderman/enderman", Move.GROUND, 0.6F, 2.9F, Voice.ENDERMAN),
    VINDICATOR("illager/vindicator", Move.GROUND, 0.6F, 1.95F, Voice.VINDICATOR),
    EVOKER("illager/evoker", Move.GROUND, 0.6F, 1.95F, Voice.EVOKER),
    PILLAGER("illager/pillager", Move.GROUND, 0.6F, 1.95F, Voice.PILLAGER),
    ILLUSIONER("illager/illusioner", Move.GROUND, 0.6F, 1.95F, Voice.ILLUSIONER),
    WITCH("witch/witch", Move.GROUND, 0.6F, 1.95F, Voice.WITCH),
    // beasts and crawlers
    SPIDER("spider/spider", Move.GROUND, 1.4F, 0.9F, Voice.SPIDER),
    CAVE_SPIDER("spider/cave_spider", Move.GROUND, 0.7F, 0.5F, Voice.SPIDER),
    WOLF("wolf/wolf_angry", Move.GROUND, 0.6F, 0.85F, Voice.HOUND),
    SNOW_WOLF("wolf/wolf_snowy_angry", Move.GROUND, 0.6F, 0.85F, Voice.HOUND),
    HOGLIN("hoglin/hoglin", Move.GROUND, 1.4F, 1.4F, Voice.HOGLIN),
    ZOGLIN("hoglin/zoglin", Move.GROUND, 1.4F, 1.4F, Voice.ZOGLIN),
    POLAR_BEAR("bear/polarbear", Move.GROUND, 1.4F, 1.4F, Voice.BEAR),
    RAVAGER("illager/ravager", Move.GROUND, 1.95F, 2.2F, Voice.RAVAGER),
    IRON_GOLEM("iron_golem/iron_golem", Move.GROUND, 1.4F, 2.7F, Voice.GOLEM),
    WARDEN("warden/warden", Move.GROUND, 0.9F, 2.9F, Voice.WARDEN),
    CREAKING("creaking/creaking", Move.GROUND, 0.9F, 2.7F, Voice.CREAKING),
    CREEPER("creeper/creeper", Move.GROUND, 0.6F, 1.7F, Voice.CREEPER),
    SILVERFISH("silverfish/silverfish", Move.GROUND, 0.4F, 0.3F, Voice.SILVERFISH),
    ENDERMITE("endermite/endermite", Move.GROUND, 0.4F, 0.3F, Voice.ENDERMITE),
    SLIME("slime/slime", Move.HOP, 1.02F, 1.02F, Voice.SLIME),
    MAGMA("slime/magmacube", Move.HOP, 1.02F, 1.02F, Voice.MAGMA),
    BREEZE("breeze/breeze", Move.HOP, 0.6F, 1.77F, Voice.BREEZE),
    // fliers
    VEX("illager/vex", Move.FLY, 0.4F, 0.8F, Voice.VEX),
    BLAZE("blaze/blaze", Move.FLY, 0.6F, 1.8F, Voice.BLAZE),
    PHANTOM("phantom/phantom", Move.FLY, 0.9F, 0.5F, Voice.PHANTOM),
    BEE("bee/bee_angry", Move.FLY, 0.7F, 0.6F, Voice.BEE),
    GUARDIAN("guardian/guardian", Move.FLY, 0.85F, 0.85F, Voice.GUARDIAN),
    ELDER_GUARDIAN("guardian/guardian_elder", Move.FLY, 2.0F, 2.0F, Voice.ELDER),
    GHAST("ghast/ghast", Move.FLY, 4.0F, 4.0F, Voice.GHAST),
    WITHER("wither/wither", Move.FLY, 0.9F, 3.5F, Voice.WITHER),
    // our own models
    SERPENT("", Move.FLY, 1.0F, 1.0F, Voice.SERPENT),
    LISTENER("", Move.GROUND, 0.8F, 2.6F, Voice.LISTENER),
    BELL("", Move.GROUND, 1.4F, 3.2F, Voice.BELL),
    LANTERN("", Move.GROUND, 0.8F, 2.4F, Voice.LANTERN),
    SHADE("", Move.FLY, 0.9F, 2.8F, Voice.SHADE);

    public enum Move { GROUND, HOP, FLY }

    /** Path under minecraft:textures/entity/ (without .png), or "" when we ship our own texture. */
    public final String vanillaTexture;
    public final Move move;
    public final float width;
    public final float height;
    public final Voice voice;

    Model(String vanillaTexture, Move move, float width, float height, Voice voice) {
        this.vanillaTexture = vanillaTexture;
        this.move = move;
        this.width = width;
        this.height = height;
        this.voice = voice;
    }

    public boolean ownTexture() {
        return vanillaTexture.isEmpty();
    }

    public boolean humanoid() {
        return switch (this) {
            case ZOMBIE, HUSK, DROWNED, SKELETON, STRAY, WITHER_SKELETON, BOGGED, PIGLIN, PIGLIN_BRUTE, MIMIC, HUMAN -> true;
            default -> false;
        };
    }

    /** Ambient / hurt / death sounds borrowed from vanilla creatures. */
    public enum Voice {
        ZOMBIE(SoundEvents.ZOMBIE_AMBIENT, SoundEvents.ZOMBIE_HURT, SoundEvents.ZOMBIE_DEATH),
        HUSK(SoundEvents.HUSK_AMBIENT, SoundEvents.HUSK_HURT, SoundEvents.HUSK_DEATH),
        DROWNED(SoundEvents.DROWNED_AMBIENT, SoundEvents.DROWNED_HURT, SoundEvents.DROWNED_DEATH),
        SKELETON(SoundEvents.SKELETON_AMBIENT, SoundEvents.SKELETON_HURT, SoundEvents.SKELETON_DEATH),
        STRAY(SoundEvents.STRAY_AMBIENT, SoundEvents.STRAY_HURT, SoundEvents.STRAY_DEATH),
        WITHER_SKELETON(SoundEvents.WITHER_SKELETON_AMBIENT, SoundEvents.WITHER_SKELETON_HURT, SoundEvents.WITHER_SKELETON_DEATH),
        BOGGED(SoundEvents.BOGGED_AMBIENT, SoundEvents.BOGGED_HURT, SoundEvents.BOGGED_DEATH),
        PIGLIN(SoundEvents.PIGLIN_BRUTE_AMBIENT, SoundEvents.PIGLIN_BRUTE_HURT, SoundEvents.PIGLIN_BRUTE_DEATH),
        PLAYERLIKE(null, SoundEvents.PLAYER_HURT, SoundEvents.PLAYER_DEATH),
        ENDERMAN(SoundEvents.ENDERMAN_AMBIENT, SoundEvents.ENDERMAN_HURT, SoundEvents.ENDERMAN_DEATH),
        VINDICATOR(SoundEvents.VINDICATOR_AMBIENT, SoundEvents.VINDICATOR_HURT, SoundEvents.VINDICATOR_DEATH),
        EVOKER(SoundEvents.EVOKER_AMBIENT, SoundEvents.EVOKER_HURT, SoundEvents.EVOKER_DEATH),
        PILLAGER(SoundEvents.PILLAGER_AMBIENT, SoundEvents.PILLAGER_HURT, SoundEvents.PILLAGER_DEATH),
        ILLUSIONER(SoundEvents.ILLUSIONER_AMBIENT, SoundEvents.ILLUSIONER_HURT, SoundEvents.ILLUSIONER_DEATH),
        WITCH(SoundEvents.WITCH_AMBIENT, SoundEvents.WITCH_HURT, SoundEvents.WITCH_DEATH),
        SPIDER(SoundEvents.SPIDER_AMBIENT, SoundEvents.SPIDER_HURT, SoundEvents.SPIDER_DEATH),
        HOUND(SoundEvents.ZOGLIN_ANGRY, SoundEvents.SKELETON_HORSE_HURT, SoundEvents.SKELETON_HORSE_DEATH),
        HOGLIN(SoundEvents.HOGLIN_AMBIENT, SoundEvents.HOGLIN_HURT, SoundEvents.HOGLIN_DEATH),
        ZOGLIN(SoundEvents.ZOGLIN_AMBIENT, SoundEvents.ZOGLIN_HURT, SoundEvents.ZOGLIN_DEATH),
        BEAR(SoundEvents.POLAR_BEAR_AMBIENT, SoundEvents.POLAR_BEAR_HURT, SoundEvents.POLAR_BEAR_DEATH),
        RAVAGER(SoundEvents.RAVAGER_AMBIENT, SoundEvents.RAVAGER_HURT, SoundEvents.RAVAGER_DEATH),
        GOLEM(null, SoundEvents.IRON_GOLEM_HURT, SoundEvents.IRON_GOLEM_DEATH),
        WARDEN(SoundEvents.WARDEN_AMBIENT, SoundEvents.WARDEN_HURT, SoundEvents.WARDEN_DEATH),
        CREAKING(SoundEvents.CREAKING_AMBIENT, SoundEvents.CREAKING_TWITCH, SoundEvents.CREAKING_DEATH),
        CREEPER(null, SoundEvents.CREEPER_HURT, SoundEvents.CREEPER_DEATH),
        SILVERFISH(SoundEvents.SILVERFISH_AMBIENT, SoundEvents.SILVERFISH_HURT, SoundEvents.SILVERFISH_DEATH),
        ENDERMITE(SoundEvents.ENDERMITE_AMBIENT, SoundEvents.ENDERMITE_HURT, SoundEvents.ENDERMITE_DEATH),
        SLIME(null, SoundEvents.SLIME_HURT, SoundEvents.SLIME_DEATH),
        MAGMA(null, SoundEvents.MAGMA_CUBE_HURT, SoundEvents.MAGMA_CUBE_DEATH),
        BREEZE(SoundEvents.BREEZE_IDLE_GROUND, SoundEvents.BREEZE_HURT, SoundEvents.BREEZE_DEATH),
        VEX(SoundEvents.VEX_AMBIENT, SoundEvents.VEX_HURT, SoundEvents.VEX_DEATH),
        BLAZE(SoundEvents.BLAZE_AMBIENT, SoundEvents.BLAZE_HURT, SoundEvents.BLAZE_DEATH),
        PHANTOM(SoundEvents.PHANTOM_AMBIENT, SoundEvents.PHANTOM_HURT, SoundEvents.PHANTOM_DEATH),
        BEE(SoundEvents.BEE_LOOP_AGGRESSIVE, SoundEvents.BEE_HURT, SoundEvents.BEE_DEATH),
        GUARDIAN(SoundEvents.GUARDIAN_AMBIENT, SoundEvents.GUARDIAN_HURT, SoundEvents.GUARDIAN_DEATH),
        ELDER(SoundEvents.ELDER_GUARDIAN_AMBIENT, SoundEvents.ELDER_GUARDIAN_HURT, SoundEvents.ELDER_GUARDIAN_DEATH),
        GHAST(SoundEvents.GHAST_AMBIENT, SoundEvents.GHAST_HURT, SoundEvents.GHAST_DEATH),
        WITHER(SoundEvents.WITHER_AMBIENT, SoundEvents.WITHER_HURT, SoundEvents.WITHER_DEATH),
        SERPENT(SoundEvents.ELDER_GUARDIAN_AMBIENT_LAND, SoundEvents.SHULKER_HURT, SoundEvents.ELDER_GUARDIAN_DEATH_LAND),
        LISTENER(null, SoundEvents.WARDEN_HURT, SoundEvents.WARDEN_DEATH),
        BELL(null, SoundEvents.IRON_GOLEM_DAMAGE, SoundEvents.IRON_GOLEM_DEATH),
        LANTERN(SoundEvents.WANDERING_TRADER_AMBIENT, SoundEvents.WANDERING_TRADER_HURT, SoundEvents.WANDERING_TRADER_DEATH),
        SHADE(SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM, SoundEvents.WARDEN_HURT, SoundEvents.WITHER_DEATH);

        public final SoundEvent ambient;
        public final SoundEvent hurt;
        public final SoundEvent death;

        Voice(SoundEvent ambient, SoundEvent hurt, SoundEvent death) {
            this.ambient = ambient;
            this.hurt = hurt;
            this.death = death;
        }
    }

    @SuppressWarnings("unused")
    private static SoundEvent v(Holder<SoundEvent> h) {
        return h.value();
    }
}
