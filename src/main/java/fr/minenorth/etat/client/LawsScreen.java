package fr.minenorth.etat.client;

import fr.minenorth.etat.network.EtatNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Grand tableau des lois du maire (lecture seule, molette pour défiler). */
public final class LawsScreen extends Screen {
    private static final int PANEL = 0xF01A1208, BORDER = 0xFF8B5A2B, TEXT = 0xFFF2E6CF, TITLE = 0xFFFFD27A, DIM = 0xFFB59B73, WHITE = 0xFFFFFFFF;
    private static final int LINE = 10;

    private final EtatNetwork.LawsPacket data;
    private final List<Line> lines = new ArrayList<>();
    private int scroll;

    private record Line(FormattedCharSequence text, int color, int indent, boolean separator) {}

    public LawsScreen(EtatNetwork.LawsPacket data) {
        super(Component.literal("Tableau des lois"));
        this.data = data;
    }

    private int pw() { return Math.min(width - 20, 460); }
    private int ph() { return height - 20; }
    private int left() { return (width - pw()) / 2; }
    private int top() { return 10; }
    private int viewTop() { return top() + 34; }
    private int viewBottom() { return top() + ph() - 30; }

    @Override
    protected void init() {
        lines.clear();
        int wrap = pw() - 36;
        SimpleDateFormat fmt = new SimpleDateFormat("dd/MM/yyyy");
        for (String row : data.laws()) {
            String[] r = row.split("\\|", 5);
            if (r.length < 5) continue;
            for (FormattedCharSequence l : font.split(FormattedText.of("Loi n°" + r[0] + " · " + r[1]), wrap))
                lines.add(new Line(l, TITLE, 0, false));
            for (FormattedCharSequence l : font.split(FormattedText.of(r[2]), wrap - 8))
                lines.add(new Line(l, TEXT, 8, false));
            String date = r[4];
            try { date = fmt.format(new Date(Long.parseLong(r[4]))); } catch (NumberFormatException ignored) {}
            lines.add(new Line(Component.literal("Promulguée le " + date + " par " + r[3]).getVisualOrderText(), DIM, 8, false));
            lines.add(new Line(FormattedCharSequence.EMPTY, 0, 0, true));
        }
        clampScroll();
        addRenderableWidget(Button.builder(Component.literal("Fermer"), b -> onClose())
                .bounds(width / 2 - 40, top() + ph() - 24, 80, 18).build());
    }

    private int maxScroll() { return Math.max(0, lines.size() * LINE - (viewBottom() - viewTop())); }
    private void clampScroll() { scroll = Math.max(0, Math.min(scroll, maxScroll())); }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll -= (int) Math.signum(delta) * LINE * 3;
        clampScroll();
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int x = left(), y = top();
        g.fill(x - 2, y - 2, x + pw() + 2, y + ph() + 2, BORDER);
        g.fill(x, y, x + pw(), y + ph(), PANEL);
        g.drawCenteredString(font, "§l§6TABLEAU DES LOIS", x + pw() / 2, y + 8, WHITE);
        g.drawCenteredString(font, data.mayor().isEmpty() ? "Pas de maire en fonction" : "Maire : " + data.mayor(), x + pw() / 2, y + 20, DIM);
        int vt = viewTop(), vb = viewBottom();
        g.enableScissor(x, vt, x + pw(), vb);
        if (lines.isEmpty()) g.drawCenteredString(font, "Aucune loi en vigueur.", x + pw() / 2, vt + 20, DIM);
        for (int i = 0; i < lines.size(); i++) {
            int ly = vt + i * LINE - scroll;
            if (ly + LINE < vt || ly > vb) continue;
            Line l = lines.get(i);
            if (l.separator()) g.fill(x + 16, ly + 4, x + pw() - 16, ly + 5, BORDER);
            else g.drawString(font, l.text(), x + 16 + l.indent(), ly, l.color(), false);
        }
        g.disableScissor();
        if (maxScroll() > 0) {
            int bar = Math.max(12, (vb - vt) * (vb - vt) / (lines.size() * LINE));
            int by = vt + (int) ((long) (vb - vt - bar) * scroll / maxScroll());
            g.fill(x + pw() - 8, by, x + pw() - 4, by + bar, BORDER);
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
