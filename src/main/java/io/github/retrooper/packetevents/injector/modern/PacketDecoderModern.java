/*
 * This file is part of packetevents - https://github.com/retrooper/packetevents
 * Copyright (C) 2021 retrooper and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package io.github.retrooper.packetevents.injector.modern;

import io.github.retrooper.packetevents.PacketEvents;
import io.github.retrooper.packetevents.event.impl.PacketReceiveEvent;
import io.github.retrooper.packetevents.protocol.ConnectionState;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.bukkit.entity.Player;


public class PacketDecoderModern extends ChannelInboundHandlerAdapter {
    public volatile Player player;
    public ConnectionState connectionState = ConnectionState.HANDSHAKING;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf byteBuf = (ByteBuf) msg;
        int firstReaderIndex = byteBuf.readerIndex();
        //TODO https://wiki.vg/Protocol#Without_compression https://wiki.vg/Protocol#With_compression
        PacketReceiveEvent packetReceiveEvent = new PacketReceiveEvent(ctx.channel(), player, byteBuf);
        int readerIndex = byteBuf.readerIndex();
        PacketEvents.get().getEventManager().callEvent(packetReceiveEvent, () -> {
            byteBuf.readerIndex(readerIndex);
        });
        byteBuf.readerIndex(firstReaderIndex);
        ctx.fireChannelRead(byteBuf);
    }
}
