package dev.awaken.bells;

import dev.awaken.Awaken;
import dev.awaken.core.PlayDays;
import dev.awaken.core.api.CitizenHooks;
import dev.awaken.core.api.CityHooks;
import dev.awaken.core.api.Goals.Goal;
import dev.awaken.core.api.Goals.Step;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import dev.awaken.village.api.Villages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The villages' five bells (SPEC §43.16 stage 5). Five villages hid their bells before they walked to the city, so the
 * gates couldn't have them. Each is a quest: someone in Carillon remembers it aloud (one a play-day, when you're near
 * them, once your rank is the bell's), naming a real empty village; by its well there's a letter on a lectern saying
 * exactly where it is; and at the hiding place (sunk in water, buried under a mossy stone, carried up to the highest
 * ground, or kept by a wolf) you hear it, muffled, within 32 of the spot and nowhere else. Bring it home, and the next
 * time you pass the person who told you, they say so in their own words. Never a task list, never a reward screen.
 */
final class VillageBells {
    private VillageBells() {}

    enum Hide { SUNK, BURIED, HIGH, KEPT }

    /** One village's bell: which, who in Carillon remembers it (a named citizen), and how it was hidden. */
    record Quest(String bell, String teller, String name, Hide hide) {}

    static final List<Quest> QUESTS = List.of(
            new Quest("fenwick", "sun", "Sun", Hide.SUNK),
            new Quest("mill", "oren", "Oren", Hide.BURIED),
            new Quest("shepherd", "kestrel", "Kestrel", Hide.KEPT),
            new Quest("hill", "teodor", "Teodor", Hide.HIGH),
            new Quest("ford", "mira", "Mira", Hide.SUNK));

    /** Where a player is on a bell's trail. */
    static final int HEARD = 1, READ = 2, THANKED = 3;
    /** A quest's village: this far from the city at least, and within this. */
    static final int FROM_CITY = 250, WITHIN = 2400;
    /** The teller speaks to someone this near; the letter and bell are laid once someone's this near the well; the hum's reach. */
    static final int TELL_NEAR = 10, LAY_NEAR = 96, HUM = 32;
    static final int HUM_EVERY = 120;
    /** The letter book's tag. */
    static final String LETTER = "awaken_bell_letter";

    static @Nullable Quest quest(String bell) {
        for (Quest q : QUESTS) if (q.bell().equals(bell)) return q;
        return null;
    }

    static int stage(MinecraftServer server, UUID who, int no) {
        Map<Integer, Integer> m = BellWorld.get(server).quests.get(who);
        return m == null ? 0 : m.getOrDefault(no, 0);
    }

    static void stage(MinecraftServer server, UUID who, int no, int st) {
        BellWorld w = BellWorld.get(server);
        w.quests.computeIfAbsent(who, k -> new HashMap<>()).put(no, st);
        w.setDirty();
    }

    // ---- ticking ----------------------------------------------------------------------------------------------------

    static void tick(MinecraftServer server) {
        ServerLevel ow = server.overworld();
        long now = ow.getGameTime();
        for (ServerPlayer p : ow.players()) {
            if (p.isSpectator() && !dev.awaken.veil.Veil.isSpirit(p)) continue;
            if ((now + p.getId()) % HUM_EVERY == 0) hum(p);
            if ((p.tickCount + p.getId()) % 100 != 50) continue;
            if (CityHooks.inCity(ow, p.blockPosition())) city(p);
            lay(p);
            carried(p);
        }
    }

