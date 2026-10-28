package dev.awaken.powers;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.awaken.core.AwakenCommands;
import dev.awaken.powers.content.PowerContent;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * /awaken powers list · /awaken powers equip &lt;slot&gt; &lt;id&gt; · /awaken admin power give &lt;id&gt; [variant]
 * · /awaken admin power learn &lt;id&gt; [variant] · /awaken admin power all · /awaken admin power reset
 * · /awaken admin power cast &lt;id&gt; · /awaken admin energy full
 */
final class PowersCommands {
    private PowersCommands() {}

    private static final SuggestionProvider<CommandSourceStack> POWERS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(PowerRegistry.all().stream().map(t -> t.path), builder);

    static void register() {
        AwakenCommands.add((root, buildCtx) -> {
            root.then(Commands.literal("powers")
                    .then(Commands.literal("list").executes(c -> list(c.getSource())))
                    .then(Commands.literal("equip")
                            .then(Commands.argument("slot", IntegerArgumentType.integer(1, Loadout.SLOTS))
                                    .then(Commands.argument("id", StringArgumentType.word()).suggests(POWERS).executes(c -> {
                                        ServerPlayer sp = c.getSource().getPlayerOrException();
                                        int slot = IntegerArgumentType.getInteger(c, "slot") - 1;
                                        boolean ok = Powers.equip(sp, slot, StringArgumentType.getString(c, "id"));
                                        c.getSource().sendSuccess(() -> Component.literal(ok ? "Equipped on " + Casting.slotLabel(slot)
                                                : "Can't put that there"), false);
                                        return ok ? 1 : 0;
                                    })))));

            var power = Commands.literal("power");
            power.then(Commands.literal("give").then(Commands.argument("id", StringArgumentType.word()).suggests(POWERS)
                    .executes(c -> give(c.getSource(), StringArgumentType.getString(c, "id"), 0))
                    .then(Commands.argument("variant", IntegerArgumentType.integer(0, 9))
                            .executes(c -> give(c.getSource(), StringArgumentType.getString(c, "id"), IntegerArgumentType.getInteger(c, "variant"))))));
            power.then(Commands.literal("learn").then(Commands.argument("id", StringArgumentType.word()).suggests(POWERS)
                    .executes(c -> learn(c.getSource(), StringArgumentType.getString(c, "id"), 0))
                    .then(Commands.argument("variant", IntegerArgumentType.integer(0, 9))
                            .executes(c -> learn(c.getSource(), StringArgumentType.getString(c, "id"), IntegerArgumentType.getInteger(c, "variant"))))));
            power.then(Commands.literal("all").executes(c -> {
                ServerPlayer sp = c.getSource().getPlayerOrException();
                int n = 0;
                for (PowerType t : PowerRegistry.all()) {
                    if (!Powers.owns(sp, t.id.toString())) {
                        Powers.learnOrLevel(sp, t.id, 0, false, false);
                        n++;
                    }
                }
                Powers.fillEnergy(sp);
                int learned = n;
                c.getSource().sendSuccess(() -> Component.literal("Learned " + learned + " powers (" + PowerRegistry.all().size() + " total)"), true);
                return n;
            }));
            power.then(Commands.literal("reset").executes(c -> {
                ServerPlayer sp = c.getSource().getPlayerOrException();
                Powers.resetCooldowns(sp);
                Powers.fillEnergy(sp);
                c.getSource().sendSuccess(() -> Component.literal("Cooldowns cleared, energy full"), false);
                return 1;
            }));
            power.then(Commands.literal("forget").then(Commands.argument("id", StringArgumentType.word()).suggests(POWERS).executes(c -> {
                ServerPlayer sp = c.getSource().getPlayerOrException();
                PowerType t = PowerRegistry.get(StringArgumentType.getString(c, "id"));
                if (t == null) return 0;
                Powers.forget(sp, t.id.toString());
                return 1;
            })));
            power.then(Commands.literal("cast").then(Commands.argument("id", StringArgumentType.word()).suggests(POWERS).executes(c -> {
                ServerPlayer sp = c.getSource().getPlayerOrException();
                PowerType t = PowerRegistry.get(StringArgumentType.getString(c, "id"));
                if (t == null) return 0;
                if (!Powers.owns(sp, t.id.toString())) Powers.learnOrLevel(sp, t.id, 0, false);
                PowerType.Result r = Casting.cast(sp, t, -1, false, sp.getYRot(), sp.getXRot());
                c.getSource().sendSuccess(() -> Component.literal(r.ok() ? "Cast " + t.name : "Failed: " + r.reason()), false);
                return r.ok() ? 1 : 0;
            })));
            // the ladder (SPEC 34.9): a power at a level on its key, ready to cast; and a level's moment again
            power.then(Commands.literal("showcase").then(Commands.argument("id", StringArgumentType.word()).suggests(POWERS)
                    .then(Commands.argument("level", IntegerArgumentType.integer(1, Loadout.MAX_LEVEL))
                            .executes(c -> showcase(c.getSource(), StringArgumentType.getString(c, "id"), IntegerArgumentType.getInteger(c, "level"), 0))
                            .then(Commands.argument("variant", IntegerArgumentType.integer(0, 9))
                                    .executes(c -> showcase(c.getSource(), StringArgumentType.getString(c, "id"), IntegerArgumentType.getInteger(c, "level"),
                                            IntegerArgumentType.getInteger(c, "variant")))))));
            power.then(Commands.literal("evolve").then(Commands.argument("id", StringArgumentType.word()).suggests(POWERS)
                    .then(Commands.argument("level", IntegerArgumentType.integer(2, Loadout.MAX_LEVEL)).executes(c -> {
                        ServerPlayer sp = c.getSource().getPlayerOrException();
                        PowerType t = PowerRegistry.get(StringArgumentType.getString(c, "id"));
                        if (t == null) return 0;
                        int level = IntegerArgumentType.getInteger(c, "level");
                        Powers.setLevel(sp, t, level - 1, 0);
                        Loadout.Owned o = Powers.loadout(sp).get(t.id.toString());
                        Powers.loadout(sp).owned().put(t.id.toString(), new Loadout.Owned(t.id.toString(), o == null ? 0 : o.variant(), level));
                        dev.awaken.powers.ladder.Evolution.moment(sp, t, level);
                        Powers.markDirty(sp);
                        return 1;
                    }))));
            var energy = Commands.literal("energy").then(Commands.literal("full").executes(c -> {
                Powers.fillEnergy(c.getSource().getPlayerOrException());
                c.getSource().sendSuccess(() -> Component.literal("Energy full"), false);
                return 1;
            }));
            root.then(Commands.literal("admin").requires(AwakenCommands.admin()).then(power).then(energy));
        });
    }

