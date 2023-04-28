package net.optifine;

import java.lang.reflect.Field;
import java.util.Map;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

public class RenderPlayerOF extends RenderPlayer
{
    protected void renderEquippedItems(EntityLivingBase entityLiving, float partialTicks)
    {
        super.renderEquippedItems(entityLiving, partialTicks);
        this.renderEquippedItems(entityLiving, 0.0625F, partialTicks);
    }

    private void renderEquippedItems(EntityLivingBase entityLiving, float scale, float partialTicks)
    {
        if (Config.isShowCapes())
        {
            if (entityLiving instanceof AbstractClientPlayer)
            {
                AbstractClientPlayer player = (AbstractClientPlayer)entityLiving;
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                GL11.glDisable(GL12.GL_RESCALE_NORMAL);
                GlStateManager.enableCull();
                ModelBiped modelBipedMain = (ModelBiped)this.mainModel;
                PlayerConfigurations.renderPlayerItems(modelBipedMain, player, scale, partialTicks);
                GlStateManager.disableCull();
            }
        }
    }

    public static void register()
    {
        RenderManager rm = RenderManager.instance;
        Map mapRenderTypes = getMapRenderTypes(rm);

        if (mapRenderTypes != null)
        {
            RenderPlayerOF rpof = new RenderPlayerOF();
            rpof.setRenderManager(rm);
            mapRenderTypes.put(EntityPlayer.class, rpof);
        }
    }

    private static Map getMapRenderTypes(RenderManager rm)
    {
        Map<Class<? extends Entity>, Render> map = RenderManager.instance.getEntityRenderMap();
        if (map != null)
        {
            RenderPlayer renderSteve = (RenderPlayer) map.get(EntityPlayer.class);
            if (renderSteve != null)
            {
                return map;
            }
        }
        return null;
    }
}
