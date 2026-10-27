/*
 * explainstone —— 讲解石 / 颂美铃 / 广播发信器（Minecraft Fabric 1.21.1 模组）
 *
 *  作者 / Author : GINKA_3L96WW1
 *
 * 本文件由作者手工编写；贴图与模型由 tools/ 下的脚本程序化生成。
 * 保留此注释即可随意修改、分发。 喵
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.ginka.explainstone;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/**
 * 颂美铃：壁挂式音响。碰撞箱/选中框按模型实际厚度收窄（贴着背面那一侧的一块薄板），
 * 空出来的部分可以走人。 喵
 */
public class SongmelynBlock extends SpeakerBlock {
    // 模型：x 1.6~14.4，y 1.2~15.2，z 11.2~15.9（背面朝 +Z） 喵
    // 模型背面在 +Z；blockstate 旋转 y=90 会把 +Z 转到 -X，所以朝东时背面在 x 0~5 喵
    private static final VoxelShape SHAPE_NORTH = Block.createCuboidShape(1.5, 1.0, 11.0, 14.5, 15.2, 16.0);
    private static final VoxelShape SHAPE_SOUTH = Block.createCuboidShape(1.5, 1.0, 0.0, 14.5, 15.2, 5.0);
    private static final VoxelShape SHAPE_EAST = Block.createCuboidShape(0.0, 1.0, 1.5, 5.0, 15.2, 14.5);
    private static final VoxelShape SHAPE_WEST = Block.createCuboidShape(11.0, 1.0, 1.5, 16.0, 15.2, 14.5);

    public SongmelynBlock(Settings settings) {
        super(settings);
    }

    private static VoxelShape shapeOf(BlockState state) {
        Direction facing = state.get(FACING);
        return switch (facing) {
            case SOUTH -> SHAPE_SOUTH;
            case EAST -> SHAPE_EAST;
            case WEST -> SHAPE_WEST;
            default -> SHAPE_NORTH;
        };
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return shapeOf(state);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return shapeOf(state);
    }

    /** 不参与方块面剔除（薄板，避免把背后的墙面剔掉） 喵 */
    @Override
    protected VoxelShape getCullingShape(BlockState state, BlockView world, BlockPos pos) {
        return net.minecraft.util.shape.VoxelShapes.empty();
    }
}
