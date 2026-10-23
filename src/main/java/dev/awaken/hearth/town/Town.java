package dev.awaken.hearth.town;

import dev.awaken.core.PlayDays;
import dev.awaken.core.api.GateHooks;
import dev.awaken.hearth.HearthContent;
import dev.awaken.hearth.HearthData;
import dev.awaken.hearth.HearthRecord;
import dev.awaken.hearth.HearthService;
import dev.awaken.hearth.HearthViews;
import dev.awaken.hearth.Later;
import dev.awaken.hearth.Offers;
import dev.awaken.hearth.Upgrades;
import dev.awaken.hearth.block.SpireBoardBlock;
import dev.awaken.hearth.block.SpireBoardBlockEntity;
import dev.awaken.hearth.player.HearthPlayer;
import dev.awaken.hearth.trophy.TrophyData;
import dev.awaken.progression.Counters;
import dev.awaken.progression.Profile;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import dev.awaken.progression.content.Content;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The town: people who arrive as you rank up, live around your Hearth, recognise you, and offer services. Nothing to
 * assign: they show up, they wander, they talk when you talk to them.
 */
public final class Town {
    private Town() {}

    private static final TagKey<Structure> PRECURSOR_RUINS = TagKey.create(Registries.STRUCTURE, dev.awaken.Awaken.id("precursor_ruins"));
    private static final Map<UUID, Long> TALK_COOLDOWN = new HashMap<>();
    private static final int NPC_HOME_RADIUS = 20;

    // ---- arrivals ---------------------------------------------------------------------------------------------------

    public static void onHearthCreated(ServerPlayer owner, ServerLevel level, HearthRecord h) {
        arrivals(owner, level, h, true);
    }

    /** Rank-up: new people arrive, the town celebrates (now if you're home, otherwise when you get back). */
    public static void onRankUp(ServerPlayer owner, Rank rank) {
        Optional<HearthRecord> ho = HearthService.hearthOf(owner);
        if (ho.isEmpty()) return;
        HearthRecord h = ho.get();
        ServerLevel level = HearthService.levelOf(owner.level().getServer(), h);
        h.pendingCelebration = rank.ordinal();
        HearthService.dirty(owner.level().getServer());
        arrivals(owner, level, h, false);
        if (owner.level() == level && h.contains(owner.blockPosition())) celebrate(owner, level, h);
    }

