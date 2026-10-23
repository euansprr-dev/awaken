package dev.awaken.gear;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.awaken.core.AwakenCommands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Locale;

/**
 * {@code /awaken gear}: the sets you wear and what they do now. Admin: {@code kit <E|D|C|B>} (wear and hold a whole
 * tier) and {@code lineup [clear]} (every tier on mannequins in a row, for screenshots and look tests).
 */
final class GearCommands {
    private GearCommands() {}

    static final String LINEUP_TAG = "awaken_gear_lineup";

    static void register() {
        AwakenCommands.add((root, ctx) -> {
            var gear = Commands.literal("gear").executes(GearCommands::status);
            gear.then(Commands.literal("kit").requires(AwakenCommands.admin())
                    .then(Commands.argument("tier", StringArgumentType.word())
                            .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("E", "D", "C", "B"), b))
                            .executes(GearCommands::kit)));
            gear.then(Commands.literal("lineup").requires(AwakenCommands.admin()).executes(GearCommands::lineup)
                    .then(Commands.literal("clear").executes(c -> clear(c.getSource().getPlayerOrException()))));
            root.then(gear);
        });
    }

    private static int status(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        int shown = 0;
        for (HunterTier tier : HunterTier.values()) {
            int n = SetBonus.count(p, tier);
            if (n == 0) continue;
            shown++;
            c.getSource().sendSuccess(() -> Component.literal(tier.setName + " (" + tier.rank.label + ") · " + n + "/4 worn").withColor(tier.rank.colour), false);
            for (SetBonus.Line line : SetBonus.lines(tier)) {
                boolean on = n >= line.pieces();
                c.getSource().sendSuccess(() -> Component.literal((on ? "  ✔ " : "  · ") + line.pieces() + ": " + line.text())
                        .withColor(on ? HunterGearItem.LIGHT : HunterGearItem.GREY), false);
            }
        }
        double focus = GearCombat.bestFocus(p);
        if (focus > 0) {
            shown++;
            c.getSource().sendSuccess(() -> Component.literal("Focus in hand · powers +" + Math.round(focus * 100) + "%"), false);
        }
        if (SetBonus.cornered(p)) c.getSource().sendSuccess(() -> Component.literal("Cornered: you hit harder"), false);
        if (shown == 0) {
            c.getSource().sendSuccess(() -> Component.literal("No Hunter gear on you. Creature hide, bone and essence make the first set: "
                    + "see the recipe book at a crafting table."), false);
        }
        return shown;
    }

    private static HunterTier tierArg(CommandContext<CommandSourceStack> c) {
        String s = StringArgumentType.getString(c, "tier").toUpperCase(Locale.ROOT);
        for (HunterTier t : HunterTier.values()) if (t.name().equals(s) || t.id.equalsIgnoreCase(s)) return t;
        return HunterTier.E;
    }

    /** Wears the whole tier: armour on, blade in the main hand, focus in the off hand, mattock in the pack. */
    private static int kit(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        HunterTier tier = tierArg(c);
        equip(p, tier);
        SetBonus.refresh(p);
        c.getSource().sendSuccess(() -> Component.literal("Wearing " + tier.setName + " (" + tier.rank.label + ")"), false);
        return 1;
    }

    static void equip(ServerPlayer p, HunterTier tier) {
        for (Piece piece : Piece.ARMOUR) swap(p, piece.slot(), GearItems.stack(tier, piece));
        swap(p, EquipmentSlot.MAINHAND, GearItems.stack(tier, Piece.BLADE));
        swap(p, EquipmentSlot.OFFHAND, GearItems.stack(tier, Piece.FOCUS));
        p.getInventory().placeItemBackInInventory(GearItems.stack(tier, Piece.MATTOCK), Prediction.SERVER_ONLY);
    }

    private static void swap(ServerPlayer p, EquipmentSlot slot, ItemStack stack) {
        ItemStack old = p.getItemBySlot(slot);
        p.setItemSlot(slot, stack);
        if (!old.isEmpty()) p.getInventory().placeItemBackInInventory(old, Prediction.SERVER_ONLY);
    }

    /**
     * Two mannequins per tier, six blocks ahead, facing you: the first holds the blade and the focus, the second the
     * mattock. Names over the first of each pair.
     */
    private static int lineup(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        clear(p);
        ServerLevel level = p.level();
        float yaw = p.getYRot();
        double fx = -Mth.sin(yaw * Mth.DEG_TO_RAD), fz = Mth.cos(yaw * Mth.DEG_TO_RAD);
        double rx = -fz, rz = fx; // to the player's right
        double cx = p.getX() + fx * 6, cz = p.getZ() + fz * 6;
        HunterTier[] tiers = HunterTier.values();
        int n = tiers.length * 2;
        int made = 0;
        for (int i = 0; i < n; i++) {
            HunterTier tier = tiers[i / 2];
            boolean first = i % 2 == 0;
            double off = (i - (n - 1) / 2.0) * 1.4;
            double x = cx + rx * off, z = cz + rz * off;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
            Mannequin m = mannequin(level, x, y, z, yaw + 180);
            if (m == null) continue;
            for (Piece piece : Piece.ARMOUR) m.setItemSlot(piece.slot(), GearItems.stack(tier, piece));
            m.setItemSlot(EquipmentSlot.MAINHAND, GearItems.stack(tier, first ? Piece.BLADE : Piece.MATTOCK));
            if (first) {
                m.setItemSlot(EquipmentSlot.OFFHAND, GearItems.stack(tier, Piece.FOCUS));
                m.setCustomName(Component.literal(tier.setName + " · " + tier.rank.label).withColor(tier.rank.colour));
                m.setCustomNameVisible(true);
            }
            m.addTag(LINEUP_TAG);
            level.addFreshEntity(m);
            made++;
        }
        int count = made;
        c.getSource().sendSuccess(() -> Component.literal(count + " mannequins wear Hunter gear (/awaken gear lineup clear)"), false);
        return made;
    }

    private static Mannequin mannequin(ServerLevel level, double x, double y, double z, float yaw) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("immovable", true);
        tag.putBoolean("hide_description", true);
        tag.putBoolean("Invulnerable", true);
        Entity e = EntityType.loadEntityRecursive(EntityTypes.MANNEQUIN, tag, level, EntitySpawnReason.COMMAND, ent -> {
            ent.snapTo(x, y, z, yaw, 0f);
            return ent;
        });
        if (!(e instanceof Mannequin m)) return null;
        m.setYBodyRot(yaw);
        m.setYHeadRot(yaw);
        return m;
    }

    private static int clear(ServerPlayer p) {
        int n = 0;
        for (Entity e : p.level().getEntities((Entity) null, new AABB(p.blockPosition()).inflate(48), e -> e.entityTags().contains(LINEUP_TAG))) {
            e.discard();
            n++;
        }
        return n;
    }
}
