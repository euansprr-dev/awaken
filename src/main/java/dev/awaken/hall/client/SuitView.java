package dev.awaken.hall.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.awaken.armour.ArmourBonus;
import dev.awaken.armour.ArmourForge;
import dev.awaken.armour.ArmourInlay;
import dev.awaken.armour.ArmourNow;
import dev.awaken.armour.ArmourSet;
import dev.awaken.armour.ForgeSong;
import dev.awaken.armour.client.ForgeClient;
import dev.awaken.armour.net.ArmourPayloads;
import dev.awaken.arms.ArmsArmoury;
import dev.awaken.core.Forces;
import dev.awaken.gear.SetBonus;
import dev.awaken.hall.HallNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.ArmorType;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One suit, close (SPEC §52): it steps off its terrace and stands turning, each piece named beside it; the same kind at
 * every rank on the left; on the right what the whole suit does, the piece you've picked and the real things it takes.
 */
final class SuitView extends CloseView {
    private static final int KEY_HERO = 7, KEY_LADDER = 8;
    private static final float CX = 598F, CY = 470F, MOST_SCALE = 400F, MOST_TALL = 830F;
    private static final int LADDER_X = 26, LADDER_TOP = 120, LADDER_BOTTOM = 848, RUNG = (LADDER_BOTTOM - LADDER_TOP) / 6, PORTRAIT = 88;
    private static final float X = Panel.X, RIGHT = Panel.RIGHT, TEXT_W = Panel.TEXT_W;
    private static final String[] PIECE = {"Helm", "Cuirass", "Greaves", "Boots"};
    private static final String[] ROLE = {"Light and quick. Armour that gets out of the way.", "Heavy. Takes the hit so you can stay in the fight.",
            "Thin, but your powers run hotter.", "Made for close work and clean counters.", "Taken from a Lord. Each one fights its own way."};
    /** Where each piece's name stands, and the point on the figure (model space) its line runs to. */
    private static final float[][] ZONE = {{838F, 196F}, {385F, 360F}, {838F, 560F}, {385F, 752F}};
    private static final float[][] ANCHOR = {{0F, -.25F, -.28F}, {0F, .32F, -.2F}, {0F, .92F, -.16F}, {0F, 1.38F, -.18F}};
    private static final float ZONE_W = 176F, ZONE_H = 64F;
    private static final int FORGE = 0, MEMORY = 1, TRACK = 2, UNTRACK = 3, LOOKS = 4, CHOOSE = 5;
    private static final float INLAY_SIZE = 52F, INLAY_STEP = 60F;
    /** The catalyst chosen to inlay (its Force + 1; 0 none), kept between visits. */
    private static int inlay;

    /** Opened from a step on K > Tasks: the suits it offers are all you can move between, and Choose stands where Track would. */
    interface Choosing {
        boolean chosen(ArmourSet s);

        void choose(ArmourSet s);

        @Nullable ArmourSet step(ArmourSet s, int d);
    }

    private final @Nullable Runnable looks;
    private final @Nullable Choosing choosing;
    ArmourSet set;
    int slot;
    private final float fromX, fromY, fromS;
    private ArmourSet shown;
    private float morph = 1F, bob;
    private long heroVersion, ladderVersion;
    private Placed[] ladder = new Placed[0];
    private long ladderBuilt = -1;
    private ArmourPayloads.@Nullable Offers ladderOffers;
    private final Component[] letter = {Stage.blackletter("E"), Stage.blackletter("D"), Stage.blackletter("C"), Stage.blackletter("B"), Stage.blackletter("A"), Stage.blackletter("S")};

    // the panel: rebuilt only when what it reads changes
    private boolean dirty = true, builtChosen;
    private ArmourPayloads.@Nullable Offers built;
    private @Nullable Component name, pieceName;
    private float nameSize = 4F;
    private String eyebrow = "", compare = "";
    private int compareColour = Stage.LABEL;
    private List<FormattedCharSequence> blurb = List.of(), why = List.of(), inlayLine = List.of();
    private final List<List<FormattedCharSequence>> special = new ArrayList<>(4);
    private final List<String[]> stats = new ArrayList<>(3);
    private final List<Panel.Need> needs = new ArrayList<>(6);
    private ItemStack pieceIcon = ItemStack.EMPTY;
    private int whyColour = Stage.LABEL;
    private Panel.@Nullable Act primary;
    private final List<Panel.Act> subs = new ArrayList<>(3);
    private boolean showInlay;
    private int modes;
    private final int[] states = new int[4];
    private String prevName = "", nextName = "";

