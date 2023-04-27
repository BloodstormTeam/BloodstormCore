/*
 * Forge Mod Loader
 * Copyright (c) 2012-2013 cpw.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 *
 * Contributors:
 *     cpw - implementation
 */

package cpw.mods.fml.common.asm;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.security.CodeSource;
import java.security.cert.Certificate;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import net.minecraft.launchwrapper.LaunchClassLoader;

import com.google.common.base.Charsets;
import com.google.common.io.ByteStreams;

import cpw.mods.fml.common.CertificateHelper;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper;
import cpw.mods.fml.common.patcher.ClassPatchManager;
import cpw.mods.fml.relauncher.FMLLaunchHandler;
import cpw.mods.fml.relauncher.IFMLCallHook;
import cpw.mods.fml.relauncher.Side;

public class FMLSanityChecker implements IFMLCallHook
{
    private static final String FMLFINGERPRINT =   "51:0A:FB:4C:AF:A4:A0:F2:F5:CF:C5:0E:B4:CC:3C:30:24:4A:E3:8E".toLowerCase().replace(":", "");
    private static final String FORGEFINGERPRINT = "E3:C3:D5:0C:7C:98:6D:F7:4C:64:5C:0A:C5:46:39:74:1C:90:A5:57".toLowerCase().replace(":", "");
    private static final String MCFINGERPRINT =    "CD:99:95:96:56:F7:53:DC:28:D8:63:B4:67:69:F7:F8:FB:AE:FC:FC".toLowerCase().replace(":", "");
    private LaunchClassLoader cl;
    private boolean liveEnv;
    public static File fmlLocation;

    @Override
    public Void call() throws Exception
    {
        CodeSource codeSource = getClass().getProtectionDomain().getCodeSource();
        boolean fmlIsJar = false;
        // Server is not signed, so assume it's good - a deobf env is dev time so it's good too
        boolean goodMC = FMLLaunchHandler.side() == Side.SERVER || !liveEnv;
        int certCount = 0;
        try
        {
            Class<?> cbr = Class.forName("net.minecraft.client.ClientBrandRetriever",false, cl);
            codeSource = cbr.getProtectionDomain().getCodeSource();
        }
        catch (Exception e)
        {
            // Probably a development environment, or the server (the server is not signed)
            goodMC = true;
        }
        JarFile mcJarFile = null;
        if (fmlIsJar && !goodMC && codeSource.getLocation().getProtocol().equals("jar"))
        {
            try
            {
                String mcPath = codeSource.getLocation().getPath().substring(5);
                mcPath = mcPath.substring(0, mcPath.lastIndexOf('!'));
                mcPath = URLDecoder.decode(mcPath, Charsets.UTF_8.name());
                mcJarFile = new JarFile(mcPath,true);
                mcJarFile.getManifest();
                JarEntry cbrEntry = mcJarFile.getJarEntry("net/minecraft/client/ClientBrandRetriever.class");
                ByteStreams.toByteArray(mcJarFile.getInputStream(cbrEntry));
                Certificate[] certificates = cbrEntry.getCertificates();
                if (certificates!=null)
                {

                    for (Certificate cert : certificates)
                    {
                        String fingerprint = CertificateHelper.getFingerprint(cert);
                        assert fingerprint != null;
                        if (fingerprint.equals(MCFINGERPRINT))
                        {
                            goodMC = true;
                        }
                    }
                }
            }
            catch (Throwable ignored) {}
            finally {
                if (mcJarFile != null) {
                    try {
                        mcJarFile.close();
                    } catch (IOException ignored) {}
                }
            }
        }
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data)
    {
        liveEnv = (Boolean)data.get("runtimeDeobfuscationEnabled");
        cl = (LaunchClassLoader) data.get("classLoader");
        File mcDir = (File)data.get("mcLocation");
        fmlLocation = (File)data.get("coremodLocation");
        ClassPatchManager.INSTANCE.setup(FMLLaunchHandler.side());
        FMLDeobfuscatingRemapper.INSTANCE.setup(mcDir, cl, (String) data.get("deobfuscationFileName"));
    }

}