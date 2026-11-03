package fr.minenorth.etat.client.phone;

import fr.minenorth.etat.Etat;
import fr.minenorth.etat.TabletService;
import fr.minenorth.etat.network.EtatNetwork;
import net.minecraft.client.gui.GuiGraphics;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Application « Mairie » dessinée dans l'écran du téléphone. Mêmes fonctions que la tablette de la mairie ({@link fr.minenorth.etat.client.TabletScreen}) :
 * résumé et impôts, salaires, agents, élection, lois, annonce. Mise en page portrait. Le serveur revérifie chaque action.
 */
public class PhoneMairieScreen extends PhoneAppBase {
    private EtatNetwork.TabletState state;
    private long receivedAt = System.currentTimeMillis();
    private int selectedSalary = -1;
    private final Field wtax = new Field("Comptes en %", "", 8), stax = new Field("Salaires en %", "", 8), salary = new Field("Euros", "", 12),
            agentName = new Field("Pseudo Minecraft", "", 32), agentTitle = new Field("Titre", "", 64), minutes = new Field("Minutes", "", 6),
            lawTitle = new Field("Titre de la loi", "", 64), lawText = new Field("Texte de la loi (400 caractères max.)", "", 400),
            annTitle = new Field("Titre affiché à l'écran de tous (40 max.)", "", 40), annText = new Field("Message (200 caractères max., facultatif)", "", 200);

    public PhoneMairieScreen(EtatNetwork.TabletState state) {
        super("Mairie");
        this.state = state;
        resetTaxes();
    }

    public void update(EtatNetwork.TabletState p) {
        boolean done = p.ok() && !p.message().isEmpty();
        this.state = p;
        this.receivedAt = System.currentTimeMillis();
        if (done) {
            resetTaxes();
            for (Field f : new Field[]{salary, agentName, agentTitle, minutes, lawTitle, lawText, annTitle, annText}) f.value = "";
        }
        if (selectedSalary >= p.salaries().size()) selectedSalary = -1;
    }

    private void resetTaxes() {
        wtax.value = Etat.percent(state.wealthTax()).replace(" %", "");
        stax.value = Etat.percent(state.salaryTax()).replace(" %", "");
    }

    private boolean mayor() { return state.role() == TabletService.ROLE_MAYOR && !state.locked(); }

    private void act(String cmd, String a, String b) { EtatNetwork.CHANNEL.sendToServer(new EtatNetwork.TabletAction(cmd, a, b)); }

    private static double parse(String v) {
        try { return Double.parseDouble(v); } catch (NumberFormatException e) { return 0; }
    }

    @Override protected String title() { return "Mairie"; }

    @Override protected String[] tabs() { return new String[]{"Résumé", "Salaires", "Agents", "Élection", "Lois", "Annonce"}; }

    @Override protected String badge() { return state.locked() ? "Lecture seule" : state.role() == TabletService.ROLE_MAYOR ? "Maire" : "Agent municipal"; }

    @Override protected int badgeColor() { return state.locked() ? RED : state.role() == TabletService.ROLE_MAYOR ? GREEN : DARK; }

    @Override protected String message() { return state.message(); }

    @Override protected boolean messageError() { return !state.ok(); }

    @Override
    protected float content(GuiGraphics g, int mx, int my, float y) {
        return switch (tab) {
            case 0 -> summary(g, mx, my, y);
            case 1 -> salaries(g, mx, my, y);
            case 2 -> agents(g, mx, my, y);
            case 3 -> election(g, mx, my, y);
            case 4 -> laws(g, mx, my, y);
            default -> announce(g, mx, my, y);
        };
    }

    private float section(GuiGraphics g, String title, float y) {
        text(g, title, L, y, 9, R - L, CYAN);
        return y + 16;
    }

    private float onlyMayor(GuiGraphics g, String what, float y) { return wrap(g, "Seul le maire " + what + ".", L, y, 9, R - L, MUTED); }

    // ------------------------------------------------------------------ résumé et impôts

