package fr.minenorth.etat.client;

import fr.minenorth.etat.wallet.WalletMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** Interface du portefeuille, à la palette MineNorth (indigo / cyan) comme l'ATM et l'imprimante. */
public class WalletScreen extends AbstractContainerScreen<WalletMenu> {
    private static final int BORDER = 0xFF0E0E10;
    private static final int BLUE = 0xFF161048;
    private static final int SCREEN = 0xFF0E0A34;
    private static final int SLOT_BG = 0xFF2A2468;
    private static final int SLOT_EDGE = 0xFF4A3CB4;
    private static final int CYAN = 0xFF20AAEB;
    private static final int TEXT = 0xFFCFE3FF;
    private static final int HEADER_H = 26;

    public WalletScreen(WalletMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 192;
        this.inventoryLabelY = 99;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x - 3, y - 3, x + imageWidth + 3, y + imageHeight + 3, BORDER);
        g.fill(x, y, x + imageWidth, y + imageHeight, BLUE);
        g.fill(x, y, x + imageWidth, y + HEADER_H, SCREEN);
        g.fill(x, y + HEADER_H, x + imageWidth, y + HEADER_H + 1, CYAN);
        for (Slot s : menu.slots) {
            g.fill(x + s.x - 1, y + s.y - 1, x + s.x + 17, y + s.y + 17, SLOT_EDGE);
            g.fill(x + s.x, y + s.y, x + s.x + 16, y + s.y + 16, SLOT_BG);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title.copy().withStyle(s -> s.withBold(true)), 10, 9, TEXT, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, CYAN, false);
    }
}
