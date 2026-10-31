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

import com.ginka.explainstone.speaker.BroadcastRegistry;
import com.ginka.explainstone.speaker.SpeakerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** 广播发信器：把选中的音乐以某个频道播出去，收信模式的讲解石会自动收听。 */
public class BroadcastTransmitterBlock extends Block implements EntityBlock {
    public static final BooleanProperty PLAYING = BooleanProperty.create("broadcasting");
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static int beaconTick;

    public BroadcastTransmitterBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(PLAYING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PLAYING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpeakerBlockEntity(ModBlocks.speakerBlockEntity, pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block sourceBlock,
                                   Orientation orientation, boolean isMoving) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof SpeakerBlockEntity be) {
            boolean powered = level.hasNeighborSignal(pos);
            boolean edge = powered != be.isLastPowered();
            be.setLastPowered(powered);
            if (edge && be.isRespectRedstone() && state.getValue(PLAYING) != powered) {
                level.setBlock(pos, state.setValue(PLAYING, powered), Block.UPDATE_ALL);
                if (powered) {
                    if (be.getTimerSeconds() > 0) {
                        be.startTimer();
                    }
                } else {
                    be.stopTimer();
                }
                if (level instanceof ServerLevel serverLevel) {
                    BroadcastRegistry.refresh(serverLevel, pos, be);
                }
            }
        }
        super.neighborChanged(state, level, pos, sourceBlock, orientation, isMoving);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean isMoving) {
        BroadcastRegistry.forget(level, pos);
        super.affectNeighborsAfterRemoval(state, level, pos, isMoving);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return state.getValue(PLAYING) ? 15 : 0;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (!(blockEntity instanceof SpeakerBlockEntity speaker)) {
                return;
            }
            BlockState current = tickLevel.getBlockState(pos);
            boolean broadcasting = current.getBlock() instanceof BroadcastTransmitterBlock && current.getValue(PLAYING);
            if (broadcasting && tickLevel instanceof ServerLevel serverLevel) {
                if (++beaconTick % 100 == 0) {
                    BroadcastRegistry.refresh(serverLevel, pos, speaker);
                }
            }
            if (broadcasting && speaker.getTimerSeconds() > 0 && !speaker.tickTimer()) {
                tickLevel.setBlock(pos, current.setValue(PLAYING, false), Block.UPDATE_ALL);
                if (tickLevel instanceof ServerLevel serverLevel) {
                    BroadcastRegistry.refresh(serverLevel, pos, speaker);
                }
            }
        };
    }
}
