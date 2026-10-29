package dev.awaken.compat.client.jade;

import dev.awaken.Awaken;
import dev.awaken.above.MachineType;
import dev.awaken.above.block.BrokenMachineBlock;
import dev.awaken.above.block.DoorCoreBlock;
import dev.awaken.above.block.LogTerminalBlock;
import dev.awaken.above.client.ClientAbove;
import dev.awaken.above.machine.MachineBlock;
import dev.awaken.compat.LookAt;
import dev.awaken.compat.SpoilerRule;
import dev.awaken.compat.client.CompatClientConfig;
import dev.awaken.compat.client.Spoilers;
import dev.awaken.compat.jade.ServerData;
import dev.awaken.gates.GateVortex;
import dev.awaken.gates.GateVortexBlockEntity;
import dev.awaken.gates.block.GateBlocks;
import dev.awaken.hearth.Upgrades;
import dev.awaken.hearth.block.HearthAnchorBlock;
import dev.awaken.hearth.block.RolePadBlock;
import dev.awaken.hearth.block.TrophyMountBlock;
import dev.awaken.hearth.block.TrophyMountBlockEntity;
import dev.awaken.hearth.town.TownNpc;
import dev.awaken.mobs.def.FaunaRole;
import dev.awaken.mobs.entity.AwakenMob;
import dev.awaken.mobs.entity.FaunaMob;
import dev.awaken.progression.Rank;
import dev.awaken.signal.client.Glass;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.JadeIds;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.theme.IThemeHelper;

import java.util.Locale;

/**
 * Jade on the client: Awaken's lines for what you look at, the System's glass for Awaken targets (Jade theme
 * {@code awaken:system} plus an accent bar in the rank's colour), and the spoiler guard: nothing that would give away a
 * disguise (mimics, dormant statues, the ritual's seated ones, Veil marks), no details for a creature the Codex doesn't
 * have yet, and a broken machine keeps its name until its blueprint is yours. Loaded only by Jade.
 */
public final class JadeClient {
    private JadeClient() {}

    public static final Identifier THEME = Awaken.id("system");

    public static void register(IWailaClientRegistration r) {
        r.registerBlockComponent(new Gate(), GateBlocks.Core.class);
        r.registerBlockComponent(new Machine(), MachineBlock.class);
        r.registerBlockComponent(new Broken(), BrokenMachineBlock.class);
        r.registerBlockComponent(new Door(), DoorCoreBlock.class);
        r.registerBlockComponent(new Terminal(), LogTerminalBlock.class);
        r.registerBlockComponent(new Hearth(), HearthAnchorBlock.class);
        r.registerBlockComponent(new Pad(), RolePadBlock.class);
        r.registerBlockComponent(new Trophy(), TrophyMountBlock.class);
        r.registerEntityComponent(new Creature(), AwakenMob.class);
        r.registerEntityComponent(new Townsperson(), TownNpc.class);

        r.addRayTraceCallback((hit, accessor, original) -> hidden(accessor) || plated(accessor) ? null : accessor);
        r.addBeforeTooltipCollectCallback((theme, accessor) -> {
            if (CompatClientConfig.systemStyle() && isAwaken(accessor) && IThemeHelper.get().hasTheme(THEME)) {
                theme.setValue(IThemeHelper.get().getTheme(THEME));
            }
            return true;
        });
        r.addAfterRenderCallback((root, animation, g, accessor) -> {
            if (!CompatClientConfig.systemStyle() || !isAwaken(accessor)) return;
            // the System card's accent bar, in the colour of what you're looking at
            int x = root.getX(), y = root.getY();
            g.fill(x, y + 1, x + 2, y + root.getHeight() - 1, Glass.a(accent(accessor), animation.alpha));
        });
        Awaken.LOG.info("[compat] Jade plugin ready (System style, spoiler guard)");
    }

    // ---- the guard --------------------------------------------------------------------------------------------------

    /** Things Jade must not name at all ({@link SpoilerRule#disguised}): naming them would give the trick away. */
    static boolean hidden(Accessor<?> a) {
        if (!Spoilers.active()) return false;
        if (a instanceof EntityAccessor ea) return SpoilerRule.disguised(ea.getEntity());
        if (a instanceof BlockAccessor ba) return SpoilerRule.disguised(ba.getBlockState());
        return false;
    }

    /**
     * A creature wearing Awaken's plate gets no Jade card: the plate is its one readout (Euan 2026-10-29: "only the plate
     * shows"). Players and tamed pets have no plate, so they keep Jade's card; so does everything while plates are off.
     */
    static boolean plated(Accessor<?> a) {
        if (!(a instanceof EntityAccessor ea) || !(ea.getEntity() instanceof net.minecraft.world.entity.LivingEntity e)) return false;
        if (e instanceof net.minecraft.world.entity.player.Player || e instanceof net.minecraft.world.entity.TamableAnimal t && t.isTame()) return false;
        return dev.awaken.living.client.LivingClient.platesOn() && dev.awaken.core.api.CreatureTag.of(e).present();
    }

