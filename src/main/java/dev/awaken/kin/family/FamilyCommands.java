package dev.awaken.kin.family;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.awaken.core.AwakenCommands;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The preview's admin tools (for the plan's look tour, tools/autopilot/family.txt):
 * {@code /awaken admin family place aya|dubu <pose> <yaw>} stands one where you stand, facing {@code yaw}, in a pose
 * ({@link FamilyPet#ACTS}); {@code pose aya|dubu <pose>} changes the pose of those within 64 blocks; {@code walk aya|dubu
 * <blocks> <speed>} sends them that far the way they face; {@code clear} takes every one within 64 away.
 */
final class FamilyCommands {
    private FamilyCommands() {}

    private static EntityType<FamilyPet> type(String who) {
        return "dubu".equals(who) ? FamilyContent.DUBU.get() : FamilyContent.AYA.get();
    }

    private static List<FamilyPet> near(ServerPlayer p, String who) {
        EntityType<FamilyPet> t = who == null ? null : type(who);
        return p.level().getEntitiesOfClass(FamilyPet.class, new AABB(p.blockPosition()).inflate(64), e -> t == null || e.getType() == t);
    }

    static void register() {
        AwakenCommands.add((root, ctx) -> {
            root.then(Commands.literal("admin").requires(AwakenCommands.admin()).then(Commands.literal("family")
                    .then(Commands.literal("place").then(who().then(pose().then(Commands.argument("yaw", FloatArgumentType.floatArg()).executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        ServerLevel level = p.level();
                        FamilyPet b = type(StringArgumentType.getString(c, "who")).create(level, EntitySpawnReason.COMMAND);
                        if (b == null) return 0;
                        float yaw = FloatArgumentType.getFloat(c, "yaw");
                        b.snapTo(p.getX(), p.getY(), p.getZ(), yaw, 0);
                        b.setYBodyRot(yaw);
                        b.setYHeadRot(yaw);
                        b.setPersistenceRequired();
                        level.addFreshEntity(b);
                        b.act(Math.max(0, FamilyPet.actOf(StringArgumentType.getString(c, "pose"))));
                        return 1;
                    })))))
                    .then(Commands.literal("pose").then(who().then(pose().executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        int a = Math.max(0, FamilyPet.actOf(StringArgumentType.getString(c, "pose")));
                        List<FamilyPet> all = near(p, StringArgumentType.getString(c, "who"));
                        for (FamilyPet b : all) b.act(a);
                        return all.size();
                    }))))
                    .then(Commands.literal("walk").then(who().then(Commands.argument("blocks", FloatArgumentType.floatArg(0, 48))
                            .then(Commands.argument("speed", FloatArgumentType.floatArg(0.2F, 3)).executes(c -> {
                                ServerPlayer p = c.getSource().getPlayerOrException();
                                float blocks = FloatArgumentType.getFloat(c, "blocks");
                                List<FamilyPet> all = near(p, StringArgumentType.getString(c, "who"));
                                for (FamilyPet b : all) {
                                    Vec3 ahead = Vec3.directionFromRotation(0, b.yBodyRot).scale(blocks);
                                    b.walkSpeed = FloatArgumentType.getFloat(c, "speed");
                                    b.walkTo = b.position().add(ahead);
                                }
                                return all.size();
                            })))))
                    .then(Commands.literal("clear").executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        List<FamilyPet> all = near(p, null);
                        for (FamilyPet b : all) b.discard();
                        return all.size();
                    }))));
        });
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<net.minecraft.commands.CommandSourceStack, String> who() {
        return Commands.argument("who", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("aya", "dubu"), b));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<net.minecraft.commands.CommandSourceStack, String> pose() {
        return Commands.argument("pose", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(List.of(FamilyPet.ACTS), b));
    }
}