    /** Lets everyone in who belongs at the owner's rank, and sees off anyone whose time is over (Vael at S). */
    public static void arrivals(ServerPlayer owner, ServerLevel level, HearthRecord h, boolean newHome) {
        Rank rank = Progression.rank(owner);
        List<String> came = new ArrayList<>();
        for (var e : Townsfolk.inArrivalOrder()) {
            String id = e.getKey();
            Townsfolk.Def d = e.getValue();
            if (d.presentAt(rank) && !h.arrived.contains(id)) {
                h.arrived.add(id);
                came.add(id);
            } else if (!d.presentAt(rank) && h.arrived.contains(id) && rank.ordinal() >= d.arrivesAt().ordinal()) {
                depart(owner, level, h, id, d);
            }
        }
        if (came.isEmpty()) return;
        HearthService.dirty(level.getServer());
        if (level.isPositionEntityTicking(h.pos)) for (String id : came) spawn(level, h, id);
        if (h.spire == null) raiseSpire(level, h);
        if (came.size() == 1 && !newHome) {
            String id = came.get(0);
            Townsfolk.Def d = Townsfolk.get(id).orElseThrow();
            Signal.send(owner, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(Townsfolk.displayName(id) + " arrived"))
                    .body(Component.literal("“" + d.line(rank) + "”"))
                    .footer(Component.literal("They live at your Hearth now")).priority(4).ttl(20 * 10).build());
        } else {
            List<String> names = new ArrayList<>();
            for (String id : came) names.add(Townsfolk.displayName(id));
            String first = came.get(0);
            Signal.send(owner, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(newHome ? "Someone moved in" : "New neighbours"))
                    .body(Component.literal(String.join(", ", names) + (came.size() > 1 ? " came to live at your Hearth." : " came to live at your Hearth.")))
                    .row(Component.literal(Townsfolk.shortName(first)), Component.literal("“" + Townsfolk.get(first).map(d -> d.line(rank)).orElse("") + "”"))
                    .priority(4).ttl(20 * 10).build());
        }
        if (Progression.profile(owner).awakened()) Progression.markNewThing(owner, "townsfolk");
        HearthViews.refresh(owner);
    }

    /** Admin: someone arrives now, whatever the rank. */
    public static void forceArrive(ServerPlayer owner, ServerLevel level, HearthRecord h, String id) {
        if (h.arrived.add(id) || h.npcs.get(id) == null || !(level.getEntity(h.npcs.get(id)) instanceof TownNpc)) {
            if (level.isLoaded(h.pos)) spawn(level, h, id);
        }
        if (h.spire == null) raiseSpire(level, h);
        HearthService.dirty(level.getServer());
        Townsfolk.get(id).ifPresent(d -> Signal.send(owner, SignalWindow.builder(SignalWindow.Kind.NOTICE,
                        Component.literal(Townsfolk.displayName(id) + " arrived"))
                .body(Component.literal("“" + d.line(Progression.rank(owner)) + "”")).ttl(20 * 8).build()));
        HearthViews.refresh(owner);
    }

    private static void depart(ServerPlayer owner, ServerLevel level, HearthRecord h, String id, Townsfolk.Def d) {
        h.arrived.remove(id);
        UUID u = h.npcs.remove(id);
        if (u != null && level.getEntity(u) instanceof TownNpc npc) npc.discard();
        HearthService.dirty(level.getServer());
        String note = "rival".equalsIgnoreCase(d.role()) ? "Gone to the Court. Don't follow. (Follow.)" : "Moved on.";
        Signal.send(owner, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(d.name() + " left town"))
                .body(Component.literal("A note on the Spire: “" + note + "”")).ttl(20 * 10).build());
        HearthViews.refresh(owner);
    }

    /** Puts an arrived NPC into the world near the Anchor. */
    static TownNpc spawn(ServerLevel level, HearthRecord h, String id) {
        TownNpc npc = HearthContent.TOWNSFOLK.get().create(level, EntitySpawnReason.MOB_SUMMONED);
        if (npc == null) return null;
        Vec3 at = HearthService.safeSpotNear(level, h.pos);
        npc.snapTo(at.x, at.y, at.z, level.getRandom().nextFloat() * 360f, 0f);
        npc.setKind(id);
        npc.setHearthOwner(h.owner);
        npc.setHomeTo(h.pos, NPC_HOME_RADIUS);
        npc.setCustomName(Component.literal(Townsfolk.displayName(id)));
        npc.setCustomNameVisible(true);
        level.addFreshEntity(npc);
        h.npcs.put(id, npc.getUUID());
        HearthService.dirty(level.getServer());
        level.sendParticles(HearthContent.HEARTH_EMBER.get(), at.x, at.y + 1, at.z, 16, 0.4, 0.8, 0.4, 0.01);
        return npc;
    }

    /** Townsfolk loaded from disk check they're still the one their Hearth expects; duplicates quietly leave. */
    public static void onJoin(EntityJoinLevelEvent e) {
        if (!(e.getEntity() instanceof TownNpc npc) || !(e.getLevel() instanceof ServerLevel level) || !e.loadedFromDisk()) return;
        UUID owner = npc.hearthOwner();
        Optional<HearthRecord> h = owner == null ? Optional.empty() : HearthData.get(level.getServer()).of(owner);
        UUID expected = h.map(r -> r.npcs.get(npc.kind())).orElse(null);
        if (h.isEmpty() || !h.get().arrived.contains(npc.kind()) || (expected != null && !expected.equals(npc.getUUID()))) {
            e.setCanceled(true);
        }
    }

    public static void dissolve(ServerLevel level, HearthRecord h) {
        for (UUID u : h.npcs.values()) if (level.getEntity(u) instanceof TownNpc npc) npc.discard();
        h.npcs.clear();
    }

    // ---- upkeep -----------------------------------------------------------------------------------------------------

    public static void upkeep(ServerLevel level, HearthRecord h, ServerPlayer owner, long now) {
        // Bring back anyone missing (only when the whole home area is loaded, so we never duplicate an unloaded NPC).
        if (now % 100 < 20 && areaLoaded(level, h.pos, NPC_HOME_RADIUS + 8)) {
            for (String id : List.copyOf(h.arrived)) {
                UUID u = h.npcs.get(id);
                Entity e = u == null ? null : level.getEntity(u);
                if (e == null || !e.isAlive()) spawn(level, h, id);
                else if (e.distanceToSqr(Vec3.atCenterOf(h.pos)) > 48 * 48 && e instanceof TownNpc npc) {
                    Vec3 at = HearthService.safeSpotNear(level, h.pos);
                    npc.teleportTo(at.x, at.y, at.z);
                }
            }
        }
        if (owner == null) return;
        if (now % 200 < 20) {
            arrivals(owner, level, h, false);
            refreshBoards(owner, level, h);
        }
        if (h.pendingCelebration >= 0 && owner.level() == level && h.contains(owner.blockPosition())) celebrate(owner, level, h);
    }

    private static boolean areaLoaded(ServerLevel level, BlockPos c, int r) {
        return level.isPositionEntityTicking(c) && level.isPositionEntityTicking(c.offset(r, 0, r)) && level.isPositionEntityTicking(c.offset(-r, 0, r))
                && level.isPositionEntityTicking(c.offset(r, 0, -r)) && level.isPositionEntityTicking(c.offset(-r, 0, -r));
    }

    static List<TownNpc> present(ServerLevel level, HearthRecord h) {
        List<TownNpc> out = new ArrayList<>();
        for (UUID u : h.npcs.values()) if (level.getEntity(u) instanceof TownNpc npc && npc.isAlive()) out.add(npc);
        return out;
    }

    // ---- recognition: the rank-up celebration -------------------------------------------------------------------------

    static void celebrate(ServerPlayer owner, ServerLevel level, HearthRecord h) {
        Rank rank = Rank.of(h.pendingCelebration);
        h.pendingCelebration = -1;
        HearthService.dirty(level.getServer());
        int colour = rank.colour;
        for (int i = 0; i < 7; i++) {
            int n = i;
            Later.run(level.getServer(), 10 + i * 12, () -> firework(level, h.pos, colour, n));
        }
        List<TownNpc> npcs = present(level, h);
        Vec3 at = owner.position();
        for (TownNpc npc : npcs) npc.gather(at, 20 * 15);
        level.playSound(null, h.pos, SoundEvents.PLAYER_LEVELUP, SoundSource.AMBIENT, 1f, 0.7f);
        var w = SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(npcs.isEmpty() ? "The Hearth burns brighter" : "The town gathers"))
                .body(Component.literal(owner.getGameProfile().name() + ", " + rank.label + "-rank. Your name goes on the Spire."));
        int rows = 0;
        for (TownNpc npc : npcs) {
            Optional<Townsfolk.Def> d = Townsfolk.get(npc.kind());
            if (d.isEmpty() || rows >= 4) continue;
            w.row(Component.literal(d.get().name()), Component.literal("“" + d.get().line(rank) + "”"));
            rows++;
        }
        Later.run(level.getServer(), 30, () -> Signal.send(owner, w.priority(6).ttl(20 * 14).build()));
        refreshBoards(owner, level, h);
    }

    private static void firework(ServerLevel level, BlockPos centre, int colour, int n) {
        var rnd = level.getRandom();
        double a = n * 0.9;
        double x = centre.getX() + 0.5 + Math.cos(a) * (4 + rnd.nextInt(5)), z = centre.getZ() + 0.5 + Math.sin(a) * (4 + rnd.nextInt(5));
        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
        FireworkExplosion.Shape shape = n % 3 == 0 ? FireworkExplosion.Shape.STAR : n % 3 == 1 ? FireworkExplosion.Shape.LARGE_BALL : FireworkExplosion.Shape.BURST;
        rocket.set(DataComponents.FIREWORKS, new Fireworks(1 + rnd.nextInt(2),
                List.of(new FireworkExplosion(shape, IntList.of(colour, 0xF2A541), IntList.of(0xFFD28A), true, n % 2 == 0))));
        level.addFreshEntity(new FireworkRocketEntity(level, x, centre.getY() + 2, z, rocket));
    }

    // ---- talking ----------------------------------------------------------------------------------------------------

    public static void talk(ServerPlayer sp, TownNpc npc) {
        long now = sp.level().getGameTime();
        Long last = TALK_COOLDOWN.get(sp.getUUID());
        if (last != null && now - last < 10) return;
        TALK_COOLDOWN.put(sp.getUUID(), now);
        String id = npc.kind();
        Optional<Townsfolk.Def> od = Townsfolk.get(id);
        if (od.isEmpty()) return;
        Townsfolk.Def d = od.get();
        boolean mine = sp.getUUID().equals(npc.hearthOwner());
        Rank rank = Progression.rank(sp);
        String line = d.line(rank);
        if (!d.chatter().isEmpty() && sp.getRandom().nextInt(3) == 0) line = d.chatter().get(sp.getRandom().nextInt(d.chatter().size()));
        if (!mine) {
            Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(Townsfolk.displayName(id)))
                    .body(Component.literal("“" + line + "” They're busy looking after someone else's Hearth.")).ttl(20 * 6).build());
            return;
        }
        ServerLevel level = (ServerLevel) sp.level();
        switch (Townsfolk.skin(id)) {
            case "oren" -> oren(sp, level, npc, d, line);
            case "mira" -> mira(sp, level, d, line);
            case "teodor" -> teodor(sp, level, d, line);
            case "sun" -> sun(sp, level, d, line);
            default -> Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(Townsfolk.displayName(id)))
                    .body(Component.literal("“" + line + "”")).ttl(20 * 8).build());
        }
    }

    private static void oren(ServerPlayer sp, ServerLevel level, TownNpc npc, Townsfolk.Def d, String line) {
        if (Upgrades.isTemperable(sp.getMainHandItem())) {
            Upgrades.offerTemper(sp, level, npc.blockPosition(), InteractionHand.MAIN_HAND, "oren");
            return;
        }
        Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(Townsfolk.displayName("oren_blacksmith")))
                .body(Component.literal("“" + line + "”"))
                .footer(Component.literal("Hold a weapon or armour piece and talk to him to temper it")).ttl(20 * 8).build());
    }

    private static void mira(ServerPlayer sp, ServerLevel level, Townsfolk.Def d, String line) {
        HearthPlayer hp = HearthService.player(sp);
        long today = PlayDays.current(level.getServer());
        var w = SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(d.name() + " the " + d.role())).body(Component.literal("“" + line + "”"));
        if (hp.rumourDay == today) {
            w.footer(Component.literal("Nothing new today. Mira hears things overnight."));
        } else {
            w.action(Offers.offer(sp, "rumour", 20 * 30, p -> Rumours.give(p, (ServerLevel) p.level())));
            w.footer(Component.literal("Hear her rumour?"));
        }
        Signal.send(sp, w.ttl(20 * 10).build());
    }

    private static void teodor(ServerPlayer sp, ServerLevel level, Townsfolk.Def d, String line) {
        ItemStack held = sp.getMainHandItem();
        if (!held.isEmpty()) {
            identify(sp, held);
            return;
        }
        Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(d.name() + " the " + d.role()))
                .body(Component.literal("“" + line + "”"))
                .action(Offers.offer(sp, "codex", 20 * 30, Town::readCodex))
                .footer(Component.literal("Have him read your Codex aloud? (Hold an item to have it identified)")).ttl(20 * 10).build());
    }

    private static void sun(ServerPlayer sp, ServerLevel level, Townsfolk.Def d, String line) {
        Profile p = Progression.profile(sp);
        boolean fractured = p.fractureUntil() > level.getGameTime();
        var w = SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(d.name() + " the " + d.role())).body(Component.literal("“" + line + "”"));
        if (fractured) {
            w.row(Component.literal("Fracture"), Component.literal(((p.fractureUntil() - level.getGameTime()) / 1200 + 1) + " min left"));
            w.action(Offers.offer(sp, "heal", 20 * 30, pl -> {
                Progression.clearFracture(pl);
                pl.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 10, 1));
                pl.level().playSound(null, pl.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1.2f);
                Signal.toast(pl, Component.literal("Sun mends the Fracture. “No charge. Eat something.”"));
            }));
            w.footer(Component.literal("Let her mend it? Free."));
        } else {
            sp.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 8, 0));
            w.footer(Component.literal("How are you actually doing?"));
        }
        Signal.send(sp, w.ttl(20 * 10).build());
    }

    /** Teodor reads the Codex aloud: Path, Truths, insight, rumours, trophies. Only what the player already knows. */
    static void readCodex(ServerPlayer sp) {
        Profile p = Progression.profile(sp);
        HearthPlayer hp = HearthService.player(sp);
        var w = SignalWindow.builder(SignalWindow.Kind.REVELATION, Component.literal("Teodor reads your Codex"));
        p.path().map(Content.PATHS::get).ifPresent(path -> w.row(Component.literal("Path"), Component.literal(path.name())));
        w.row(Component.literal("Truths"), Component.literal(p.truthsTrue().size() + " true, " + p.truthsDeclared().size() + " declared"));
        w.row(Component.literal("Titles"), Component.literal(String.valueOf(p.titles().size())));
        w.row(Component.literal("Latents"), Component.literal(p.latents().size() + " / ??"));
        long bosses = p.counter("awaken:bosses_killed");
        if (bosses > 0) w.row(Component.literal("Gate Lords"), Component.literal(bosses + " fallen"));
        if (!hp.rumours.isEmpty()) w.row(Component.literal("Rumours"), Component.literal(hp.rumours.get(hp.rumours.size() - 1).title()));
        long depth = p.counter(Counters.DEPTH_RECORD);
        if (depth > 0) w.row(Component.literal("Deepest"), Component.literal("stratum " + depth));
        w.body(Component.literal(teodorAside(p)));
        Signal.send(sp, w.priority(3).ttl(20 * 16).build());
    }

    private static String teodorAside(Profile p) {
        if (p.counter(Counters.MACHINES_SCANNED) > 0) return "“You scanned " + p.counter(Counters.MACHINES_SCANNED) + " machines. The Precursors labelled nothing. Rude of them.”";
        if (p.counter(Counters.SPIRITS_SPOKEN) > 0) return "“You've spoken with the dead. Write down what they said. Exactly.”";
        if (p.counter(Counters.LUCID_DREAMS) > 0) return "“Lucid dreams. The Sleepers would like you. That's not a compliment.”";
        return "“It's a thin book so far. Thin books are the exciting ones.”";
    }

    /** Teodor identifies an item: where it comes from and what it carries. */
    static void identify(ServerPlayer sp, ItemStack stack) {
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        var w = SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Teodor looks closely")).row(Component.literal("Item"), stack.getHoverName());
        String origin = switch (id.getNamespace()) {
            case "minecraft" -> "Made here, in the waking world";
            case "awaken" -> "Gate-touched. The Signal knows this one";
            default -> "Foreign. From somewhere I haven't read about";
        };
        w.row(Component.literal("Origin"), Component.literal(origin));
        w.row(Component.literal("Rarity"), Component.literal(stack.getRarity().name().toLowerCase(java.util.Locale.ROOT)));
        int temper = Upgrades.temperOf(stack);
        if (temper > 0) w.row(Component.literal("Temper"), Component.literal(Upgrades.roman(temper)));
        TrophyData t = stack.get(HearthContent.TROPHY.get());
        if (t != null) w.row(Component.literal("Trophy"), Component.literal(t.bossName() + (t.placed() ? " · has hung in a Hall" : "")));
        var ench = stack.getEnchantments();
        if (!ench.isEmpty()) w.row(Component.literal("Enchanted"), Component.literal(ench.size() + " enchantment" + (ench.size() > 1 ? "s" : "")));
        if (stack.isDamageableItem()) w.row(Component.literal("Wear"), Component.literal((stack.getMaxDamage() - stack.getDamageValue()) + " / " + stack.getMaxDamage()));
        if (id.getPath().contains("power_core")) w.body(Component.literal("“A Power Core. Use it and it teaches you what it holds. I'd love to keep one. I won't.”"));
        else if (id.getNamespace().equals("awaken")) w.body(Component.literal("“Precursor make, or close to it. Don't lick it.”"));
        Signal.send(sp, w.ttl(20 * 12).build());
    }

    /** The nearest townsperson has something to say about a newly hung trophy. */
    public static void commentTrophy(ServerPlayer sp, ServerLevel level, BlockPos pos, TrophyData d) {
        Optional<HearthRecord> h = HearthService.hearthOf(sp).filter(r -> r.contains(pos));
        if (h.isEmpty()) return;
        TownNpc nearest = null;
        double best = 32 * 32;
        for (TownNpc npc : present(level, h.get())) {
            double dist = npc.distanceToSqr(Vec3.atCenterOf(pos));
            if (dist < best) {
                best = dist;
                nearest = npc;
            }
        }
        if (nearest == null) return;
        String who = Townsfolk.skin(nearest.kind());
        String boss = d.bossName();
        String line = switch (who) {
            case "oren" -> boss + "? I'd have mounted it higher. Looks good though.";
            case "mira" -> "I saw " + boss + " once. From very, very far away.";
            case "teodor" -> boss + ". I'll need to write that down. All of it.";
            case "sun" -> "Every one of these was someone's bad day. Glad it wasn't yours.";
            case "vael" -> "Nice. I have two of those.";
            default -> "That's " + boss + ".";
        };
        nearest.getLookControl().setLookAt(sp);
        Signal.toast(sp, Component.literal(Townsfolk.shortName(nearest.kind()) + ": “" + line + "”"));
    }

    // ---- the Spire --------------------------------------------------------------------------------------------------

    /** Raises a small Spire (pedestal, Spire Board, Reassessment Monolith, lanterns) on open ground near the Anchor. */
    public static void raiseSpire(ServerLevel level, HearthRecord h) {
        if (h.spire != null || !level.isLoaded(h.pos)) return;
        BlockPos spot = findSpireSpot(level, h.pos);
        if (spot == null) {
            HearthService.owner(level.getServer(), h).ifPresent(sp -> {
                sp.getInventory().placeItemBackInInventory(new ItemStack(HearthContent.SPIRE_BOARD_ITEM.get()), net.minecraft.util.Prediction.SERVER_ONLY);
                sp.getInventory().placeItemBackInInventory(new ItemStack(HearthContent.MONOLITH_ITEM.get()), net.minecraft.util.Prediction.SERVER_ONLY);
                Signal.toast(sp, Component.literal("No open ground for the town Spire. Place the Spire Board and Monolith yourself."));
            });
            h.spire = h.pos;
            return;
        }
        Direction toAnchor = Direction.getApproximateNearest(h.pos.getX() - spot.getX(), 0, h.pos.getZ() - spot.getZ());
        if (!toAnchor.getAxis().isHorizontal()) toAnchor = Direction.NORTH;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) level.setBlock(spot.offset(dx, 0, dz), Blocks.POLISHED_ANDESITE.defaultBlockState(), 3);
        level.setBlock(spot.above(), HearthContent.REASSESSMENT_MONOLITH.get().defaultBlockState(), 3);
        level.setBlock(spot.above(2), Blocks.LANTERN.defaultBlockState(), 3);
        BlockPos board = spot.above().relative(toAnchor);
        level.setBlock(board, HearthContent.SPIRE_BOARD.get().defaultBlockState().setValue(SpireBoardBlock.FACING, toAnchor), 3);
        if (level.getBlockEntity(board) instanceof SpireBoardBlockEntity be) be.setOwner(h.owner);
        Direction side = toAnchor.getClockWise();
        level.setBlock(spot.above().relative(side), Blocks.LANTERN.defaultBlockState(), 3);
        level.setBlock(spot.above().relative(side.getOpposite()), Blocks.LANTERN.defaultBlockState(), 3);
        h.spire = board;
        HearthService.dirty(level.getServer());
        HearthService.owner(level.getServer(), h).ifPresent(sp -> refreshBoards(sp, level, h));
    }

    static BlockPos findSpireSpot(ServerLevel level, BlockPos anchor) {
        for (int r = 6; r <= 14; r += 2) {
            for (int i = -r; i <= r; i += 2) {
                int[][] candidates = {{i, -r}, {i, r}, {-r, i}, {r, i}};
                for (int[] c : candidates) {
                    for (int dy = 2; dy >= -3; dy--) {
                        BlockPos ground = anchor.offset(c[0], dy, c[1]);
                        if (fits(level, ground)) return ground;
                    }
                }
            }
        }
        return null;
    }

    private static boolean fits(ServerLevel level, BlockPos ground) {
        if (!level.isLoaded(ground)) return false;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos g = ground.offset(dx, 0, dz);
                BlockState gs = level.getBlockState(g);
                if (!gs.isFaceSturdy(level, g, Direction.UP) || gs.hasBlockEntity()) return false;
                for (int dy = 1; dy <= 3; dy++) {
                    BlockState above = level.getBlockState(g.above(dy));
                    if (!above.isAir() && !above.canBeReplaced()) return false;
                }
            }
        }
        return true;
    }

    // ---- the Spire Board --------------------------------------------------------------------------------------------

    static void refreshBoards(ServerPlayer owner, ServerLevel level, HearthRecord h) {
        if (h.spire != null && level.isLoaded(h.spire) && level.getBlockEntity(h.spire) instanceof SpireBoardBlockEntity be) refreshBoard(owner, be);
    }

    public static void refreshBoard(ServerPlayer owner, SpireBoardBlockEntity be) {
        if (be.owner() != null && !be.owner().equals(owner.getUUID())) return;
        Profile p = Progression.profile(owner);
        be.show(owner.getGameProfile().name(), p.rank().ordinal(), p.level(), recordLines(owner));
    }

    static List<String> recordLines(ServerPlayer sp) {
        Profile p = Progression.profile(sp);
        HearthPlayer hp = HearthService.player(sp);
        List<String> out = new ArrayList<>();
        out.add("Gates cleared · " + p.counter(Counters.GATES_CLEARED));
        out.add("Gate Lords · " + p.counter("awaken:bosses_killed"));
        long depth = p.counter(Counters.DEPTH_RECORD);
        out.add("Deepest · " + (depth > 0 ? "stratum " + depth : "not yet"));
        out.add("Echoes · " + hp.echoes.size());
        int best = hp.bests.values().stream().mapToInt(Integer::intValue).min().orElse(0);
        if (best > 0) out.add("Best lap · " + dev.awaken.hearth.training.Training.formatTime(best));
        return out;
    }

    public static void showBoard(ServerPlayer viewer, ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof SpireBoardBlockEntity be)) return;
        if (be.owner() == null) be.setOwner(viewer.getUUID());
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(be.owner());
        if (owner != null) refreshBoard(owner, be);
        Rank rank = Rank.of(be.rank());
        var w = SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("The Spire · " + (be.name().isEmpty() ? "unclaimed" : be.name())));
        w.row(Component.literal("Rank"), Component.literal(rank.label + "-rank"));
        w.row(Component.literal("Level"), Component.literal(String.valueOf(be.hunterLevel())));
        for (String l : be.lines()) {
            int dot = l.indexOf(" · ");
            if (dot > 0) w.row(Component.literal(l.substring(0, dot)), Component.literal(l.substring(dot + 3)));
        }
        if (owner != null && owner == viewer) {
            Profile p = Progression.profile(owner);
            Rank next = p.rank().next();
            if (next != null) w.footer(Component.literal(p.reassessmentReady() ? "Reassessment ready · touch the Monolith"
                    : "Next rank: " + next.label + " · Reassessment at Level " + next.levelRequired));
        }
        Signal.send(viewer, w.ttl(20 * 12).build());
    }

    /** Nearest structure-tag lookups for rumours run here so the class owning the tags stays the same. */
    static TagKey<Structure> ruinsTag() {
        return PRECURSOR_RUINS;
    }

    static TagKey<Structure> fallbackRuins() {
        return StructureTags.RUINED_PORTAL;
    }

    static List<GateHooks.GateView> gatesNear(ServerLevel level, BlockPos pos) {
        return GateHooks.near(level, pos, 2000);
    }

    static AABB around(BlockPos p, int r) {
        return new AABB(p).inflate(r);
    }
}