    static boolean isAwaken(Accessor<?> a) {
        if (a instanceof BlockAccessor ba) return BuiltInRegistries.BLOCK.getKey(ba.getBlock()).getNamespace().equals(Awaken.MODID);
        if (a instanceof EntityAccessor ea) return BuiltInRegistries.ENTITY_TYPE.getKey(ea.getEntity().getType()).getNamespace().equals(Awaken.MODID);
        return false;
    }

    static int accent(Accessor<?> a) {
        if (a instanceof BlockAccessor ba) {
            if (ba.getBlock() instanceof GateBlocks.Core) {
                int rank = ServerData.GATE.decodeFromData(ba).map(LookAt.Gate::rank).orElse(ba.getBlockState().getValue(GateBlocks.Core.RANK));
                return Rank.of(rank).colour;
            }
            if (ba.getBlock() instanceof HearthAnchorBlock || ba.getBlock() instanceof RolePadBlock) return Glass.LANTERN;
            if (ba.getBlock() instanceof DoorCoreBlock) return Rank.of(ba.getBlockState().getValue(DoorCoreBlock.RANK)).colour;
        } else if (a instanceof EntityAccessor ea) {
            if (ea.getEntity() instanceof TownNpc) return Glass.LANTERN;
            var c = ServerData.CREATURE.decodeFromData(ea);
            if (c.isPresent()) return Rank.of(c.get().rank()).colour;
        }
        return Glass.EDGE;
    }

    // ---- line helpers: the System's rows (pale label, bright value) -------------------------------------------------

    static MutableComponent row(String label, String value, int valueColour) {
        return Component.literal(label + "  ").withColor(Glass.LABEL).append(Component.literal(value).withColor(valueColour));
    }

    static MutableComponent row(String label, String value) {
        return row(label, value, Glass.TITLE);
    }

    static MutableComponent note(String text) {
        return Component.literal(text).withColor(Glass.LABEL);
    }

    static MutableComponent line(String text, int colour) {
        return Component.literal(text).withColor(colour);
    }

