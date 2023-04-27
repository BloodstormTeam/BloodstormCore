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

package cpw.mods.fml.relauncher;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.ObjectArrays;
import com.google.common.primitives.Ints;

import cpw.mods.fml.common.asm.transformers.ModAccessTransformer;
import cpw.mods.fml.common.launcher.FMLInjectionAndSortingTweaker;
import cpw.mods.fml.common.launcher.FMLTweaker;
import cpw.mods.fml.common.toposort.TopologicalSort;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin.DependsOn;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin.MCVersion;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin.Name;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin.SortingIndex;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin.TransformerExclusions;

public class CoreModManager {
    private static final Attributes.Name COREMODCONTAINSFMLMOD = new Attributes.Name("FMLCorePluginContainsFMLMod");
    private static final Attributes.Name MODTYPE = new Attributes.Name("ModType");
    private static final Attributes.Name MODSIDE = new Attributes.Name("ModSide");
    private static String[] rootPlugins = { "cpw.mods.fml.relauncher.FMLCorePlugin", "net.minecraftforge.classloading.FMLForgePlugin", "pw.prok.imagine.ImagineLoadingPlugin" };
    private static List<String> loadedCoremods = Lists.newArrayList();
    private static List<FMLPluginWrapper> loadPlugins;
    private static boolean deobfuscatedEnvironment;
    private static FMLTweaker tweaker;
    private static File mcDir;
    private static List<String> reparsedCoremods = Lists.newArrayList();
    private static List<String> accessTransformers = Lists.newArrayList();

    private static class FMLPluginWrapper implements ITweaker {
        public final String name;
        public final IFMLLoadingPlugin coreModInstance;
        public final List<String> predepends;
        public final File location;
        public final int sortIndex;

        public FMLPluginWrapper(String name, IFMLLoadingPlugin coreModInstance, File location, int sortIndex, String... predepends)
        {
            super();
            this.name = name;
            this.coreModInstance = coreModInstance;
            this.location = location;
            this.sortIndex = sortIndex;
            this.predepends = Lists.newArrayList(predepends);
        }

        @Override
        public String toString()
        {
            return String.format("%s {%s}", this.name, this.predepends);
        }

        @Override
        public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile)
        {
            // NO OP
        }

        @Override
        public void injectIntoClassLoader(LaunchClassLoader classLoader)
        {
            // Cauldron end
            if (coreModInstance.getASMTransformerClass() != null) for (String transformer : coreModInstance.getASMTransformerClass())
            {
                classLoader.registerTransformer(transformer);
            }
            Map<String, Object> data = new HashMap<String, Object>();
            data.put("mcLocation", mcDir);
            data.put("coremodList", loadPlugins);
            data.put("runtimeDeobfuscationEnabled", !deobfuscatedEnvironment);
            data.put("coremodLocation", location);
            coreModInstance.injectData(data);
            String setupClass = coreModInstance.getSetupClass();
            if (setupClass != null)
            {
                try
                {
                    IFMLCallHook call = (IFMLCallHook) Class.forName(setupClass, true, classLoader).newInstance();
                    Map<String, Object> callData = new HashMap<String, Object>();
                    callData.put("runtimeDeobfuscationEnabled", !deobfuscatedEnvironment);
                    callData.put("mcLocation", mcDir);
                    callData.put("classLoader", classLoader);
                    callData.put("coremodLocation", location);
                    callData.put("deobfuscationFileName", FMLInjectionData.debfuscationDataName());
                    call.injectData(callData);
                    call.call();
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
            }

            String modContainer = coreModInstance.getModContainerClass();
            if (modContainer != null)
            {
                FMLInjectionData.containers.add(modContainer);
            }
        }

        @Override
        public String getLaunchTarget()
        {
            return "";
        }

        @Override
        public String[] getLaunchArguments()
        {
            return new String[0];
        }

    }

    // Cauldron - group output of @MCVersion warnings
    private static final List<String> noVersionAnnotationCoreMods = new ArrayList<String>();

