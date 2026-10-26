package dev.awaken.hearth.client;

import dev.awaken.core.client.AwakenKeys;
import dev.awaken.hearth.guide.Guide;
import dev.awaken.hearth.guide.GuideData;
import dev.awaken.hearth.room.Furniture;
import dev.awaken.hearth.room.RoomScanner;
import dev.awaken.hearth.room.RoomType;
import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.Ui;
import dev.awaken.signal.client.UiSection;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The content of "Your Hearth", as {@link UiSection}s the screen hosts (it draws their titles, scrolls and clips them).
 * Built only from {@link Ui} primitives (text, pairs, cards, item flows, recipe grids, buttons), so any surface that
 * hosts Ui sections can show them. Buttons act in the click pass.
 */
final class GuideSections {
    private GuideSections() {}

    /** The scanner's rule in one line. */
    static final String RULE = "A closed, roofed room: walls, a roof, a door; at most " + RoomScanner.MAX_EXTENT + " blocks a side.";
    private static final int DIM = 0x4A5A72;
    private static final Map<Furniture, ItemStack> ICONS = new EnumMap<>(Furniture.class);
    private static final Map<Identifier, ItemStack> STACKS = new HashMap<>();
    private static final ItemStack DOOR = new ItemStack(Items.OAK_DOOR);

    /** In screen order: where you stand, the home's Comfort, Oren's offer, rooms, town, the next upgrade, a declined offer last. */
    static List<UiSection> all(Supplier<GuideData> data) {
        return List.of(new Facts(data), new Home(data), new CottageOffer(data, true), new Rooms(data), new Town(data), new Upgrade(data),
                new CottageOffer(data, false));
    }

    static ItemStack stack(Identifier id) {
        return STACKS.computeIfAbsent(id, k -> new ItemStack(BuiltInRegistries.ITEM.getValue(k)));
    }

