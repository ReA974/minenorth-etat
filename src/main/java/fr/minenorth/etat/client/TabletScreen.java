package fr.minenorth.etat.client;

import fr.minenorth.etat.Etat;
import fr.minenorth.etat.TabletService;
import fr.minenorth.etat.network.EtatNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/** Tablette de la mairie : résumé, salaires, agents, élection. Le serveur revérifie chaque action. */
public final class TabletScreen extends Screen {
    private static final int W = 380, H = 244;
    private static final int PANEL = 0xFF161048, BORDER = 0xFF0E0E10, TEXT = 0xFFCFE3FF, DIM = 0xFF8FA8E0, OK = 0xFF5FE0A0, BAD = 0xFFFF5F6B, WHITE = 0xFFFFFFFF;
    private static final String[] TABS = {"Résumé", "Salaires", "Agents", "Élection", "Lois", "Annonce"};

    private EtatNetwork.TabletState state;
    private int tab;
    private int selectedSalary = -1, salaryScroll;
    private long receivedAt = System.currentTimeMillis();
    private EditBox box1, box2;

    public TabletScreen(EtatNetwork.TabletState state) {
        super(Component.literal("Tablette de la mairie"));
        this.state = state;
    }

    public void update(EtatNetwork.TabletState p) {
        this.state = p;
        this.receivedAt = System.currentTimeMillis();
        rebuildWidgets();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (tab == 1 && state.salaries().size() > 5) {
            int max = state.salaries().size() - 5;
            int next = Math.max(0, Math.min(max, salaryScroll - (int) Math.signum(delta)));
            if (next != salaryScroll) { salaryScroll = next; rebuildWidgets(); }
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    private boolean mayor() { return state.role() == TabletService.ROLE_MAYOR && !state.locked(); }
    private boolean staff() { return state.role() != TabletService.ROLE_NONE && !state.locked(); }
    private int left() { return (width - W) / 2; }
    private int top() { return Math.max(0, (height - H) / 2); }

    private void act(String cmd, String a, String b) {
        EtatNetwork.CHANNEL.sendToServer(new EtatNetwork.TabletAction(cmd, a, b));
    }

    private EditBox edit(int x, int y, int w, String hint, String value) {
        EditBox e = new EditBox(font, x, y, w, 18, Component.literal(hint));
        e.setHint(Component.literal(hint));
        e.setMaxLength(64);
        e.setValue(value);
        return addRenderableWidget(e);
    }

    /** Bouton arrondi : vert = valider, rose = supprimer / annuler, violet = choix neutre, cyan = onglet ouvert. */
    private Btn button(int x, int y, int w, String label, Runnable r, boolean active) {
        int color = label.startsWith("Révoquer") || label.startsWith("Abroger") || label.equals("Annuler") ? 0xFFC83CF0 : 0xFF1E9E52;
        return addRenderableWidget(new Btn(x, y, w, 20, label, color, r, active));
    }

    private Btn tabButton(int x, int y, int w, String label, boolean open, Runnable r) {
        return addRenderableWidget(new Btn(x, y, w, 22, label, open ? 0xFF20AAEB : 0xFF4A3CB4, r, true));
    }

    private static void rfill(GuiGraphics g, int x1, int y1, int x2, int y2, int r, int c) {
        r = Math.max(0, Math.min(r, Math.min(x2 - x1, y2 - y1) / 2));
        g.fill(x1, y1 + r, x2, y2 - r, c);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = r - (int) Math.round(Math.sqrt(r * (double) r - dy * dy));
            g.fill(x1 + inset, y1 + i, x2 - inset, y1 + i + 1, c);
            g.fill(x1 + inset, y2 - i - 1, x2 - inset, y2 - i, c);
        }
    }

    private static final class Btn extends AbstractButton {
        private final Runnable action;
        private final int color;
        Btn(int x, int y, int w, int h, String label, int color, Runnable action, boolean active) {
            super(x, y, w, h, Component.literal(label));
            this.action = action;
            this.color = color;
            this.active = active;
        }
        @Override public void onPress() { if (active) action.run(); }
        @Override protected void updateWidgetNarration(NarrationElementOutput out) { defaultButtonNarrationText(out); }
        @Override public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            int c = color;
            if (isHoveredOrFocused() && active) {
                int r = Math.min(255, ((c >> 16) & 255) + 35), gg = Math.min(255, ((c >> 8) & 255) + 35), b = Math.min(255, (c & 255) + 35);
                c = 0xFF000000 | (r << 16) | (gg << 8) | b;
            }
            rfill(g, getX(), getY(), getX() + width, getY() + height, 5, active ? c : 0xFF2A2468);
            var font = net.minecraft.client.Minecraft.getInstance().font;
            String t = font.plainSubstrByWidth(getMessage().getString(), width - 8);
            g.drawString(font, t, getX() + width / 2 - font.width(t) / 2, getY() + (height - 8) / 2, active ? WHITE : DIM, false);
        }
    }

