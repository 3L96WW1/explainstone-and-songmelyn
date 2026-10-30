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

import com.ginka.explainstone.speaker.BroadcastRegistry;
import com.ginka.explainstone.speaker.SpeakerBlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
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

/**
 * 广播发信器：把选中的音乐以某个频道播出去，收信模式的讲解石会自动收听。 喵
 */
public class BroadcastTransmitterBlock extends Block implements BlockEntityProvider {
    /** 是否正在广播 喵 */
    public static final BooleanProperty PLAYING = BooleanProperty.of("broadcasting");
    /** 只用来节流"定期重推广播" 喵 */
    private static int BEACON_TICK;
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;

    public BroadcastTransmitterBlock(Settings settings) {
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
        // 屏幕朝向玩家 喵
        return this.getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new SpeakerBlockEntity(ModBlocks.SPEAKER_BLOCK_ENTITY, pos, state);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, net.minecraft.util.Hand hand, BlockHitResult hit) {
        return world.isClient ? ActionResult.SUCCESS : ActionResult.CONSUME;
    }

    @Override
    public void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos,
                                  boolean notify) {
        if (!world.isClient && world.getBlockEntity(pos) instanceof SpeakerBlockEntity be) {
            boolean powered = world.isReceivingRedstonePower(pos);
            boolean edge = powered != be.isLastPowered();
            be.setLastPowered(powered);
            if (edge && be.isRespectRedstone() && state.get(PLAYING) != powered) {
                world.setBlockState(pos, state.with(PLAYING, powered), Block.NOTIFY_ALL);
                if (powered) {
                    if (be.getTimerSeconds() > 0) {
                        be.startTimer();
                    }
                } else {
                    be.stopTimer();
                }
                if (world instanceof ServerWorld serverWorld) {
                    BroadcastRegistry.refresh(serverWorld, pos, be);
                }
            }
        }
        super.neighborUpdate(state, world, pos, sourceBlock, sourcePos, notify);
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!world.isClient && !state.isOf(newState.getBlock()) && world instanceof ServerWorld serverWorld) {
            BroadcastRegistry.forget(serverWorld, pos);
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Override
    public boolean hasComparatorOutput(BlockState state) {
        return true;
    }

    @Override
    public int getComparatorOutput(BlockState state, World world, BlockPos pos) {
        return state.get(PLAYING) ? 15 : 0;
    }

    /** 定时播放：广播中按秒倒数，到点自动停 喵 */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient) {
            return null;
        }
        return (w, pos, st, be) -> {
            if (!(be instanceof SpeakerBlockEntity speaker)) {
                return;
            }
            BlockState current = w.getBlockState(pos);
            boolean broadcasting = current.getBlock() instanceof BroadcastTransmitterBlock && current.get(PLAYING);
            if (broadcasting && w instanceof ServerWorld serverWorld) {
                // 每 5 秒重推一次，客户端换了维度/晚进信号范围/漏收包都能自愈 喵
                if (++BEACON_TICK % 100 == 0) {
                    BroadcastRegistry.refresh(serverWorld, pos, speaker);
                }
            }
            if (broadcasting && speaker.getTimerSeconds() > 0 && !speaker.tickTimer()) {
                w.setBlockState(pos, current.with(PLAYING, false), Block.NOTIFY_ALL);
                if (w instanceof ServerWorld serverWorld) {
                    BroadcastRegistry.refresh(serverWorld, pos, speaker);
                }
            }
        };
    }
}
