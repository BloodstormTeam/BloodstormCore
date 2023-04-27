package cpw.mods.fml.common.network.internal;

import net.minecraft.entity.player.EntityPlayer;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.common.network.internal.FMLMessage.OpenGui;

public class OpenGuiHandler extends SimpleChannelInboundHandler<FMLMessage.OpenGui> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, OpenGui msg) throws Exception
    {
        EntityPlayer player = FMLClientHandler.instance().getClient().thePlayer;
        player.openGui(msg.modId, msg.modGuiId, player.worldObj, msg.x, msg.y, msg.z);
        player.openContainer.windowId = msg.windowId;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception
    {
        super.exceptionCaught(ctx, cause);
    }
}