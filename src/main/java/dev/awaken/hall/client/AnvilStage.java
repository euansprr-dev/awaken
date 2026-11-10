package dev.awaken.hall.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.awaken.arms.ArmsArmoury;
import dev.awaken.arms.ArmsData;
import dev.awaken.arms.ArmsForge;
import dev.awaken.arms.ArmsItems;
import dev.awaken.arms.Marks;
import dev.awaken.arms.anvil.AnvilPayloads;
import dev.awaken.arms.anvil.AnvilWork;
import dev.awaken.arms.anvil.client.AnvilClient;
import dev.awaken.arms.client.ArmouryCache;
import dev.awaken.core.Forces;
import dev.awaken.hearth.Upgrades;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * At the War Anvil with a weapon in your hand (SPEC §52): no window. Your weapon hangs over your fire with your forge dim
 * behind it, and three rites wait under it. Point at one and the steel shows the change, the numbers drawn out of it; pick
 * one and what it takes comes up. Every word and number is the server's, from the code that does the work.
 */
public final class AnvilStage extends Screen implements dev.awaken.signal.client.QuietScreen, dev.awaken.signal.client.ReadingScreen {
    private static final int KEY_HERO = 9;
    private static final int TEMPER = 0, REFORGE = 1, ATTUNE = 2;
    private static final float CX = 854F, CY = 352F, CY_ARMED = 322F, ROLL = 82F, LENGTH = 1120F;
    private static final float SEAL_W = 310F, SEAL_GAP = 90F, SEAL_TOP = 648F, SEAL_ICON = 128F;
    private static final float SMALL_X = 28F, SMALL_Y = 96F, SMALL_STEP = 74F, SMALL_ICON = 56F;
    private static final float TAG_LEFT = 300F, TAG_RIGHT = 1290F, TAG_Y = 122F;
    private static final float HOLD_X0 = 554F, HOLD_X1 = 1154F, HOLD_Y0 = 812F, HOLD_Y1 = 900F;
    private static final float PLATE_W = 1040F, NEED_W = 170F, NEED_H = 128F;
    private static final float STONES_TOP = 590F, STONE_STEP = 190F;
    private static final int FORGE = 0xFFF2B35F;

    private AnvilPayloads.Held held;
    private final ItemStack weapon;
    private final String id;
    private @Nullable Stage stage;
    private long last = System.nanoTime(), version;
    private float time, fly, armT, landing, shake, heat = -1F, front = 1F;

    /** The rite picked (-1 none), the one pointed at, and the stone. */
    private int armed = -1, hover = -1, pointed = -1, stone, stoneHover = -1;
    private boolean holdKey, holdMouse, tracked;
    private final Panel.Hold hold = new Panel.Hold();

    // the words, built when what they read changes
    private boolean dirty = true;
    private final Component name, reforgeTitle = Stage.blackletter("Reforge"), temperTitle = Stage.blackletter("Temper"), attuneTitle = Stage.blackletter("Attune");
    private final List<Panel.Need> needs = new ArrayList<>(6), small = new ArrayList<>(3);
    private List<FormattedCharSequence> says = List.of(), notes = List.of(), why = List.of(), where = List.of();
    private final List<int[]> noteTones = new ArrayList<>();
    private int whyColour = Stage.LANTERN, hoverNeed = -1;
    private String actLabel = "";
    private boolean canAct;

    private float armouryX0;
    private final ItemStack hammer = icon("awaken:smith_hammer"), forgeIcon = icon("awaken:forge");
    private ItemStack[] stoneIcons;

