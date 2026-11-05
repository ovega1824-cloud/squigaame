package com.squidgame;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Un cuarto de un panel de cristal 2x2 (losa de vidrio). PX/PZ dicen qué mitad del panel es,
 * así el juego sabe a qué panel pertenece cada bloque. Los cristales "reales" y "falsos"
 * son idénticos: la diferencia solo la conoce el servidor.
 */
public class BridgeGlassBlock extends Block {
    public static final BooleanProperty PX = BooleanProperty.create("px");
    public static final BooleanProperty PZ = BooleanProperty.create("pz");
    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 8.0D, 16.0D);

    public BridgeGlassBlock(Properties props) {
        super(props);
        this.registerDefaultState(this.stateDefinition.any().setValue(PX, false).setValue(PZ, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PX, PZ);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    /** No dibujar las caras de en medio entre losas de cristal vecinas. */
    public boolean skipRendering(BlockState state, BlockState adjacent, Direction dir) {
        return adjacent.is(this) && dir.getAxis().isHorizontal();
    }

    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return new ItemStack(ModRegistry.BRIDGE_GLASS_ITEM.get());
    }
}
