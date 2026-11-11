package dev.awaken.kin;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.awaken.core.AwakenCommands;
import dev.awaken.hearth.Hearth;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Admin tools for the Hearth's family (SPEC §69.10), for tours and for putting things right:
 * {@code /awaken admin family arrive} sends the two to your Hearth now; {@code use aya|dubu [sneak]} is a use on yours
 * (sneak asks it along or tells it to stay); {@code state} says where the record has them; and, for a body to look at
 * that belongs to nobody, {@code place aya|dubu <pose> <yaw>}, {@code pose aya|dubu <pose>}, {@code walk aya|dubu <blocks>
 * <speed>} and {@code clear} (those within 64 blocks).
 */
final class FamilyCommands {
    private FamilyCommands() {}

    private static EntityType<FamilyPet> type(String who) {
        return "dubu".equals(who) ? FamilyContent.DUBU.get() : FamilyContent.AYA.get();
    }

    /** The bodies near that belong to nobody (placed to be looked at). */
    private static List<FamilyPet> placed(ServerPlayer p, String who) {
        EntityType<FamilyPet> t = who == null ? null : type(who);
        return p.level().getEntitiesOfClass(FamilyPet.class, new AABB(p.blockPosition()).inflate(64), e -> e.owner == null && (t == null || e.getType() == t));
    }

    private static FamilyPet mine(ServerPlayer p, String who) {
        Hearth.HearthInfo h = Hearth.home(p).orElse(null);
        if (h == null) return null;
        FamilyWorld.Home home = FamilyWorld.get(p.level().getServer()).of(h.owner());
        return home == null ? null : FamilyService.body(p.level(), home.one(FamilyKind.of(who)));
    }

    static void register() {
        AwakenCommands.add((root, ctx) -> {
            root.then(Commands.literal("admin").requires(AwakenCommands.admin()).then(Commands.literal("family")
                    .then(Commands.literal("arrive").executes(c -> FamilyService.arriveNow(c.getSource().getPlayerOrException()) ? 1 : 0))
                    .then(Commands.literal("use").then(who().executes(c -> use(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "who"), false))
                            .then(Commands.literal("sneak").executes(c -> use(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "who"), true)))))
                    .then(Commands.literal("state").executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        Hearth.HearthInfo h = Hearth.home(p).orElse(null);
                        FamilyWorld.Home home = h == null ? null : FamilyWorld.get(p.level().getServer()).of(h.owner());
                        if (home == null) {
                            c.getSource().sendSuccess(() -> Component.literal("family: no record (no Hearth, or the clock has not seen it yet)"), false);
                            return 0;
                        }
                        for (FamilyKind k : FamilyKind.values()) {
                            FamilyWorld.One one = home.one(k);
                            FamilyPet b = FamilyService.body(p.level(), one);
                            String line = "family: " + k.id + " arrived=" + one.arrived + " state=" + one.state + " stage=" + home.stage
                                    + (b == null ? " body=none" : " body at " + b.blockPosition().toShortString() + " pose=" + FamilyPet.ACTS[b.act()]);
                            dev.awaken.Awaken.LOG.info("[family] {}", line);
                            c.getSource().sendSuccess(() -> Component.literal(line), false);
                        }
                        return 1;
                    }))
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
                        b.again(Math.max(0, FamilyPet.actOf(StringArgumentType.getString(c, "pose"))));
                        return 1;
                    })))))
                    .then(Commands.literal("pose").then(who().then(pose().executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        int a = Math.max(0, FamilyPet.actOf(StringArgumentType.getString(c, "pose")));
                        List<FamilyPet> all = placed(p, StringArgumentType.getString(c, "who"));
                        for (FamilyPet b : all) b.again(a);
                        return all.size();
                    }))))
                    .then(Commands.literal("walk").then(who().then(Commands.argument("blocks", FloatArgumentType.floatArg(0, 48))
                            .then(Commands.argument("speed", FloatArgumentType.floatArg(0.2F, 3)).executes(c -> {
                                ServerPlayer p = c.getSource().getPlayerOrException();
                                float blocks = FloatArgumentType.getFloat(c, "blocks");
                                List<FamilyPet> all = placed(p, StringArgumentType.getString(c, "who"));
                                for (FamilyPet b : all) {
                                    Vec3 ahead = Vec3.directionFromRotation(0, b.yBodyRot).scale(blocks);
                                    b.walkSpeed = FloatArgumentType.getFloat(c, "speed");
                                    b.walkTo = b.position().add(ahead);
                                }
                                return all.size();
                            })))))
                    .then(Commands.literal("clear").executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        List<FamilyPet> all = placed(p, null);
                        for (FamilyPet b : all) b.discard();
                        return all.size();
                    }))));
        });
    }

    private static int use(ServerPlayer p, String who, boolean sneak) {
        FamilyPet b = mine(p, who);
        if (b == null) return 0;
        FamilyService.used(p, b, sneak);
        return 1;
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<net.minecraft.commands.CommandSourceStack, String> who() {
        return Commands.argument("who", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("aya", "dubu"), b));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<net.minecraft.commands.CommandSourceStack, String> pose() {
        return Commands.argument("pose", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(List.of(FamilyPet.ACTS), b));
    }
}