    /** In Carillon: a teller near you remembers a village's bell (one a play-day), or says they heard you found it. */
    static void city(ServerPlayer p) {
        ServerLevel level = p.level();
        MinecraftServer server = level.getServer();
        for (Quest q : QUESTS) {
            BellCatalog.Def d = BellCatalog.of(q.bell());
            int st = stage(server, p.getUUID(), d.no());
            if (st == 0 || st == THANKED || !Bells.has(p, q.bell()) || !near(level, p, q.teller())) continue;
            stage(server, p.getUUID(), d.no(), THANKED);
            speak(p, q.name(), thanks(q.bell()));
            return;
        }
        BellBook b = Bells.book(p);
        long today = PlayDays.current(server);
        if (b.rememberedDay == today) return;
        for (Quest q : QUESTS) {
            BellCatalog.Def d = BellCatalog.of(q.bell());
            if (Bells.has(p, q.bell()) || stage(server, p.getUUID(), d.no()) > 0 || Progression.rank(p).ordinal() < d.rank().ordinal()) continue;
            if (!near(level, p, q.teller())) continue;
            if (tell(p, q)) {
                b.rememberedDay = today;
                Bells.save(p, b);
            }
            return;
        }
    }

    private static boolean near(ServerLevel level, ServerPlayer p, String who) {
        Vec3 at = CitizenHooks.where(level, who);
        return at != null && p.distanceToSqr(at) <= TELL_NEAR * TELL_NEAR;
    }

    /** A teller remembers this bell aloud, naming its village and which way it lies. False if no village is ready yet. */
    static boolean tell(ServerPlayer p, Quest q) {
        BellWorld.Village v = village(p.level(), q);
        if (v == null) return false;
        BellCatalog.Def d = BellCatalog.of(q.bell());
        stage(p.level().getServer(), p.getUUID(), d.no(), HEARD);
        BlockPos from = CityHooks.square(p.level());
        if (from == null) from = p.blockPosition();
        String dir = Bells.toward(v.well.getX() - from.getX(), v.well.getZ() - from.getZ()).replaceFirst("^to ", "");
        speak(p, q.name(), rumour(q.bell(), v.name, dir));
        Bells.say(p, "[" + v.name + " is out " + dir + ". It's on your Tasks.]");
        Awaken.LOG.info("[bells] {} told {} of {} at {} ({})", q.name(), p.getName().getString(), d.id(), v.name, v.well.toShortString());
        return true;
    }

    /** The village chosen for this bell, choosing it the first time (nearest empty, planned village not taken by another). */
    static BellWorld.@Nullable Village village(ServerLevel level, Quest q) {
        BellWorld w = BellWorld.get(level.getServer());
        int no = BellCatalog.of(q.bell()).no();
        BellWorld.Village v = w.villages.get(no);
        if (v != null) return v;
        ServerLevel ow = level.getServer().overworld();
        // a Far Landing world (SPEC §67): the empty villages round the landing, where the game is played
        BlockPos city = home(ow);
        if (city == null) return null;
        Set<String> taken = new HashSet<>();
        for (BellWorld.Village o : w.villages.values()) taken.add(o.key);
        for (Villages.Empty e : Villages.emptyNear(ow, city, WITHIN)) {
            if (e.name() == null || e.well() == null || taken.contains(e.key())) continue;
            if (Math.hypot(e.anchor().getX() - city.getX(), e.anchor().getZ() - city.getZ()) < FROM_CITY) continue;
            return choose(w, no, e.key(), e.name(), e.well());
        }
        return null;
    }

    /** Where the villages are counted from and their way told: the far landing in a Far Landing world (SPEC §67), else the square. */
    static @Nullable BlockPos home(ServerLevel ow) {
        BlockPos l = dev.awaken.core.api.TheLull.landing(ow.getServer());
        return l != null ? l : CityHooks.square(ow);
    }

    static BellWorld.Village choose(BellWorld w, int no, String key, String name, BlockPos well) {
        BellWorld.Village v = new BellWorld.Village(key, name, well.immutable());
        w.villages.put(no, v);
        w.setDirty();
        return v;
    }

