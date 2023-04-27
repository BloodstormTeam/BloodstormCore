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

package cpw.mods.fml.common;

import java.io.File;
import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.regex.Pattern;
import org.objectweb.asm.Type;

import com.google.common.base.Throwables;
import com.google.common.collect.Maps;

import cpw.mods.fml.common.discovery.ModCandidate;
import cpw.mods.fml.common.discovery.asm.ASMModParser;
import cpw.mods.fml.common.discovery.asm.ModAnnotation;

public class ModContainerFactory
{
    public static Map<Type, Constructor<? extends ModContainer>> modTypes = Maps.newHashMap();
    private static Pattern modClass = Pattern.compile(".*(\\.|)(mod\\_[^\\s$]+)$");
    private static ModContainerFactory INSTANCE = new ModContainerFactory();
    
    private ModContainerFactory() {
        // We always know about Mod type
        registerContainerType(Type.getType(Mod.class), FMLModContainer.class);
    }
    public static ModContainerFactory instance() {
        return INSTANCE;
    }
    
    public void registerContainerType(Type type, Class<? extends ModContainer> container)
    {
        try {
            Constructor<? extends ModContainer> constructor = container.getConstructor(new Class<?>[] { String.class, ModCandidate.class, Map.class });
            modTypes.put(type, constructor);
        } catch (Exception e) {
            Throwables.throwIfUnchecked(e);
        }
    }
    public ModContainer build(ASMModParser modParser, File modSource, ModCandidate container)
    {
        String className = modParser.getASMType().getClassName();
        if (modClass.matcher(className).find())
        {
            container.rememberModCandidateType(modParser);
        }
        else if (modParser.isBaseMod(container.getRememberedBaseMods()))
        {
            container.rememberBaseModType(className);
        }

        for (ModAnnotation ann : modParser.getAnnotations())
        {
            if (modTypes.containsKey(ann.getASMType()))
            {
                try {
                    return modTypes.get(ann.getASMType()).newInstance(className, container, ann.getValues());
                } catch (Exception e) {
                    return null;
                }
            }
        }

        return null;
    }
}