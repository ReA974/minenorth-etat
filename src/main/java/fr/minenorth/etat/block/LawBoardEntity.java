package fr.minenorth.etat.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Support du rendu des titres de lois (aucune donnée : les titres viennent du cache client). */
public class LawBoardEntity extends BlockEntity {
    private LawPanel panel;
    private long checkedAt;
    private boolean checked;

    public LawBoardEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.LAW_BOARD_BE.get(), pos, state);
    }

    /** Panneau dont ce bloc est le coin haut-gauche, ou null. Recalculé une fois par seconde. */
    public LawPanel panel() {
        if (level == null) return null;
        long now = level.getGameTime();
        if (!checked || now - checkedAt >= 20 || now < checkedAt) {
            panel = LawPanel.of(level, worldPosition, getBlockState());
            checkedAt = now;
            checked = true;
        }
        return panel;
    }

    @Override
    public AABB getRenderBoundingBox() {
        LawPanel p = panel();
        if (p == null) return super.getRenderBoundingBox();
        Direction right = LawBoardBlock.right(getBlockState().getValue(LawBoardBlock.FACING));
        BlockPos far = worldPosition.relative(right, p.w() - 1).below(p.h() - 1);
        return new AABB(worldPosition).minmax(new AABB(far));
    }
}
