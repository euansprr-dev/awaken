package dev.awaken.mobs.entity;

import dev.awaken.core.api.MobRole;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.mobs.Mobs;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.mobs.def.MobDef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * GameTests for the P2 creatures' talents, called directly (the goals only decide when): a bogwick draws poison out,
 * a dart frog's skin poisons a bare hand, a petalwing's flower, a gloam snail's lichen in the dark, a toad eating a
 * small slime, an owl marking a monster, a marmot's find, a honey bear shaking a full nest, a shroomlet planting in the
 * shade, a startled basilisk standing on water, a kite's feather. Registered by the world module's fauna tests.
 */
public final class WildTalentTests {
    private WildTalentTests() {}

    /** Above the test origin, clear of what other tests leave behind (like the fauna tests). */
    static final int UP = 65;

    static FaunaMob spawn(ServerLevel level, String id, BlockPos at) {
        MobDef d = Bestiary.get(id);
        AwakenMob m = Mobs.spawn(level, d, Vec3.atBottomCenterOf(at), 1, new MobRole(d.tier.role, 1, d.theme.id, Optional.empty(), ""), Set.of());
        if (!(m instanceof FaunaMob f)) throw new IllegalStateException(id + " did not spawn as a fauna creature");
        f.setNoAi(true);
        return f;
    }

