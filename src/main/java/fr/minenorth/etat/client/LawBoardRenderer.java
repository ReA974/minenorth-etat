package fr.minenorth.etat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.minenorth.etat.block.LawBoardBlock;
import fr.minenorth.etat.block.LawBoardEntity;
import fr.minenorth.etat.block.LawPanel;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;

import java.util.List;

/** Écrit les titres des lois sur tout le panneau ; seul le bloc en haut à gauche dessine, sur toute la surface. */
public final class LawBoardRenderer implements BlockEntityRenderer<LawBoardEntity> {
    /** Titres reçus du serveur, "num|titre", la plus récente d'abord. */
    public static volatile List<String> titles = List.of();

    private static final float SCALE = 0.009f;       // 1 px de police = 0,009 bloc
    private static final float BLOCK = 1f / SCALE;   // un bloc = ~111 px de police
    private static final int MARGIN = 7, LINE = 9;
    private static final int TITLE = 0xFF5A2E0A, TEXT = 0xFF1A1A1A, DIM = 0xFF777777;
    private final Font font;

    public LawBoardRenderer(BlockEntityRendererProvider.Context ctx) { this.font = ctx.getFont(); }

    @Override
    public boolean shouldRenderOffScreen(LawBoardEntity be) { return true; }

    @Override
    public void render(LawBoardEntity be, float pt, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
        LawPanel panel = be.panel();
        if (panel == null) return;
        Direction facing = be.getBlockState().getValue(LawBoardBlock.FACING);
        List<String> list = titles;

        // Texte plus gros sur les grands panneaux.
        float ts = Math.min(2.2f, 1f + 0.35f * (Math.min(panel.w(), panel.h()) - 1));
        float pw = panel.w() * BLOCK / ts, ph = panel.h() * BLOCK / ts;
        float x0 = -0.5f * BLOCK / ts + MARGIN, y0 = -0.5f * BLOCK / ts + MARGIN;
        float inner = pw - 2 * MARGIN;
        int maxLines = Math.max(1, (int) ((ph - 2 * MARGIN) / LINE) - 2);

        ps.pushPose();
        ps.translate(0.5, 0.5, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        // Le papier est à 13/16 du fond, donc 0,3125 DERRIÈRE le centre du bloc : +z local = côté lecteur.
        ps.translate(0, 0, -0.3125 + 0.003);
        ps.scale(SCALE * ts, -SCALE * ts, SCALE * ts);
        var pose = ps.last().pose();

        String head = "LOIS EN VIGUEUR";
        font.drawInBatch(head, x0 + (inner - font.width(head)) / 2f, y0, TITLE, false, pose, buf, Font.DisplayMode.NORMAL, 0, light);
        float y = y0 + LINE + 4;
        if (list.isEmpty()) {
            String none = "Aucune loi";
            font.drawInBatch(none, x0 + (inner - font.width(none)) / 2f, y + 8, DIM, false, pose, buf, Font.DisplayMode.NORMAL, 0, light);
        }
        int shown = Math.min(list.size(), list.size() > maxLines ? maxLines - 1 : maxLines);
        for (int i = 0; i < shown; i++) {
            String[] r = list.get(i).split("\\|", 2);
            String line = font.plainSubstrByWidth(r[0] + ". " + (r.length > 1 ? r[1] : ""), (int) inner);
            font.drawInBatch(line, x0, y, TEXT, false, pose, buf, Font.DisplayMode.NORMAL, 0, light);
            y += LINE;
        }
        if (list.size() > shown)
            font.drawInBatch("+ " + (list.size() - shown) + " autre(s)", x0, y, DIM, false, pose, buf, Font.DisplayMode.NORMAL, 0, light);
        ps.popPose();
    }
}
