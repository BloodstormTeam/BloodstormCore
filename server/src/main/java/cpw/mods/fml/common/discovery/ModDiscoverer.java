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

package cpw.mods.fml.common.discovery;

import java.io.File;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.ObjectArrays;

import cpw.mods.fml.common.LoaderException;
import cpw.mods.fml.common.ModClassLoader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.relauncher.CoreModManager;
import cpw.mods.fml.relauncher.FileListHelper;

public class ModDiscoverer
{
    private static Pattern zipJar = Pattern.compile("(.+).(zip|jar)$");

    private List<ModCandidate> candidates = Lists.newArrayList();

    private ASMDataTable dataTable = new ASMDataTable();

    private List<File> nonModLibs = Lists.newArrayList();

    public void findClasspathMods(ModClassLoader modClassLoader)
    {
        List<String> knownLibraries = ImmutableList.<String>builder()
                // skip default libs
                .addAll(modClassLoader.getDefaultLibraries())
                // skip loaded coremods
                .addAll(CoreModManager.getLoadedCoremods())
                // skip reparse coremods here
                .addAll(CoreModManager.getReparseableCoremods())
                .build();
        File[] minecraftSources = modClassLoader.getParentSources();
        if (minecraftSources.length == 1 && minecraftSources[0].isFile()) {
            candidates.add(new ModCandidate(minecraftSources[0], minecraftSources[0], ContainerType.JAR, true, true));
        } else {
            for (int i = 0; i < minecraftSources.length; i++) {
                if (minecraftSources[i].isFile()) {
                    if (!knownLibraries.contains(minecraftSources[i].getName())) {
                        candidates.add(new ModCandidate(minecraftSources[i], minecraftSources[i], ContainerType.JAR, i==0, true));
                    }
                } else if (minecraftSources[i].isDirectory()) {
                    candidates.add(new ModCandidate(minecraftSources[i], minecraftSources[i], ContainerType.DIR, i==0, true));
                }
            }
        }

    }

    public void findModDirMods(File modsDir)
    {
        findModDirMods(modsDir, new File[0]);
    }

    public void findModDirMods(File modsDir, File[] supplementalModFileCandidates)
    {
        File[] modList = FileListHelper.sortFileList(modsDir, null);
        modList = FileListHelper.sortFileList(ObjectArrays.concat(modList, supplementalModFileCandidates, File.class));
        for (File modFile : modList)
        {
            // skip loaded coremods
            if (!CoreModManager.getLoadedCoremods().contains(modFile.getName()))
            {
                if (modFile.isDirectory()) {
                    candidates.add(new ModCandidate(modFile, modFile, ContainerType.DIR));
                }
                Matcher matcher = zipJar.matcher(modFile.getName());

                if (matcher.matches())
                {
                    candidates.add(new ModCandidate(modFile, modFile, ContainerType.JAR));
                }
            }
        }
    }

    public List<ModContainer> identifyMods()
    {
        List<ModContainer> modList = Lists.newArrayList();

        for (ModCandidate candidate : candidates)
        {
            try
            {
                List<ModContainer> mods = candidate.explore(dataTable);
                if (mods.isEmpty() && !candidate.isClasspath())
                {
                    nonModLibs.add(candidate.getModContainer());
                }
                else
                {
                    modList.addAll(mods);
                }
            }
            catch (LoaderException ignored) {}
            catch (Throwable t) {
                Throwables.throwIfUnchecked(t);
            }
        }

        if (!"false".equals(System.getProperty("thermos.fastcraft.disable", "true"))) {
            modList.removeIf(container -> "FastCraft".equals(container.getModId()));
        }

        return modList;
    }

    public ASMDataTable getASMTable()
    {
        return dataTable;
    }

    public List<File> getNonModLibs()
    {
        return nonModLibs;
    }
}