    static void floor(ServerLevel level, BlockPos centre, int r, net.minecraft.world.level.block.Block ground) {
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            level.setBlock(centre.offset(dx, -1, dz), ground.defaultBlockState(), 3);
            for (int dy = 0; dy <= 3; dy++) level.setBlock(centre.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 3);
        }
    }

    /** A sealed stone room around pos with a floor of {@code ground} (dark inside once the light engine catches up). */
    static void room(ServerLevel level, BlockPos pos, net.minecraft.world.level.block.Block ground) {
        for (int dx = -3; dx <= 3; dx++) for (int dy = -1; dy <= 3; dy++) for (int dz = -3; dz <= 3; dz++) {
            boolean shell = Math.abs(dx) == 3 || Math.abs(dz) == 3 || dy == -1 || dy == 3;
            level.setBlock(pos.offset(dx, dy, dz), shell ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
        }
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) level.setBlock(pos.offset(dx, -1, dz), ground.defaultBlockState(), 3);
    }

    /** Air in a box: the test spot is shared, so tests that build put it back as they found it. */
    static void wipe(ServerLevel level, BlockPos a, BlockPos b) {
        for (BlockPos q : BlockPos.betweenClosed(a, b)) {
            if (!level.getBlockState(q).isAir()) level.setBlock(q, Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    static boolean dark(ServerLevel level, BlockPos pos) {
        return level.getBrightness(LightLayer.SKY, pos) == 0 && level.getBrightness(LightLayer.BLOCK, pos) == 0;
    }

    @SuppressWarnings("removal")
    public static void register() {
        AwakenTests.registerIsolated("fauna_p2_talents_day", 200, h -> {
            ServerLevel level = h.getLevel();
            BlockPos at = h.absolutePos(new BlockPos(0, UP, 0));
            floor(level, at, 6, Blocks.GRASS_BLOCK);
            ServerPlayer p = h.makeMockServerPlayerInLevel();
            p.setGameMode(GameType.SURVIVAL);
            p.connection.markClientLoaded();
            p.setPos(Vec3.atBottomCenterOf(at.south(2)));
            List<FaunaMob> made = new ArrayList<>();
            try {
                // a bogwick draws the poison out of a player (and rests ten seconds before the next cure)
                FaunaMob bog = spawn(level, "bogwick", at.north(1));
                made.add(bog);
                p.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 0));
                h.assertTrue(WildGoals.cleanse(bog, level, p) && !p.hasEffect(MobEffects.POISON), "the bogwick cured the poison");
                p.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 0));
                h.assertTrue(!WildGoals.cleanse(bog, level, p), "one cure at a time");
                p.removeEffect(MobEffects.POISON);
                // a dart frog poisons a bare hand, not a stick
                FaunaMob frog = spawn(level, "dart_frog", at.east(3));
                made.add(frog);
                frog.hurtServer(level, level.damageSources().playerAttack(p), 0.5F);
                h.assertTrue(p.hasEffect(MobEffects.POISON), "bare hands on venomous skin");
                p.removeEffect(MobEffects.POISON);
                p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
                frog.damageCooldownTime = 0;
                frog.hurtServer(level, level.damageSources().playerAttack(p), 0.5F);
                h.assertTrue(!p.hasEffect(MobEffects.POISON), "a stick keeps you safe");
                p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                // a petalwing's flower on open grass
                FaunaMob wing = spawn(level, "petalwing", at.west(3).above());
                made.add(wing);
                BlockPos lawn = at.west(4);
                h.assertTrue(WildGoals.bloom(level, wing, lawn), "a flower opened");
                h.assertTrue(level.getBlockState(lawn).is(net.minecraft.tags.BlockTags.SMALL_FLOWERS), "it is a flower");
                h.assertTrue(!WildGoals.bloom(level, wing, lawn), "not twice in one spot");
                // a bog toad eats a small slime: it is gone and its slime ball drops
                FaunaMob toad = spawn(level, "bog_toad", at.north(4));
                made.add(toad);
                Slime slime = EntityTypes.SLIME.create(level, EntitySpawnReason.MOB_SUMMONED);
                slime.setSize(1, true);
                slime.snapTo(at.north(5).getX() + 0.5, at.getY(), at.north(5).getZ() + 0.5, 0, 0);
                level.addFreshEntity(slime);
                h.assertTrue(WildGoals.smallCube(level, toad, 8.0) == slime, "the toad finds the small slime");
                WildTalents.gulp(toad, level, slime);
                h.assertTrue(slime.isRemoved(), "eaten");
                h.assertTrue(!level.getEntitiesOfClass(ItemEntity.class, new AABB(at.north(5)).inflate(3), e -> e.getItem().is(Items.SLIME_BALL)).isEmpty(), "a slime ball");
                // a marmot's find on gravel
                floor(level, at.south(5), 1, Blocks.GRAVEL);
                FaunaMob marmot = spawn(level, "scree_marmot", at.south(5));
                made.add(marmot);
                h.assertTrue(WildTalents.diggable(level.getBlockState(at.south(5).below()), true), "gravel is scree");
                ItemStack find = WildTalents.dig(marmot, level);
                h.assertTrue(!find.isEmpty(), "the marmot dug something up: " + find);
                // a honey bear shakes a full bee nest: honeycomb falls, the nest is empty
                BlockPos nest = at.east(5).above(3);
                level.setBlock(nest, Blocks.BEE_NEST.defaultBlockState().setValue(BeehiveBlock.HONEY_LEVEL, BeehiveBlock.MAX_HONEY_LEVELS), 3);
                FaunaMob bear = spawn(level, "honey_bear", at.east(5));
                made.add(bear);
                h.assertTrue(WildGoals.shake(level, bear, nest) == 2, "two honeycomb");
                h.assertTrue(level.getBlockState(nest).getValue(BeehiveBlock.HONEY_LEVEL) == 0, "the nest is emptied");
                // a startled basilisk stands on water; a calm one doesn't
                FaunaMob lizard = spawn(level, "crested_basilisk", at.west(5));
                made.add(lizard);
                h.assertTrue(!lizard.canStandOnFluid(Fluids.WATER.defaultFluidState()), "calm: it swims");
                lizard.scare(p.position(), 60);
                h.assertTrue(lizard.canStandOnFluid(Fluids.WATER.defaultFluidState()), "startled: it runs across the water");
                // a kite sheds a feather near a player now and then
                FaunaMob kite = spawn(level, "peak_kite", at.above(3));
                made.add(kite);
                boolean shed = false;
                for (int i = 0; i < 400 && !shed; i++) {
                    kite.talentCooldown = 0;
                    shed = WildTalents.shedFeather(kite, level);
                }
                h.assertTrue(shed, "a feather fell");
                h.assertTrue(!level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(8),
                        e -> BuiltInRegistries.ITEM.getKey(e.getItem().getItem()).getPath().equals("crag_feather")).isEmpty(), "a crag feather");
                // a sporeback snail finds small mushrooms to rest by (out in the open, as on a mushroom island)
                BlockPos patch = at.south(4).west(4);
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                    level.setBlock(patch.offset(dx, -1, dz), Blocks.PODZOL.defaultBlockState(), 3);
                    level.setBlock(patch.offset(dx, 0, dz), Blocks.BROWN_MUSHROOM.defaultBlockState(), 3);
                }
                FaunaMob spore = spawn(level, "sporeback_snail", at.south(2).west(2));
                made.add(spore);
                BlockPos found = null;
                for (int i = 0; i < 60 && found == null; i++) found = spore.findCrop(level);
                h.assertTrue(found != null && level.getBlockState(found).getBlock() instanceof net.minecraft.world.level.block.MushroomBlock,
                        "the sporeback snail finds a mushroom");
                // every P2 talent has its goal or its tick: creatures spawn with their goals without error
                for (MobDef d : Bestiary.fauna()) {
                    if (d.theme.ordinal() <= dev.awaken.mobs.def.Theme.FROSTWILD.ordinal()) continue;
                    FaunaMob m = spawn(level, d.id, at.above(8));
                    made.add(m);
                }
            } finally {
                made.forEach(FaunaMob::discard);
                level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(12)).forEach(ItemEntity::discard);
                wipe(level, at.offset(-7, -1, -7), at.offset(7, 9, 7));
            }
            h.succeed();
        });

        AwakenTests.registerIsolated("fauna_p2_talents_dark", 1200, h -> {
            ServerLevel level = h.getLevel();
            BlockPos snailRoom = h.absolutePos(new BlockPos(-6, UP, 0));
            BlockPos owlRoom = h.absolutePos(new BlockPos(6, UP, 0));
            BlockPos sporeRoom = h.absolutePos(new BlockPos(0, UP, 8));
            room(level, snailRoom, Blocks.MOSS_BLOCK);
            room(level, owlRoom, Blocks.STONE);
            room(level, sporeRoom, Blocks.PODZOL);
            ServerPlayer p = h.makeMockServerPlayerInLevel();
            p.setPos(Vec3.atBottomCenterOf(owlRoom.east(1)));
            List<net.minecraft.world.entity.Entity> made = new ArrayList<>();
            h.startSequence()
                    // the light engine runs on its own threads, behind the block changes: wait for the light itself
                    .thenWaitUntil(() -> AwakenTests.await(h, () -> dark(level, snailRoom) && dark(level, owlRoom) && dark(level, sporeRoom), "the rooms are dark"))
                    .thenExecute(() -> {
                        try {
                            // a gloam snail leaves glow lichen where it rests in the dark, never two patches side by side
                            FaunaMob snail = spawn(level, "gloam_snail", snailRoom);
                            made.add(snail);
                            snail.setOnGround(true);
                            h.assertTrue(WildTalents.trail(snail, level), "lichen left behind");
                            h.assertTrue(level.getBlockState(snailRoom).is(Blocks.GLOW_LICHEN), "glow lichen on the floor");
                            snail.talentCooldown = 0;
                            snail.setPos(Vec3.atBottomCenterOf(snailRoom.east(1)));
                            h.assertTrue(!WildTalents.trail(snail, level), "patches keep their distance");
                            // an owl marks the monster nearest its player
                            FaunaMob owl = spawn(level, "hollow_owl", owlRoom.west(1));
                            made.add(owl);
                            Zombie z = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.MOB_SUMMONED);
                            z.snapTo(owlRoom.getX() + 0.5, owlRoom.getY(), owlRoom.north(2).getZ() + 0.5, 0, 0);
                            z.setNoAi(true);
                            level.addFreshEntity(z);
                            made.add(z);
                            h.assertTrue(WildTalents.watch(owl, level) == z, "the owl stares at the zombie");
                            h.assertTrue(z.hasEffect(MobEffects.GLOWING), "and it glows");
                            // a shroomlet plants a small mushroom in the shade
                            FaunaMob shroom = spawn(level, "shroomlet", sporeRoom);
                            made.add(shroom);
                            boolean planted = false;
                            for (int i = 0; i < 30 && !planted; i++) {
                                shroom.talentCooldown = 0;
                                planted = WildTalents.plantMushroom(shroom, level);
                            }
                            h.assertTrue(planted, "a mushroom planted");
                        } finally {
                            made.forEach(net.minecraft.world.entity.Entity::discard);
                            for (BlockPos c : List.of(snailRoom, owlRoom, sporeRoom)) wipe(level, c.offset(-3, -1, -3), c.offset(3, 3, 3));
                        }
                    })
                    .thenSucceed();
        });
    }
}
