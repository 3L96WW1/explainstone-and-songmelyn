/*
 * explainstone —— 讲解石 / 颂美铃 / 广播发信器
 *
 * 作者 / Author : GINKA_3L96WW1
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.ginka.explainstone;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** 颂美铃：壁挂式音响，碰撞箱/选中框按模型实际厚度收窄。 */
public class SongmelynBlock extends SpeakerBlock {
    private static final VoxelShape SHAPE_NORTH = Block.box(1.5, 1.0, 11.0, 14.5, 15.2, 16.0);
    private static final VoxelShape SHAPE_SOUTH = Block.box(1.5, 1.0, 0.0, 14.5, 15.2, 5.0);
    private static final VoxelShape SHAPE_EAST = Block.box(0.0, 1.0, 1.5, 5.0, 15.2, 14.5);
    private static final VoxelShape SHAPE_WEST = Block.box(11.0, 1.0, 1.5, 16.0, 15.2, 14.5);

    public SongmelynBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    private static VoxelShape shapeOf(BlockState state) {
        Direction facing = state.getValue(FACING);
        return switch (facing) {
            case SOUTH -> SHAPE_SOUTH;
            case EAST -> SHAPE_EAST;
            case WEST -> SHAPE_WEST;
            default -> SHAPE_NORTH;
        };
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeOf(state);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeOf(state);
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }
}