    @Override
    protected void init() {
        int x = left(), y = top();
        int tw = (W - 16 - 4 * (TABS.length - 1)) / TABS.length;
        for (int i = 0; i < TABS.length; i++) {
            final int t = i;
            tabButton(x + 8 + i * (tw + 4), y + 26, tw, TABS[i], tab == i, () -> { tab = t; rebuildWidgets(); });
        }
        box1 = box2 = null;
        int cy = y + 52;
        switch (tab) {
            case 0 -> {
                if (mayor()) {
                    box1 = edit(x + 8, y + 112, 80, "Impôt en %", Etat.percent(state.tax()).replace(" %", ""));
                    button(x + 92, y + 112, 150, "Fixer l'impôt (achats)", () -> act("tax", box1.getValue(), ""), true);
                    box2 = edit(x + 8, y + 134, 80, "Compte en %", Etat.percent(state.wealthTax()).replace(" %", ""));
                    button(x + 92, y + 134, 150, "Fixer l'impôt (comptes)", () -> act("wtax", box2.getValue(), ""), true);
                }
            }
            case 1 -> {
                List<String> rows = state.salaries();
                salaryScroll = Math.max(0, Math.min(salaryScroll, Math.max(0, rows.size() - 5)));
                for (int i = 0; i < Math.min(5, rows.size() - salaryScroll); i++) {
                    final int idx = salaryScroll + i;
                    String[] r = rows.get(idx).split("\\|");
                    tabButton(x + 8, cy + i * 24, W - 16, r[1] + " : " + Etat.money(Etat.cents(parse(r[2]))),
                            selectedSalary == idx, () -> { selectedSalary = idx; rebuildWidgets(); }).active = mayor();
                }
                if (mayor() && selectedSalary >= 0 && selectedSalary < rows.size()) {
                    String key = rows.get(selectedSalary).split("\\|")[0];
                    box1 = edit(x + 8, y + 195, 80, "Euros", "");
                    button(x + 92, y + 194, 150, "Fixer le salaire", () -> act("salary", key, box1.getValue()), true);
                }
            }
            case 2 -> {
                List<String> agents = state.agents();
                for (int i = 0; i < Math.min(5, agents.size()); i++) {
                    String[] r = agents.get(i).split("\\|", 3);
                    final String id = r[0];
                    button(x + W - 88, cy + i * 20, 80, "Révoquer", () -> act("revoke", id, ""), mayor()).setHeight(19);
                }
                if (mayor()) {
                    box1 = edit(x + 8, y + 170, 110, "Pseudo Minecraft", "");
                    box2 = edit(x + 122, y + 170, 110, "Titre", "");
                    button(x + 8, y + 192, 224, "Nommer agent municipal", () -> act("appoint", box1.getValue(), box2.getValue()), true);
                }
            }
            case 3 -> {
                box1 = edit(x + 8, y + 112, 80, "Minutes", "");
                button(x + 92, y + 112, 150, "Ouvrir une élection", () -> act("elec_open", box1.getValue(), ""), mayor() && !state.electionOpen());
                button(x + 8, y + 136, 150, "Clôturer et compter", () -> act("elec_close", "", ""), mayor() && state.electionOpen());
                button(x + 162, y + 136, 150, "Annuler", () -> act("elec_cancel", "", ""), mayor() && state.electionOpen());
            }
            case 5 -> {
                if (mayor()) {
                    box1 = edit(x + 8, y + 90, W - 16, "Titre affiché à l'écran de tous (40 max.)", "");
                    box1.setMaxLength(40);
                    box2 = edit(x + 8, y + 114, W - 16, "Message (200 caractères max., facultatif)", "");
                    box2.setMaxLength(200);
                    button(x + 8, y + 140, 150, "Diffuser l'annonce", () -> act("announce", box1.getValue(), box2.getValue()), true);
                }
            }
            default -> {
                List<String> laws = state.laws();
                for (int i = 0; i < Math.min(4, laws.size()); i++) {
                    final String num = laws.get(i).split("\\|", 5)[0];
                    button(x + W - 76, cy + i * 18, 68, "Abroger", () -> act("law_repeal", num, ""), mayor()).setHeight(17);
                }
                if (mayor()) {
                    box1 = edit(x + 8, y + 132, W - 16, "Titre de la loi", "");
                    box2 = edit(x + 8, y + 154, W - 16, "Texte de la loi (400 caractères max.)", "");
                    box2.setMaxLength(400);
                    button(x + 8, y + 178, 150, "Promulguer la loi", () -> act("law_add", box1.getValue(), box2.getValue()), true);
                }
            }
        }
        button(x + W / 2 - 50, y + H - 28, 100, "Fermer", this::onClose, true);
    }

