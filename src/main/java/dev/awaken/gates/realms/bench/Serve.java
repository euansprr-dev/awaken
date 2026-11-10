package dev.awaken.gates.realms.bench;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * The bench that stays running (tools/bench/bench.sh up): the GameTest server, started outside Gradle, sits in the test
 * realm_bench and answers requests dropped in run-bench/req/ until run-bench/stop appears or nothing has asked for twenty
 * minutes. Each answer is a {@link Bench#check}, so a builder's change to a gate is checked in seconds instead of a
 * Gradle run. Dev tooling behind AWAKEN_BENCH_SERVE: nothing in the game reaches this.
 */
final class Serve {
    private Serve() {}

    private static final long IDLE_MS = 20 * 60 * 1000L;
    private static final String LANDS = "dev.awaken.gates.realms.land.";

    /**
     * A gate's code as bench.sh just rebuilt it: the land package comes from run-bench/classes first, everything else
     * from the running game. A fresh one per request, so each check sees the latest build and the old one can be dropped.
     */
    private static final class LandLoader extends URLClassLoader {
        LandLoader(Path classes, ClassLoader game) throws IOException {
            super(new URL[]{classes.toUri().toURL()}, game);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith(LANDS)) return super.loadClass(name, resolve);
            synchronized (getClassLoadingLock(name)) {
                Class<?> c = findLoadedClass(name);
                if (c == null) {
                    try {
                        c = findClass(name);
                    } catch (ClassNotFoundException e) {
                        return super.loadClass(name, resolve); // not rebuilt: the game's own
                    }
                }
                if (resolve) resolveClass(c);
                return c;
            }
        }
    }

    static void run(Path dir) {
        Path req = dir.resolve("req"), out = dir.resolve("out"), stop = dir.resolve("stop"), ready = dir.resolve("ready");
        Path root = Path.of(System.getenv("AWAKEN_BENCH_ROOT"));
        try {
            Files.createDirectories(req);
            Files.createDirectories(out);
            Files.writeString(ready, Long.toString(ProcessHandle.current().pid()));
            System.out.println("[bench] serving " + dir);
            long last = System.currentTimeMillis();
            while (!Files.exists(stop) && System.currentTimeMillis() - last < IDLE_MS) {
                List<Path> asks;
                try (Stream<Path> s = Files.list(req)) {
                    asks = s.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList();
                }
                if (asks.isEmpty()) {
                    Thread.sleep(50);
                    continue;
                }
                for (Path ask : asks) one(ask, root, dir);
                last = System.currentTimeMillis();
            }
            System.out.println("[bench] " + (Files.exists(stop) ? "told to stop" : "nothing asked for twenty minutes: stopping"));
        } catch (IOException e) {
            throw new RuntimeException("the bench stopped", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            try {
                Files.deleteIfExists(ready);
            } catch (IOException ignored) {
            }
        }
    }

    private static void one(Path file, Path root, Path dir) throws IOException {
        String name = file.getFileName().toString().replaceFirst("\\.json$", "");
        Path out = dir.resolve("out").resolve(name), classes = dir.resolve("classes");
        Bench.Answer answer;
        LandLoader lands = null;
        try {
            JsonObject o = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            double[] eye = null;
            if (o.has("eye")) {
                JsonArray a = o.getAsJsonArray("eye");
                eye = new double[5];
                for (int i = 0; i < 5; i++) eye[i] = a.get(i).getAsDouble();
            }
            if (Files.isDirectory(classes.resolve(LANDS.replace('.', '/')))) lands = new LandLoader(classes, Serve.class.getClassLoader());
            answer = Bench.answer(new Bench.Ask(o.get("what").getAsString(), o.has("seed") ? o.get("seed").getAsLong() : 1,
                    o.has("shot") ? o.get("shot").getAsString() : "mouth", eye, root, out, lands));
        } catch (RuntimeException e) {
            e.printStackTrace();
            Files.createDirectories(out);
            Files.writeString(out.resolve("bench.txt"), "BENCH FAILED: couldn't read the request " + file.getFileName() + ": " + e + "\n");
            answer = new Bench.Answer(false, "");
        } finally {
            if (lands != null) lands.close();
            Files.deleteIfExists(file);
        }
        Files.writeString(dir.resolve("out").resolve(name + ".done"), answer.clean() ? "clean\n" : "not clean\n");
    }
}
