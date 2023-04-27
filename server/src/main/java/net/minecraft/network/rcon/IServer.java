package net.minecraft.network.rcon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.SERVER)
public interface IServer
{
    int getIntProperty(String p_71327_1_, int p_71327_2_);

    String getStringProperty(String p_71330_1_, String p_71330_2_);

    void setProperty(String p_71328_1_, Object p_71328_2_);

    void saveProperties();

    String getSettingsFilename();

    String getHostname();

    int getPort();

    String getMotd();

    String getMinecraftVersion();

    int getCurrentPlayerCount();

    int getMaxPlayers();

    String[] getAllUsernames();

    String getFolderName();

    String getPlugins();

    String handleRConCommand(String p_71252_1_);
}