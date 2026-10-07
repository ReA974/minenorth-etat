package fr.minenorth.etat.client;

import fr.minenorth.etat.network.EtatNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

/** Écran du bureau de vote : se présenter, retirer sa candidature, voter. Le décompte des voix reste secret jusqu'à la fin. */
public final class BallotScreen extends Screen {
    private static final int W = 260, ROW = 22, ROWS = 6;
    private static final int PANEL = 0xF00E0A34, BORDER = 0xFF2E2480, TEXT = 0xFFCFE3FF, DIM = 0xFF8FA8E0, OK = 0xFF5FE0A0, BAD = 0xFFFF5F6B, WHITE = 0xFFFFFFFF;

    private EtatNetwork.StatePacket state;
    private int offset;
    private long receivedAt = System.currentTimeMillis();

    public BallotScreen(EtatNetwork.StatePacket state) {
        super(Component.literal("Bureau de vote"));
        this.state = state;
    }

    public void update(EtatNetwork.StatePacket p) {
        this.state = p;
        this.receivedAt = System.currentTimeMillis();
        rebuildWidgets();
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - panelHeight()) / 2; }
    private int panelHeight() { return 118 + Math.min(ROWS, Math.max(1, state.candidates().size())) * ROW; }

    private void send(int action, UUID target) {
        EtatNetwork.CHANNEL.sendToServer(new EtatNetwork.ActionPacket(action, target));
    }

    @Override
    protected void init() {
        int x = left(), y = top();
        List<EtatNetwork.Candidate> cands = state.candidates();
        int shown = Math.min(ROWS, cands.size());
        offset = Math.max(0, Math.min(offset, Math.max(0, cands.size() - ROWS)));
        int ry = y + 62;
        for (int i = 0; i < shown; i++) {
            EtatNetwork.Candidate c = cands.get(offset + i);
            boolean can = state.open() && state.canVote() && !state.hasVoted();
            addRenderableWidget(Button.builder(Component.literal(c.mine() ? "Votre vote" : "Voter"), b -> send(EtatNetwork.A_VOTE, c.id()))
                    .bounds(x + W - 78, ry + i * ROW, 70, 18).build()).active = can;
        }
        int by = y + 62 + Math.min(ROWS, Math.max(1, cands.size())) * ROW + 6;
        Button candidate = addRenderableWidget(Button.builder(Component.literal("Me présenter"), b -> send(EtatNetwork.A_CANDIDATE, new UUID(0, 0)))
                .bounds(x + 8, by, 118, 18).build());
        candidate.active = state.open() && state.canVote() && !state.isCandidate();
        Button withdraw = addRenderableWidget(Button.builder(Component.literal("Retirer ma candidature"), b -> send(EtatNetwork.A_WITHDRAW, new UUID(0, 0)))
                .bounds(x + 130, by, 122, 18).build());
        withdraw.active = state.open() && state.isCandidate();
        addRenderableWidget(Button.builder(Component.literal("Fermer"), b -> onClose()).bounds(x + W / 2 - 40, by + 24, 80, 18).build());
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int max = Math.max(0, state.candidates().size() - ROWS);
        int old = offset;
        offset = Math.max(0, Math.min(max, offset - (int) Math.signum(delta)));
        if (offset != old) rebuildWidgets();
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int x = left(), y = top();
        g.fill(x - 1, y - 1, x + W + 1, y + panelHeight() + 1, BORDER);
        g.fill(x, y, x + W, y + panelHeight(), PANEL);
        g.drawCenteredString(font, "§lBUREAU DE VOTE", x + W / 2, y + 8, WHITE);
        g.drawString(font, state.mayor().isEmpty() ? "Maire : personne" : "Maire : " + state.mayor(), x + 8, y + 22, TEXT, false);
        if (state.open()) {
            int left = Math.max(0, state.secondsLeft() - (int) ((System.currentTimeMillis() - receivedAt) / 1000));
            String time = left >= 3600 ? (left / 3600) + " h " + ((left % 3600) / 60) + " min" : (left / 60) + " min " + (left % 60) + " s";
            g.drawString(font, "Élection en cours · fin dans " + time, x + 8, y + 34, OK, false);
        } else {
            g.drawString(font, "Aucune élection en cours.", x + 8, y + 34, DIM, false);
        }
        String status = !state.canVote() ? "Il faut une carte d'identité pour voter."
                : state.hasVoted() ? "Vous avez voté." : state.open() ? "Un vote par citoyen, le décompte reste secret." : "";
        g.drawString(font, status, x + 8, y + 46, DIM, false);
        List<EtatNetwork.Candidate> cands = state.candidates();
        if (cands.isEmpty()) g.drawString(font, state.open() ? "Aucun candidat pour le moment." : "", x + 10, y + 68, DIM, false);
        for (int i = 0; i < Math.min(ROWS, cands.size()); i++) {
            EtatNetwork.Candidate c = cands.get(offset + i);
            g.drawString(font, font.plainSubstrByWidth(c.name(), W - 100), x + 10, y + 62 + i * ROW + 5, c.mine() ? OK : TEXT, false);
        }
        if (cands.size() > ROWS) g.drawString(font, (offset + 1) + "-" + Math.min(cands.size(), offset + ROWS) + " / " + cands.size() + " (molette)", x + 10, y + 62 + ROWS * ROW, DIM, false);
        if (!state.message().isEmpty()) g.drawCenteredString(font, state.message(), x + W / 2, y + panelHeight() - 12, state.ok() ? OK : BAD);
        super.render(g, mx, my, pt);
    }

    @Override
    public void onClose() {
        send(EtatNetwork.A_CLOSE, new UUID(0, 0));
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
