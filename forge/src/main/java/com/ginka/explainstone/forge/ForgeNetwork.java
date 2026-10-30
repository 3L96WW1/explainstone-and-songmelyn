package com.ginka.explainstone.forge;

import com.ginka.explainstone.Explainstone;
import com.ginka.explainstone.client.audio.SpeakerAudioCache;
import com.ginka.explainstone.platform.Network;
import com.ginka.explainstone.speaker.SpeakerNetworking;
import com.ginka.explainstone.speaker.SpeakerPayloads;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ForgeNetwork implements Network {
    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Explainstone.MOD_ID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static void register(IEventBus bus) {
        CHANNEL.messageBuilder(SpeakerMessage.class, 0)
                .encoder((message, buffer) -> {
                    buffer.writeVarInt(message.channelOrdinal);
                    buffer.writeByteArray(message.data);
                })
                .decoder(buffer -> new SpeakerMessage(buffer.readVarInt(), buffer.readByteArray()))
                .consumerMainThread((message, context) -> handle(message, context.get()))
                .add();
    }

    private static void handle(SpeakerMessage message, NetworkEvent.Context context) {
        SpeakerPayloads.Channel[] channels = SpeakerPayloads.Channel.values();
        if (message.channelOrdinal < 0 || message.channelOrdinal >= channels.length) {
            return;
        }
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(message.data));
        SpeakerPayloads.Channel channel = channels[message.channelOrdinal];
        if (context.getDirection().getReceptionSide().isServer()) {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            switch (channel) {
                case ACTION -> SpeakerNetworking.handleAction(player, SpeakerPayloads.Action.read(buffer));
                case IMPORT -> SpeakerNetworking.handleImport(player, SpeakerPayloads.ImportChunk.read(buffer));
                case REQUEST -> SpeakerNetworking.handleRequest(player, SpeakerPayloads.RequestAudio.read(buffer));
                case RESYNC -> {
                    SpeakerPayloads.Resync.read(buffer);
                    SpeakerNetworking.onJoin(player);
                }
                default -> {
                }
            }
        } else {
            switch (channel) {
                case AUDIO -> SpeakerAudioCache.onAudioChunk(SpeakerPayloads.AudioChunk.read(buffer));
                case BROADCAST -> SpeakerAudioCache.onBroadcast(SpeakerPayloads.Broadcast.read(buffer));
                default -> {
                }
            }
        }
    }

    @Override
    public void sendToServer(SpeakerPayloads.Packet payload) {
        CHANNEL.sendToServer(toMessage(payload));
    }

    @Override
    public boolean canSendToServer(SpeakerPayloads.Channel channel) {
        return true;
    }

    @Override
    public void sendToPlayer(ServerPlayer player, SpeakerPayloads.Packet payload) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), toMessage(payload));
    }

    private static SpeakerMessage toMessage(SpeakerPayloads.Packet payload) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        payload.write(buffer);
        byte[] data = new byte[buffer.readableBytes()];
        buffer.readBytes(data);
        return new SpeakerMessage(payload.type().ordinal(), data);
    }

    private record SpeakerMessage(int channelOrdinal, byte[] data) {
    }
}
