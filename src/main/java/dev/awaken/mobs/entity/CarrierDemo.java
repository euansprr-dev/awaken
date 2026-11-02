package dev.awaken.mobs.entity;

import dev.awaken.core.api.MobRole;
import dev.awaken.core.api.Strikes;
import dev.awaken.mobs.Mobs;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.mobs.def.BoltKind;
import dev.awaken.mobs.def.MobDef;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The carriers on cue, for tours and a look in game (admin: {@code /awaken bosses carriers <kind>}, SPEC §46.2): a flat
 * stage where you stand, a still maker 9 blocks to its north, and one carrier at a time sent south across the view, so
 * a frozen tick ({@code /tick freeze}) catches it mid-flight. Nothing is hurt: the target is a point, and creatures
 * take no aim at a creative player.
 */
public final class CarrierDemo {
    private CarrierDemo() {}

    public static final List<String> KINDS = List.of("stage", "wave", "volley", "lob", "ground", "line", "wedge", "ring", "field", "pull",
            "shot_light", "shot_heavy", "shot_red", "fan");

    private static @Nullable AwakenMob maker;
    private static Vec3 centre = Vec3.ZERO;

    /** Runs one; the reason when it can't. */
    public static @Nullable String run(ServerPlayer p, String kind) {
        ServerLevel level = p.level();
        if (kind.equals("stage")) return stage(p);
        AwakenMob m = maker;
        if (m == null || !m.isAlive() || m.level() != level) return "Build the stage first: /awaken bosses carriers stage";
        Vec3 at = centre.add(0, 0, 4), from = m.position(), eye = m.getEyePosition();
        switch (kind) {
            case "wave" -> Carriers.wave(level, m, from, 1F, 9F, 0F, BoltKind.PLAIN, null, 0);
            case "volley" -> Carriers.volley(level, m, from.add(0, 2, 0), List.of(at, at.add(3.5, 0, -3), at.add(-3.5, 0, -2)), 2.2F, 0F,
                    BoltKind.PLAIN, null);
            case "lob" -> Carriers.lob(level, m, from.add(0, 2, 0), at, 0F, BoltKind.SPORE, null, 2F, 200, 0F);
            case "ground" -> Carriers.ground(level, m, Strikes.Kind.PERILOUS, from, at, 13F, 0F, BoltKind.PLAIN, null);
            case "line" -> Carriers.line(level, m, Strikes.Kind.HEAVY, eye, at.add(0, 1.2, 0), 60, 0.45F);
            case "wedge" -> Carriers.wedge(level, m, Strikes.Kind.PERILOUS, from, at.subtract(from), 9F, 60);
            case "ring" -> Carriers.ring(level, m, Strikes.Kind.HEAVY, at, 2.5F, 80);
            case "field" -> Carriers.field(level, m, at, 2F, 200);
            case "pull" -> Carriers.pull(level, m, from, 8F, 0F, BoltKind.PLAIN, null, 0);
            case "shot_light" -> shoot(level, m, Strikes.Kind.LIGHT, eye, at.add(0, 1.2, 0));
            case "shot_heavy" -> shoot(level, m, Strikes.Kind.HEAVY, eye, at.add(0, 1.2, 0));
            case "shot_red" -> shoot(level, m, Strikes.Kind.PERILOUS, eye, at.add(0, 1.2, 0));
            case "fan" -> {
                // the three classes side by side, fired together: their speeds part them as they fly
                shoot(level, m, Strikes.Kind.LIGHT, eye.add(-1.2, 0, 0), at.add(-4, 1.2, 0));
                shoot(level, m, Strikes.Kind.HEAVY, eye, at.add(0, 1.2, 0));
                shoot(level, m, Strikes.Kind.PERILOUS, eye.add(1.2, 0, 0), at.add(4, 1.2, 0));
            }
            default -> {
                return "No carrier called " + kind + ". Kinds: " + String.join(", ", KINDS);
            }
        }
        return null;
    }

    private static void shoot(ServerLevel level, AwakenMob m, Strikes.Kind cls, Vec3 from, Vec3 to) {
        Strikes.deliver(m, cls, () -> {
            AwBolt.shoot(level, m, from, to.subtract(from), BoltKind.PLAIN, 0F, null, Abilities.shotSpeed());
            return true;
        });
    }

    /** A 33-block floor where you stand, the maker at its north end, you at its west edge looking across. */
    private static @Nullable String stage(ServerPlayer p) {
        ServerLevel level = p.level();
        BlockPos c = p.blockPosition();
        for (int x = -16; x <= 16; x++) {
            for (int z = -16; z <= 16; z++) {
                level.setBlock(c.offset(x, -1, z), Blocks.POLISHED_ANDESITE.defaultBlockState(), Block.UPDATE_CLIENTS);
                for (int y = 0; y <= 10; y++) level.setBlock(c.offset(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        centre = Vec3.atBottomCenterOf(c);
        if (maker != null) maker.discard();
        MobDef def = Bestiary.get("toll_ghoul");
        maker = Mobs.spawn(level, def, centre.add(0, 0, -9), 5, new MobRole(MobRole.Role.NORMAL, 5, def.theme.id, Optional.empty(), ""), Set.of());
        if (maker == null) return "The maker didn't spawn.";
        maker.setNoAi(true);
        maker.setPersistenceRequired();
        maker.setYRot(0F);
        maker.setYHeadRot(0F);
        maker.setYBodyRot(0F);
        // the camera: west of the lane, raised, looking east across it
        Vec3 eyeAt = centre.add(-14, 4.5, -2.5), look = centre.add(0, 1, -2.5);
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
