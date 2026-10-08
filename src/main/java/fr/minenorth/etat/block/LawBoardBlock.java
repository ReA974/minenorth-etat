package fr.minenorth.etat.block;

import fr.minenorth.etat.LawService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Tableau des lois : panneau mural. Les tableaux voisins (même orientation) se raccordent en un grand panneau
 * rectangulaire ; clic droit pour lire toutes les lois du maire dans un grand écran.
 * UP / DOWN / LEFT / RIGHT = « raccordé à un tableau voisin de ce côté » (LEFT / RIGHT vus par le lecteur).
 */
public class LawBoardBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty UP = BlockStateProperties.UP, DOWN = BlockStateProperties.DOWN;
    public static final BooleanProperty LEFT = BooleanProperty.create("left"), RIGHT = BooleanProperty.create("right");
    private static final VoxelShape NORTH = Block.box(0, 0, 13, 16, 16, 16), SOUTH = Block.box(0, 0, 0, 16, 16, 3),
            WEST = Block.box(13, 0, 0, 16, 16, 16), EAST = Block.box(0, 0, 0, 3, 16, 16);

    public LawBoardBlock() {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0f).sound(SoundType.WOOD).ignitedByLava().noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(UP, false).setValue(DOWN, false).setValue(LEFT, false).setValue(RIGHT, false));
    }

    /** Côté droit du lecteur (qui regarde le panneau). */
    public static Direction right(Direction facing) { return facing.getCounterClockWise(); }
    public static Direction left(Direction facing) { return facing.getClockWise(); }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, UP, DOWN, LEFT, RIGHT); }

    private boolean linked(BlockState neighbor, Direction facing) {
        return neighbor.getBlock() == this && neighbor.getValue(FACING) == facing;
    }

    private BlockState link(BlockState s, Direction dir, BlockState neighbor) {
        Direction f = s.getValue(FACING);
        boolean c = linked(neighbor, f);
        if (dir == Direction.UP) return s.setValue(UP, c);
        if (dir == Direction.DOWN) return s.setValue(DOWN, c);
        if (dir == right(f)) return s.setValue(RIGHT, c);
        if (dir == left(f)) return s.setValue(LEFT, c);
        return s;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState s = defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
        for (Direction d : new Direction[]{Direction.UP, Direction.DOWN, left(s.getValue(FACING)), right(s.getValue(FACING))})
            s = link(s, d, ctx.getLevel().getBlockState(ctx.getClickedPos().relative(d)));
        return s;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return link(state, dir, neighbor);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case EAST -> EAST;
            default -> NORTH;
        };
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LawBoardEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) LawService.open(sp);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