    /**
     * Near a quest's village well (once its village is chosen) with its ground loaded: the letter by the well and the bell
     * in its hiding place, laid once; and the bell laid again for someone who doesn't hold it after another has lifted it.
     */
    static void lay(ServerPlayer p) {
        ServerLevel level = p.level();
        BellWorld w = BellWorld.get(level.getServer());
        for (Map.Entry<Integer, BellWorld.Village> e : w.villages.entrySet()) {
            BellWorld.Village v = e.getValue();
            if (Math.hypot(v.well.getX() - p.getX(), v.well.getZ() - p.getZ()) > LAY_NEAR || !level.isLoaded(v.well)) continue;
            Quest q = quest(BellCatalog.of(e.getKey()).id());
            if (q == null) continue;
            if (v.letter == null) {
                laid(level, v, q);
                w.setDirty();
            } else if (v.spot != null && !Bells.has(p, q.bell()) && level.isLoaded(v.spot) && !level.getBlockState(v.spot).is(BellContent.FOUND_BELL.get()))
                bell(level, v.spot, q);
        }
    }

    /** The hiding place chosen and the bell set in it, then the letter saying where, on a lectern by the well. */
    static void laid(ServerLevel level, BellWorld.Village v, Quest q) {
        Hide hide = q.hide();
        BlockPos spot = switch (hide) {
            case SUNK -> sunk(level, v.well);
            case HIGH -> high(level, v.well);
            default -> null;
        };
        if (spot == null && hide == Hide.SUNK) hide = Hide.BURIED;
        if (spot == null) spot = ground(level, v.well, hide == Hide.KEPT ? 12 : 8, hide == Hide.KEPT ? 28 : 18);
        if (spot != null) hideAt(level, v, q, hide, spot);
    }

    /** The bell hidden at {@code spot} (the ground it lies on, or a water bed), and the letter by the well saying where. */
    static void hideAt(ServerLevel level, BellWorld.Village v, Quest q, Hide hide, BlockPos spot) {
        if (hide == Hide.BURIED) {
            BlockPos top = spot.below();
            level.setBlock(top, Blocks.MOSSY_COBBLESTONE.defaultBlockState(), Block.UPDATE_ALL);
            spot = top.below();
            level.setBlock(spot, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        } else if (hide == Hide.HIGH) {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos side = spot.relative(dir);
                if (level.getBlockState(side).canBeReplaced()) level.setBlock(side, Blocks.MOSSY_COBBLESTONE.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        v.spot = spot;
        bell(level, spot, q);
        if (hide == Hide.KEPT) dog(level, spot);
        int dx = spot.getX() - v.well.getX(), dz = spot.getZ() - v.well.getZ();
        int steps = Math.max(5, (int) Math.round(Math.hypot(dx, dz) / 5.0) * 5);
        v.words = letter(q.bell(), hide, v.name, steps, Bells.toward(dx, dz).replaceFirst("^to the ", ""));
        BlockPos at = LeftSpot.by(level, v.well, v.key.hashCode());
        if (at == null) at = v.well.above();
        BlockState lectern = Blocks.LECTERN.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING,
                Direction.fromYRot(Math.toDegrees(Math.atan2(-(v.well.getX() - at.getX()), v.well.getZ() - at.getZ()))));
        level.setBlock(at, lectern, Block.UPDATE_ALL);
        LecternBlock.tryPlaceBook(null, level, at, level.getBlockState(at), book(q, v));
        v.letter = at;
        Awaken.LOG.info("[bells] {}'s letter at {}, the bell {} at {} ({})", v.name, at.toShortString(), q.bell(), spot.toShortString(), hide);
    }

    /** The letter, as a book: the village's words, tagged with its bell. */
    static ItemStack book(Quest q, BellWorld.Village v) {
        ItemStack s = new ItemStack(Items.WRITTEN_BOOK);
        s.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("A letter"), "the people of " + v.name, 0,
                List.of(Filterable.passThrough(Component.literal(v.words))), true));
        CompoundTag tag = new CompoundTag();
        tag.putInt(LETTER, BellCatalog.of(q.bell()).no());
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return s;
    }

    private static void bell(ServerLevel level, BlockPos spot, Quest q) {
        BellCatalog.Def d = BellCatalog.of(q.bell());
        FoundBells.place(level, spot, d.no(), "hidden by the people of its village before they walked to the city", List.of(), 0L, false, false);
    }

