package net.minecraft.util;

import com.google.common.collect.BiMap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import java.io.IOException;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.NetworkStatistics;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;

public class MessageSerializer extends MessageToByteEncoder
{
    private final NetworkStatistics field_152500_c;
    private static final String __OBFID = "CL_00001253";

    public MessageSerializer(NetworkStatistics p_i1182_1_)
    {
        this.field_152500_c = p_i1182_1_;
    }

    protected void encode(ChannelHandlerContext p_encode_1_, Packet p_encode_2_, ByteBuf p_encode_3_) throws IOException
    {
        Integer integer = (Integer)((BiMap)p_encode_1_.channel().attr(NetworkManager.attrKeySendable).get()).inverse().get(p_encode_2_.getClass());
        if (integer == null)
        {
            throw new IOException("Can\'t serialize unregistered packet");
        }
        else
        {
            PacketBuffer packetbuffer = new PacketBuffer(p_encode_3_);
            packetbuffer.writeVarIntToBuffer(integer.intValue());
            p_encode_2_.writePacketData(packetbuffer);
            this.field_152500_c.func_152464_b(integer.intValue(), (long)packetbuffer.readableBytes());
        }
    }

    protected void encode(ChannelHandlerContext p_encode_1_, Object p_encode_2_, ByteBuf p_encode_3_) throws IOException
    {
        this.encode(p_encode_1_, (Packet)p_encode_2_, p_encode_3_);
    }
}