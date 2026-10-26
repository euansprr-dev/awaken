package dev.awaken.mobs.def;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * The body a creature wears. Most are vanilla models re-tinted with our own skin and glow layers on top (the vanilla
 * texture is referenced, never copied); the last few are our own LayerDefinitions with generated textures. The client
 * maps each value to a renderer in {@code mobs.client.AwRenderers}; this common enum only holds what the server needs.
 * The overworld creatures' bodies (moth, stag, tortoise, ray, scarab; P2: lizard, snail, mushroom, marmot) are ours;
 * allay, parrot, fox (P2: frog, goat, ocelot) are vanilla bodies whose every face our skin layer repaints.
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
    LISTENER("", Move.GROUND, 0.8F, 2.4F, Voice.LISTENER),
    BELL("", Move.GROUND, 1.4F, 3.0F, Voice.BELL),
    LANTERN("", Move.GROUND, 0.8F, 2.0F, Voice.LANTERN),
    SHADE("", Move.FLY, 0.9F, 2.8F, Voice.SHADE),
    // the Awakened layer (overworld creatures): our own bodies...
    MOTH("", Move.FLY, 0.6F, 0.5F, Voice.MOTH),
    STAG("", Move.GROUND, 0.9F, 1.6F, Voice.STAG),
    TORTOISE("", Move.GROUND, 0.9F, 0.7F, Voice.TORTOISE),
    RAY("", Move.FLY, 1.0F, 0.35F, Voice.RAY),
    SCARAB("", Move.GROUND, 0.45F, 0.3F, Voice.SCARAB),
    // ...and vanilla animal bodies repainted by our skin layer
    ALLAY("allay/allay", Move.FLY, 0.35F, 0.6F, Voice.ALLAY),
    PARROT("parrot/parrot_grey", Move.GROUND, 0.4F, 0.6F, Voice.PARROT),
    FOX("fox/fox_snow", Move.GROUND, 0.6F, 0.7F, Voice.FOX),
    // P2 (the rest of the land): our own bodies where a vanilla one would look wrong...
    LIZARD("", Move.GROUND, 0.6F, 0.45F, Voice.LIZARD),
    SNAIL("", Move.GROUND, 0.5F, 0.6F, Voice.SNAIL),
    MUSHROOM("", Move.GROUND, 0.5F, 0.95F, Voice.MUSHROOM),
    MARMOT("", Move.GROUND, 0.5F, 0.5F, Voice.MARMOT),
    // ...and three more vanilla animal bodies, repainted by the skin layer
    FROG("frog/frog_temperate", Move.GROUND, 0.5F, 0.5F, Voice.FROG),
    GOAT("goat/goat", Move.GROUND, 0.9F, 1.3F, Voice.GOAT),
    OCELOT("cat/ocelot", Move.GROUND, 0.6F, 0.7F, Voice.OCELOT),
    // Hero bodies: each named beast's own rig (tools/art/specs/heroes.py -> mobs.client.anim.HeroData), plus a real owl
    // and a firefly. Sizes are the collision box before the def's scale; the beasts keep the boxes they had.
    BRIAR_SOW("", Move.GROUND, 1.83F, 1.83F, Voice.BOAR),
    DUNE_WYRM("", Move.FLY, 1.0F, 1.0F, Voice.SERPENT),
    OLD_ANTLER("", Move.GROUND, 1.43F, 2.53F, Voice.STAG),
    MIRE_MOTHER("", Move.GROUND, 1.5F, 1.5F, Voice.FROG),
    PLUMED_COIL("", Move.FLY, 1.0F, 1.0F, Voice.SERPENT),
    WEAVER_QUEEN("", Move.GROUND, 2.37F, 1.52F, Voice.SPIDER),
    EMPRESS_MOTH("", Move.FLY, 1.33F, 1.11F, Voice.MOTH),
    STONEHORN("", Move.GROUND, 1.46F, 2.11F, Voice.GOAT),
    MYCELORD("", Move.GROUND, 1.4F, 2.67F, Voice.MYCELORD),
    OWL("", Move.GROUND, 0.6F, 0.9F, Voice.OWL),
    FIREFLY("", Move.FLY, 0.5F, 0.42F, Voice.FIREFLY),
    // P3 (the water and the caves): every body is a rig of its own (tools/art/specs/fauna3.py -> mobs.client.anim.Fauna3Data),
    // drawn by mobs.client.anim.WildRenderer (swimmers pitch with their swim, a hanging lurker hangs).
    GLIMMERFIN("", Move.SWIM, 0.5F, 0.35F, Voice.FISH),
    BROOK_OTTER("", Move.SWIM, 0.6F, 0.55F, Voice.OTTER),
    BREATHBELL("", Move.SWIM, 0.7F, 0.8F, Voice.FISH),
    TIDESTEED("", Move.SWIM, 1.2F, 1.3F, Voice.OTTER),
    LANTERN_MAW("", Move.SWIM, 2.0F, 1.6F, Voice.ELDER),
    CHIME_CRICKET("", Move.GROUND, 0.5F, 0.4F, Voice.SCARAB),
    WICKLING("", Move.GROUND, 0.5F, 0.35F, Voice.LIZARD),
    DRIP_LURKER("", Move.GROUND, 0.8F, 1.1F, Voice.SPIDER),
    HUSHLING("", Move.GROUND, 0.6F, 0.75F, Voice.LISTENER),
    THOUSANDFOOT("", Move.GROUND, 2.0F, 1.1F, Voice.SPIDER),
    // W5 (Between the Gates): the Deep grown through the rock at a seep's heart; rooted, it never walks (mobs.entity.SeepHeart)
    SEEP_HEART("", Move.GROUND, 2.0F, 2.6F, Voice.WARDEN);

    /** Bodies drawn by a hero rig (mobs.client.anim.HeroRenderer). */
    public boolean hero() {
        return switch (this) {
            case BRIAR_SOW, DUNE_WYRM, OLD_ANTLER, MIRE_MOTHER, PLUMED_COIL, WEAVER_QUEEN, EMPRESS_MOTH, STONEHORN, MYCELORD, OWL, FIREFLY -> true;
            case GLIMMERFIN, BROOK_OTTER, BREATHBELL, TIDESTEED, LANTERN_MAW, CHIME_CRICKET, WICKLING, DRIP_LURKER, HUSHLING, THOUSANDFOOT, SEEP_HEART -> true;
            default -> false;
        };
    }

    /** SWIM: water creatures (their own navigation and move control, mobs.entity.WaterFauna); P3. */
    public enum Move { GROUND, HOP, FLY, SWIM }

    /** P3 bodies drawn by mobs.client.anim.WildRenderer (a hero rig with swim pitch and hold poses). */
    public boolean wild() {
        return switch (this) {
            case GLIMMERFIN, BROOK_OTTER, BREATHBELL, TIDESTEED, LANTERN_MAW, CHIME_CRICKET, WICKLING, DRIP_LURKER, HUSHLING, THOUSANDFOOT, SEEP_HEART -> true;
            default -> false;
        };
    }

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
        SHADE(SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM, SoundEvents.WARDEN_HURT, SoundEvents.WITHER_DEATH),
        MOTH(null, SoundEvents.BAT_HURT, SoundEvents.BAT_DEATH),
        STAG(SoundEvents.GOAT_AMBIENT, SoundEvents.GOAT_HURT, SoundEvents.GOAT_DEATH),
        TORTOISE(SoundEvents.TURTLE_AMBIENT_LAND, SoundEvents.TURTLE_HURT, SoundEvents.TURTLE_DEATH),
        RAY(SoundEvents.DOLPHIN_AMBIENT, SoundEvents.DOLPHIN_HURT, SoundEvents.DOLPHIN_DEATH),
        SCARAB(SoundEvents.SILVERFISH_AMBIENT, SoundEvents.SILVERFISH_HURT, SoundEvents.SILVERFISH_DEATH),
        ALLAY(SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM, SoundEvents.ALLAY_HURT, SoundEvents.ALLAY_DEATH),
        PARROT(SoundEvents.PARROT_AMBIENT, SoundEvents.PARROT_HURT, SoundEvents.PARROT_DEATH),
        FOX(SoundEvents.FOX_AMBIENT, SoundEvents.FOX_HURT, SoundEvents.FOX_DEATH),
        /** Night hunters of the dark: a fox's screech, a big wolf's growl. */
        STALKER(SoundEvents.FOX_SCREECH, SoundEvents.FOX_HURT, SoundEvents.FOX_DEATH),
        WISP(SoundEvents.WOLF_SOUNDS.get(net.minecraft.world.entity.animal.wolf.WolfSoundVariants.SoundSet.BIG).adultSounds().growlSound().value(),
                SoundEvents.WOLF_SOUNDS.get(net.minecraft.world.entity.animal.wolf.WolfSoundVariants.SoundSet.BIG).adultSounds().hurtSound().value(),
                SoundEvents.WOLF_SOUNDS.get(net.minecraft.world.entity.animal.wolf.WolfSoundVariants.SoundSet.BIG).adultSounds().deathSound().value()),
        BOAR(SoundEvents.HOGLIN_AMBIENT, SoundEvents.HOGLIN_HURT, SoundEvents.HOGLIN_DEATH),
        // P2 bodies and overrides
        LIZARD(SoundEvents.ARMADILLO_AMBIENT, SoundEvents.ARMADILLO_HURT, SoundEvents.ARMADILLO_DEATH),
        SNAIL(null, SoundEvents.SLIME_HURT_SMALL, SoundEvents.SLIME_DEATH_SMALL),
        MUSHROOM(null, SoundEvents.SLIME_HURT_SMALL, SoundEvents.SLIME_DEATH_SMALL),
        MARMOT(SoundEvents.RABBIT_AMBIENT, SoundEvents.RABBIT_HURT, SoundEvents.RABBIT_DEATH),
        FROG(SoundEvents.FROG_AMBIENT, SoundEvents.FROG_HURT, SoundEvents.FROG_DEATH),
        GOAT(SoundEvents.GOAT_AMBIENT, SoundEvents.GOAT_HURT, SoundEvents.GOAT_DEATH),
        OCELOT(SoundEvents.OCELOT_AMBIENT, SoundEvents.OCELOT_HURT, SoundEvents.OCELOT_DEATH),
        /** A swamp firefly: no buzzing loop. */
        FIREFLY(null, SoundEvents.BEE_HURT, SoundEvents.BEE_DEATH),
        /** A low flute note reads as a hoot at an owl's pitch. */
        OWL(SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundEvents.PARROT_HURT, SoundEvents.PARROT_DEATH),
        /** A soaring bird on a phantom's body must not shriek like one. */
        KITE(SoundEvents.PARROT_AMBIENT, SoundEvents.PARROT_HURT, SoundEvents.PARROT_DEATH),
        /** A big cat's growl. */
        PROWLER(SoundEvents.WOLF_SOUNDS.get(net.minecraft.world.entity.animal.wolf.WolfSoundVariants.SoundSet.BIG).adultSounds().growlSound().value(),
                SoundEvents.OCELOT_HURT, SoundEvents.OCELOT_DEATH),
        /** The walking mushroom's giant: a deep snuffle. */
        MYCELORD(SoundEvents.SNIFFER_IDLE, SoundEvents.SNIFFER_HURT, SoundEvents.SNIFFER_DEATH),
        // P3 fallbacks (every P3 creature has its own voice in sounds.json)
        FISH(null, SoundEvents.COD_HURT, SoundEvents.COD_DEATH),
        OTTER(SoundEvents.DOLPHIN_AMBIENT, SoundEvents.DOLPHIN_HURT, SoundEvents.DOLPHIN_DEATH);

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