    private float summary(GuiGraphics g, int mx, int my, float y) {
        box(g, L, y, R, y + 56, 14, CARD);
        text(g, "Trésor de la ville", L + 12, y + 8, 9, 200, MUTED);
        text(g, Etat.money(state.balance()), L + 12, y + 23, 18, R - L - 24, OK);
        y += 62;
        text(g, state.mayor().isEmpty() ? "Maire : personne" : "Maire : " + state.mayor(), L, y, 10, R - L, TEXT);
        y += 20;
        y = section(g, "IMPÔTS", y);
        y = wrap(g, "Sur les comptes : " + Etat.percent(state.wealthTax()) + " (max " + Etat.percent(state.wealthTaxMax()) + ")", L, y, 9.5f, R - L, TEXT);
        y = wrap(g, "Sur les salaires : " + Etat.percent(state.salaryTax()) + " (max " + Etat.percent(state.salaryTaxMax()) + ")", L, y + 2, 9.5f, R - L, TEXT) + 6;
        if (mayor()) {
            field(g, mx, my, wtax, L, y, L + 90);
            button(g, mx, my, L + 96, y, R, y + 22, "Fixer l'impôt (comptes)", 9, GREEN, true, () -> act("wtax", wtax.value.trim(), ""));
            y += 28;
            field(g, mx, my, stax, L, y, L + 90);
            button(g, mx, my, L + 96, y, R, y + 22, "Fixer l'impôt (salaires)", 9, GREEN, true, () -> act("stax", stax.value.trim(), ""));
            y += 32;
        }
        y = section(g, "DERNIERS MOUVEMENTS", y + 4);
        if (state.ledger().isEmpty()) return wrap(g, "Aucun mouvement.", L, y, 9, R - L, MUTED);
        SimpleDateFormat fmt = new SimpleDateFormat("dd/MM HH:mm");
        for (String row : state.ledger()) {
            String[] parts = row.split("\\|", 2);
            String when = "";
            try { when = fmt.format(new Date(Long.parseLong(parts[0]))); } catch (RuntimeException ignored) {}
            String line = parts.length > 1 ? parts[1] : parts[0];
            if (!when.isEmpty()) text(g, when, L, y, 8, R - L, MUTED);
            y = wrap(g, line, L, y + (when.isEmpty() ? 0 : 12), 8.5f, R - L, TEXT) + 5;
        }
        return y;
    }

    // ------------------------------------------------------------------ salaires

    private float salaries(GuiGraphics g, int mx, int my, float y) {
        y = section(g, "SALAIRES PAR POSTE", y);
        List<String> rows = state.salaries();
        if (rows.isEmpty()) return wrap(g, "Aucun poste.", L, y, 9, R - L, MUTED);
        for (int i = 0; i < rows.size(); i++) {
            final int idx = i;
            String[] r = rows.get(i).split("\\|");
            boolean sel = selectedSalary == i && mayor();
            float h = sel ? 64 : 32;
            box(g, L, y, R, y + h, 12, sel ? HOVER : CARD);
            text(g, r.length > 1 ? r[1] : r[0], L + 12, y + 10, 10, R - L - 110, WHITE);
            right(g, Etat.money(Etat.cents(parse(r.length > 2 ? r[2] : "0"))), R - 12, y + 10, 10, OK);
            if (mayor()) hit(L, y, R, y + 32, () -> selectedSalary = selectedSalary == idx ? -1 : idx);
            if (sel) {
                final String key = r[0];
                field(g, mx, my, salary, L + 10, y + 34, L + 100);
                button(g, mx, my, L + 106, y + 34, R - 10, y + 56, "Fixer le salaire", 9, GREEN, !salary.value.isBlank(), () -> act("salary", key, salary.value.trim()));
            }
            y += h + 6;
        }
        return mayor() ? wrap(g, "Touchez un poste pour changer son salaire.", L, y + 2, 8.5f, R - L, MUTED) : onlyMayor(g, "peut modifier les salaires", y + 2);
    }

    // ------------------------------------------------------------------ agents

    private float agents(GuiGraphics g, int mx, int my, float y) {
        y = section(g, "AGENTS MUNICIPAUX", y);
        if (state.agents().isEmpty()) y = wrap(g, "Aucun agent municipal.", L, y, 9, R - L, MUTED) + 4;
        for (String row : state.agents()) {
            String[] r = row.split("\\|", 3);
            final String id = r[0];
            box(g, L, y, R, y + 40, 12, CARD);
            text(g, r.length > 1 ? r[1] : id, L + 12, y + 7, 10.5f, R - L - 110, WHITE);
            text(g, r.length > 2 ? r[2] : "", L + 12, y + 23, 8.5f, R - L - 110, MUTED);
            if (mayor()) button(g, mx, my, R - 12 - 84, y + 10, R - 12, y + 30, "Révoquer", 9, PINK, true, () -> act("revoke", id, ""));
            y += 46;
        }
        if (!mayor()) return onlyMayor(g, "nomme et révoque les agents", y + 2);
        y = section(g, "NOMMER UN AGENT", y + 6);
        field(g, mx, my, agentName, L, y, R);
        field(g, mx, my, agentTitle, L, y + 28, R);
        button(g, mx, my, L, y + 56, R, y + 80, "Nommer agent municipal", 10, GREEN, !agentName.value.isBlank(),
                () -> act("appoint", agentName.value.trim(), agentTitle.value.trim()));
        return y + 88;
    }