    // what the last frame drew where
    private float primaryY0, primaryY1, subY0, subY1, inlayY = -1F;
    private final float[] subX0 = new float[3], subX1 = new float[3];
    private final Panel.Hold hold = new Panel.Hold();

    SuitView(TerraceLayout.Fig from, int slot, @Nullable Runnable looks, @Nullable Choosing choosing) {
        this.looks = looks;
        this.choosing = choosing;
        this.set = from.set();
        this.shown = set;
        this.slot = slot >= 0 ? slot : firstUseful();
        this.fromX = from.x();
        this.fromY = from.cy();
        this.fromS = from.scale();
        this.yaw = -24F;
        this.vel = 10F;
    }

    private int firstUseful() {
        ArmourPayloads.Offers o = HallClient.suits;
        for (int want : new int[] {SuitState.READY, SuitState.SHORT, SuitState.OVER, SuitState.UNFOUND}) {
            for (int i = 0; i < 4; i++) if (SuitState.of(o, set, i) == want) return i;
        }
        return 1;
    }

    private void select(ArmourSet to) {
        if (to == set) return;
        HallSounds.rank();
        set = to;
        slot = firstUseful();
        dirty = true;
        ladderVersion++;
        hold.amount = 0F;
        if (morph >= 1F) morph = 0F;
    }

    private void pick(int newSlot) {
        slot = Math.floorMod(newSlot, 4);
        dirty = true;
        hold.amount = 0F;
    }

    @Override void step(int dc, int dt) {
        if (choosing != null) {
            ArmourSet to = choosing.step(set, dc);
            if (to != null) select(to);
            return;
        }
        TerraceLayout.Fig f = TerraceLayout.of(set);
        int tier = Math.clamp(f.tier() + dt, 0, HallNet.ROWS - 1), col = Math.floorMod(f.col() + dc, 5);
        if (tier == 0 && col == 4) col = dc > 0 ? 0 : 3; // E has no Lord-forged suit
        ArmourSet to = TerraceLayout.set(col, tier);
        if (to != null) select(to);
    }

    @Override float zoomX() {
        return fromX;
    }

    @Override float zoomY() {
        return fromY;
    }

    @Override int tier() {
        return set.rank.ordinal();
    }

    // ---- what it says -------------------------------------------------------------------------------------------------