    private static ItemStack icon(String item) {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(item)));
    }

    private void readStones() {
        stoneIcons = held.stones().stream().map(st -> icon(st.item())).toArray(ItemStack[]::new);
    }

    private AnvilStage(AnvilPayloads.Held held) {
        super(Component.literal("War Anvil"));
        this.held = held;
        var player = Minecraft.getInstance().player;
        this.weapon = player == null ? ItemStack.EMPTY : player.getMainHandItem().copy();
        ArmsData.Weapon data = ArmsItems.of(weapon.getItem());
        this.id = data == null ? "wildhide_longsword" : data.id();
        this.name = Stage.blackletter(weapon.getHoverName().getString());
        this.stone = firstStone();
        readStones();
    }

    /** The weapon came to the anvil, or the server sent its lines again. */
    public static void show(AnvilPayloads.Held msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof AnvilStage open && open.held.pos().equals(msg.pos())) {
            open.held = msg;
            open.readStones();
            open.landing = 0F;
            open.dirty = true;
        } else {
            HallSounds.open();
            mc.gui.setScreen(new AnvilStage(msg));
        }
    }

    private int firstStone() {
        List<AnvilPayloads.Stone> list = held.stones();
        for (AnvilPayloads.Stone s : list) if (s.held()) return s.force();
        for (AnvilPayloads.Stone s : list) if (s.ready()) return s.force();
        for (AnvilPayloads.Stone s : list) if (s.have() > 0) return s.force();
        for (AnvilPayloads.Stone s : list) if (s.on()) return s.force();
        return 0;
    }

    private AnvilPayloads.@Nullable Stone selected() {
        return stone >= 0 && stone < held.stones().size() ? held.stones().get(stone) : null;
    }

    private AnvilPayloads.Option option(int rite) {
        return held.options().get(rite);
    }

    /** Dev: what a click would pick. */
    void show(String what, int n) {
        switch (what) {
            case "rite" -> arm(n > 2 ? -1 : n);
            case "stone" -> pickStone(n);
            case "point" -> pointed = n > 2 ? -1 : n;
            default -> {}
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        var player = Minecraft.getInstance().player;
        if (landing <= 0F && (player == null || !ItemStack.isSameItem(player.getMainHandItem(), weapon))) onClose();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        // your own forge, out of focus and dim behind the steel
        extractBlurredBackground(g);
        g.fill(0, 0, width, height, 0xB0070403);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        long now = System.nanoTime();
        float dt = Math.min(.1F, (now - last) / 1_000_000_000F);
        last = now;
        time += dt;
        if (stage == null) stage = new Stage(font);
        Stage s = stage;
        s.begin(g, width, height);
        // the floor falls away into shade, so the words under the steel never fight the fire behind them
        int left = (int) Math.floor(-s.ox / s.k), right = (int) Math.ceil(Stage.W + s.ox / s.k), below = (int) Math.ceil(Stage.H + s.oy / s.k);
        g.fillGradient(left, 500, right, Stage.H, 0x00000000, 0xC0050302);
        if (below > Stage.H) g.fill(left, Stage.H, right, below, 0xC0050302); // to the window's edge, whatever its shape
        frame(s, dt, mx, my);
        s.endBare();
    }

    private void frame(Stage s, float dt, int mouseX, int mouseY) {
        float mx = s.sx(mouseX), my = s.sy(mouseY);
        fly = Stage.calm() ? 1F : Math.min(1F, fly + dt / .5F);
        armT += ((armed >= 0 ? 1F : 0F) - armT) * Math.min(1F, dt * 10F);
        if (landing > 0F) landing = Math.min(1F, landing + dt / .28F);
        if (armed < 0) hover = pointed >= 0 ? pointed : fly >= 1F && landing <= 0F ? sealAt(mx, my) : -1;
        rebuild(s);
        steel(dt);
        s.glow(CX, 560F, 780F, 300F, 0xFF8A3A, .34F);
        s.glow(CX, CY, 620F, 420F, 0xFFCF7A, .14F);
        hero(s, dt);
        s.g.nextStratum();
        float e = 1F - (1F - fly) * (1F - fly) * (1F - fly);
        title(s);
        tags(s, e);
        seals(s, mx, my);
        if (armT > .55F && landing <= 0F) plate(s, mx, my, dt);
        foot(s, mx, my);
    }

    // ---- the steel ----------------------------------------------------------------------------------------------------

    private int focus() {
        return armed >= 0 ? armed : hover;
    }

    private void steel(float dt) {
        int f = focus();
        float wantHeat = -1F, wantFront = 1F;
        if (f == TEMPER && !option(TEMPER).changes().isEmpty()) {
            wantHeat = .55F + .075F * (float) Math.sin(time * 2.4F);
            wantFront = Math.clamp(1F - (time * .55F) % 1.4F, 0F, 1F);
        } else if (f == ATTUNE && shownStone() != null) {
            wantHeat = .72F;
            wantFront = .15F;
        }
        boolean pressing = hold.amount > 0F;
        if (pressing) {
            wantHeat = .85F;
            wantFront = 1F - hold.amount;
            front = wantFront;
        } else {
            front += (wantFront - front) * Math.min(1F, dt * 14F);
        }
        heat = wantHeat;
        shake = hold.amount * hold.amount;
    }

    private AnvilPayloads.@Nullable Stone shownStone() {
        if (armed == ATTUNE) return stoneHover >= 0 ? held.stones().get(stoneHover) : selected();
        for (AnvilPayloads.Stone s : held.stones()) if (s.on()) return s;
        return selected();
    }

    private void hero(Stage s, float dt) {
        Meshes.Up up = Meshes.up(id);
        if (up == null) return;
        float e = 1F - (1F - fly) * (1F - fly) * (1F - fly);
        float sc = LENGTH / up.length() * (.35F + .65F * e) * (1F - .08F * armT);
        float sway = (float) Math.sin(time * .6F) * 7F, bob = (float) Math.sin(time * .9F) * 5F;
        float cy = 690F + (CY + (CY_ARMED - CY) * armT + bob - 690F) * e;
        float cx = CX + (float) Math.sin(time * 70F) * shake * 2.6F;
        cy += (float) Math.cos(time * 83F) * shake * 2.2F;
        float alpha = 1F;
        if (landing > 0F) {
            cy += landing * landing * 420F;
            sc *= 1F - .3F * landing;
            alpha = 1F - landing * landing;
        }
        int f = focus();
        AnvilPayloads.Stone st = f == ATTUNE ? shownStone() : null;
        boolean ghost = f == REFORGE && !held.next().isEmpty() && Meshes.up(held.next()) != null;
        int mode = ghost ? Placed.DIM : heat >= 0F ? Placed.lit(Placed.FULL, heat, front, st != null, 0F) : Placed.FULL;
        int accent = st != null ? st.colour() & 0xFFFFFF | 0xFF000000 : -1;
        Placed main = Placed.weapon(id, cx, cy, sc, sway, 5F, -ROLL, mode, alpha, 0F, accent);
        Placed[] all;
        if (ghost) {
            Meshes.Up next = Meshes.up(held.next());
            float pulse = .46F + .2F * (float) Math.sin(time * 3F);
            all = new Placed[] {main, Placed.weapon(held.next(), cx, cy, LENGTH / next.length() * (1F - .08F * armT), sway, 5F, -ROLL, Placed.lit(Placed.FULL, 0F, 0F, false, .3F), pulse * alpha, 400F, -1)};
        } else {
            all = new Placed[] {main};
        }
        version++;
        s.g.submitPictureInPictureRenderState(new PicState(KEY_HERO, 0, 0, Stage.W, Stage.H, s.rps, version, all, RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA, -1, s.g.pose()));
    }

    /** Where a point along the weapon's length (0 the pommel, 1 the tip) is on the sheet. */
    private float[] along(float f) {
        Meshes.Up up = Meshes.up(id);
        float scale = up == null ? 1F : LENGTH * (1F - .08F * armT), d = (f - .5F) * scale * .996F;
        float r = (float) Math.toRadians(ROLL);
        return new float[] {CX + (float) Math.sin(r) * d, CY + (CY_ARMED - CY) * armT + (float) Math.sin(time * .9F) * 5F - (float) Math.cos(r) * d};
    }

    private void title(Stage s) {
        s.titleCentre(name, CX, 20F, 2F, Stage.TITLE);
        s.centre(subtitle(), CX, 78F, 3F, Stage.LANTERN);
    }

    private String subtitle() {
        int t = Upgrades.temperOf(weapon);
        var force = Marks.of(weapon).attunement().orElse(null);
        AnvilPayloads.Option r = option(REFORGE);
        if (focus() == REFORGE && !r.changes().isEmpty() && r.changes().getFirst().label().equals("Tier"))
            return r.changes().getFirst().from() + ", becoming " + r.changes().getFirst().to();
        String temper = t > 0 ? "Temper " + Upgrades.roman(t) + " " + "✦".repeat(t) : "Not tempered yet";
        return force == null ? temper : temper + " · " + ArmsForge.forceName(force);
    }

    // ---- the numbers on the steel -------------------------------------------------------------------------------------

    private void tags(Stage s, float e) {
        int f = focus();
        if (f < 0 || fly < 1F || landing > 0F) return;
        if (f == ATTUNE) {
            AnvilPayloads.Stone st = shownStone();
            if (st == null) return;
            int colour = st.colour();
            bigWord(s, TAG_RIGHT, st.name(), 0xFF000000 | colour, "Your Force", along(.72F));
            int off = Math.round((1F - ArmsForge.POWER_PRIME) * 100F);
            bigWord(s, TAG_LEFT, "-" + off + "%", Stage.GOOD, "Power energy, for " + ArmsForge.primeTime() + " after a hit", along(.24F));
            return;
        }
        List<dev.awaken.hearth.Upgrades.Change> shown = new ArrayList<>(2);
        dev.awaken.hearth.Upgrades.Change damage = null;
        for (var c : option(f).changes()) {
            if (c.label().equals("Tier")) continue;
            if (c.label().equals("Damage") && damage == null) damage = c;
            else if (shown.size() < 1) shown.add(c);
        }
        if (damage != null) bigChange(s, TAG_RIGHT, damage, along(.72F));
        if (!shown.isEmpty()) bigChange(s, TAG_LEFT, shown.getFirst(), along(.24F));
    }

    private void bigChange(Stage s, float x, dev.awaken.hearth.Upgrades.Change c, float[] point) {
        float n = 7F, y = TAG_Y;
        s.text(c.from(), x, y, n, Stage.TITLE);
        float ax = x + s.width(c.from(), n) + 16F;
        arrow(s, ax, y + 20F);
        s.text(c.to(), ax + 44F, y, n, Stage.GOOD);
        String label = c.best().isEmpty() ? c.label() : c.label() + " · " + c.best() + " if Flawless";
        s.text(label, x, y + 64F, 3F, Stage.EDGE);
        s.ray(point[0], point[1], x + 6F, y + 96F, 1F, 0xC07FC3FF);
        s.fill(point[0] - 4F, point[1] - 4F, point[0] + 4F, point[1] + 4F, Stage.EDGE);
    }

    private void bigWord(Stage s, float x, String word, int colour, String label, float[] point) {
        float y = TAG_Y;
        s.text(word, x, y, 7F, colour);
        List<FormattedCharSequence> lines = s.wrap(label, 260F, 3F);
        float ly = y + 64F;
        for (FormattedCharSequence l : lines) {
            s.text(l, x, ly, 3F, Stage.EDGE);
            ly += 28F;
        }
        s.ray(point[0], point[1], x + 6F, ly + 2F, 1F, 0xC07FC3FF);
        s.fill(point[0] - 4F, point[1] - 4F, point[0] + 4F, point[1] + 4F, Stage.EDGE);
    }

    private static void arrow(Stage s, float x, float y) {
        s.fill(x, y + 8F, x + 24F, y + 12F, FORGE);
        s.fill(x + 16F, y + 2F, x + 20F, y + 18F, FORGE);
        s.fill(x + 20F, y + 5F, x + 24F, y + 15F, FORGE);
        s.fill(x + 24F, y + 8F, x + 28F, y + 12F, FORGE);
    }

    // ---- the three rites ----------------------------------------------------------------------------------------------

    private static float sealCx(int i) {
        return CX + (i - 1) * (SEAL_W + SEAL_GAP);
    }

    private int sealAt(float x, float y) {
        if (armed >= 0) {
            for (int i = 0; i < 3; i++) {
                float ry = SMALL_Y + i * SMALL_STEP;
                if (x >= SMALL_X - 6F && x < SMALL_X + 220F && y >= ry - 6F && y < ry + SMALL_ICON + 6F) return i;
            }
            return -1;
        }
        for (int i = 0; i < 3; i++) if (Math.abs(x - sealCx(i)) < SEAL_W / 2F && y >= SEAL_TOP && y < SEAL_TOP + 236F) return i;
        return -1;
    }

    private Component riteTitle(int i) {
        return i == TEMPER ? temperTitle : i == REFORGE ? reforgeTitle : attuneTitle;
    }

    private void seals(Stage s, float mx, float my) {
        float e = armT * armT * (3F - 2F * armT);
        boolean small = armT > .5F;
        for (int i = 0; i < 3; i++) {
            AnvilPayloads.Option o = option(i);
            boolean on = armed == i, off = armed >= 0 && !on, point = hover == i || (armed >= 0 && sealAt(mx, my) == i);
            float bigX = sealCx(i) - SEAL_ICON / 2F, smallY = SMALL_Y + i * SMALL_STEP, iconSize = SEAL_ICON + (SMALL_ICON - SEAL_ICON) * e;
            float x = bigX + (SMALL_X - bigX) * e, y = SEAL_TOP + (smallY - SEAL_TOP) * e;
            if (point || on) s.glow(x + iconSize / 2F, y + iconSize / 2F, iconSize * .9F, iconSize * .9F, 0xFFA046, on ? .5F : .4F);
            Panel.icon(s, riteIcon(i), x, y, iconSize / 16F);
            int titleColour = on ? FORGE : off ? 0x80AFA08A : point ? 0xFFFFFFFF : FORGE;
            if (small) {
                s.title(riteTitle(i), x + iconSize + 10F, y + 4F, 2F, titleColour);
            } else {
                float cx = sealCx(i);
                s.titleCentre(riteTitle(i), cx, y + iconSize + 2F, 2F, titleColour);
                s.fitted(o.headline(), cx, y + iconSize + 62F, SEAL_W + 60F, 3F, o.ready() ? Stage.GOOD : Stage.LANTERN, 0);
            }
        }
    }

    /** Each rite shown by a real thing from the game: the smith's hammer, the Forge, and the stones in turn. */
    private ItemStack riteIcon(int kind) {
        if (kind == TEMPER) return hammer;
        if (kind == REFORGE) return forgeIcon;
        if (armed == ATTUNE && stone < stoneIcons.length) return stoneIcons[stone];
        return stoneIcons[(int) (time / 1.2F) % stoneIcons.length];
    }

    // ---- what the picked rite takes -----------------------------------------------------------------------------------

    private void arm(int rite) {
        if (armed == rite) HallSounds.back();
        else {
            HallSounds.rank();
            Stage.say(riteName(rite) + ". " + (held.options().size() > rite ? held.options().get(rite).headline() : ""));
        }
        armed = armed == rite ? -1 : rite;
        hold.amount = 0F;
        holdKey = holdMouse = false;
        stoneHover = -1;
        tracked = false;
        dirty = true;
    }

    private static String riteName(int rite) {
        return rite == TEMPER ? "Temper" : rite == REFORGE ? "Reforge" : "Attune";
    }

    private void pickStone(int n) {
        if (n < 0 || n >= held.stones().size() || n == stone) return;
        HallSounds.rank();
        stone = n;
        hold.amount = 0F;
        dirty = true;
    }

    private void rebuild(Stage s) {
        if (!dirty) return;
        dirty = false;
        needs.clear();
        small.clear();
        says = notes = why = where = List.of();
        noteTones.clear();
        actLabel = "";
        canAct = false;
        whyColour = Stage.LANTERN;
        if (armed < 0) return;
        AnvilPayloads.Option o = option(armed);
        if (armed == ATTUNE) {
            AnvilPayloads.Stone st = selected();
            if (st == null) return;
            says = s.wrap(st.touch(), PLATE_W, 3F);
            for (AnvilPayloads.Cost c : st.costs()) if (!c.id().equals(st.item())) small.add(need(s, c));
            canAct = st.ready();
            actLabel = "set the " + st.stone();
            if (!st.ready()) why = s.wrap(st.line(), PLATE_W, 3F);
            if (st.have() <= 0) where = s.wrap(source(st.item()), PLATE_W, 3F);
            List<String> extra = new ArrayList<>(2);
            extra.add(ArmsForge.hitWords());
            extra.add("Its Marks take the Force too. A new Force replaces the old one.");
            notes(s, extra, Stage.LABEL);
            return;
        }
        most = "";
        List<AnvilPayloads.Say> told = new ArrayList<>(o.says());
        for (AnvilPayloads.Say say : o.says()) if (say.tone() == AnvilPayloads.Say.MOST) {
            most = say.text();
            told.remove(say);
        }
        if (!told.isEmpty()) says = s.wrap(told.getFirst().text(), PLATE_W, 3F);
        for (AnvilPayloads.Cost c : o.costs()) needs.add(need(s, c));
        canAct = o.ready();
        if (o.ready()) {
            actLabel = o.button().substring(0, 1).toLowerCase(java.util.Locale.ROOT) + o.button().substring(1);
            actLabel = actLabel.replace(" it to ", " to ");
            if (!o.line().isEmpty()) {
                why = s.wrap(o.line(), PLATE_W, 3F);
                whyColour = Stage.LABEL;
            }
        } else {
            why = s.wrap(o.line(), PLATE_W, 3F);
        }
        List<String> extra = new ArrayList<>(3);
        for (int i = 1; i < told.size(); i++) extra.add(told.get(i).text());
        notes(s, extra, Stage.LABEL);
    }

    /** What the weapon would be at its highest Temper ("Damage 12"), "" when this rite doesn't say. */
    private String most = "";

    /** The Temper it has, the one this heat takes it to, and how far it can go: a pip each, the most it can be at the end. */
    private void temperPips(Stage s, float x, float y) {
        int now = Upgrades.temperOf(weapon), to = now;
        AnvilPayloads.Option o = option(TEMPER);
        for (Upgrades.Change c : o.changes()) if (c.label().equals("Temper")) for (int t = 1; t <= Upgrades.MAX_TEMPER; t++) if (Upgrades.roman(t).equals(c.to())) to = t;
        for (int i = 0; i < Upgrades.MAX_TEMPER; i++) {
            float px = x + i * 22F;
            s.fill(px, y, px + 16F, y + 16F, i < now ? Stage.GOOD : i < to ? Stage.LANTERN : 0x736F829C);
        }
        s.text(Upgrades.roman(Upgrades.MAX_TEMPER) + " · " + most, x + Upgrades.MAX_TEMPER * 22F + 8F, y - 1F, 2F, Stage.LABEL);
    }

    private void notes(Stage s, List<String> extra, int colour) {
        List<FormattedCharSequence> all = new ArrayList<>();
        for (String text : extra) {
            if (!all.isEmpty()) all.add(FormattedCharSequence.EMPTY);
            all.addAll(s.wrap(text, 236F, 2F));
        }
        notes = all;
    }

    private static Panel.Need need(Stage s, AnvilPayloads.Cost c) {
        return Panel.need(s, c.tag(), c.id(), c.tag() ? c.label() : "", c.have(), c.need());
    }

    private static String source(String item) {
        Identifier id = Identifier.parse(item);
        String key = "awaken.source." + id.getNamespace() + "." + id.getPath();
        return Language.getInstance().has(key) ? Language.getInstance().getOrDefault(key) : "";
    }

    /**
     * Lines centred on the plate, in the room of {@code most} of them. It drew {@code most} and dropped the rest; nothing is
     * cut now (SPEC §57.6): more than fit are set again a size smaller, which fits half as many lines again in the same room.
     */
    private static void centred(Stage s, List<FormattedCharSequence> lines, float y, int most, int colour) {
        if (lines.size() <= most) {
            for (int i = 0; i < lines.size(); i++) {
                FormattedCharSequence l = lines.get(i);
                s.text(l, CX - s.font.width(l) * s.snap(3F) / 2F, y + i * 30F, 3F, colour);
            }
            return;
        }
        StringBuilder whole = new StringBuilder();
        for (FormattedCharSequence l : lines) {
            if (!whole.isEmpty()) whole.append(' ');
            l.accept((index, style, code) -> {
                whole.appendCodePoint(code);
                return true;
            });
        }
        float step = 10F * s.snap(2F), ly = y;
        for (FormattedCharSequence l : s.wrap(whole.toString(), PLATE_W, 2F)) {
            s.text(l, CX - s.font.width(l) * s.snap(2F) / 2F, ly, 2F, colour);
            ly += step;
        }
    }

    private void plate(Stage s, float mx, float my, float dt) {
        float noteY = SMALL_Y + 3 * SMALL_STEP + 20F;
        if (!most.isEmpty() && armed == TEMPER) {
            temperPips(s, SMALL_X, noteY);
            noteY += 32F;
        }
        for (FormattedCharSequence l : notes) {
            s.text(l, SMALL_X, noteY, 2F, Stage.LABEL);
            noteY += 21F;
        }
        if (armed == ATTUNE) {
            stones(s, mx, my, dt);
            return;
        }
        boolean act = !actLabel.isEmpty() && canAct;
        float bottom = HOLD_Y1;
        float y = act ? HOLD_Y0 : bottom - 30F * Math.min(2, why.size() + where.size()) - (trackable() ? 46F : 0F);
        float needsTop = y - 10F - NEED_H - (act && !why.isEmpty() ? 34F : 0F);
        int shown = Math.min(2, says.size());
        centred(s, says, needsTop - 8F - 30F * shown, shown, 0xFFE6E0D4);
        hoverNeed = needRow(s, needs, needsTop, mx, my);
        if (act) {
            if (!why.isEmpty()) centred(s, why, HOLD_Y0 - 34F, 1, Stage.LABEL);
            commit(s, mx, my, dt);
        } else {
            float ly = needsTop + NEED_H + 10F;
            centred(s, why, ly, 2, whyColour);
            ly += 30F * Math.min(2, why.size());
            Panel.Need show = hoverNeed >= 0 ? needs.get(hoverNeed) : firstShort(needs);
            if (show != null && !show.source().isEmpty()) {
                centred(s, s.wrap(show.source(), PLATE_W, 3F), ly, 1, Stage.DIM);
                ly += 30F;
            }
            if (trackable()) track(s, ly + 4F, mx, my);
        }
    }

    private boolean trackable() {
        return armed == REFORGE && !canAct && !held.next().isEmpty() && ArmsItems.of(weapon.getItem()) != null && ArmsItems.data(held.next()) != null && !isLord();
    }

    private boolean isLord() {
        return dev.awaken.arms.LordForged.byWeapon(id) != null;
    }

    private float trackX0, trackX1, trackY;

    private void track(Stage s, float y, float mx, float my) {
        String label = tracked ? "Tracking it. It's on K > Tasks." : "Track " + PieceView.weaponName(held.next());
        float w = s.width(label, 3F);
        trackX0 = CX - w / 2F;
        trackX1 = CX + w / 2F;
        trackY = y;
        boolean over = !tracked && mx >= trackX0 - 8F && mx < trackX1 + 8F && my >= y - 4F && my < y + 32F;
        if (!tracked) {
            if (over) s.fill(trackX0 - 12F, y - 5F, trackX1 + 12F, y + 31F, 0x552A3A56);
            s.outline(trackX0 - 12F, y - 5F, trackX1 + 12F, y + 31F, 2, 0xA07FC3FF);
        }
        s.text(label, trackX0, y + 1F, 3F, tracked ? Stage.GOOD : over ? Stage.TITLE : Stage.LABEL);
    }

    private static Panel.@Nullable Need firstShort(List<Panel.Need> list) {
        for (Panel.Need n : list) if (!n.ok()) return n;
        return null;
    }

    /** The real items, big and centred; returns the one the pointer is on. */
    private int needRow(Stage s, List<Panel.Need> list, float y, float mx, float my) {
        int n = list.size(), hit = -1;
        float x0 = CX - n * NEED_W / 2F;
        for (int i = 0; i < n; i++) {
            Panel.Need need = list.get(i);
            float cx = x0 + i * NEED_W, mid = cx + NEED_W / 2F;
            if (mx >= cx && mx < cx + NEED_W && my >= y && my < y + NEED_H) hit = i;
            if (need.icons().length > 0) Panel.icon(s, need.icons()[(int) (time / 1.2F) % need.icons().length], mid - 32F, y, 4F);
            s.centre(need.count(), mid, y + 68F, 3F, need.ok() ? Stage.GOOD : Stage.BAD);
            for (int l = 0; l < Math.min(2, need.nameLines().size()); l++) {
                FormattedCharSequence line = need.nameLines().get(l);
                s.text(line, mid - s.font.width(line) * s.snap(2F) / 2F, y + 94F + l * 18F, 2F, need.ok() ? Stage.LABEL : Stage.TITLE);
            }
        }
        return hit;
    }

    private void stones(Stage s, float mx, float my, float dt) {
        List<AnvilPayloads.Stone> list = held.stones();
        float x0 = CX - (list.size() - 1) * STONE_STEP / 2F;
        int over = -1;
        for (int i = 0; i < list.size(); i++) {
            AnvilPayloads.Stone st = list.get(i);
            float mid = x0 + i * STONE_STEP;
            boolean on = i == stone, has = st.have() > 0 || st.held();
            if (Math.abs(mx - mid) < STONE_STEP / 2F && my >= STONES_TOP && my < STONES_TOP + 118F) over = i;
            if (on) s.glow(mid, STONES_TOP + 32F, 76F, 66F, st.colour(), .5F);
            Panel.icon(s, stoneIcons[i], mid - 32F, STONES_TOP, 4F);
            s.centre(st.name(), mid, STONES_TOP + 68F, 3F, has ? 0xFF000000 | st.colour() : Stage.DIM);
            String sub = st.on() ? "On it now" : has ? st.have() + " " + st.stone() : st.stone(); // never dimmed: you should know it when you find it
            s.fitted(sub, mid, STONES_TOP + 96F, STONE_STEP - 6F, 2F, st.on() ? Stage.GOOD : has ? Stage.LABEL : Stage.DIM, 0);
            if (on) s.fill(mid - 38F, STONES_TOP + 114F, mid + 38F, STONES_TOP + 117F, 0xFF000000 | st.colour());
        }
        stoneHover = over;
        AnvilPayloads.Stone st = selected();
        if (st == null) return;
        boolean act = canAct;
        float y = STONES_TOP + 124F;
        centred(s, says, y, 2, 0xFF000000 | st.colour());
        y += 30F * Math.min(2, says.size()) + 4F;
        if (!small.isEmpty()) {
            smallRow(s, y);
            y += 40F;
        }
        if (act) {
            commit(s, mx, my, dt);
        } else {
            centred(s, why, y, 1, Stage.LANTERN);
            y += 30F;
            centred(s, where, y, 1, Stage.DIM);
        }
    }

    /** The rest of a Force's price: small real items beside their counts, in one line. */
    private void smallRow(Stage s, float y) {
        float total = 0F;
        for (Panel.Need n : small) total += 44F + s.width(n.count() + " " + n.name(), 3F) + 30F;
        float x = CX - (total - 30F) / 2F;
        for (Panel.Need n : small) {
            if (n.icons().length > 0) Panel.icon(s, n.icons()[0], x, y - 2F, 2F);
            float tx = x + 40F;
            s.text(n.count(), tx, y + 4F, 3F, n.ok() ? Stage.GOOD : Stage.BAD);
            float nx = tx + s.width(n.count() + " ", 3F);
            s.text(n.name(), nx, y + 4F, 3F, Stage.LABEL);
            x = nx + s.width(n.name(), 3F) + 30F;
        }
    }

    private void commit(Stage s, float mx, float my, float dt) {
        boolean over = mx >= HOLD_X0 && mx < HOLD_X1 && my >= HOLD_Y0 && my < HOLD_Y1;
        if (hold.draw(s, HOLD_X0, HOLD_X1, HOLD_Y0, HOLD_Y1, Panel.holdLabel(actLabel), true, true, holdKey || holdMouse, over, dt)) {
            holdKey = holdMouse = false;
            fire();
        }
    }

    private void fire() {
        if (landing > 0F || armed < 0) return;
        landing = .0001F;
        HallSounds.set();
        int kind = armed == TEMPER ? AnvilWork.TEMPER : armed == REFORGE ? AnvilWork.REFORGE : AnvilWork.ATTUNE;
        int chosen = armed == ATTUNE ? kind | stone + 1 << 8 : kind;
        AnvilClient.send(held.pos(), AnvilPayloads.HELD, chosen, 0F, 0F);
    }

    private void foot(Stage s, float mx, float my) {
        s.hint("Esc", armed >= 0 ? "Back to the three rites" : "Step away from the anvil", 28F, 918F);
        String list = "The Armoury";
        float w = s.width(list, 3F);
        armouryX0 = 1684F - w;
        boolean over = armed < 0 && mx >= armouryX0 - 8F && my >= 14F && my < 54F;
        if (armed < 0) s.text(list, armouryX0, 22F, 3F, over ? Stage.TITLE : Stage.EDGE);
    }

    // ---- input --------------------------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || stage == null || fly < 1F || landing > 0F) return super.mouseClicked(event, doubleClick);
        float x = stage.sx(event.x()), y = stage.sy(event.y());
        int seal = sealAt(x, y);
        if (seal >= 0) {
            arm(seal);
            return true;
        }
        if (x < 240F && y >= 906F) {
            back();
            return true;
        }
        if (armed < 0) {
            if (x >= armouryX0 - 8F && y >= 14F && y < 54F) {
                AnvilClient.send(held.pos(), AnvilPayloads.LIST, 0, 0F, 0F);
                onClose();
            }
            return true;
        }
        if (canAct && x >= HOLD_X0 && x < HOLD_X1 && y >= HOLD_Y0 && y < HOLD_Y1) {
            if (HallConfig.clickToCommit()) fire();
            else holdMouse = true;
            return true;
        }
        if (armed == ATTUNE) {
            float x0 = CX - (held.stones().size() - 1) * STONE_STEP / 2F - STONE_STEP / 2F;
            if (y >= STONES_TOP && y < STONES_TOP + 118F && x >= x0) pickStone((int) ((x - x0) / STONE_STEP));
            return true;
        }
        if (trackable() && !tracked && x >= trackX0 - 8F && x < trackX1 + 8F && y >= trackY - 4F && y < trackY + 32F) track();
        return true;
    }

    private void track() {
        ArmsData.Weapon data = ArmsItems.data(held.next());
        if (data == null) return;
        ArmouryCache.ask(ArmsArmoury.TRACK, data.style(), data.tier());
        tracked = true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        holdMouse = false;
        return super.mouseReleased(event);
    }

    private void back() {
        if (armed >= 0) arm(armed);
        else onClose();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (landing > 0F) return true;
        if (key == InputConstants.KEY_ESCAPE) {
            back();
            return true;
        }
        switch (key) {
            case InputConstants.KEY_1 -> arm(TEMPER);
            case InputConstants.KEY_2 -> arm(REFORGE);
            case InputConstants.KEY_3 -> arm(ATTUNE);
            case InputConstants.KEY_LEFT -> {
                if (armed == ATTUNE) pickStone(Math.floorMod(stone - 1, held.stones().size()));
            }
            case InputConstants.KEY_RIGHT -> {
                if (armed == ATTUNE) pickStone(Math.floorMod(stone + 1, held.stones().size()));
            }
            case InputConstants.KEY_T -> {
                if (trackable() && !tracked) track();
            }
            case InputConstants.KEY_SPACE, InputConstants.KEY_RETURN -> {
                if (!canAct) return true;
                if (HallConfig.clickToCommit()) fire();
                else holdKey = true;
            }
            default -> {
                return super.keyPressed(event);
            }
        }
        return true;
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (event.key() == InputConstants.KEY_SPACE || event.key() == InputConstants.KEY_RETURN) holdKey = false;
        return super.keyReleased(event);
    }
}