    static String number(int n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    static boolean isLocal(String name) {
        var p = Minecraft.getInstance().player;
        return p != null && p.getScoreboardName().equals(name);
    }

    // ---- gates ------------------------------------------------------------------------------------------------------

    static final class Gate implements IBlockComponentProvider {
        @Override
        public void appendTooltip(ITooltip t, BlockAccessor a, IPluginConfig config) {
            var data = ServerData.GATE.decodeFromData(a);
            Rank rank = Rank.of(data.map(LookAt.Gate::rank).orElse(a.getBlockState().getValue(GateBlocks.Core.RANK)));
            t.replace(JadeIds.CORE_OBJECT_NAME, line(rank.label + "-rank gate", rank.colour));
            if (data.isEmpty()) {
                // no server data (Jade missing on the server): what the vortex shows everyone
                String mode = a.getBlockState().getValue(GateBlocks.Core.BROKEN) ? "broken" : "open";
                if (a.getBlockEntity() instanceof GateVortexBlockEntity v) mode = GateVortex.MODE_NAMES[Math.max(0, Math.min(5, v.mode()))];
                t.add(row("State", Character.toUpperCase(mode.charAt(0)) + mode.substring(1)));
                return;
            }
            LookAt.Gate g = data.get();
            if (g.ritual()) {
                t.add(note("A bell tolls, far below. It sounds like it's waiting."));
                return;
            }
            MutableComponent state = line(g.state(), g.state().startsWith("Broken") ? Glass.WARN : Glass.TITLE);
            if (g.breaksIn() == 0) state.append(line("  ·  breaks today", Glass.WARN));
            else if (g.breaksIn() == 1) state.append(line("  ·  breaks tomorrow", Glass.AMBER));
            else if (g.breaksIn() > 1) state.append(note("  ·  breaks in ").append(line(g.breaksIn() + " play-days", Glass.TITLE)));
            t.add(state);
            if (!g.omen().isEmpty()) t.add(row("Omen", g.omen(), Glass.TEXT));
            if (!g.theme().isEmpty()) t.add(row("Theme", g.theme()));
            if (!g.layout().isEmpty()) t.add(row("Layout", g.layout()));
            if (!g.modifiers().isEmpty()) t.add(row("Modifiers", g.modifiers(), g.modifiers().equals("None") ? Glass.TEXT : Glass.AMBER));
            if (g.floors() >= 0) t.add(row("Floors", Integer.toString(g.floors())));
            if (!g.lord().isEmpty()) t.add(row("Gate Lord", g.lord(), Glass.WARN));
            // what Perception would read next (Awareness is power)
            if (g.layout().isEmpty()) t.add(note("PER " + LookAt.PER_LAYOUT + " reads its layout"));
            else if (g.modifiers().isEmpty()) t.add(note("PER " + LookAt.PER_MODIFIERS + " reads its modifiers"));
            else if (g.floors() < 0) t.add(note("PER " + LookAt.PER_FLOORS + " counts its floors"));
        }

        @Override
        public Identifier getUid() {
            return ServerData.GATE_ID;
        }
    }

    // ---- Precursor machines and ruins -------------------------------------------------------------------------------

    static final class Machine implements IBlockComponentProvider {
        @Override
        public void appendTooltip(ITooltip t, BlockAccessor a, IPluginConfig config) {
            MachineType type = a.getBlock() instanceof MachineBlock mb ? mb.type() : null;
            var data = ServerData.MACHINE.decodeFromData(a);
            if (data.isPresent()) {
                LookAt.Machine m = data.get();
                if (!m.owner().isEmpty()) t.add(row("Built by", isLocal(m.owner()) ? "you" : m.owner()));
                if (type == MachineType.REACTOR) {
                    t.add(row("Essence", number(m.stored()) + " / " + number(dev.awaken.above.machine.MachineBlockEntity.REACTOR_CAPACITY),
                            m.stored() > 0 ? Glass.GOOD : Glass.AMBER));
                    t.add(note("Powers every machine within " + dev.awaken.above.power.Power.RANGE + " blocks"));
                } else if (type == null) {
                    t.add(row("Power", "none needed", Glass.TEXT));
                } else if (m.reactors() > 0) {
                    t.add(row("Power", number(m.powered()) + " essence  ·  " + m.reactors() + (m.reactors() == 1 ? " reactor" : " reactors"),
                            m.powered() > 0 ? Glass.GOOD : Glass.AMBER));
                } else {
                    t.add(line("No reactor within " + dev.awaken.above.power.Power.RANGE + " blocks", Glass.WARN));
                }
                if (m.status() == 1) t.add(line("A clone is ready", Glass.GOOD));
                else if (m.status() == 2) t.add(note("A clone is growing"));
                else if (m.status() >= 10) {
                    int missing = m.status() - 10;
                    int built = dev.awaken.above.machine.LaunchRing.FRAMES - missing;
                    t.add(row("Frames", built + " / " + dev.awaken.above.machine.LaunchRing.FRAMES, missing == 0 ? Glass.GOOD : Glass.AMBER));
                }
            }
            if (type == null) {
                int known = ClientAbove.data().blueprints().size();
                t.add(row("Blueprints", known + " / " + MachineType.values().length));
            } else if (a.showDetails()) {
                t.add(note(type.blurb));
            }
        }

        @Override
        public Identifier getUid() {
            return ServerData.MACHINE_ID;
        }
    }

    static final class Broken implements IBlockComponentProvider {
        @Override
        public void appendTooltip(ITooltip t, BlockAccessor a, IPluginConfig config) {
            MachineType type = a.getBlockState().getValue(BrokenMachineBlock.MACHINE);
            boolean known = !Spoilers.active() || ClientAbove.data().knows(type);
            t.replace(JadeIds.CORE_OBJECT_NAME, line(known ? "Broken " + type.displayName : "Broken machine", Glass.TITLE));
            if (known) t.add(line("Blueprint known  ·  build it at a Fabricator", Glass.GOOD));
            else t.add(line("Scan it to learn what it was", Glass.AMBER));
        }

        @Override
        public Identifier getUid() {
            return Awaken.id("ruin_machine");
        }
    }

    static final class Door implements IBlockComponentProvider {
        @Override
        public void appendTooltip(ITooltip t, BlockAccessor a, IPluginConfig config) {
            Rank need = DoorCoreBlock.required(a.getBlockState());
            t.add(row("Opens for", need.label + "-rank hunters", need.colour));
            boolean can = DoorCoreBlock.canOpen(a.getPlayer(), a.getBlockState());
            t.add(can ? line("It knows you", Glass.GOOD) : line("Your rank is too low", Glass.WARN));
        }

        @Override
        public Identifier getUid() {
            return Awaken.id("ruin_door");
        }
    }

    static final class Terminal implements IBlockComponentProvider {
        @Override
        public void appendTooltip(ITooltip t, BlockAccessor a, IPluginConfig config) {
            t.add(note("Read it with a Precursor Scanner"));
        }

        @Override
        public Identifier getUid() {
            return Awaken.id("ruin_terminal");
        }
    }

    // ---- the Hearth -------------------------------------------------------------------------------------------------

    static final class Hearth implements IBlockComponentProvider {
        @Override
        public void appendTooltip(ITooltip t, BlockAccessor a, IPluginConfig config) {
            var data = ServerData.HEARTH.decodeFromData(a);
            if (data.isEmpty()) return;
            LookAt.Hearth h = data.get();
            t.add(row("Hearth of", isLocal(h.owner()) ? "you" : h.owner(), Glass.LANTERN));
            t.add(row("Tier", Upgrades.roman(h.tier())).append(note("  ·  reach ")).append(line(h.radius() + " blocks", Glass.TITLE)));
            t.add(row("Forge", Upgrades.roman(h.forgeTier())));
        }

        @Override
        public Identifier getUid() {
            return ServerData.HEARTH_ID;
        }
    }

    static final class Pad implements IBlockComponentProvider {
        @Override
        public void appendTooltip(ITooltip t, BlockAccessor a, IPluginConfig config) {
            if (!(a.getBlock() instanceof RolePadBlock pad)) return;
            t.add(row("Echo duty", pad.role.label, pad.role.colour));
            t.add(note(pad.role.does));
        }

        @Override
        public Identifier getUid() {
            return Awaken.id("role_pad");
        }
    }

    static final class Trophy implements IBlockComponentProvider {
        @Override
        public void appendTooltip(ITooltip t, BlockAccessor a, IPluginConfig config) {
            if (!(a.getBlockEntity() instanceof TrophyMountBlockEntity be)) return;
            if (be.isEmpty()) t.add(note("Empty  ·  hang a trophy here"));
            else t.add(row("Trophy", be.trophy().getHoverName().getString(), Glass.LANTERN));
        }

        @Override
        public Identifier getUid() {
            return Awaken.id("trophy");
        }
    }

    // ---- creatures and people ---------------------------------------------------------------------------------------

    static final class Creature implements IEntityComponentProvider {
        @Override
        public void appendTooltip(ITooltip t, EntityAccessor a, IPluginConfig config) {
            if (!(a.getEntity() instanceof AwakenMob mob)) return;
            if (mob instanceof FaunaMob f) {
                if (!Spoilers.knowsCreature(mob.def().id)) {
                    t.add(note("Not in your Codex yet"));
                    return;
                }
                boolean angry = (f.pose() & FaunaMob.P_ANGRY) != 0;
                t.add(temperament(f.faunaRole(), angry));
            }
            if (mob.isDecoy()) return; // an illusion says nothing more than its name
            if (dev.awaken.living.client.LivingClient.platesOn()) return; // the band's plate shows level, rank and role (SPEC §21.0.12)
            var data = ServerData.CREATURE.decodeFromData(a);
            if (data.isEmpty()) return;
            LookAt.Creature c = data.get();
            Rank rank = Rank.of(c.rank());
            MutableComponent lv = row("Level", Integer.toString(c.level())).append(note("  ·  ")).append(line(rank.label + "-rank", rank.colour));
            if (!c.role().isEmpty()) lv.append(note("  ·  ")).append(line(c.role(), c.role().equals("Gate Lord") ? Glass.WARN : Glass.AMBER));
            t.add(lv);
        }

        static MutableComponent temperament(FaunaRole role, boolean angry) {
            if (angry && role != FaunaRole.AMBIENT) return line("Hostile now", Glass.WARN);
            return switch (role) {
                case AMBIENT -> line("Harmless", Glass.GOOD);
                case NEUTRAL -> line("Neutral  ·  fights back when struck", Glass.TEXT);
                case HUNTER -> line("Hunts in the dark  ·  torchlight is safe", Glass.AMBER);
                case BEAST -> line("Named beast  ·  warns before it fights", Glass.WARN);
            };
        }

        @Override
        public Identifier getUid() {
            return ServerData.CREATURE_ID;
        }
    }

    static final class Townsperson implements IEntityComponentProvider {
        @Override
        public void appendTooltip(ITooltip t, EntityAccessor a, IPluginConfig config) {
            if (!(a.getEntity() instanceof TownNpc npc)) return;
            var data = ServerData.TOWNSPERSON.decodeFromData(a);
            String job = data.map(LookAt.Townsperson::job).filter(s -> !s.isEmpty()).orElse(LookAt.jobFromId(npc.kind()));
            if (!job.isEmpty()) t.add(row("Job", job, Glass.LANTERN));
            if (data.isEmpty()) return;
            LookAt.Townsperson p = data.get();
            if (!p.owner().isEmpty()) t.add(row("Town", isLocal(p.owner()) ? "yours" : p.owner() + "'s"));
            if (p.arrivedAt() >= 0) {
                Rank r = Rank.of(p.arrivedAt());
                t.add(row("Came when you reached", r.label + "-rank", r.colour));
            }
        }

        @Override
        public Identifier getUid() {
            return ServerData.TOWNSPERSON_ID;
        }
    }
}