    private void rebuild(Stage s, HallNet.Wall w) {
        ArmourPayloads.Offers o = HallClient.suits;
        boolean chosenNow = choosing != null && choosing.chosen(set); // the server's answer to Choose arrives a moment later
        if (!dirty && o == built && chosenNow == builtChosen) return;
        dirty = false;
        built = o;
        builtChosen = chosenNow;
        boolean hidden = SuitState.hidden(o, set);
        modes = 0;
        for (int i = 0; i < 4; i++) {
            states[i] = SuitState.of(o, set, i);
            int mode = states[i] == SuitState.UNFOUND || states[i] == SuitState.NONE ? Placed.SHAPE : states[i] == SuitState.SHORT || states[i] == SuitState.OVER ? Placed.DIM : Placed.FULL;
            modes |= (i == slot ? mode | Placed.MARKED : mode) << i * 4;
        }
        name = Stage.blackletter(hidden ? "Unknown" : set.setName);
        nameSize = 4F;
        while (nameSize > 2F && s.titleWidth(name, nameSize) > TEXT_W) nameSize -= 1F;
        String rank = set.rank.label + " rank";
        eyebrow = hidden ? "Not found · " + rank : set.archetype.label + " · " + rank;
        blurb = s.wrap(hidden ? "A shape on the terraces, nothing more." : ROLE[TerraceLayout.column(set.archetype)], TEXT_W, 3F);

        stats.clear();
        compare = "";
        special.clear();
        needs.clear();
        ArmorType piece = ForgeSong.PIECES[slot];
        pieceIcon = ArmourForge.make(set, piece);
        pieceName = Stage.blackletter(hidden || pieceIcon.isEmpty() ? PIECE[slot] : pieceIcon.getHoverName().getString());
        if (!hidden) {
            double armour = 0, tough = 0;
            for (ArmorType p : ForgeSong.PIECES) {
                ItemStack made = ArmourForge.make(set, p);
                armour += ArmourNow.armour(made, p.getSlot());
                tough += ArmourNow.toughness(made, p.getSlot());
            }
            stats.add(new String[] {Integer.toString((int) Math.round(armour)), "Armour, whole suit"});
            if (tough > 0) stats.add(new String[] {HallSpecial.n((float) tough), "Toughness"});
            stats.add(new String[] {SuitState.count(o, set, SuitState.MADE) + "/4", "Pieces made"});
            compare(s, piece);
            // the suit's own trick first, the rest as room allows
            List<String> say = new ArrayList<>(4);
            String twist = ArmourBonus.twist(set);
            if (twist != null) say.add("All four: " + twist + ".");
            for (ArmourBonus.Line l : ArmourBonus.lines(set)) {
                String line = (l.pieces() == SetBonus.HALF ? "Two pieces: " : "All four: ") + l.text() + ".";
                if (!say.contains(line)) say.add(line);
            }
            say.sort(java.util.Comparator.comparing(line -> line.startsWith("Two pieces"))); // the whole suit's line is the one that must show
            for (String one : say) special.add(s.wrap(one, TEXT_W, 3F));
        }
        ArmourPayloads.Offer offer = SuitState.offer(o, set, slot);
        int state = states[slot];
        boolean showNeeds = offer != null && !hidden && state != SuitState.MADE;
        if (showNeeds) for (ArmourPayloads.Need n : offer.needs()) needs.add(Panel.need(s, !n.group().isEmpty(), n.group().isEmpty() ? n.icon() : n.group(), n.label(), n.have(), n.need(), Panel.across(offer.needs().size())));
        showInlay = offer != null && o != null && o.stand() && ForgeSong.inlays(set) && (state == SuitState.READY || state == SuitState.SHORT);
        inlayLine = !showInlay ? List.of() : s.wrap(inlay == 0 ? "No inlay. Pick a catalyst above." : ArmourInlay.line(Forces.Force.values()[inlay - 1]), TEXT_W, 3F);
        actions(s, w, o, offer, hidden, state);
        Stage.say(name.getString() + ". " + eyebrow + ". " + (pieceName == null ? "" : pieceName.getString() + ". ") + SuitState.word(state) + ".");
        TerraceLayout.Fig f = TerraceLayout.of(set);
        prevName = neighbourName(f, -1);
        nextName = neighbourName(f, 1);
    }

    private String neighbourName(TerraceLayout.Fig f, int d) {
        if (choosing != null) {
            ArmourSet to = choosing.step(set, d);
            return to == null ? "" : to.setName;
        }
        int col = Math.floorMod(f.col() + d, f.tier() == 0 ? 4 : 5);
        return TerraceLayout.kind(col);
    }

    /** The long way if the line has room for it, the short way if not: a long item name must never cut the sentence off. */
    private static String say(Stage s, String longer, String shorter) {
        return s.width(longer, 3F) <= TEXT_W ? longer : shorter;
    }

