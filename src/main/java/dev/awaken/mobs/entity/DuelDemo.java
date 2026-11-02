package dev.awaken.mobs.entity;

import dev.awaken.core.api.MobRole;
import dev.awaken.core.api.Strikes;
import dev.awaken.mobs.Mobs;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.mobs.def.MobDef;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The duel on cue, for tours and a look in game (admin: {@code /awaken bosses duel ...}, SPEC §46.3): a stage where you
 * stand with a duel boss at its middle facing a still stand-in 4 blocks south, the camera west of them looking east; then
 * a phrase played, a blow deflected (the limb's recoil, or the reel off a phrase's last blow) and the stance broken, each
 * now, so a tour's waits and a frozen tick catch them. A creative player is never its target.
 */
public final class DuelDemo {
    private DuelDemo() {}

    public static final List<String> STEPS = List.of("stage", "play", "deflect", "break");

    private static @Nullable AwakenBoss boss;
    private static @Nullable Pig standIn;

    /** {@code stage <boss>}, {@code play <move> [then ...]}, {@code deflect}, {@code break}; the reason when it can't. */
    public static @Nullable String run(ServerPlayer p, String step, String args) {
        if (step.equals("stage")) return stage(p, args.trim());
        AwakenBoss b = boss;
        if (b == null || !b.isAlive() || b.level() != p.level()) return "Build the stage first: /awaken bosses duel stage <boss>";
        switch (step) {
            case "play" -> {
                List<String> moves = Arrays.stream(args.trim().split("\\s+")).filter(s -> !s.isEmpty()).toList();
                if (moves.isEmpty()) return "Name a move, and the moves of its string after it.";
                if (standIn != null && standIn.isAlive()) b.setTarget(standIn);
                if (!b.playKitString(moves.getFirst(), moves.subList(1, moves.size()))) return b.def().id + " has no move " + moves.getFirst();
            }
            case "deflect" -> b.deflected(Strikes.Kind.LIGHT);
            case "break" -> {
                int stance = b.duelStance() > 0 ? b.duelStance() : 100;
                Strikes.posture(b, stance, p);
            }
            default -> {
                return "No step called " + step + ". Steps: " + String.join(", ", STEPS);
            }
        }
        return null;
    }

    /** A 41-block floor where you stand, the boss at its middle facing south at a still pig, you west looking across. */
    private static @Nullable String stage(ServerPlayer p, String id) {
        MobDef def = Bestiary.get(id);
        if (def == null) return "No creature called " + id;
        ServerLevel level = p.level();
        BlockPos c = p.blockPosition();
        for (int x = -20; x <= 20; x++) {
            for (int z = -20; z <= 20; z++) {
                level.setBlock(c.offset(x, -1, z), Blocks.POLISHED_ANDESITE.defaultBlockState(), Block.UPDATE_CLIENTS);
                for (int y = 0; y <= 14; y++) level.setBlock(c.offset(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        Vec3 centre = Vec3.atBottomCenterOf(c);
        if (boss != null) boss.discard();
        if (standIn != null) standIn.discard();
        AwakenMob m = Mobs.spawn(level, def, centre, 10, new MobRole(MobRole.Role.BOSS, 10, def.theme.id, Optional.of(def.bossId()), ""), Set.of());
        if (!(m instanceof AwakenBoss b)) {
            if (m != null) m.discard();
            return id + " is not a boss";
        }
        boss = b;
        b.setPersistenceRequired();
        b.snapTo(centre.x, centre.y, centre.z, 0F, 0F);
        b.setYHeadRot(0F);
        b.setYBodyRot(0F);
        Pig pig = EntityTypes.PIG.create(level, EntitySpawnReason.COMMAND);
        if (pig != null) {
            pig.snapTo(centre.x, centre.y, centre.z + 4, 180F, 0F);
            pig.setNoAi(true);
            pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000); // it stands in for you (an invulnerable one is no target)
            pig.setHealth(1000);
            pig.setPersistenceRequired();
            level.addFreshEntity(pig);
            standIn = pig;
            b.setTarget(pig);
        }
        // the camera: west of the two, raised, looking east across the gap between them
        double back = Math.max(13, b.getBbHeight() * 1.6);
        Vec3 eyeAt = centre.add(-back, Math.min(6, 1.5 + b.getBbHeight() * 0.35), 2), look = centre.add(0, b.getBbHeight() * 0.45, 2);
        Vec3 d = look.subtract(eyeAt);
        float yaw = (float) (Math.toDegrees(Math.atan2(-d.x, d.z)));
        float pitch = (float) (-Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z))));
        if (p.getAbilities().mayfly) {
            p.getAbilities().flying = true; // held in the air where the camera is
            p.onUpdateAbilities();
        }
        p.teleportTo(level, eyeAt.x, eyeAt.y - p.getEyeHeight(), eyeAt.z, Set.<Relative>of(), yaw, pitch, true);
        return null;
    }
}
