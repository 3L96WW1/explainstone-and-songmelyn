/*
 * explainstone —— 讲解石 / 颂美铃 / 广播发信器 / 特朗谱
 *
 * 作者 / Author : GINKA_3L96WW1
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.ginka.explainstone.trumpet;

import com.ginka.explainstone.speaker.Track;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 读写物品上的特朗谱状态。
 *
 * <p>组件缺省时返回 {@link TrumpetState#EMPTY} 而不是 null，调用方就不用到处判空；
 * 写入前会先确认组件类型已注册，避免在注册事件之前被误调用。
 */
public final class TrumpetStates {
    private TrumpetStates() {
    }

    public static boolean available() {
        return TrumpetComponents.TRUMPET_STATE != null;
    }

    private static DataComponentType<TrumpetState> type() {
        return TrumpetComponents.TRUMPET_STATE;
    }

    public static TrumpetState get(ItemStack stack) {
        if (!available() || stack.isEmpty()) {
            return TrumpetState.EMPTY;
        }
        return stack.getOrDefault(type(), TrumpetState.EMPTY);
    }

    public static void set(ItemStack stack, TrumpetState state) {
        if (!available() || stack.isEmpty()) {
            return;
        }
        stack.set(type(), state);
    }

    public static TrumpetState addTrack(ItemStack stack, Track track) {
        TrumpetState state = get(stack);
        List<Track> tracks = new java.util.ArrayList<>(state.tracks());
        tracks.add(track);
        TrumpetState next = state.withTracks(tracks);
        set(stack, next);
        return next;
    }

    public static TrumpetState removeTrack(ItemStack stack, int index) {
        TrumpetState state = get(stack);
        if (index < 0 || index >= state.tracks().size()) {
            return state;
        }
        List<Track> tracks = new java.util.ArrayList<>(state.tracks());
        tracks.remove(index);
        int selected = state.selected();
        if (selected >= tracks.size()) {
            selected = Math.max(0, tracks.size() - 1);
        }
        TrumpetState next = state.withTracks(tracks).withSelected(selected);
        set(stack, next);
        return next;
    }
}