    private static int list(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer sp = src.getPlayerOrException();
        Loadout l = Powers.loadout(sp);
        if (l.owned().isEmpty()) {
            src.sendSuccess(() -> Component.literal("You have no powers yet. Power Cores drop from Gate Lords and elites."), false);
            return 0;
        }
        src.sendSuccess(() -> Component.literal(String.format("Energy %.0f / %.0f", l.energy(), Powers.maxEnergy(sp))).withStyle(ChatFormatting.AQUA), false);
        for (int i = 0; i < Loadout.SLOTS; i++) {
            PowerType t = PowerRegistry.get(l.slot(i));
            String label = "[" + Casting.slotLabel(i) + "] ";
            src.sendSuccess(() -> Component.literal(label).withStyle(ChatFormatting.GRAY)
                    .append(t == null ? Component.literal("—").withStyle(ChatFormatting.DARK_GRAY) : Component.literal(t.name).withColor(t.family.colour)), false);
        }
        for (Loadout.Owned o : l.owned().values()) {
            PowerType t = PowerRegistry.get(o.id());
            if (t == null) continue;
            String extra = (o.variant() != 0 ? " · " + t.variant(o.variant()).name() : "") + " · Lv " + o.level()
                    + (t.isPassive() ? " · passive" : l.slotOf(o.id()) >= 0 ? " · " + Casting.slotLabel(l.slotOf(o.id())) : "");
            src.sendSuccess(() -> Component.literal("  " + t.name).withColor(t.family.colour)
                    .append(Component.literal(extra).withStyle(ChatFormatting.GRAY)), false);
        }
        return l.owned().size();
    }

    private static int give(CommandSourceStack src, String id, int variant) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer sp = src.getPlayerOrException();
        PowerType t = PowerRegistry.get(id);
        if (t == null) {
            src.sendFailure(Component.literal("Unknown power " + id));
            return 0;
        }
        ItemStack core = PowerContent.core(t, variant);
        if (!sp.addItem(core)) sp.drop(core, false, net.minecraft.util.Prediction.SERVER_ONLY);
        src.sendSuccess(() -> Component.literal("Gave a Power Core: " + t.name + (variant > 0 ? " (" + t.variant(variant).name() + ")" : "")), true);
        return 1;
    }

    /** The power at a level, on the key it fits (the first active key for an active), cooldowns clear and energy full. */
    private static int showcase(CommandSourceStack src, String id, int level, int variant) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer sp = src.getPlayerOrException();
        PowerType t = PowerRegistry.get(id);
        if (t == null) {
            src.sendFailure(Component.literal("Unknown power " + id));
            return 0;
        }
        Powers.setLevel(sp, t, level, variant);
        if (!t.isPassive()) {
            int slot = t.slot == PowerType.Slot.ULTIMATE ? Loadout.ULTIMATE : t.slot == PowerType.Slot.MOVEMENT ? Loadout.MOVEMENT : 0;
            Powers.equip(sp, slot, t.id.toString());
        }
        Powers.resetCooldowns(sp);
        Powers.fillEnergy(sp);
        src.sendSuccess(() -> Component.literal(t.name + " at level " + level + ": ").withColor(t.family.colour)
                .append(dev.awaken.powers.ladder.PowerLadder.name(t.path, level)), false);
        return 1;
    }

    private static int learn(CommandSourceStack src, String id, int variant) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer sp = src.getPlayerOrException();
        PowerType t = PowerRegistry.get(id);
        if (t == null) {
            src.sendFailure(Component.literal("Unknown power " + id));
            return 0;
        }
        Powers.LearnResult r = Powers.learnOrLevel(sp, t.id, variant, true);
        src.sendSuccess(() -> Component.literal(t.name + ": " + r.name().toLowerCase()), true);
        return 1;
    }
}
