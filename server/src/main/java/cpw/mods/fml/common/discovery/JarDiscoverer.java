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

import java.util.Collections;
import java.util.List;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.zip.ZipEntry;

import com.google.common.collect.Lists;

import cpw.mods.fml.common.LoaderException;
import cpw.mods.fml.common.MetadataCollection;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.ModContainerFactory;
import cpw.mods.fml.common.discovery.asm.ASMModParser;

public class JarDiscoverer implements ITypeDiscoverer
{
    @Override
    public List<ModContainer> discover(ModCandidate candidate, ASMDataTable table)
    {
        List<ModContainer> foundMods = Lists.newArrayList();
        try (JarFile jar = new JarFile(candidate.getModContainer())) {

            if (jar.getManifest() != null && (jar.getManifest().getMainAttributes().get("FMLCorePlugin") != null || jar.getManifest().getMainAttributes().get("TweakClass") != null)) {
                return foundMods;
            }
            ZipEntry modInfo = jar.getEntry("mcmod.info");
            MetadataCollection mc;
            if (modInfo != null) {
                mc = MetadataCollection.from(jar.getInputStream(modInfo), candidate.getModContainer().getName());
            } else {
                mc = MetadataCollection.from(null, "");
            }
            for (ZipEntry ze : Collections.list(jar.entries())) {
                if (ze.getName() != null && (ze.getName().startsWith("__MACOSX") || ze.getName().startsWith("META-INF/versions"))) {
                    continue;
                }
                Matcher match = classFile.matcher(ze.getName());
                if (match.matches()) {
                    ASMModParser modParser;
                    try {
                        modParser = new ASMModParser(jar.getInputStream(ze));
                        candidate.addClassEntry(ze.getName());
                    } catch (LoaderException e) {
                        jar.close();
                        throw e;
                    }
                    modParser.validate();
                    modParser.sendToTable(table, candidate);
                    ModContainer container = ModContainerFactory.instance().build(modParser, candidate.getModContainer(), candidate);
                    if (container != null) {
                        table.addContainer(container);
                        foundMods.add(container);
                        container.bindMetadata(mc);
                    }
                }
            }
        } catch (Exception ignored) {}
        return foundMods;
    }

}