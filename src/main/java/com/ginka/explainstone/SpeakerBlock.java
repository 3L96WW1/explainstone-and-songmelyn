/*
 * explainstone —— 讲解石 / 颂美铃 / 广播发信器（Minecraft Fabric 1.21.1 模组）
 *
 *  作者 / Author : GINKA_3L96WW1
 *  2011
 *
 * 本文件由作者手工编写；贴图与模型由 tools/ 下的脚本程序化生成。
 * 保留此注释即可随意修改、分发。 喵
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.ginka.explainstone;

import com.ginka.explainstone.speaker.SpeakerBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class SpeakerBlock extends Block implements BlockEntityProvider {
    /** 是否正在播放（红石触发或界面按钮都会改它） 喵 */
    public static final BooleanProperty PLAYING = BooleanProperty.of("playing");
    /** 网罩朝向（模型正面朝北，放置时朝向玩家） 喵 */
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;

    public SpeakerBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState()
                .with(FACING, Direction.NORTH)
                .with(PLAYING, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, PLAYING);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        // 网罩面朝玩家 喵
        return this.getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new SpeakerBlockEntity(ModBlocks.SPEAKER_BLOCK_ENTITY, pos, state);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        // 界面本身由客户端打开（见 ExplainstoneClient），这里只需要吃掉这次交互 喵
        return world.isClient ? ActionResult.SUCCESS : ActionResult.CONSUME;
    }

    @Override
    protected void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos,
                                  boolean notify) {
        if (!world.isClient && world.getBlockEntity(pos) instanceof SpeakerBlockEntity be
                && !be.isReceiveMode()) {
            // 收信模式忽略红石（只听广播），其它模式保留红石检测 喵
            boolean powered = world.isReceivingRedstonePower(pos);
            // 只在红石信号"变化"时动作：拉杆/红石块变化才启停， 喵
            // 破坏旁边（包括脚下）的方块只是普通邻居更新，不会把正在播放的音乐关掉 喵
            boolean edge = powered != be.isLastPowered();
            be.setLastPowered(powered);
            if (edge && be.isRespectRedstone() && state.get(PLAYING) != powered) {
                world.setBlockState(pos, state.with(PLAYING, powered), Block.NOTIFY_ALL);
            }
        }
        super.neighborUpdate(state, world, pos, sourceBlock, sourcePos, notify);
    }

    @Override
    protected boolean hasComparatorOutput(BlockState state) {
        return true;
    }

    @Override
    protected int getComparatorOutput(BlockState state, World world, BlockPos pos) {
        // 正在播放 → 15 级红石信号 喵
        return state.get(PLAYING) ? 15 : 0;
    }

}
