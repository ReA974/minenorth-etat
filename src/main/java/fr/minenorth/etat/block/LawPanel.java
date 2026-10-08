package fr.minenorth.etat.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/** Panneau rectangulaire de tableaux raccordés, vu depuis son bloc en haut à gauche (celui qui dessine le texte). */
public record LawPanel(int w, int h) {
    public static final int MAX = 16;

    private static boolean board(BlockGetter l, BlockPos p, Direction f) {
        BlockState s = l.getBlockState(p);
        return s.getBlock() instanceof LawBoardBlock && s.getValue(LawBoardBlock.FACING) == f;
    }

    /** Le panneau dont ce bloc est le coin haut-gauche, ou null (ce n'est pas le coin, ou la forme n'est pas un rectangle). */
    public static LawPanel of(BlockGetter level, BlockPos pos, BlockState state) {
        Direction f = state.getValue(LawBoardBlock.FACING), right = LawBoardBlock.right(f), left = LawBoardBlock.left(f);
        if (board(level, pos.above(), f) || board(level, pos.relative(left), f)) return null;
        int w = 1, h = 1;
        while (w < MAX && board(level, pos.relative(right, w), f)) w++;
        while (h < MAX && board(level, pos.below(h), f)) h++;
        for (int x = 0; x < w; x++)
            for (int y = 0; y < h; y++)
                if (!board(level, pos.relative(right, x).below(y), f)) return null;
        for (int x = 0; x < w; x++)
            if (board(level, pos.relative(right, x).above(), f) || board(level, pos.relative(right, x).below(h), f)) return null;
        for (int y = 0; y < h; y++)
            if (board(level, pos.below(y).relative(left), f) || board(level, pos.below(y).relative(right, w), f)) return null;
        return new LawPanel(w, h);
    }
}