    private void compare(Stage s, ArmorType piece) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        EquipmentSlot equip = piece.getSlot();
        ItemStack worn = player.getItemBySlot(equip), made = ArmourForge.make(set, piece);
        if (made.isEmpty()) return;
        double mine = ArmourNow.armour(worn, equip), here = ArmourNow.armour(made, equip);
        if (!worn.isEmpty() && worn.is(made.getItem())) {
            compare = "You are wearing this one.";
            compareColour = Stage.LABEL;
        } else if (worn.isEmpty()) {
            compare = "+" + HallSpecial.n((float) here) + " armour. That slot is empty.";
            compareColour = Stage.GOOD;
        } else {
            double delta = here - mine;
            String on = worn.getHoverName().getString();
            String change = (delta > 0 ? "+" : "") + HallSpecial.n((float) delta) + " armour on ";
            compare = Math.abs(delta) < .05 ? say(s, "The same armour as your " + on + ".", "The same armour as what you wear.") : say(s, change + "your " + on, change + "what you wear");
            compareColour = delta > .05 ? Stage.GOOD : delta < -.05 ? Stage.BAD : Stage.LABEL;
        }
    }

    private void actions(Stage s, HallNet.Wall w, ArmourPayloads.@Nullable Offers o, ArmourPayloads.@Nullable Offer offer, boolean hidden, int state) {
        primary = null;
        subs.clear();
        whyColour = Stage.LABEL;
        String line = "";
        boolean atStand = o != null && o.stand();
        boolean byHand = ForgeSong.byHand(set);
        String place = o == null ? "" : byHand ? o.hand() : o.place();
        boolean tracked = SuitState.tracked(o, set);
        if (hidden) {
            line = patternWhere();
        } else if (state == SuitState.MADE) {
            line = SuitState.count(o, set, SuitState.MADE) == 4 ? "You have the whole suit." : "You have this piece.";
            whyColour = Stage.GOOD;
            if (looks != null) subs.add(new Panel.Act("Your looks", LOOKS, false, true, InputConstants.KEY_L));
        } else if (state == SuitState.OVER) {
            line = set.setName + " opens at level " + ArmsArmoury.LEVEL[set.rank.ordinal()] + ". You are level " + w.level() + ".";
        } else if (!atStand) {
            line = "Take this to a Fitting Stand to make it.";
        } else {
            String busy = HallClient.busy;
            boolean can = offer != null && (offer.flags() & ArmourPayloads.CAN) != 0 && busy == null;
            String piece = PIECE[slot].toLowerCase(Locale.ROOT);
            primary = new Panel.Act("forge the " + piece, FORGE, true, can, 0);
            if (offer != null && (offer.flags() & ArmourPayloads.FROM_MEMORY) != 0)
                subs.add(new Panel.Act("From memory", MEMORY, false, can, InputConstants.KEY_M));
            if (busy != null) line = busy + " is forging here. Come back when the piece is done, or help at the anvil while it's shaped.";
            else if (state == SuitState.SHORT) line = stillNeeds(offer);
            else if (!place.isEmpty()) line = place;
        }
        if (choosing != null) {
            boolean on = choosing.chosen(set);
            subs.add(0, new Panel.Act(on ? "Your choice" : "Choose this one", CHOOSE, false, !on, InputConstants.KEY_RETURN));
        } else if (!hidden && state != SuitState.MADE) {
            subs.add(0, new Panel.Act(tracked ? "Stop tracking" : "Track it", tracked ? UNTRACK : TRACK, false, true, InputConstants.KEY_T));
        }
        why = line.isEmpty() ? List.of() : s.wrap(line, TEXT_W, 3F);
    }

    private String patternWhere() {
        return set.archetype == dev.awaken.armour.Archetype.LORD
                ? "You haven't found this pattern. A " + set.rank.label + "-rank gate's Lord carries it."
                : "You haven't found this pattern. " + set.rank.label + "-rank gate creatures carry the patterns: the strong ones sometimes, their Lords always.";
    }

    private static String stillNeeds(ArmourPayloads.@Nullable Offer offer) {
        if (offer == null) return "";
        List<String> parts = new ArrayList<>(3);
        for (ArmourPayloads.Need n : offer.needs()) {
            if (n.have() < n.need()) {
                String label = n.label();
                int cut = label.indexOf(" (");
                parts.add(n.need() - n.have() + " more " + (cut > 0 ? label.substring(0, cut) : label));
            }
        }
        return parts.isEmpty() ? "" : "Still needs " + String.join(", ", parts) + ".";
    }

    // ---- drawing ------------------------------------------------------------------------------------------------------

    @Override void draw(Stage s, HallNet.Wall w, float dt, float time, double mouseX, double mouseY) {
        rebuild(s, w);
        float e = eased(), mx = s.sx(mouseX), my = s.sy(mouseY);
        int glow = Stage.RANK[set.rank.ordinal()];
        s.glow(CX, CY, 720F, 620F, glow, .30F * e);
        s.glow(CX, CY, 170F, 620F, glow, .28F * e);
        hero(s, dt, time);
        if (fly <= .15F) return;
        float slide = (1F - e) * (1F - e);
        s.g.nextStratum();
        drawZones(s, slide, mx, my);
        if (choosing == null) drawLadder(s, -180F * slide);
        drawPanel(s, 720F * slide, mx, my, dt, time);
        drawFoot(s, slide);
    }

    private float heroScale() {
        float[] b = TerraceLayout.bounds(shown);
        return Math.min(MOST_SCALE, MOST_TALL / Math.max(.5F, b[1] - b[0]));
    }

    /** Stage y for the figure's middle, so the whole of it, horns and all, is centred. */
    private float heroMid(float scale) {
        float[] b = TerraceLayout.bounds(shown);
        return CY + (.5F - (b[0] + b[1]) / 2F) * scale;
    }

    private void hero(Stage s, float dt, float time) {
        spin(dt);
        if (morph < 1F) {
            morph = Math.min(1F, morph + dt / .3F);
            if (morph >= .5F) shown = set;
        } else {
            shown = set;
        }
        bob = (float) Math.sin(time * .9F) * 5F;
        float e = eased(), aim = heroScale(), size = fromS + (aim - fromS) * e, mid = heroMid(aim);
        float alpha = morph >= 1F ? 1F : morph < .5F ? 1F - morph * 2F : morph * 2F - 1F;
        float shownYaw = -24F + (yaw + 24F) * (closing ? e : Math.min(1F, fly * 3F));
        heroVersion++;
        Placed p = Placed.suit(shown, fromX + (CX - fromX) * e, fromY + (mid + bob - fromY) * e, 0F, size, shownYaw, -4F, shown == set ? modes : Placed.all(Placed.FULL), alpha);
        s.g.submitPictureInPictureRenderState(new PicState(KEY_HERO, 0, 0, Stage.W, Stage.H, s.rps, heroVersion, new Placed[] {p},
                RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA, -1, s.g.pose()));
    }

    private void drawZones(Stage s, float slide, float mx, float my) {
        var g = s.g;
        float scale = heroScale(), mid = heroMid(scale) + bob;
        float r = (float) Math.toRadians(yaw), c = (float) Math.cos(r), sn = (float) Math.sin(r);
        for (int i = 0; i < 4; i++) {
            float zx = ZONE[i][0] + (i % 2 == 0 ? 80F : -80F) * slide, zy = ZONE[i][1];
            boolean left = i % 2 == 1, on = i == slot;
            boolean over = fly >= 1F && zoneAt(mx, my) == i;
            float[] a = ANCHOR[i];
            float ax = CX + (a[0] * c + a[2] * sn) * scale, ay = mid + (a[1] - .5F) * scale;
            float lx = left ? zx + 6F : zx - 6F, ly = zy + 16F;
            int colour = on ? Stage.EDGE : 0x667FC3FF;
            s.ray(lx, ly, ax, ay, on ? 2F : 1F, colour);
            s.fill(ax - 4F, ay - 4F, ax + 4F, ay + 4F, on ? Stage.EDGE : 0x887FC3FF);
            int title = on ? Stage.LANTERN : over ? Stage.TITLE : Stage.LABEL;
            float wide = Math.max(s.width(PIECE[i], 3F), s.width(SuitState.word(states[i]), 2F)), boxX = left ? zx - wide - 8F : zx - 8F;
            s.fill(boxX, zy - 6F, boxX + wide + 16F, zy + 54F, 0xB8060A12); // a wide pauldron can turn under the label
            if (left) {
                s.right(PIECE[i], zx, zy, 3F, title);
                s.right(SuitState.word(states[i]), zx, zy + 32F, 2F, SuitState.colour(states[i]));
            } else {
                s.text(PIECE[i], zx, zy, 3F, title);
                s.text(SuitState.word(states[i]), zx, zy + 32F, 2F, SuitState.colour(states[i]));
            }
        }
        g.nextStratum();
    }

    private int zoneAt(float x, float y) {
        for (int i = 0; i < 4; i++) {
            float zx = i % 2 == 1 ? ZONE[i][0] - ZONE_W : ZONE[i][0];
            if (x >= zx && x < zx + ZONE_W && y >= ZONE[i][1] - 6F && y < ZONE[i][1] - 6F + ZONE_H) return i;
        }
        return -1;
    }

    private static float rungY(int t) {
        return LADDER_BOTTOM - RUNG * (t + .5F);
    }

    private void drawLadder(Stage s, float dx) {
        var g = s.g;
        ArmourPayloads.Offers o = HallClient.suits;
        g.pose().pushMatrix().translate(dx, 0F);
        if (ladderBuilt != ladderVersion || ladderOffers != o) buildLadder(o);
        g.submitPictureInPictureRenderState(new PicState(KEY_LADDER, 0, LADDER_TOP, LADDER_X + PORTRAIT + 8, LADDER_BOTTOM, s.rps, ladderVersion + (o == null ? 0 : o.hashCode()), ladder,
                RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA, -1, g.pose()));
        g.nextStratum();
        int col = TerraceLayout.of(set).col(), at = set.rank.ordinal();
        for (int t = 0; t < HallNet.ROWS; t++) {
            if (TerraceLayout.set(col, t) == null) continue;
            float y = rungY(t);
            int colour = 0xFF000000 | Stage.RANK[t];
            s.title(letter[t], LADDER_X + PORTRAIT + 6F, y - 26F, 2F, t == at ? colour : Stage.argb(Stage.RANK[t], .55F));
            if (t == at) s.fill(LADDER_X - 14F, y - 40F, LADDER_X - 10F, y + 40F, colour);
        }
        g.pose().popMatrix();
    }

    private void buildLadder(ArmourPayloads.@Nullable Offers o) {
        ladderBuilt = ladderVersion;
        ladderOffers = o;
        int col = TerraceLayout.of(set).col(), at = set.rank.ordinal();
        List<Placed> out = new ArrayList<>(6);
        for (int t = 0; t < HallNet.ROWS; t++) {
            ArmourSet other = TerraceLayout.set(col, t);
            if (other == null) continue;
            boolean hidden = SuitState.hidden(o, other);
            float scale = PORTRAIT * .96F / 2F / Math.max(1F, (TerraceLayout.bounds(other)[1] - TerraceLayout.bounds(other)[0]) / 2F);
            out.add(Placed.suit(other, LADDER_X + PORTRAIT / 2F, rungY(t), 0F, scale, -24F, -4F, Placed.all(hidden ? Placed.SHAPE : Placed.FULL), t == at ? 1F : .78F));
        }
        ladder = out.toArray(new Placed[0]);
    }

    private void drawPanel(Stage s, float dx, float mx, float my, float dt, float time) {
        var g = s.g;
        g.pose().pushMatrix().translate(dx, 0F);
        Panel.frame(s);
        g.nextStratum();
        int colour = 0xFF000000 | Stage.RANK[set.rank.ordinal()];
        float y = Panel.Y0 + 22F;
        s.text(eyebrow, X, y, 3F, colour);
        y += 36F;
        float wide = s.titleWidth(name, nameSize);
        s.glow(X + wide / 2F, y + 12F * nameSize, wide / 2F + 40F, 22F * nameSize, Stage.RANK[set.rank.ordinal()], .22F);
        s.title(name, X, y, nameSize, Stage.TITLE);
        y += 26F * nameSize + 4F;
        if (stats.isEmpty() || special.isEmpty()) y = Panel.lines(s, blurb, y, 2, Stage.LABEL) + 12F;
        if (!stats.isEmpty()) {
            float x = X;
            for (String[] st : stats) {
                float column = Math.max(s.width(st[0], 5F), s.width(st[1], 2F));
                s.text(st[0], x, y, 5F, Stage.TITLE);
                s.text(st[1], x, y + 44F, 2F, Stage.EDGE);
                x += column + 30F;
            }
            y += 70F;
        }

        float bottom = Panel.Y1 - 22F, subTop = bottom - 36F;
        boolean hasSubs = !subs.isEmpty();
        float primaryTop = (hasSubs ? subTop - 12F : bottom) - 60F;
        float actionTop = primary != null ? primaryTop : hasSubs ? subTop : bottom;
        float whyTop = actionTop - 8F - why.size() * 30F;
        float inlayH = showInlay ? INLAY_SIZE + 12F + inlayLine.size() * 30F : 0F;
        float needHeight = Panel.needsHeight(needs.size());
        float pieceH = 52F + (compare.isEmpty() ? 0F : 40F);
        float last = whyTop - inlayH - needHeight - pieceH - 12F;

        // What the suit does is why you'd want it, so a full panel never drops its first line. It gives up other things in turn:
        // where a material comes from (that moves to a tip on the material), a reason the red counts already give, then the comparison.
        float first = special.isEmpty() ? 0F : special.getFirst().size() * 30F;
        boolean hint = !needs.isEmpty(), showCompare = !compare.isEmpty(), showWhy = !why.isEmpty();
        if (hint && y + first > last) {
            hint = false;
            needHeight -= Panel.SOURCE_H;
            last += Panel.SOURCE_H;
        }
        if (showWhy && states[slot] == SuitState.SHORT && y + first > last) {
            showWhy = false;
            last += why.size() * 30F;
        }
        if (showCompare && y + first > last) {
            showCompare = false;
            last += 40F;
        }
        boolean headline = true;
        for (List<FormattedCharSequence> paragraph : special) {
            if (!headline && y + paragraph.size() * 30F > last) break;
            headline = false;
            y = Panel.lines(s, paragraph, y, paragraph.size(), Stage.TITLE) + 4F;
        }
        y += 6F;

        // the piece you've picked, named as the thing itself
        if (!pieceIcon.isEmpty() && !SuitState.hidden(HallClient.suits, set)) Panel.icon(s, pieceIcon, X, y, 3F);
        s.title(pieceName, X + (SuitState.hidden(HallClient.suits, set) ? 0F : 58F), y + 2F, 2F, Stage.LANTERN);
        y += 52F;
        if (showCompare) {
            s.text(s.fit(compare, TEXT_W, 3F), X, y, 3F, compareColour);
            y += 40F;
        }

        int pointed = -1;
        if (!needs.isEmpty()) {
            pointed = Panel.needs(s, "What it takes", needs, y, mx, my, fly >= 1F, time, hint);
            y += needHeight;
        }
        float belowNeeds = y;

        inlayY = -1F;
        if (showInlay) {
            inlayY = y;
            drawInlay(s, y, mx, my);
        }

        if (showWhy) Panel.lines(s, why, whyTop, why.size(), whyColour);
        primaryY0 = primaryTop;
        primaryY1 = primaryTop + 60F;
        if (primary != null) {
            Panel.Act a = primary;
            boolean can = a.enabled() && fly >= 1F;
            boolean over = can && mx >= X && mx < RIGHT && my >= primaryY0 && my < primaryY1;
            if (hold.draw(s, primaryY0, primaryY1, Panel.holdLabel(a.label()), a.enabled(), can, holdKey || holdMouse, over, dt)) {
                holdKey = holdMouse = false;
                fire(a);
            }
        } else hold.amount = 0F;
        subY0 = subTop;
        subY1 = bottom;
        float x = X;
        for (int i = 0; i < subs.size() && i < subX0.length; i++) {
            Panel.Act a = subs.get(i);
            float w = s.width(a.label(), 3F) + 24F;
            subX0[i] = x;
            subX1[i] = x + w;
            boolean over = fly >= 1F && a.enabled() && mx >= x && mx < x + w && my >= subY0 && my < subY1;
            if (over) s.fill(x, subY0, x + w, subY1, 0x552A3A56);
            s.outline(x, subY0, x + w, subY1, 2, a.enabled() ? 0xA07FC3FF : 0x602A3446);
            s.text(a.label(), x + 12F, subY0 + 7F, 3F, a.enabled() ? Stage.TITLE : Stage.DIM);
            x += w + 14F;
        }
        if (!hint && pointed >= 0) Panel.sourceTip(s, needs.get(pointed), belowNeeds);
        g.pose().popMatrix();
    }

    /** Which catalysts you hold: the none-square, then one for each Force, shown as the real items. */
    private void drawInlay(Stage s, float y, float mx, float my) {
        if (inlay > 0 && have(inlay) <= 0) {
            inlay = 0;
            dirty = true;
        }
        s.text("Inlay", X, y + 14F, 3F, Stage.EDGE);
        float x = inlayX();
        for (int k = 0; k <= Forces.Force.values().length; k++) {
            float sx = x + k * INLAY_STEP;
            boolean sel = k == inlay, open = k == 0 || have(k) > 0;
            boolean over = fly >= 1F && open && mx >= sx && mx < sx + INLAY_SIZE && my >= y && my < y + INLAY_SIZE;
            s.fill(sx, y, sx + INLAY_SIZE, y + INLAY_SIZE, over ? 0xCC141C2A : 0xA0080B12);
            s.outline(sx, y, sx + INLAY_SIZE, y + INLAY_SIZE, 2, sel ? Stage.LANTERN : 0xFF2A3446);
            if (k == 0) {
                s.centre("-", sx + INLAY_SIZE / 2F, y + 14F, 3F, sel ? Stage.TITLE : Stage.DIM);
            } else {
                Panel.icon(s, new ItemStack(ArmourInlay.catalyst(Forces.Force.values()[k - 1])), sx + 2F, y + 2F, 3F);
                if (!open) s.right("0", sx + INLAY_SIZE - 3F, y + INLAY_SIZE - 22F, 2F, Stage.BAD); // drawn in full, so you know it when you find it
            }
        }
        Panel.lines(s, inlayLine, y + INLAY_SIZE + 6F, inlayLine.size(), inlay == 0 ? Stage.DIM : ArmourInlay.colour(Forces.Force.values()[inlay - 1]));
    }

    private static float inlayX() {
        return X + 96F;
    }

    private static int have(int k) {
        var player = Minecraft.getInstance().player;
        var item = ArmourInlay.catalyst(Forces.Force.values()[k - 1]);
        if (player == null || item == Items.AIR) return 0;
        return player.isCreative() ? 99 : player.getInventory().countItem(item);
    }

    private void drawFoot(Stage s, float slide) {
        float y = 916F + 60F * slide;
        s.text("Esc", 40F, y, 3F, Stage.EDGE);
        s.text("Back", 120F, y, 3F, Stage.LABEL);
        s.text("< " + prevName, 250F, y, 3F, Stage.LABEL);
        s.centre("Drag to turn", 622F, y, 3F, Stage.DIM);
        s.right(nextName + " >", 1000F, y, 3F, Stage.LABEL);
    }

    // ---- input --------------------------------------------------------------------------------------------------------

    @Override boolean click(float x, float y) {
        if (fly < 1F) return false;
        if (x >= 30F && x < 220F && y >= 906F && y < 946F) {
            closing = true;
            return true;
        }
        if (y >= 906F && y < 946F) {
            if (x >= 250F && x < 430F) {
                step(-1, 0);
                return true;
            }
            if (x >= 840F && x < 1004F) {
                step(1, 0);
                return true;
            }
        }
        int zone = zoneAt(x, y);
        if (zone >= 0) {
            pick(zone);
            return true;
        }
        if (choosing == null && x >= 0F && x < LADDER_X + PORTRAIT + 60F && y >= LADDER_TOP && y < LADDER_BOTTOM) {
            int t = Math.clamp((int) ((LADDER_BOTTOM - y) / RUNG), 0, HallNet.ROWS - 1);
            ArmourSet other = TerraceLayout.set(TerraceLayout.of(set).col(), t);
            if (other != null) select(other);
            return true;
        }
        if (primary != null && primary.enabled() && x >= X && x < RIGHT && y >= primaryY0 && y < primaryY1) {
            if (HallConfig.clickToCommit()) fire(primary);
            else holdMouse = true;
            return true;
        }
        if (y >= subY0 && y < subY1) {
            for (int i = 0; i < subs.size() && i < subX0.length; i++) {
                Panel.Act a = subs.get(i);
                if (x >= subX0[i] && x < subX1[i]) {
                    if (a.enabled()) fire(a);
                    return true;
                }
            }
        }
        if (inlayY >= 0F && y >= inlayY && y < inlayY + INLAY_SIZE && x >= inlayX()) {
            int k = (int) ((x - inlayX()) / INLAY_STEP);
            if (k <= Forces.Force.values().length && (k == 0 || have(k) > 0) && inlay != k) {
                inlay = k;
                dirty = true;
            }
            return true;
        }
        return x >= Panel.X0;
    }

    @Override boolean key(int key) {
        if (key == InputConstants.KEY_TAB) {
            pick(slot + 1);
            return true;
        }
        for (Panel.Act a : subs) {
            if (a.key() == key) {
                if (a.enabled()) fire(a);
                return true;
            }
        }
        return false;
    }

    @Override void space(boolean down) {
        if (down && HallConfig.clickToCommit()) {
            if (primary != null && primary.enabled() && fly >= 1F) fire(primary);
            return;
        }
        holdKey = down;
    }

    private void fire(Panel.Act a) {
        ArmourPayloads.Offers o = HallClient.suits;
        switch (a.op()) {
            case FORGE, MEMORY -> {
                if (o == null || !o.stand()) return;
                ForgeClient.forge(o.pos(), a.op() == FORGE ? ArmourPayloads.START : ArmourPayloads.MEMORY, set.ordinal() * 4 + slot, ForgeSong.inlays(set) ? inlay : 0);
            }
            case TRACK -> ClientPacketDistributor.sendToServer(new HallNet.Ask(HallNet.TRACK, set.id, HallClient.standPos()));
            case UNTRACK -> ClientPacketDistributor.sendToServer(new HallNet.Ask(HallNet.UNTRACK, set.id, HallClient.standPos()));
            case LOOKS -> {
                if (looks != null) looks.run();
            }
            case CHOOSE -> {
                if (choosing != null) {
                    HallSounds.rank();
                    choosing.choose(set);
                    dirty = true;
                }
            }
            default -> {}
        }
    }
}