    // ------------------------------------------------------------------ élection

    private float election(GuiGraphics g, int mx, int my, float y) {
        y = section(g, "ÉLECTION", y);
        box(g, L, y, R, y + 50, 14, CARD);
        if (state.electionOpen()) {
            int left = Math.max(0, state.secondsLeft() - (int) ((System.currentTimeMillis() - receivedAt) / 1000));
            String time = left >= 3600 ? (left / 3600) + " h " + ((left % 3600) / 60) + " min" : (left / 60) + " min " + (left % 60) + " s";
            text(g, "Élection en cours · fin dans " + time, L + 12, y + 9, 10, R - L - 24, OK);
            text(g, state.candidates() + " candidat(s) · " + state.votes() + " vote(s)", L + 12, y + 28, 9, R - L - 24, TEXT);
        } else {
            text(g, "Aucune élection en cours.", L + 12, y + 9, 10, R - L - 24, MUTED);
            text(g, "Minutes vides = durée par défaut de la config.", L + 12, y + 28, 8.5f, R - L - 24, MUTED);
        }
        y += 58;
        if (!mayor()) return onlyMayor(g, "ouvre et clôture les élections", y);
        field(g, mx, my, minutes, L, y, L + 90);
        button(g, mx, my, L + 96, y, R, y + 22, "Ouvrir une élection", 9, GREEN, !state.electionOpen(), () -> act("elec_open", minutes.value.trim(), ""));
        y += 30;
        float mid = (L + R) / 2;
        button(g, mx, my, L, y, mid - 3, y + 24, "Clôturer et compter", 9, GREEN, state.electionOpen(), () -> act("elec_close", "", ""));
        button(g, mx, my, mid + 3, y, R, y + 24, "Annuler", 9, PINK, state.electionOpen(), () -> act("elec_cancel", "", ""));
        return y + 32;
    }

    // ------------------------------------------------------------------ lois

    private float laws(GuiGraphics g, int mx, int my, float y) {
        y = section(g, "LOIS EN VIGUEUR", y);
        if (state.laws().isEmpty()) y = wrap(g, "Aucune loi en vigueur.", L, y, 9, R - L, MUTED) + 4;
        for (String row : state.laws()) {
            String[] r = row.split("\\|", 5);
            final String num = r[0];
            box(g, L, y, R, y + 34, 12, CARD);
            text(g, "n°" + num + " · " + (r.length > 1 ? r[1] : ""), L + 12, y + 11, 10, R - L - (mayor() ? 110 : 24), WHITE);
            if (mayor()) button(g, mx, my, R - 12 - 80, y + 7, R - 12, y + 27, "Abroger", 9, PINK, true, () -> act("law_repeal", num, ""));
            y += 40;
        }
        if (!mayor()) return onlyMayor(g, "promulgue et abroge les lois", y + 2);
        y = section(g, "PROMULGUER UNE LOI", y + 6);
        field(g, mx, my, lawTitle, L, y, R);
        field(g, mx, my, lawText, L, y + 28, R);
        text(g, lawText.value.length() + " / 400", L, y + 54, 8, 100, MUTED);
        button(g, mx, my, L, y + 68, R, y + 92, "Promulguer la loi", 10, GREEN, !lawTitle.value.isBlank(), () -> act("law_add", lawTitle.value.trim(), lawText.value.trim()));
        return y + 100;
    }

    // ------------------------------------------------------------------ annonce

    private float announce(GuiGraphics g, int mx, int my, float y) {
        y = section(g, "ANNONCE OFFICIELLE", y);
        y = wrap(g, "Un titre apparaît à l'écran de tous les joueurs, avec votre message dans le chat.", L, y, 9.5f, R - L, TEXT);
        y = wrap(g, "Coût prélevé sur le trésor, délai entre deux annonces.", L, y + 2, 8.5f, R - L, MUTED) + 8;
        if (!mayor()) return onlyMayor(g, "peut faire une annonce", y);
        field(g, mx, my, annTitle, L, y, R);
        field(g, mx, my, annText, L, y + 28, R);
        text(g, annText.value.length() + " / 200", L, y + 54, 8, 100, MUTED);
        button(g, mx, my, L, y + 68, R, y + 92, "Diffuser l'annonce", 10, GREEN, !annTitle.value.isBlank(), () -> act("announce", annTitle.value.trim(), annText.value.trim()));
        return y + 100;
    }
}
