package fr.minenorth.etat.client;

import fr.minenorth.etat.Etat;
import fr.minenorth.etat.TabletService;
import fr.minenorth.etat.network.EtatNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/** Tablette de la mairie : résumé, salaires, agents, élection. Le serveur revérifie chaque action. */
public final class TabletScreen extends Screen {
    private static final int W = 320, H = 236;
    private static final int PANEL = 0xF00E0A34, BORDER = 0xFF2E2480, TEXT = 0xFFCFE3FF, DIM = 0xFF8FA8E0, OK = 0xFF5FE0A0, BAD = 0xFFFF5F6B, WHITE = 0xFFFFFFFF;
    private static final String[] TABS = {"Résumé", "Salaires", "Agents", "Élection", "Lois"};

    private EtatNetwork.TabletState state;
    private int tab;
    private int selectedSalary = -1;
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

    private boolean mayor() { return state.role() == TabletService.ROLE_MAYOR && !state.locked(); }
    private boolean staff() { return state.role() != TabletService.ROLE_NONE && !state.locked(); }
    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }

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

    private Button button(int x, int y, int w, String label, Runnable r, boolean active) {
        Button b = addRenderableWidget(Button.builder(Component.literal(label), x0 -> r.run()).bounds(x, y, w, 18).build());
        b.active = active;
        return b;
    }

    @Override
    protected void init() {
        int x = left(), y = top();
        int tw = (W - 16) / TABS.length;
        for (int i = 0; i < TABS.length; i++) {
            final int t = i;
            Button b = button(x + 8 + i * tw, y + 24, tw - 2, TABS[i], () -> { tab = t; rebuildWidgets(); }, tab != i);
        }
        box1 = box2 = null;
        int cy = y + 52;
        switch (tab) {
            case 0 -> {
                if (mayor()) {
                    box1 = edit(x + 8, y + 112, 80, "Impôt en %", Etat.percent(state.tax()).replace(" %", ""));
                    button(x + 92, y + 112, 150, "Fixer l'impôt", () -> act("tax", box1.getValue(), ""), true);
                }
            }
            case 1 -> {
                List<String> rows = state.salaries();
                for (int i = 0; i < Math.min(6, rows.size()); i++) {
                    final int idx = i;
                    String[] r = rows.get(i).split("\\|");
                    button(x + 8, cy + i * 20, W - 16, r[1] + " : " + Etat.money(Etat.cents(parse(r[2]))),
                            () -> { selectedSalary = idx; rebuildWidgets(); }, mayor() && selectedSalary != i);
                }
                if (mayor() && selectedSalary >= 0 && selectedSalary < rows.size()) {
                    String key = rows.get(selectedSalary).split("\\|")[0];
                    box1 = edit(x + 8, y + 180, 80, "Euros", "");
                    button(x + 92, y + 180, 150, "Fixer le salaire", () -> act("salary", key, box1.getValue()), true);
                }
            }
            case 2 -> {
                List<String> agents = state.agents();
                for (int i = 0; i < Math.min(5, agents.size()); i++) {
                    String[] r = agents.get(i).split("\\|", 3);
                    final String id = r[0];
                    button(x + W - 88, cy + i * 20, 80, "Révoquer", () -> act("revoke", id, ""), mayor());
                }
                if (mayor()) {
                    box1 = edit(x + 8, y + 170, 110, "Pseudo Minecraft", "");
                    box2 = edit(x + 122, y + 170, 110, "Titre", "");
                    button(x + 8, y + 192, 224, "Nommer agent municipal", () -> act("appoint", box1.getValue(), box2.getValue()), true);
                }
            }
            case 3 -> {
                box1 = edit(x + 8, y + 112, 80, "Minutes", "");
                button(x + 92, y + 112, 150, "Ouvrir une élection", () -> act("elec_open", box1.getValue(), ""), staff() && !state.electionOpen());
                button(x + 8, y + 136, 150, "Clôturer et compter", () -> act("elec_close", "", ""), staff() && state.electionOpen());
                button(x + 162, y + 136, 150, "Annuler", () -> act("elec_cancel", "", ""), staff() && state.electionOpen());
            }
            default -> {
                List<String> laws = state.laws();
                for (int i = 0; i < Math.min(4, laws.size()); i++) {
                    final String num = laws.get(i).split("\\|", 5)[0];
                    button(x + W - 76, cy + i * 18, 68, "Abroger", () -> act("law_repeal", num, ""), mayor());
                }
                if (mayor()) {
                    box1 = edit(x + 8, y + 132, W - 16, "Titre de la loi", "");
                    box2 = edit(x + 8, y + 154, W - 16, "Texte de la loi (400 caractères max.)", "");
                    box2.setMaxLength(400);
                    button(x + 8, y + 178, 150, "Promulguer la loi", () -> act("law_add", box1.getValue(), box2.getValue()), true);
                }
            }
        }
        button(x + W / 2 - 40, y + H - 26, 80, "Fermer", this::onClose, true);
    }

    private static double parse(String v) {
        try { return Double.parseDouble(v); } catch (NumberFormatException e) { return 0; }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int x = left(), y = top();
        g.fill(x - 1, y - 1, x + W + 1, y + H + 1, BORDER);
        g.fill(x, y, x + W, y + H, PANEL);
        g.drawCenteredString(font, "§lMAIRIE", x + W / 2, y + 8, WHITE);
        g.drawString(font, state.role() == TabletService.ROLE_MAYOR ? "Maire" : "Agent", x + 8, y + 8, DIM, false);
        if (state.locked()) g.drawString(font, "Pouvoirs suspendus : lecture seule", x + 8, y + 18, BAD, false);
        int cy = y + 52;
        switch (tab) {
            case 0 -> {
                g.drawString(font, "Trésor : " + Etat.money(state.balance()), x + 8, cy, OK, false);
                g.drawString(font, "Impôt sur les achats : " + Etat.percent(state.tax()) + " (max " + Etat.percent(state.taxMax()) + ")", x + 8, cy + 12, TEXT, false);
                g.drawString(font, state.mayor().isEmpty() ? "Maire : personne" : "Maire : " + state.mayor(), x + 8, cy + 24, TEXT, false);
                SimpleDateFormat fmt = new SimpleDateFormat("dd/MM HH:mm");
                int ly = y + 138;
                for (int i = 0; i < Math.min(4, state.ledger().size()); i++) {
                    String[] parts = state.ledger().get(i).split("\\|", 2);
                    String line = parts.length > 1 ? parts[1] : parts[0];
                    try { line = fmt.format(new Date(Long.parseLong(parts[0]))) + " " + line; } catch (RuntimeException ignored) {}
                    g.drawString(font, font.plainSubstrByWidth(line, W - 16), x + 8, ly + i * 11, DIM, false);
                }
            }
            case 1 -> {
                if (state.salaries().size() > 6) g.drawString(font, "(" + state.salaries().size() + " postes, 6 premiers affichés)", x + 8, y + 172, DIM, false);
                if (!mayor()) g.drawString(font, "Seul le maire peut modifier les salaires.", x + 8, y + 184, DIM, false);
                else if (selectedSalary < 0) g.drawString(font, "Choisissez un poste pour changer son salaire.", x + 8, y + 184, DIM, false);
            }
            case 2 -> {
                if (state.agents().isEmpty()) g.drawString(font, "Aucun agent municipal.", x + 8, cy + 4, DIM, false);
                for (int i = 0; i < Math.min(5, state.agents().size()); i++) {
                    String[] r = state.agents().get(i).split("\\|", 3);
                    g.drawString(font, font.plainSubstrByWidth(r[1] + " · " + (r.length > 2 ? r[2] : ""), W - 108), x + 8, cy + i * 20 + 5, TEXT, false);
                }
                if (!mayor()) g.drawString(font, "Seul le maire nomme et révoque les agents.", x + 8, y + 176, DIM, false);
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
        if (!state.message().isEmpty()) g.drawCenteredString(font, state.message(), x + W / 2, y + H - 40, state.ok() ? OK : BAD);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
