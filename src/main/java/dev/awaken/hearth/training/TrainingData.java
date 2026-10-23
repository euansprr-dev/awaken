package dev.awaken.hearth.training;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

/** Training Grounds courses (by Start Gate) and the ghost of each player's best run per course. */
public final class TrainingData extends SavedData {
    public static final int SCHEMA = 1;

    /** One course: rings in order, an optional finish, any number of dummies. */
    public static final class Course {
        public final String dimension;
        public final BlockPos start;
        public final List<BlockPos> rings = new ArrayList<>();
        public final List<BlockPos> dummies = new ArrayList<>();
        public BlockPos finish;

        public Course(String dimension, BlockPos start) {
            this.dimension = dimension;
            this.start = start;
        }

        public String key() {
            return key(dimension, start);
        }

        public static String key(String dimension, BlockPos start) {
            return dimension + "|" + start.getX() + "," + start.getY() + "," + start.getZ();
        }

        public boolean runnable() {
            return finish != null || !dummies.isEmpty();
        }

        private record Saved(String dimension, BlockPos start, List<BlockPos> rings, List<BlockPos> dummies, Optional<BlockPos> finish) {}

        static final Codec<Course> CODEC = RecordCodecBuilder.<Saved>create(i -> i.group(
                Codec.STRING.fieldOf("dimension").forGetter(Saved::dimension),
                BlockPos.CODEC.fieldOf("start").forGetter(Saved::start),
                BlockPos.CODEC.listOf().fieldOf("rings").forGetter(Saved::rings),
                BlockPos.CODEC.listOf().fieldOf("dummies").forGetter(Saved::dummies),
                BlockPos.CODEC.optionalFieldOf("finish").forGetter(Saved::finish)
        ).apply(i, Saved::new)).xmap(s -> {
            Course c = new Course(s.dimension, s.start);
            c.rings.addAll(s.rings);
            c.dummies.addAll(s.dummies);
            c.finish = s.finish.orElse(null);
            return c;
        }, c -> new Saved(c.dimension, c.start, List.copyOf(c.rings), List.copyOf(c.dummies), Optional.ofNullable(c.finish)));
    }

    private static final Codec<int[]> INTS = Codec.INT_STREAM.xmap(IntStream::toArray, IntStream::of);

    public static final Codec<TrainingData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("schema", SCHEMA).forGetter(d -> SCHEMA),
            Course.CODEC.listOf().fieldOf("courses").forGetter(d -> List.copyOf(d.courses.values())),
            Codec.unboundedMap(Codec.STRING, INTS).fieldOf("ghosts").forGetter(d -> d.ghosts)
    ).apply(i, TrainingData::new));

    public static final SavedDataType<TrainingData> TYPE = new SavedDataType<>(Awaken.id("training"), TrainingData::new, CODEC);

    final Map<String, Course> courses = new LinkedHashMap<>();
    /** "player-uuid|course-key" → float bits x,y,z,yaw per tick. */
    final Map<String, int[]> ghosts = new HashMap<>();

    public TrainingData() {}

    private TrainingData(int schema, List<Course> list, Map<String, int[]> ghosts) {
        for (Course c : list) courses.put(c.key(), c);
        this.ghosts.putAll(ghosts);
    }

    public static TrainingData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public Map<String, Course> courses() {
        return courses;
    }
}
