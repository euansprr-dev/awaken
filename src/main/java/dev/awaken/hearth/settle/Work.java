package dev.awaken.hearth.settle;

import dev.awaken.Awaken;
import dev.awaken.hearth.HearthContent;
import dev.awaken.hearth.HearthRecord;
import dev.awaken.hearth.HearthService;
import dev.awaken.hearth.Household;
import dev.awaken.hearth.Upgrades;
import dev.awaken.hearth.room.Room;
import dev.awaken.hearth.room.RoomType;
import dev.awaken.signal.Signal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What the people who live with you do with what you bring home (SPEC §28.8, PLAN 4.7; Euan: "you bring back materials and
 * you give them to workers inside of your village"). Oren takes commissions: hand him the makings of a piece and he
 * forges it overnight, a temper up, into the Forge's chest. Hettie harvests ripe crops and replants them, the harvest
 * into the chest nearest the field, and keeps two of each beast in the pen breeding. Ilse brews the herbs you bring into
 * tonics overnight (home-cooked, so they last half again). The miner brings up the evening's ore. Wren
 * strings rope lines between your roofs, one a play-day. Run once a second while the home is loaded; nothing allocates
 * unless there's work to do.
 */
public final class Work {
    private Work() {}

    /** The evening haul comes up at 17:18 (the miner's day ends; time of day, 0 = 06:00). */
    public static final int HAUL_TOD = 11300;

    /** The word under a settler's name. */
    static String title(Folk.Person p) {
        return switch (p.role()) {
            case "oren", "smith" -> "smith";
            case "mira" -> "scout";
            case "teodor" -> "archivist";
            case "sun" -> "healer";
            case "vael" -> "rival";
            case "ilse" -> "herbalist";
            case "hettie", "farmer" -> "farmer";
            case "wren" -> "rope-runner";
            case "pell" -> "courier";
            case "miner" -> "miner";
            default -> "of the house";
        };
    }

    /** What the household is told they'll do here, when they move in. */
    static String welcome(Folk.Person p) {
        return switch (p.role()) {
            case "oren" -> "He tempers at the forge, and takes commissions: hand him the makings, a piece by morning.";
            case "smith" -> "A smith: hand over the makings for a piece and it's done by morning.";
            case "mira" -> "She keeps the watch at dusk and hears things. Ask her.";
            case "teodor" -> "He reads what you bring back: relics, logs, your Codex.";
            case "sun" -> "She mends what hurts, the Fracture too.";
            case "vael" -> "Don't ask.";
            case "ilse" -> "She works the greenhouse. Bring her herbs: tonics by morning.";
            case "hettie", "farmer" -> "Your fields are tended now, and the pen kept breeding. The harvest goes in the chest by the field.";
            case "wren" -> "She'll string lines between your roofs.";
            case "pell" -> "Letters and news, once a play-day.";
            case "miner" -> "Your shaft is worked now: each evening the ore of its depth comes up.";
            default -> "Another pair of hands about the place.";
        };
    }

    // ---- once a second ----------------------------------------------------------------------------------------------

    public static void tick(ServerLevel level, HearthRecord h, long now) {
        Folk f = h.folk;
        if (f.settlers.isEmpty() && f.commissions.isEmpty() && f.brews.isEmpty()) return;
        long day = Settlers.today(level.getServer());
        int tod = (int) Math.floorMod(level.getOverworldClockTime(), 24000L);
        deliver(level, h, day);
        if (tod >= HAUL_TOD && f.haulDay < day) evening(level, h, day);
        Folk.Settler hettie = farmer(f);
        if (hettie != null && now % 600 < 20 && working(tod)) harvest(level, h, 6);
        if (hettie != null && tod >= 3800 && tod < 11000 && f.breedDay < day) breed(level, h, day);
        Folk.Settler wren = role(f, "wren");
        if (wren != null && tod >= 2000 && tod < 11000 && f.ropeDay < day && f.ropes.size() < 6) {
            f.ropeDay = day;
            if (Ropes.hang(level, h) != null) HearthService.dirty(level.getServer());
        }
    }

    static boolean working(int tod) {
        return tod >= 1000 && tod < 5400 || tod >= 6500 && tod < 11300;
    }

    static Folk.@Nullable Settler role(Folk f, String role) {
        for (Folk.Settler s : f.settlers) if (s.home() && role.equals(s.who.role())) return s;
        return null;
    }

    static Folk.@Nullable Settler farmer(Folk f) {
        Folk.Settler s = role(f, "hettie");
        return s != null ? s : role(f, "farmer");
    }

    // ---- Oren's commissions -----------------------------------------------------------------------------------------

    /** The best piece Oren could forge from what a player carries now (a weapon or armour, never one a Blueprint shapes), or null. */
    public static @Nullable RecipeHolder<CraftingRecipe> commissionFor(ServerPlayer sp) {
        StackedItemContents have = new StackedItemContents();
        sp.getInventory().fillStackedContents(have);
        RecipeHolder<CraftingRecipe> best = null;
        int bestScore = Integer.MIN_VALUE;
        for (RecipeHolder<CraftingRecipe> r : sp.level().getServer().getRecipeManager().recipeMap().byType(RecipeType.CRAFTING)) {
            ItemStack out = result(r.value());
            if (out.isEmpty() || !Upgrades.isTemperable(out) || blueprint(r.value())) continue;
            if (!have.canCraft(r.value(), null)) continue;
            int score = (BuiltInRegistries.ITEM.getKey(out.getItem()).getNamespace().equals(Awaken.MODID) ? 1000 : 0)
                    + r.value().placementInfo().ingredients().size() * 10 + out.getRarity().ordinal();
            if (score > bestScore) {
                bestScore = score;
                best = r;
            }
        }
        return best;
    }

    static ItemStack result(CraftingRecipe r) {
        try {
            return r.assemble(net.minecraft.world.item.crafting.CraftingInput.EMPTY);
        } catch (RuntimeException e) {
            return ItemStack.EMPTY;
        }
    }

    private static boolean blueprint(Recipe<?> r) {
        for (var ing : r.placementInfo().ingredients()) {
            for (var it = ing.items().iterator(); it.hasNext(); ) {
                if (BuiltInRegistries.ITEM.getKey(it.next().value()).getPath().contains("blueprint")) return true;
            }
        }
        return false;
    }

    /** Takes the makings of a commissioned piece from the player (exactly what the recipe picks); false if they're gone. */
    public static boolean take(ServerPlayer sp, CraftingRecipe r) {
        StackedItemContents have = new StackedItemContents();
        sp.getInventory().fillStackedContents(have);
        List<Holder<Item>> picked = new ArrayList<>();
        if (!have.canCraft(r, picked::add)) return false;
        var inv = sp.getInventory();
        for (Holder<Item> h : picked) {
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (s.is(h.value())) {
                    s.shrink(1);
                    break;
                }
            }
        }
        return true;
    }

    /** Oren takes the piece: done by the next play-day, a temper up. */
    public static void commission(ServerPlayer sp, HearthRecord h, CraftingRecipe r) {
        ItemStack out = result(r);
        h.folk.commissions.add(new Folk.Commission(sp.getUUID(), BuiltInRegistries.ITEM.getKey(out.getItem()).toString(), 1,
                Settlers.today(sp.level().getServer()) + 1));
        HearthService.dirty(sp.level().getServer());
    }

    public static Folk.@Nullable Commission commissionOf(HearthRecord h, UUID player) {
        for (Folk.Commission c : h.folk.commissions) if (c.player().equals(player)) return c;
        return null;
    }

    // ---- Ilse's tonics ----------------------------------------------------------------------------------------------

    /** The tonic an herb becomes in Ilse's hands (two herbs a tonic), or null when it isn't one of hers. */
    static @Nullable Item tonic(Item herb) {
        String id = BuiltInRegistries.ITEM.getKey(herb).getPath();
        String out = switch (id) {
            case "sunleaf", "emberroot" -> "herb_broth";
            case "frostcap" -> "frostcap_tea";
            case "moonmoss" -> "moonmoss_soup";
            case "spiritgrass", "duskbloom" -> "spirit_tea";
            default -> null;
        };
        if (out == null) return null;
        Item i = BuiltInRegistries.ITEM.getValue(Awaken.id(out));
        return i == Items.AIR ? null : i;
    }

    /** Ilse takes the herbs a player holds (up to sixteen), tonics by the next play-day. True when she took some. */
    public static boolean brew(ServerPlayer sp, HearthRecord h) {
        ItemStack held = sp.getMainHandItem();
        if (held.isEmpty() || tonic(held.getItem()) == null) return false;
        int n = Math.min(16, held.getCount());
        String herb = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        held.shrink(n);
        h.folk.brews.add(new Folk.Brew(sp.getUUID(), herb, n, Settlers.today(sp.level().getServer()) + 1));
        HearthService.dirty(sp.level().getServer());
        return true;
    }

    // ---- the morning: what was made overnight ------------------------------------------------------------------------

    static void deliver(ServerLevel level, HearthRecord h, long day) {
        Folk f = h.folk;
        if (!f.commissions.isEmpty()) {
            for (Folk.Commission c : List.copyOf(f.commissions)) {
                if (c.ready() > day) continue;
                f.commissions.remove(c);
                Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(c.item()));
                if (item == Items.AIR) continue;
                ItemStack piece = new ItemStack(item);
                Upgrades.applyTemper(piece, Math.max(1, c.temper()));
                BlockPos at = anvil(level, h);
                ItemStack left = Chests.put(level, h, piece, at, 10);
                if (!left.isEmpty()) f.stash.add(new Folk.Stack(c.item(), 1, "oren:" + c.player()));
                level.playSound(null, at, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6f, 1.25f);
                ServerPlayer p = level.getServer().getPlayerList().getPlayer(c.player());
                if (p != null) Signal.toast(p, Component.literal("Oren finished your " + piece.getHoverName().getString() + " · tempered I · "
                        + (left.isEmpty() ? "in the forge's chest" : "ask him for it")));
                HearthService.dirty(level.getServer());
            }
        }
        if (!f.brews.isEmpty()) {
            for (Folk.Brew b : List.copyOf(f.brews)) {
                if (b.ready() > day) continue;
                f.brews.remove(b);
                Item herb = BuiltInRegistries.ITEM.getValue(Identifier.parse(b.herb()));
                Item tonic = tonic(herb);
                if (tonic == null) continue;
                ItemStack out = new ItemStack(tonic, Math.max(1, b.count() / 2));
                out.set(HearthContent.HOME_COOKED.get(), true);
                BlockPos at = greenhouse(h);
                ItemStack left = Chests.put(level, h, out, at, 10);
                if (!left.isEmpty()) f.stash.add(new Folk.Stack(BuiltInRegistries.ITEM.getKey(tonic).toString(), left.getCount(), "ilse:" + b.player()));
                ServerPlayer p = level.getServer().getPlayerList().getPlayer(b.player());
                if (p != null) Signal.toast(p, Component.literal("Ilse's tonics are ready · " + out.getCount() + " " + out.getHoverName().getString()
                        + " · " + (left.isEmpty() ? "in the greenhouse's chest" : "ask her for them")));
                HearthService.dirty(level.getServer());
            }
        }
    }

    static BlockPos anvil(ServerLevel level, HearthRecord h) {
        Folk.Settler oren = role(h.folk, "oren");
        HomePeople.Where w = oren == null ? null : HomePeople.workOf(level, h, oren.who);
        if (w != null) return w.at();
        for (Room r : h.rooms) if (r.types().contains(RoomType.ARMORY)) return r.centre();
        return h.pos;
    }

    static BlockPos greenhouse(HearthRecord h) {
        for (Room r : h.rooms) if (r.types().contains(RoomType.GREENHOUSE)) return r.centre();
        return h.pos;
    }

    // ---- the evening haul -------------------------------------------------------------------------------------------

    /** Each shaft worked (a miner who lives here: one a shaft) brings up its haul. */
    public static void evening(ServerLevel level, HearthRecord h, long day) {
        Folk f = h.folk;
        f.haulDay = day;
        HearthService.dirty(level.getServer());
        List<MineHead.Shaft> shafts = MineHead.shafts(level, h);
        if (shafts.isEmpty()) return;
        int miners = 0;
        for (Folk.Settler s : f.settlers) if (s.home() && "miner".equals(s.who.role())) miners++;
        int workers = Math.min(shafts.size(), miners);
        List<String> said = new ArrayList<>();
        for (int k = 0; k < workers; k++) {
            MineHead.Shaft s = shafts.get(k);
            dev.awaken.delve.Ore lode = s.lode().map(dev.awaken.delve.api.Lodes.View::ore).orElse(null);
            int left = s.lode().map(v -> MineHead.lodeBudget(v) - f.lodeTaken.getOrDefault(v.id(), 0)).orElse(0);
            List<ItemStack> haul = MineHead.haul(level.getRandom(), s.foot(), lode, Math.max(0, left));
            for (ItemStack st : haul) {
                if (lode != null && st.is(MineHead.item(lode))) s.lode().ifPresent(v -> f.lodeTaken.merge(v.id(), st.getCount(), Integer::sum));
                said.add(st.getCount() + " " + st.getHoverName().getString().toLowerCase(java.util.Locale.ROOT));
                h.oresBrought += st.getCount();
                ItemStack rest = Chests.put(level, h, st.copy(), s.hatch(), 10);
                if (!rest.isEmpty()) f.stash.add(new Folk.Stack(BuiltInRegistries.ITEM.getKey(rest.getItem()).toString(), rest.getCount(), "mine"));
            }
            BlockPos top = s.hatch().above();
            level.playSound(null, top, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 0.7f, 0.7f);
            level.playSound(null, top, SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.5f, 0.9f);
            level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,
                    net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()), top.getX() + 0.5, top.getY() + 0.2, top.getZ() + 0.5, 18, 0.4, 0.1, 0.4, 0.05);
        }
        if (said.isEmpty()) return;
        String lodeWord = shafts.getFirst().lode().map(v -> ", and " + v.ore().word() + " from the lode the shaft cut").orElse("");
        for (ServerPlayer p : Household.online(level.getServer(), h)) {
            if (p.level() == level && h.contains(p.blockPosition())) {
                Signal.toast(p, Component.literal("The mine's haul · " + String.join(", ", said.subList(0, Math.min(3, said.size()))) + lodeWord),
                        dev.awaken.core.api.Notices.Tier.AMBIENT, "hearth_mine");
            }
        }
    }

    // ---- Hettie's fields and pens -----------------------------------------------------------------------------------

    /** Up to {@code n} ripe crops in the home's fields are cut and replanted, the harvest in the chest by the field. */
    public static int harvest(ServerLevel level, HearthRecord h, int n) {
        int done = 0;
        for (BlockPos f : h.yard.fields()) {
            if (done >= n) break;
            BlockPos crop = f.above();
            if (!level.isLoaded(crop)) continue;
            BlockState s = level.getBlockState(crop);
            if (!(s.getBlock() instanceof CropBlock c) || !c.isMaxAge(s)) continue;
            List<ItemStack> drops = Block.getDrops(s, level, crop, null);
            level.levelEvent(2001, crop, Block.getId(s));
            level.setBlock(crop, c.getStateForAge(0), Block.UPDATE_ALL);
            // keep one seed back for the replanting
            boolean kept = false;
            for (ItemStack d : drops) {
                if (!kept && d.getItem() == c.asItem() || !kept && d.getItem() == seedOf(s)) {
                    d.shrink(1);
                    kept = true;
                }
                if (d.isEmpty()) continue;
                ItemStack left = Chests.put(level, h, d, crop, 12);
                if (!left.isEmpty()) h.folk.stash.add(new Folk.Stack(BuiltInRegistries.ITEM.getKey(left.getItem()).toString(), left.getCount(), "field"));
            }
            h.cropsHarvested++;
            done++;
        }
        if (done > 0) HearthService.dirty(level.getServer());
        return done;
    }

    private static Item seedOf(BlockState s) {
        return s.getBlock().asItem();
    }

    /** Two of each kind of beast in the pen breed, once a play-day, while there are fewer than ten of that kind. */
    public static int breed(ServerLevel level, HearthRecord h, long day) {
        h.folk.breedDay = day;
        HearthService.dirty(level.getServer());
        if (h.yard.beasts().isEmpty()) return 0;
        BlockPos c = h.yard.beasts().getFirst();
        Map<EntityType<?>, List<Animal>> kinds = new HashMap<>();
        for (Animal a : level.getEntitiesOfClass(Animal.class, new AABB(c).inflate(16, 6, 16))) {
            if (a instanceof net.minecraft.world.entity.TamableAnimal || !h.contains(a.blockPosition())) continue;
            kinds.computeIfAbsent(a.getType(), k -> new ArrayList<>()).add(a);
        }
        int bred = 0;
        for (List<Animal> list : kinds.values()) {
            if (list.size() >= 10) continue;
            List<Animal> ready = new ArrayList<>();
            for (Animal a : list) if (!a.isBaby() && a.canFallInLove()) ready.add(a);
            if (ready.size() < 2) continue;
            ready.get(0).setInLove(null);
            ready.get(1).setInLove(null);
            bred++;
        }
        return bred;
    }

    /** What the settlers kept to hand over that's this player's (or anyone's), into their pack. */
    static int handOver(ServerPlayer sp, HearthRecord h, String from) {
        int n = 0;
        for (Folk.Stack s : List.copyOf(h.folk.stash)) {
            boolean mine = s.from().equals(from) || s.from().equals(from + ":" + sp.getUUID());
            if (!mine) continue;
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(s.item()));
            h.folk.stash.remove(s);
            if (item == Items.AIR) continue;
            ItemStack st = new ItemStack(item, s.count());
            if (from.equals("oren")) Upgrades.applyTemper(st, 1);
            if (from.equals("ilse")) st.set(HearthContent.HOME_COOKED.get(), true);
            if (!sp.getInventory().add(st)) sp.drop(st, false, net.minecraft.util.Prediction.SERVER_ONLY);
            n += s.count();
        }
        if (n > 0) HearthService.dirty(sp.level().getServer());
        return n;
    }

    /** A direction word from a to b ("north-east"). */
    static String way(BlockPos a, BlockPos b) {
        return dev.awaken.hearth.town.Rumours.direction(a, b);
    }

    static Direction facing(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ();
        return Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
    }
}