    public static void handleLaunch(File mcDir, LaunchClassLoader classLoader, FMLTweaker tweaker)
    {
        CoreModManager.mcDir = mcDir;
        CoreModManager.tweaker = tweaker;
        try
        {
            // Are we in a 'decompiled' environment?
            byte[] bs = classLoader.getClassBytes("net.minecraft.world.World");
            if (bs != null)
            {
                deobfuscatedEnvironment = true;
            }
        } catch (IOException ignored) {}

        tweaker.injectCascadingTweak("cpw.mods.fml.common.launcher.FMLInjectionAndSortingTweaker");
        try
        {
            classLoader.registerTransformer("cpw.mods.fml.common.asm.transformers.PatchingTransformer");
        }
        catch (Exception e)
        {
            throw Throwables.propagate(e);
        }

        loadPlugins = new ArrayList<FMLPluginWrapper>();
        for (String rootPluginName : rootPlugins)
        {
            loadCoreMod(classLoader, rootPluginName, new File(FMLTweaker.getJarLocation()));
        }

        if (loadPlugins.isEmpty())
        {
            throw new RuntimeException("A fatal error has occured - no valid fml load plugin was found - this is a completely corrupt FML installation.");
        }

        // Now that we have the root plugins loaded - lets see what else might
        // be around
        String commandLineCoremods = System.getProperty("fml.coreMods.load", "");
        for (String coreModClassName : commandLineCoremods.split(","))
        {
            if (coreModClassName.isEmpty())
            {
                continue;
            }
            loadCoreMod(classLoader, coreModClassName, null);
        }
        discoverCoreMods(mcDir, classLoader);
    }

    private static void discoverCoreMods(File mcDir, LaunchClassLoader classLoader)
    {
        ModListHelper.parseModList(mcDir);
        File coreMods = setupCoreModDir(mcDir);
        FilenameFilter ff = (dir, name) -> name.endsWith(".jar");
        FileFilter derpdirfilter = pathname -> pathname.isDirectory() && new File(pathname,"META-INF").isDirectory();
        File[] derpdirlist = coreMods.listFiles(derpdirfilter);

        if (derpdirlist != null && derpdirlist.length > 0)
        {
            RuntimeException re = new RuntimeException("Extracted mod jars found, loading will NOT continue");
            // We're generating a crash report for the launcher to show to the user here
            try
            {
                Class<?> crashreportclass = classLoader.loadClass("b");
                Object crashreport = crashreportclass.getMethod("a", Throwable.class, String.class).invoke(null, re, "FML has discovered extracted jar files in the mods directory.\nThis breaks mod loading functionality completely.\nRemove the directories and replace with the jar files originally provided.");
                File crashreportfile = new File(new File(coreMods.getParentFile(),"crash-reports"),String.format("fml-crash-%1$tY-%1$tm-%1$td_%1$tT.txt",Calendar.getInstance()));
                crashreportclass.getMethod("a",File.class).invoke(crashreport, crashreportfile);
                System.out.println("#@!@# FML has crashed the game deliberately. Crash report saved to: #@!@# " + crashreportfile.getAbsolutePath());
            } catch (Exception e)
            {
                e.printStackTrace();
                // NOOP - hopefully
            }
            throw re;
        }
        File[] coreModList = coreMods.listFiles(ff);
        File versionedModDir = new File(coreMods, FMLInjectionData.mccversion);
        if (versionedModDir.isDirectory())
        {
            File[] versionedCoreMods = versionedModDir.listFiles(ff);
            coreModList = ObjectArrays.concat(coreModList, versionedCoreMods, File.class);
        }

        coreModList = ObjectArrays.concat(coreModList, ModListHelper.additionalMods.values().toArray(new File[0]), File.class);

        coreModList = FileListHelper.sortFileList(coreModList);

        for (File coreMod : coreModList)
        {
            JarFile jar = null;
            Attributes mfAttributes;
            try
            {
                jar = new JarFile(coreMod);
                if (jar.getManifest() == null)
                {
                    // Not a coremod and no access transformer list
                    continue;
                }
                ModAccessTransformer.addJar(jar);
                mfAttributes = jar.getManifest().getMainAttributes();
            }
            catch (IOException ioe)
            {
                continue;
            }
            finally
            {
                if (jar != null)
                {
                    try
                    {
                        jar.close();
                    }
                    catch (IOException e)
                    {
                        // Noise
                    }
                }
            }
            String cascadedTweaker = mfAttributes.getValue("TweakClass");
            if (cascadedTweaker != null)
            {
                if ("fastcraft.Tweaker".equals(cascadedTweaker) && !"false".equals(System.getProperty("thermos.fastcraft.disable", "true"))) {
                    continue;
                }
                Integer sortOrder = Integer.parseInt(Strings.nullToEmpty(mfAttributes.getValue("TweakOrder")));
                handleCascadingTweak(coreMod, jar, cascadedTweaker, classLoader, sortOrder);
                loadedCoremods.add(coreMod.getName());
                continue;
            }
            List<String> modTypes = mfAttributes.containsKey(MODTYPE) ? Arrays.asList(mfAttributes.getValue(MODTYPE).split(",")) : ImmutableList.of("FML");

            if (!modTypes.contains("FML"))
            {
                loadedCoremods.add(coreMod.getName());
                continue;
            }
            String modSide = mfAttributes.containsKey(MODSIDE) ? mfAttributes.getValue(MODSIDE) : "BOTH";
            if (! ("BOTH".equals(modSide) || FMLLaunchHandler.side.name().equals(modSide)))
            {
                loadedCoremods.add(coreMod.getName());
                continue;
            }
            String fmlCorePlugin = mfAttributes.getValue("FMLCorePlugin");
            if (fmlCorePlugin == null)
            {
                // Not a coremod
                continue;
            }
            if ("fastcraft.LoadingPlugin".equals(fmlCorePlugin) && !"false".equals(System.getProperty("thermos.fastcraft.disable", "true"))) {
                continue;
            }
            // Support things that are mod jars, but not FML mod jars
            try
            {
                classLoader.addURL(coreMod.toURI().toURL());
                if (!mfAttributes.containsKey(COREMODCONTAINSFMLMOD))
                {
                    loadedCoremods.add(coreMod.getName());
                }
                else
                {
                    reparsedCoremods.add(coreMod.getName());
                }
            }
            catch (MalformedURLException e)
            {
                continue;
            }
            loadCoreMod(classLoader, fmlCorePlugin, coreMod);
        }
    }

