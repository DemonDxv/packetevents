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

package io.github.retrooper.packetevents.manager.player;

import io.github.retrooper.packetevents.PacketEvents;
import io.github.retrooper.packetevents.manager.server.ServerVersion;
import io.github.retrooper.packetevents.utils.dependencies.VersionLookupUtils;
import io.github.retrooper.packetevents.utils.dependencies.protocolsupport.ProtocolSupportVersionLookupUtils;
import io.github.retrooper.packetevents.utils.dependencies.v_1_7_10.SpigotVersionLookup_1_7;
import io.github.retrooper.packetevents.utils.dependencies.viaversion.ViaVersionLookupUtils;
import io.github.retrooper.packetevents.utils.gameprofile.GameProfileUtil;
import io.github.retrooper.packetevents.utils.gameprofile.WrappedGameProfile;
import io.github.retrooper.packetevents.utils.geyser.GeyserUtils;
import io.github.retrooper.packetevents.utils.netty.buffer.ByteBufAbstract;
import io.github.retrooper.packetevents.utils.netty.channel.ChannelAbstract;
import io.github.retrooper.packetevents.utils.nms.MinecraftReflection;
import io.github.retrooper.packetevents.utils.nms.PlayerPingAccessorModern;
import io.github.retrooper.packetevents.wrapper.SendablePacketWrapper;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerManager {
    public final Map<Object, ClientVersion> clientVersions = new ConcurrentHashMap<>();
    public final Map<String, Object> channels = new ConcurrentHashMap<>();

    /**
     * Use the ping PacketEvents calculates for the player. (Updates every incoming Keep Alive packet)
     *
     * @param player Target player.
     * @return Non-smoothed ping.
     */
    public int getPing(Player player) {
        if (MinecraftReflection.V_1_17_OR_HIGHER) {
            return PlayerPingAccessorModern.getPing(player);
        } else {
            return MinecraftReflection.getPlayerPing(player);
        }
    }

    /**
     * Get a player's client version.
     *
     * @param player Target player.
     * @return Client Version.
     * @see #clientVersions
     */
    @NotNull
    public ClientVersion getClientVersion(@NotNull final Player player) {
        if (player.getAddress() == null) {
            return ClientVersion.UNKNOWN;
        }
        Object channel = PacketEvents.get().getPlayerManager().getChannel(player);
        ClientVersion version = clientVersions.get(channel);
        if (version == null) {
            //Prioritize asking ViaVersion and ProtocolSupport as they modify the protocol version in the packet we access it from.
            if (VersionLookupUtils.isDependencyAvailable()) {
                try {
                    version = ClientVersion.getClientVersion(VersionLookupUtils.getProtocolVersion(player));
                    clientVersions.put(channel, version);
                    return version;
                } catch (Exception ex) {
                    //Try ask the dependency again the next time, for now it is temporarily unresolved...
                    //Temporary unresolved means there is still hope, an exception was thrown on the dependency's end.
                    return ClientVersion.TEMP_UNRESOLVED;
                }
            } else {
                //We can trust the version we retrieved from the packet.
                short protocolVersion;
                //Handle 1.7.10, luckily 1.7.10 provides a method for us to access a player's protocol version(because 1.7.10 servers support 1.8 clients too)
                if (PacketEvents.get().getServerManager().getVersion().isOlderThan(ServerVersion.v_1_8)) {
                    protocolVersion = (short) SpigotVersionLookup_1_7.getProtocolVersion(player);
                } else {
                    //No dependency available, couldn't snatch the version from the packet AND server version is not 1.7.10
                    //We are pretty safe to assume the version is the same as the server, as ViaVersion AND ProtocolSupport could not be found.
                    //If you aren't using ViaVersion or ProtocolSupport, how are you supporting multiple protocol versions?
                    //(WE DONT SUPPORT CUSTOM PROTOCOL VERSION HACKS other than viaversion & protocolsupport)
                    protocolVersion = PacketEvents.get().getServerManager().getVersion().getProtocolVersion();
                }
                version = ClientVersion.getClientVersion(protocolVersion);
                clientVersions.put(channel, version);
            }
        }
        return version;
    }

    public void spoofIncomingPacket(ChannelAbstract channel, ByteBufAbstract byteBuf) {
        //TODO Also check if our encoder is RIGHT before minecraft's,
        //if it is, then don't use context to writeflush, otherwise use it (to support multiple packetevents instances)
        if (ViaVersionLookupUtils.isAvailable() && !ProtocolSupportVersionLookupUtils.isAvailable()) {
            channel.pipeline().fireChannelRead(byteBuf);
        }
        else {
            channel.pipeline().context(PacketEvents.get().encoderName).fireChannelRead(byteBuf);
        }
    }

    public void sendPacket(ChannelAbstract channel, ByteBufAbstract byteBuf) {
        //TODO Also check if our encoder is RIGHT before minecraft's,
        //if it is, then don't use context to writeflush, otherwise use it (to support multiple packetevents instances)
        if (ViaVersionLookupUtils.isAvailable() && !ProtocolSupportVersionLookupUtils.isAvailable()) {
            channel.writeAndFlush(byteBuf);
        }
        else {
            channel.pipeline().context(PacketEvents.get().encoderName).writeAndFlush(byteBuf);
        }
    }

    public void sendPacket(ChannelAbstract channel, SendablePacketWrapper wrapper) {
        wrapper.createPacket();
        sendPacket(channel, wrapper.byteBuf);
    }

    public void sendPacket(Player player, ByteBufAbstract byteBuf) {
        ChannelAbstract channel = ChannelAbstract.generate(getChannel(player));
        sendPacket(channel, byteBuf);
    }

    public void sendPacket(Player player, SendablePacketWrapper wrapper) {
        wrapper.createPacket();
        ChannelAbstract channel = ChannelAbstract.generate(getChannel(player));
        sendPacket(channel, wrapper.byteBuf);
    }



/*
    public void writePacket(Player player, SendableWrapper wrapper) {
        try {
            Object nmsPacket = wrapper.asNMSPacket();
            PacketEvents.get().getInjector().writePacket(getChannel(player), nmsPacket);
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void flushPackets(Player player) {
        try {
            PacketEvents.get().getInjector().flushPackets(getChannel(player));
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }


    public void sendPacket(Player player, SendableWrapper wrapper) {
        try {
            Object nmsPacket = wrapper.asNMSPacket();
            PacketEvents.get().getInjector().sendPacket(getChannel(player), nmsPacket);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Deprecated
    public void sendPacket(Object channel, SendableWrapper wrapper) {
        try {
            Object nmsPacket = wrapper.asNMSPacket();
            PacketEvents.get().getInjector().sendPacket(channel, nmsPacket);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }*/

    public void sendNMSPacket(Player player, Object packet) {
        PacketEvents.get().getInjector().sendPacket(getChannel(player), packet);
    }


    public WrappedGameProfile getGameProfile(Player player) {
        Object gameProfile = GameProfileUtil.getGameProfile(player.getUniqueId(), player.getName());
        return GameProfileUtil.getWrappedGameProfile(gameProfile);
    }

    public boolean isGeyserPlayer(Player player) {
        if (!PacketEvents.get().getServerManager().isGeyserAvailable()) {
            return false;
        }
        return GeyserUtils.isGeyserPlayer(player.getUniqueId());
    }

    public boolean isGeyserPlayer(UUID uuid) {
        if (!PacketEvents.get().getServerManager().isGeyserAvailable()) {
            return false;
        }
        return GeyserUtils.isGeyserPlayer(uuid);
    }

    public void changeSkinProperty(Player player, Skin skin) {
        Object gameProfile = MinecraftReflection.getGameProfile(player);
        GameProfileUtil.setGameProfileSkin(gameProfile, skin);
    }

    public Skin getSkin(Player player) {
        Object gameProfile = MinecraftReflection.getGameProfile(player);
        return GameProfileUtil.getGameProfileSkin(gameProfile);
    }

    public Object getChannel(Player player) {
        String name = player.getName();
        Object channel = channels.get(name);
        if (channel == null) {
            channel = MinecraftReflection.getChannel(player);
            if (channel != null) {
                channels.put(name, channel);
            }
        }
        return channel;
    }
}
