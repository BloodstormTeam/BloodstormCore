package net.minecraft.client.shader;

import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.util.JsonException;

public class ShaderLinkHelper {
    private static ShaderLinkHelper staticShaderLinkHelper;
    private static final String __OBFID = "CL_00001045";

    public static void setNewStaticShaderLinkHelper()
    {
        staticShaderLinkHelper = new ShaderLinkHelper();
    }

    public static ShaderLinkHelper getStaticShaderLinkHelper()
    {
        return staticShaderLinkHelper;
    }

    public void func_148077_a(ShaderManager p_148077_1_)
    {
        p_148077_1_.func_147994_f().func_148054_b(p_148077_1_);
        p_148077_1_.func_147989_e().func_148054_b(p_148077_1_);
        OpenGlHelper.func_153187_e(p_148077_1_.func_147986_h());
    }

    public int func_148078_c() throws JsonException
    {
        int var1 = OpenGlHelper.func_153183_d();

        if (var1 <= 0)
        {
            throw new JsonException("Could not create shader program (returned program ID " + var1 + ")");
        }
        else
        {
            return var1;
        }
    }

    public void func_148075_b(ShaderManager p_148075_1_)
    {
        p_148075_1_.func_147994_f().func_148056_a(p_148075_1_);
        p_148075_1_.func_147989_e().func_148056_a(p_148075_1_);
        OpenGlHelper.func_153179_f(p_148075_1_.func_147986_h());
    }
}