    /** Bess's pup: a wolf lying by the Shepherd's Bell. It won't let anyone lift it until it's tamed (or gone). */
    private static void dog(ServerLevel level, BlockPos spot) {
        Wolf wolf = EntityTypes.WOLF.create(level, EntitySpawnReason.TRIGGERED);
        if (wolf == null) return;
        wolf.snapTo(spot.getX() + 1.5, spot.getY(), spot.getZ() + 0.5, 0F, 0F);
        wolf.setPersistenceRequired();
        level.addFreshEntity(wolf);
    }

    /** A wild wolf is lying by the bell at {@code pos}: nobody lifts it yet. */
    static boolean kept(ServerLevel level, BlockPos pos) {
        for (Wolf w : level.getEntitiesOfClass(Wolf.class, new AABB(pos).inflate(4))) if (!w.isTame() && w.isAlive()) return true;
        return false;
    }

    /** Water at least two deep, 6 to 64 blocks from the well: the bell on its bed (the deepest such, nearest first). Null if none. */
    static @Nullable BlockPos sunk(ServerLevel level, BlockPos well) {
        BlockPos best = null;
        int bestDepth = 1;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -64; dx <= 64; dx += 2)
            for (int dz = -64; dz <= 64; dz += 2) {
                double dist = Math.hypot(dx, dz);
                if (dist < 6 || dist > 64) continue;
                int x = well.getX() + dx, z = well.getZ() + dz;
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1, y = top;
                while (y > level.getMinY() && level.getFluidState(new BlockPos(x, y, z)).isSource() && level.getBlockState(new BlockPos(x, y, z)).is(Blocks.WATER)) y--;
                int depth = top - y;
                if (depth < 2 || depth < bestDepth || depth == bestDepth && dist >= bestDist) continue;
                if (!level.getBlockState(new BlockPos(x, y, z)).isFaceSturdy(level, new BlockPos(x, y, z), Direction.UP)) continue;
                best = new BlockPos(x, y + 1, z);
                bestDepth = depth;
                bestDist = dist;
            }
        return best;
    }

    /** The highest ground within 64 of the well (dry, open to the sky), or null. */
    static @Nullable BlockPos high(ServerLevel level, BlockPos well) {
        BlockPos best = null;
        for (int dx = -64; dx <= 64; dx += 3)
            for (int dz = -64; dz <= 64; dz += 3) {
                if (dx * dx + dz * dz > 64 * 64) continue;
                int x = well.getX() + dx, z = well.getZ() + dz;
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                BlockPos at = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
                if (best != null && at.getY() <= best.getY()) continue;
                if (!level.getBlockState(at).isAir() || !level.getFluidState(at.below()).isEmpty()) continue;
                if (!level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)) continue;
                best = at;
            }
        return best;
    }

    /** Open, dry ground {@code near} to {@code far} blocks from the well, about level with it (the village's own fields). */
    static @Nullable BlockPos ground(ServerLevel level, BlockPos well, int near, int far) {
        for (int r = near; r <= far; r += 2)
            for (int k = 0; k < 16; k++) {
                double a = k * Math.PI / 8 + r;
                int x = well.getX() + (int) Math.round(Math.cos(a) * r), z = well.getZ() + (int) Math.round(Math.sin(a) * r);
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                BlockPos at = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
                if (Math.abs(at.getY() - well.getY()) > 6 || !level.getBlockState(at).isAir() || !level.getFluidState(at.below()).isEmpty()) continue;
                BlockState under = level.getBlockState(at.below());
                if (!under.isFaceSturdy(level, at.below(), Direction.UP) || !level.canSeeSky(at)) continue;
                return at;
            }
        return null;
    }

    /** Free ground three to six blocks from the well for the lectern (like the Stilled's left chests). */
    static final class LeftSpot {
        static @Nullable BlockPos by(ServerLevel level, BlockPos well, long seed) {
            double start = Math.floorMod(seed, 360) * Math.PI / 180;
            for (int r = 3; r <= 6; r++)
                for (int k = 0; k < 16; k++) {
                    double a = start + k * Math.PI / 8;
                    int x = well.getX() + (int) Math.round(Math.cos(a) * r), z = well.getZ() + (int) Math.round(Math.sin(a) * r);
                    if (!level.isLoaded(new BlockPos(x, 0, z))) continue;
                    BlockPos feet = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
                    if (Math.abs(feet.getY() - well.getY()) > 4) continue;
                    BlockState under = level.getBlockState(feet.below()), here = level.getBlockState(feet);
                    if (!under.isFaceSturdy(level, feet.below(), Direction.UP) || !under.getFluidState().isEmpty()) continue;
                    if (!here.getFluidState().isEmpty() || !(here.isAir() || here.canBeReplaced())) continue;
                    return feet;
                }
            return null;
        }
    }

    /** Someone used a lectern: if it's a village's letter, they've read where its bell is. */
    static void read(ServerPlayer p, BlockPos pos) {
        BellWorld w = BellWorld.get(p.level().getServer());
        for (Map.Entry<Integer, BellWorld.Village> e : w.villages.entrySet())
            if (pos.equals(e.getValue().letter)) {
                readLetter(p, e.getKey(), e.getValue());
                return;
            }
    }

    /** Each 5 s for someone on a trail: the letter carried in their bag counts as read. */
    static void carried(ServerPlayer p) {
        Map<Integer, Integer> mine = BellWorld.get(p.level().getServer()).quests.get(p.getUUID());
        if (mine == null || !mine.containsValue(HEARD)) return;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            CustomData c = p.getInventory().getItem(i).get(DataComponents.CUSTOM_DATA);
            if (c == null || !c.contains(LETTER)) continue;
            int no = c.copyTag().getIntOr(LETTER, -1);
            BellWorld.Village v = BellWorld.get(p.level().getServer()).villages.get(no);
            if (v != null) readLetter(p, no, v);
        }
    }

    static void readLetter(ServerPlayer p, int no, BellWorld.Village v) {
        MinecraftServer server = p.level().getServer();
        if (stage(server, p.getUUID(), no) >= READ || Bells.has(p, BellCatalog.of(no).id())) return;
        stage(server, p.getUUID(), no, READ);
        Bells.say(p, "[It's a letter from the people of " + v.name + ", about their bell. It says where they hid it.]");
    }

    /** Within 32 of a bell you're on the trail of, and only there: its note, muffled, now and then, to you alone. */
    static void hum(ServerPlayer p) {
        Map<Integer, Integer> mine = BellWorld.get(p.level().getServer()).quests.get(p.getUUID());
        if (mine == null) return;
        for (Map.Entry<Integer, Integer> e : mine.entrySet()) {
            if (e.getValue() < HEARD || e.getValue() == THANKED) continue;
            BellWorld.Village v = BellWorld.get(p.level().getServer()).villages.get(e.getKey());
            if (v == null || v.spot == null || Bells.has(p, BellCatalog.of(e.getKey()).id())) continue;
            if (p.distanceToSqr(Vec3.atCenterOf(v.spot)) > HUM * HUM) continue;
            // its own note, muffled through the ground: lifted, the same note rings out clear
            var note = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(BellContent.muffled(e.getKey()));
            p.connection.send(new ClientboundSoundPacket(note, SoundSource.AMBIENT, v.spot.getX() + 0.5, v.spot.getY() + 0.5, v.spot.getZ() + 0.5,
                    1.2F, 1F, p.level().getRandom().nextLong()));
        }
    }

    // ---- words ------------------------------------------------------------------------------------------------------

    private static void speak(ServerPlayer p, String who, String line) {
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(who)).body(Component.literal(line))
                .dialogue().ttl(20 * 14).priority(8).build());
    }

    static String rumour(String bell, String village, String dir) {
        return switch (bell) {
            case "fenwick" -> "You're the one bringing the old bells home? My gran came from " + village + ", out " + dir + ". It's empty now. Before they walked here, "
                    + "they sank their bell, Fenwick's Bell, so the gates couldn't have it. She always said they left a letter by the well saying where.";
            case "mill" -> "My father made the bolts for the Mill Bell at " + village + ", out " + dir + ". When they left, the miller buried it rather than let the gates "
                    + "take it. There's a letter about it by the well. Go and read it.";
            case "shepherd" -> "I used to run messages out to " + village + ", " + dir + ", before it emptied. The shepherds there had a bell for the flock. They left it "
                    + "in the fold with their old dog, and she wouldn't leave it. Somebody wrote it all down and left the letter by the well.";
            case "hill" -> "The archive has a note from " + village + ", out " + dir + ". Their bell, the Hill Bell, was carried up to the highest ground they could find "
                    + "and left there for the sky to look after. They left a letter by the well saying where.";
            default -> "You hear things on the watch. The people of " + village + ", out " + dir + ", dropped their bell in the water before they came in. "
                    + "There's a letter by their well that says where.";
        };
    }

    static String letter(String bell, Hide hide, String village, int steps, String dir) {
        String where = steps + " steps " + dir + " of the well";
        return switch (hide) {
            case SUNK -> "We sank the bell in the water " + where + ", where it's deepest. Mam cried. Whoever finds this, take it somewhere it can ring again.\n\nThe people of " + village;
            case BURIED -> "We buried the bell " + where + ", under the mossy stone. Dig there. We'd rather it slept in the ground than rang for the gates.\n\nThe people of " + village;
            case HIGH -> "We carried the bell up to the highest ground we could find, " + where + ", and built stones round it. Let the sky look after it till someone comes.\n\nThe people of " + village;
            case KEPT -> "We left the bell in the fold, " + where + ". Bess lay down beside it and wouldn't come with us. If her pups are still there, be kind: a bone will win them over.\n\nThe people of " + village;
        };
    }

    static String thanks(String bell) {
        return switch (bell) {
            case "fenwick" -> "You found Fenwick's Bell? My gran was married under that bell. Hang it somewhere it'll be heard.";
            case "mill" -> "You dug up the Mill Bell! My father's bolts still holding, I'd bet. Thank you.";
            case "shepherd" -> "You got the shepherds' bell back? And the dog let you? Then she's found someone to look after at last.";
            case "hill" -> "The Hill Bell, home at last. I'll write it in the archive: found, and ringing again.";
            default -> "I heard you fished the Ford Bell out of the water. Good. It never did like being quiet.";
        };
    }

    // ---- the Tasks page ---------------------------------------------------------------------------------------------

    /** Each village bell you're on the trail of: go to the village and read the letter, then find the bell where it says. */
    static List<Step> steps(ServerPlayer p) {
        Map<Integer, Integer> mine = BellWorld.get(p.level().getServer()).quests.get(p.getUUID());
        if (mine == null) return List.of();
        List<Step> out = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : mine.entrySet()) {
            BellCatalog.Def d = BellCatalog.of(e.getKey());
            BellWorld.Village v = BellWorld.get(p.level().getServer()).villages.get(e.getKey());
            if (v == null || Bells.has(p, d.id()) || e.getValue() == THANKED) continue;
            String name = d.name().replaceFirst("^The ", "the ");
            if (e.getValue() == HEARD)
                out.add(Step.of("bells/village/" + d.no(), Goal.BELLS, "Find the letter about " + name + " in " + v.name).lead()
                        .why("They left it by the well before they walked to the city. It says where they hid their bell.").priority(38).at(v.well).build());
            else
                out.add(Step.of("bells/village/" + d.no(), Goal.BELLS, "Find " + name + " where the letter says").lead()
                        .why(v.words.isEmpty() ? "" : v.words.substring(0, v.words.indexOf('\n') < 0 ? v.words.length() : v.words.indexOf('\n')))
                        .priority(38).build());
        }
        return out;
    }
}
