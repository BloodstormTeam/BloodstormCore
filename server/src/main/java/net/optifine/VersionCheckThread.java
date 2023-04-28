package net.optifine;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import net.minecraft.client.ClientBrandRetriever;

public class VersionCheckThread extends Thread
{
    public void run()
    {
        HttpURLConnection conn;

        try
        {
            URL e = new URL("https://optifine.net/version/1.7.10/HD_U.txt");
            conn = (HttpURLConnection)e.openConnection();

            if (Config.getGameSettings().snooperEnabled)
            {
                conn.setRequestProperty("OF-MC-Version", "1.7.10");
                conn.setRequestProperty("OF-MC-Brand", "" + ClientBrandRetriever.getClientModName());
                conn.setRequestProperty("OF-Edition", "HD_U");
                conn.setRequestProperty("OF-Release", "E7");
                conn.setRequestProperty("OF-Java-Version", "" + System.getProperty("java.version"));
                conn.setRequestProperty("OF-CpuCount", "" + Config.getAvailableProcessors());
                conn.setRequestProperty("OF-OpenGL-Version", "" + Config.openGlVersion);
                conn.setRequestProperty("OF-OpenGL-Vendor", "" + Config.openGlVendor);
            }

            conn.setDoInput(true);
            conn.setDoOutput(false);
            conn.connect();

            try
            {
                InputStream in = conn.getInputStream();
                String verStr = Config.readInputStream(in);
                in.close();
                String[] verLines = Config.tokenize(verStr, "\n\r");

                if (verLines.length >= 1)
                {
                    String newVer = verLines[0].trim();

                    if (Config.compareRelease(newVer, "E7") <= 0)
                    {
                        return;
                    }

                    Config.setNewRelease(newVer);
                }
            }
            finally
            {
                conn.disconnect();
            }
        }
        catch (Exception ignored) {}
    }
}