    private static Method ADDURL;

    private static void handleCascadingTweak(File coreMod, JarFile jar, String cascadedTweaker, LaunchClassLoader classLoader, Integer sortingOrder)
    {
        try
        {
            // Have to manually stuff the tweaker into the parent classloader
            if (ADDURL == null)
            {
                ADDURL = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
                ADDURL.setAccessible(true);
            }
            ADDURL.invoke(classLoader.getClass().getClassLoader(), coreMod.toURI().toURL());
            classLoader.addURL(coreMod.toURI().toURL());
            CoreModManager.tweaker.injectCascadingTweak(cascadedTweaker);
            tweakSorting.put(cascadedTweaker,sortingOrder);
        }
        catch (Exception ignored) {}
    }

    /**
     * @param mcDir
     *            the minecraft home directory
     * @return the coremod directory
     */
    private static File setupCoreModDir(File mcDir)
    {
        File coreModDir = new File(mcDir, "mods");
        try
        {
            coreModDir = coreModDir.getCanonicalFile();
        }
        catch (IOException e)
        {
            throw new RuntimeException(String.format("Unable to canonicalize the coremod dir at %s", mcDir.getName()), e);
        }
        if (!coreModDir.exists())
        {
            coreModDir.mkdir();
        }
        else if (coreModDir.exists() && !coreModDir.isDirectory())
        {
            throw new RuntimeException(String.format("Found a coremod file in %s that's not a directory", mcDir.getName()));
        }
        return coreModDir;
    }

    public static List<String> getLoadedCoremods()
    {
        return loadedCoremods;
    }

    public static List<String> getReparseableCoremods()
    {
        return reparsedCoremods;
    }

    private static FMLPluginWrapper loadCoreMod(LaunchClassLoader classLoader, String coreModClass, File location)
    {
        String coreModName = coreModClass.substring(coreModClass.lastIndexOf('.') + 1);
        try
        {
            classLoader.addTransformerExclusion(coreModClass);
            Class<?> coreModClazz = Class.forName(coreModClass, true, classLoader);
            Name coreModNameAnn = coreModClazz.getAnnotation(IFMLLoadingPlugin.Name.class);
            if (coreModNameAnn != null && !Strings.isNullOrEmpty(coreModNameAnn.value()))
            {
                coreModName = coreModNameAnn.value();
            }
            MCVersion requiredMCVersion = coreModClazz.getAnnotation(IFMLLoadingPlugin.MCVersion.class);
            if (!Arrays.asList(rootPlugins).contains(coreModClass) && (requiredMCVersion == null || Strings.isNullOrEmpty(requiredMCVersion.value())))
            {
                // Cauldron start - group output of @MCVersion warnings
                // FMLRelaunchLog.log(Level.WARN, "The coremod %s does not have a MCVersion annotation, it may cause issues with this version of Minecraft",
                //        coreModClass);
                noVersionAnnotationCoreMods.add(coreModClass);
                // Cauldron end
            }
            else if (requiredMCVersion != null && !FMLInjectionData.mccversion.equals(requiredMCVersion.value()))
            {
                return null;
            }
            TransformerExclusions trExclusions = coreModClazz.getAnnotation(IFMLLoadingPlugin.TransformerExclusions.class);
            if (trExclusions != null)
            {
                for (String st : trExclusions.value())
                {
                    classLoader.addTransformerExclusion(st);
                }
            }
            DependsOn deplist = coreModClazz.getAnnotation(IFMLLoadingPlugin.DependsOn.class);
            String[] dependencies = new String[0];
            if (deplist != null)
            {
                dependencies = deplist.value();
            }
            SortingIndex index = coreModClazz.getAnnotation(IFMLLoadingPlugin.SortingIndex.class);
            int sortIndex = index != null ? index.value() : 0;

            IFMLLoadingPlugin plugin = (IFMLLoadingPlugin) coreModClazz.newInstance();
            String accessTransformerClass = plugin.getAccessTransformerClass();
            if (accessTransformerClass != null)
            {
                accessTransformers.add(accessTransformerClass);
            }
            FMLPluginWrapper wrap = new FMLPluginWrapper(coreModName, plugin, location, sortIndex, dependencies);
            loadPlugins.add(wrap);
            return wrap;
        }
        catch (ClassNotFoundException | ClassCastException | InstantiationException | IllegalAccessException ignored) {}
        return null;
    }