    private static double parse(String v) {
        try { return Double.parseDouble(v); } catch (NumberFormatException e) { return 0; }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int x = left(), y = top();
        rfill(g, x - 3, y - 3, x + W + 3, y + H + 3, 9, BORDER);
        rfill(g, x, y, x + W, y + H, 7, PANEL);
        g.drawCenteredString(font, "§lTABLETTE DE LA MAIRIE", x + W / 2, y + 8, WHITE);
        g.drawString(font, state.role() == TabletService.ROLE_MAYOR ? "Maire" : "Agent", x + 8, y + 8, DIM, false);
        if (state.locked()) g.drawString(font, "Pouvoirs suspendus : lecture seule", x + 8, y + 18, BAD, false);
        int cy = y + 52;
        switch (tab) {
            case 0 -> {
                g.drawString(font, "Trésor : " + Etat.money(state.balance()), x + 8, cy, OK, false);
                g.drawString(font, "Impôt sur les achats : " + Etat.percent(state.tax()) + " (max " + Etat.percent(state.taxMax()) + ")", x + 8, cy + 12, TEXT, false);
                g.drawString(font, state.mayor().isEmpty() ? "Maire : personne" : "Maire : " + state.mayor(), x + 8, cy + 24, TEXT, false);
                g.drawString(font, "Impôt sur les comptes : " + Etat.percent(state.wealthTax()) + " (max " + Etat.percent(state.wealthTaxMax()) + ")", x + 8, cy + 36, TEXT, false);
                SimpleDateFormat fmt = new SimpleDateFormat("dd/MM HH:mm");
                int ly = y + 160;
                for (int i = 0; i < Math.min(3, state.ledger().size()); i++) {
                    String[] parts = state.ledger().get(i).split("\\|", 2);
                    String line = parts.length > 1 ? parts[1] : parts[0];
                    try { line = fmt.format(new Date(Long.parseLong(parts[0]))) + " " + line; } catch (RuntimeException ignored) {}
                    g.drawString(font, font.plainSubstrByWidth(line, W - 16), x + 8, ly + i * 11, DIM, false);
                }
            }
            case 1 -> {
                if (state.salaries().size() > 5) g.drawString(font, "(" + (salaryScroll + 1) + " à " + Math.min(state.salaries().size(), salaryScroll + 5) + " sur " + state.salaries().size() + " postes : molette pour défiler)", x + 8, y + 176, DIM, false);
                if (!mayor()) g.drawString(font, "Seul le maire peut modifier les salaires.", x + 8, y + 188, DIM, false);
                else if (selectedSalary < 0) g.drawString(font, "Choisissez un poste pour changer son salaire.", x + 8, y + 188, DIM, false);
            }
            case 2 -> {
                if (state.agents().isEmpty()) g.drawString(font, "Aucun agent municipal.", x + 8, cy + 4, DIM, false);
                for (int i = 0; i < Math.min(5, state.agents().size()); i++) {
                    String[] r = state.agents().get(i).split("\\|", 3);
                    g.drawString(font, font.plainSubstrByWidth(r[1] + " · " + (r.length > 2 ? r[2] : ""), W - 108), x + 8, cy + i * 20 + 5, TEXT, false);
                }
                if (!mayor()) g.drawString(font, "Seul le maire nomme et révoque les agents.", x + 8, y + 176, DIM, false);
            }
            case 5 -> {
                g.drawString(font, "Annonce officielle : un titre apparaît à l'écran de tous", x + 8, cy, TEXT, false);
                g.drawString(font, "les joueurs, avec votre message dans le chat.", x + 8, cy + 12, TEXT, false);
                g.drawString(font, "Coût prélevé sur le trésor, délai entre deux annonces.", x + 8, cy + 24, DIM, false);
                if (!mayor()) g.drawString(font, "Seul le maire peut faire une annonce.", x + 8, y + 140, DIM, false);
            }
            case 4 -> {
                List<String> laws = state.laws();
                if (laws.isEmpty()) g.drawString(font, "Aucune loi en vigueur.", x + 8, cy + 4, DIM, false);
                for (int i = 0; i < Math.min(4, laws.size()); i++) {
                    String[] r = laws.get(i).split("\\|", 5);
                    g.drawString(font, font.plainSubstrByWidth("n°" + r[0] + " · " + (r.length > 1 ? r[1] : ""), W - 90), x + 8, cy + i * 18 + 5, TEXT, false);
                }
                if (laws.size() > 4) g.drawString(font, "(" + laws.size() + " lois, 4 dernières affichées : tableau des lois pour tout voir)", x + 8, y + 122, DIM, false);
                if (!mayor()) g.drawString(font, "Seul le maire promulgue et abroge les lois.", x + 8, y + 140, DIM, false);
            }
            default -> {
                if (state.electionOpen()) {
                    int left = Math.max(0, state.secondsLeft() - (int) ((System.currentTimeMillis() - receivedAt) / 1000));
                    String time = left >= 3600 ? (left / 3600) + " h " + ((left % 3600) / 60) + " min" : (left / 60) + " min " + (left % 60) + " s";
                    g.drawString(font, "Élection en cours · fin dans " + time, x + 8, cy, OK, false);
                    g.drawString(font, state.candidates() + " candidat(s) · " + state.votes() + " vote(s)", x + 8, cy + 12, TEXT, false);
                } else {
                    g.drawString(font, "Aucune élection en cours.", x + 8, cy, DIM, false);
                    g.drawString(font, "Minutes vides = durée par défaut de la config.", x + 8, cy + 12, DIM, false);
                }
            }
        }
        if (!state.message().isEmpty()) g.drawCenteredString(font, state.message(), x + W / 2, y + H - 42, state.ok() ? OK : BAD);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