    static String roman(int n) {
        return switch (n) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V";
            default -> String.valueOf(n);
        };
    }

    private static String pos(BlockPos p) {
        return p.getX() + " " + p.getY() + " " + p.getZ();
    }

    private static void button(Ui ui, String label, int accent, boolean enabled, int action) {
        ui.gap(2);
        int bw = Math.min(ui.width, ui.font().width(label) + 16);
        if (ui.buttonAt(label, ui.x, ui.y(), bw, Ui.BUTTON_H, accent, enabled)) GuideClient.send(action);
        ui.gap(Ui.BUTTON_H + 2);
    }

    private abstract static class Part extends UiSection {
        final Supplier<GuideData> data;

        Part(Supplier<GuideData> data) {
            this.data = data;
        }
    }

    /** Radius, rest healing, where you respawn. */
    static final class Facts extends Part {
        Facts(Supplier<GuideData> data) {
            super(data);
        }

        @Override public String title() { return ""; }

        @Override
        protected void build(Ui ui) {
            GuideData d = data.get();
            int label = Glass.a(Glass.LABEL, 1f), text = Glass.a(Glass.TEXT, 1f);
            ui.pair("Radius", d.radius() + " blocks around " + (d.tier() <= 0 ? "your fire" : "the Anchor"), label, text);
            ui.pair("Rest heals a Fracture", "in " + d.rest() + " s standing still (" + d.sanctuaryRest() + " s in a Sanctuary)", label, text);
            String home = d.tier() <= 0 ? "the fire" : "the Anchor";
            ui.pair("You respawn", d.respawnHere() ? "here, by " + home : "at a bed elsewhere (using " + home + " brings it back)", label, text);
        }
    }

    /** The home as the Hearth reads it: Comfort (and what adds a point), Style, the land, how long you sleep Rested. */
    static final class Home extends Part {
        Home(Supplier<GuideData> data) {
            super(data);
        }

        @Override public String title() { return "HOME"; }

        @Override
        protected void build(Ui ui) {
            GuideData.Home h = data.get().home();
            int label = Glass.a(Glass.LABEL, 1f), text = Glass.a(Glass.TEXT, 1f);
            ui.pair("Comfort", h.comfort() + " of " + dev.awaken.hearth.Comfort.MAX, label, Glass.a(Glass.AMBER, 1f));
            ui.bar(h.comfort() / (double) dev.awaken.hearth.Comfort.MAX, Glass.a(Glass.LANTERN, 1f));
            ui.gap(2);
            if (!h.comfortNext().isEmpty()) ui.text("A point more: " + h.comfortNext() + ".", Glass.a(Glass.AMBER, 0.9f));
            ui.text("Comfort is your best bedroom, a warm room, a table and seats, soft things, plants, and the land round them: "
                    + "a field, a pen, a garden, a lit yard, a wall. The best of each counts.", Glass.a(Glass.LABEL, 0.85f));
            if (!h.style().isEmpty()) ui.pair("Style", h.style(), label, text);
            if (!h.land().isEmpty()) ui.pair("The land", String.join(", ", h.land()), label, text);
            ui.pair("Rested", h.restedMinutes() + " min when you wake in your own bed here", label, text);
        }
    }

    /** Oren's cottage: on top until it stands; after a "no", at the bottom ("Changed your mind?"). */
    static final class CottageOffer extends Part {
        private final boolean top;

        CottageOffer(Supplier<GuideData> data, boolean top) {
            super(data);
            this.top = top;
        }

        @Override public String title() { return "OREN'S COTTAGE"; }

        @Override
        public boolean visible() {
            int st = data.get().cottage();
            if (st == GuideData.COTTAGE_NONE || st == GuideData.COTTAGE_BUILT) return false;
            return top != (st == GuideData.COTTAGE_DECLINED);
        }

        @Override
        protected void build(Ui ui) {
            int st = data.get().cottage();
            ui.card(Glass.LANTERN, in -> {
                switch (st) {
                    case GuideData.COTTAGE_BUILDING -> in.text("Oren is raising it beside your Anchor. Close this and watch.", Glass.a(Glass.TEXT, 1f));
                    case GuideData.COTTAGE_NO_ROOM -> {
                        in.text("Oren found no open ground for it. Clear a flat patch about 12 blocks across near the Anchor, away "
                                + "from your builds (a few trees are fine), then try again.", Glass.a(Glass.TEXT, 1f));
                        button(in, "Try again", Glass.LANTERN, true, Guide.Act.COTTAGE);
                    }
                    case GuideData.COTTAGE_DECLINED -> {
                        in.text("Changed your mind? Oren will still raise a small cottage with a bed and a Meditation Mat beside "
                                + "your Anchor. Free, this once.", Glass.a(Glass.TEXT, 0.9f));
                        button(in, "Raise it", Glass.LANTERN, true, Guide.Act.COTTAGE);
                    }
                    default -> {
                        in.text("Oren can raise a small cottage beside your Anchor: a bed and a Meditation Mat, your first "
                                + "Sanctuary. Free, this once.", Glass.a(Glass.TEXT, 1f));
                        button(in, "Raise it", Glass.LANTERN, true, Guide.Act.COTTAGE);
                    }
                }
            });
        }
    }

    /** A card per room type: what it gives, what it needs (ticked from the radius), recipes, the rule, why not yet. */
    static final class Rooms extends Part {
        Rooms(Supplier<GuideData> data) {
            super(data);
        }

        @Override public String title() { return "ROOMS"; }

        @Override
        protected void build(Ui ui) {
            GuideData d = data.get();
            ui.text("Build it and the Hearth reads it: no menus. Every closed, roofed room is read on six things (closed, light, "
                    + "warmth, room, care, detail), graded Bare, Snug, Fine or Grand (never more than a step above its weakest), and "
                    + "named. Furniture says what a room is for. Doors, glass panes, fences, walls, slabs and stairs close gaps.",
                    Glass.a(Glass.LABEL, 1f));
            ui.gap(4);
            for (GuideData.RoomView r : d.home().rooms()) {
                RoomPanel.card(ui, r.name(), r.grade(), r::reading, r.purpose(), r.lift(), false);
                ui.gap(4);
            }
            // the kinds you have, as chips
            var have = ui.flow();
            boolean any = false;
            for (RoomType t : RoomType.values()) {
                GuideData.RoomCard c = d.card(t.ordinal());
                if (c.built().isEmpty()) continue;
                have.chip(c.built().size() > 1 ? t.label + " ×" + c.built().size() : t.label, Glass.GOOD);
                any = true;
            }
            if (any) {
                have.end();
                ui.gap(4);
            } else {
                have.end();
            }
            // at most three to build next (what you've started first), the rest one dim line each
            List<RoomType> next = suggestions(d);
            for (RoomType t : next) {
                room(ui, t);
                ui.gap(5);
            }
            for (RoomType t : RoomType.values()) {
                if (next.contains(t) || !d.card(t.ordinal()).built().isEmpty()) continue;
                ui.text(t.label + " · " + t.needs + " · " + t.gives, Glass.a(DIM, 1f));
            }
        }

        /** What you've started (some of its furniture is here, or it has a reason it isn't one yet), then the first rooms. */
        static List<RoomType> suggestions(GuideData d) {
            List<RoomType> out = new ArrayList<>(3);
            for (RoomType t : RoomType.values()) {
                GuideData.RoomCard c = d.card(t.ordinal());
                if (!c.built().isEmpty() || out.size() >= 3) continue;
                if (c.have() != 0 || !c.why().isEmpty()) out.add(t);
            }
            for (RoomType t : List.of(RoomType.SHELTER, RoomType.BEDROOM, RoomType.KITCHEN, RoomType.SANCTUARY)) {
                if (out.size() >= 3) break;
                if (!out.contains(t) && d.card(t.ordinal()).built().isEmpty()) out.add(t);
            }
            return out;
        }

        private void room(Ui ui, RoomType t) {
            GuideData d = data.get();
            GuideData.RoomCard c = d.card(t.ordinal());
            boolean built = !c.built().isEmpty();
            int accent = built ? Glass.GOOD : !c.why().isEmpty() ? Glass.AMBER : DIM;
            ui.card(accent, in -> {
                String status = !built ? "Not yet" : c.built().size() > 1 ? "Built ×" + c.built().size() : "Built · " + pos(c.built().get(0));
                in.pair(t.label, status, Glass.a(Glass.TITLE, 1f), Glass.a(built ? Glass.GOOD : Glass.LABEL, 1f));
                in.text(t.does, Glass.a(Glass.TEXT, 0.9f));
                in.gap(2);
                var flow = in.flow();
                for (Furniture f : t.furniture) {
                    boolean ok = built || (c.have() & f.bit()) != 0;
                    flow.item(ICONS.computeIfAbsent(f, Furniture::icon), (ok ? "✓ " : "✗ ") + f.label, Glass.a(ok ? Glass.GOOD : Glass.AMBER, 1f));
                }
                flow.item(DOOR, (built ? "✓ " : "✗ ") + "all of it in one room", Glass.a(built ? Glass.GOOD : Glass.AMBER, 1f));
                flow.end();
                if (t.condition != null) in.text("And " + t.condition + ".", Glass.a(built ? Glass.GOOD : Glass.AMBER, 0.9f));
                if (!built) {
                    in.text(RULE, Glass.a(Glass.LABEL, 0.85f));
                    for (Furniture f : t.furniture) if ((c.have() & f.bit()) == 0) recipe(in, d, f);
                }
                if (!c.why().isEmpty()) {
                    in.gap(1);
                    in.text(c.why() + (c.mark().isPresent() ? " The spot is marked with blue sparks." : ""), Glass.a(Glass.AMBER, 1f));
                }
                if (c.byOren()) in.text("Oren built this one. Build the rest yourself.", Glass.a(Glass.GOOD, 1f));
            });
        }

        /** The Hearth furniture's own recipe, read from the server; tag slots cycle once a second. */
        private void recipe(Ui ui, GuideData d, Furniture f) {
            Optional<GuideData.RecipeView> rv = d.recipe(f.ordinal());
            if (rv.isEmpty()) return;
            GuideData.RecipeView r = rv.get();
            ItemStack result = stack(r.result());
            String extra = switch (f) {
                case PAD -> " (any role pad works)";
                case TROPHY -> ", then hang a Lord's trophy on it";
                default -> "";
            };
            ui.text("Make " + (r.count() > 1 ? r.count() + " × " : "a ") + result.getHoverName().getString() + extra + ":", Glass.a(Glass.LABEL, 1f));
            var mc = Minecraft.getInstance();
            long second = mc.level == null ? 0 : mc.level.getGameTime() / 20;
            List<ItemStack> cells = new ArrayList<>(r.slots().size());
            for (List<Identifier> opts : r.slots()) cells.add(opts.isEmpty() ? ItemStack.EMPTY : stack(opts.get((int) (second % opts.size()))));
            ui.recipe(r.width(), r.height(), cells, result, r.count());
        }
    }

    /** Who lives here and who comes next. */
    static final class Town extends Part {
        Town(Supplier<GuideData> data) {
            super(data);
        }

        @Override public String title() { return "TOWN"; }

        @Override
        protected void build(Ui ui) {
            GuideData d = data.get();
            if (d.tier() <= 0) {
                ui.text("Nobody lives here yet: the first nights are yours alone. People come with the Anchor, when a Lord's Heart "
                        + "goes on the fire.", Glass.a(Glass.TEXT, 1f));
                return;
            }
            // (W6) people move in when you know them, a spare Snug bedroom stands and their trade has its room
            if (d.town().isEmpty()) {
                ui.text("Nobody lives here yet. People you know come when there's a spare bedroom, Snug or better, and a room for their work.",
                        Glass.a(Glass.TEXT, 1f));
            } else {
                for (GuideData.Person p : d.town()) {
                    ui.text(p.name() + " · " + p.role() + "   " + p.when(), Glass.a(p.when().startsWith("needs") ? Glass.AMBER : Glass.TEXT, 1f));
                }
            }
            if (!d.coming().isEmpty()) {
                ui.gap(1);
                for (GuideData.Person p : d.coming()) {
                    ui.text(p.name() + " · " + p.role() + "   " + p.when(), Glass.a(Glass.LABEL, 1f));
                }
            }
            ui.text("Talk to them: commissions at the forge, tonics, the mine's haul, rumours, your Codex, mending.", Glass.a(Glass.LABEL, 0.8f));
        }
    }

    /** The next Anchor tier: what it gives, the price with what you carry, and [Y] Build. */
    static final class Upgrade extends Part {
        Upgrade(Supplier<GuideData> data) {
            super(data);
        }

        @Override public String title() { return "NEXT UPGRADE"; }

        @Override
        protected void build(Ui ui) {
            GuideData.Upgrade u = data.get().upgrade();
            if (u.maxed()) {
                ui.text("Your Hearth is complete.", Glass.a(Glass.GOOD, 1f));
                return;
            }
            if (data.get().tier() <= 0) {
                ui.text("Tier I, the Anchor: a Lord's Heart on the fire. Radius " + u.radius() + ", the town comes, and requests.",
                        Glass.a(Glass.TEXT, 1f));
                ui.text("A Gate Lord's hoard holds one. Use it on the fire, or here.", Glass.a(Glass.LABEL, 1f));
            } else {
                ui.text("Tier " + roman(u.next()) + ": radius " + u.radius() + ", rest heals in " + u.rest() + " s.", Glass.a(Glass.TEXT, 1f));
            }
            if (!u.rankMet()) ui.text("Opens at " + u.rank() + " (pass the Reassessment). You will need:", Glass.a(Glass.LABEL, 1f));
            // (W6) the tier grows from the home: what it must have first
            for (GuideData.Need n : u.needs()) ui.text((n.met() ? "✓ " : "✗ ") + n.text(), Glass.a(n.met() ? Glass.GOOD : Glass.AMBER, 1f));
            ui.gap(1);
            var flow = ui.flow();
            for (GuideData.Part p : u.parts()) {
                ItemStack s = stack(p.item());
                boolean enough = p.have() >= p.need();
                flow.item(s, Math.min(p.have(), 999) + "/" + p.need() + " " + s.getHoverName().getString(), Glass.a(enough ? Glass.GOOD : Glass.AMBER, 1f));
            }
            flow.end();
            if (u.discount() > 0) ui.text("Your rank takes " + u.discount() + "% off.", Glass.a(Glass.LABEL, 1f));
            if (u.rankMet()) {
                String key = AwakenKeys.ACCEPT.getTranslatedKeyMessage().getString();
                String act = data.get().tier() <= 0 ? "Feed the fire the Heart" : "Build Tier " + roman(u.next());
                button(ui, u.affordable() ? "[" + key + "] " + act : data.get().tier() <= 0 ? "Bring a Lord's Heart"
                                : !u.homeReady() ? "The home grows first" : "Gather the materials first",
                        Glass.LANTERN, u.affordable(), Guide.Act.UPGRADE);
            }
        }
    }
}