    @SuppressWarnings("unused")
    private static void sortCoreMods()
    {
        TopologicalSort.DirectedGraph<FMLPluginWrapper> sortGraph = new TopologicalSort.DirectedGraph<FMLPluginWrapper>();
        Map<String, FMLPluginWrapper> pluginMap = Maps.newHashMap();
        for (FMLPluginWrapper plug : loadPlugins)
        {
            sortGraph.addNode(plug);
            pluginMap.put(plug.name, plug);
        }

        for (FMLPluginWrapper plug : loadPlugins)
        {
            for (String dep : plug.predepends)
            {
                if (!pluginMap.containsKey(dep))
                {
                    throw new RuntimeException();
                }
                sortGraph.addEdge(plug, pluginMap.get(dep));
            }
        }
        try
        {
            loadPlugins = TopologicalSort.topologicalSort(sortGraph);
            }
        catch (Exception e) {
            Throwables.throwIfUnchecked(e);
        }
    }

    public static void injectTransformers(LaunchClassLoader classLoader)
    {

        Launch.blackboard.put("fml.deobfuscatedEnvironment", deobfuscatedEnvironment);
        tweaker.injectCascadingTweak("cpw.mods.fml.common.launcher.FMLDeobfTweaker");
        tweakSorting.put("cpw.mods.fml.common.launcher.FMLDeobfTweaker", Integer.valueOf(1000));
    }

    public static void injectCoreModTweaks(FMLInjectionAndSortingTweaker fmlInjectionAndSortingTweaker)
    {
        @SuppressWarnings("unchecked")
        List<ITweaker> tweakers = (List<ITweaker>) Launch.blackboard.get("Tweaks");
        // Add the sorting tweaker first- it'll appear twice in the list
        tweakers.add(0, fmlInjectionAndSortingTweaker);
        for (FMLPluginWrapper wrapper : loadPlugins)
        {
            tweakers.add(wrapper);
        }
    }

    private static Map<String,Integer> tweakSorting = Maps.newHashMap();

    public static void sortTweakList()
    {
        @SuppressWarnings("unchecked")
        List<ITweaker> tweakers = (List<ITweaker>) Launch.blackboard.get("Tweaks");
        // Basically a copy of Collections.sort pre 8u20, optimized as we know we're an array list.
        // Thanks unhelpful fixer of http://bugs.java.com/view_bug.do?bug_id=8032636
        ITweaker[] toSort = tweakers.toArray(new ITweaker[tweakers.size()]);
        Arrays.sort(toSort, new Comparator<ITweaker>() {
            @Override
            public int compare(ITweaker o1, ITweaker o2)
            {
                Integer first = null;
                Integer second = null;
                if (o1 instanceof FMLInjectionAndSortingTweaker)
                {
                    first = Integer.MIN_VALUE;
                }
                if (o2 instanceof FMLInjectionAndSortingTweaker)
                {
                    second = Integer.MIN_VALUE;
                }

                if (o1 instanceof FMLPluginWrapper)
                {
                    first = ((FMLPluginWrapper) o1).sortIndex;
                }
                else if (first == null)
                {
                    first = tweakSorting.get(o1.getClass().getName());
                }
                if (o2 instanceof FMLPluginWrapper)
                {
                    second = ((FMLPluginWrapper) o2).sortIndex;
                }
                else if (second == null)
                {
                    second = tweakSorting.get(o2.getClass().getName());
                }
                if (first == null)
                {
                    first = 0;
                }
                if (second == null)
                {
                    second = 0;
                }

                return Ints.saturatedCast((long)first - (long)second);
            }
        });
        // Basically a copy of Collections.sort, optimized as we know we're an array list.
        // Thanks unhelpful fixer of http://bugs.java.com/view_bug.do?bug_id=8032636
        for (int j = 0; j < toSort.length; j++) {
            tweakers.set(j, toSort[j]);
        }
    }

    public static List<String> getAccessTransformers()
    {
        return accessTransformers;
    }
}