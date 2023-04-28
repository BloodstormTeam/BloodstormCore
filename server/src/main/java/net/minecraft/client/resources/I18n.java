package net.minecraft.client.resources;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

import java.util.Map;

@SideOnly(Side.CLIENT)
public class I18n
{
    private static Locale i18nLocale;
    private static final String __OBFID = "CL_00001094";

    static void setLocale(Locale p_135051_0_)
    {
        i18nLocale = p_135051_0_;
    }

    public static String format(String p_135052_0_, Object ... p_135052_1_)
    {
        return i18nLocale.formatMessage(p_135052_0_, p_135052_1_);
    }

    public static Map getLocaleProperties()
    {
        return i18nLocale.field_135032_a;
    }
}