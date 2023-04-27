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

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.BiMap;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import cpw.mods.fml.common.LoaderState.ModState;
import cpw.mods.fml.common.ProgressManager.ProgressBar;
import cpw.mods.fml.common.event.FMLEvent;
import cpw.mods.fml.common.event.FMLLoadEvent;
import cpw.mods.fml.common.event.FMLModDisabledEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLStateEvent;
import cpw.mods.fml.common.functions.ArtifactVersionNameFunction;
import cpw.mods.fml.common.versioning.ArtifactVersion;

public class LoadController
{
    private Loader loader;
    private EventBus masterChannel;
    private ImmutableMap<String,EventBus> eventChannels;
    private LoaderState state;
    private Multimap<String, ModState> modStates = ArrayListMultimap.create();
    private Multimap<String, Throwable> errors = ArrayListMultimap.create();
    private List<ModContainer> activeModList = Lists.newArrayList();
    private ModContainer activeContainer;
    private BiMap<ModContainer, Object> modObjectList;
    private ListMultimap<String, ModContainer> packageOwners;

    public LoadController(Loader loader)
    {
        this.loader = loader;
        this.masterChannel = new EventBus("FMLMainChannel");
        this.masterChannel.register(this);

        state = LoaderState.NOINIT;
        packageOwners = ArrayListMultimap.create();

    }

    void disableMod(ModContainer mod)
    {
        HashMap<String, EventBus> temporary = Maps.newHashMap(eventChannels);
        String modId = mod.getModId();
        EventBus bus = temporary.remove(modId);
        bus.post(new FMLModDisabledEvent());
        if (errors.get(modId).isEmpty())
        {
            eventChannels = ImmutableMap.copyOf(temporary);
            modStates.put(modId, ModState.DISABLED);
            modObjectList.remove(mod);
            activeModList.remove(mod);
        }
    }
    @Subscribe
    public void buildModList(FMLLoadEvent event)
    {
        Builder<String, EventBus> eventBus = ImmutableMap.builder();

        for (ModContainer mod : loader.getModList())
        {
            //Create mod logger, and make the EventBus logger a child of it.
            EventBus bus = new EventBus(mod.getModId());
            boolean isActive = mod.registerBus(bus, this);
            if (isActive) {
                activeModList.add(mod);
                modStates.put(mod.getModId(), ModState.UNLOADED);
                eventBus.put(mod.getModId(), bus);
                FMLCommonHandler.instance().addModToResourcePack(mod);
            } else {
                modStates.put(mod.getModId(), ModState.UNLOADED);
                modStates.put(mod.getModId(), ModState.DISABLED);
            }
        }

        eventChannels = eventBus.build();
    }

    public void distributeStateMessage(LoaderState state, Object... eventData)
    {
        if (state.hasEvent())
        {
            masterChannel.post(state.getEvent(eventData));
        }
    }

    public void transition(LoaderState desiredState, boolean forceState)
    {
        state = state.transition(!errors.isEmpty());
        if (state != desiredState && !forceState)
        {
            Throwable toThrow = null;
            StringBuilder sb = new StringBuilder();
            printModStates(sb);
            if (errors.size()>0)
            {
                for (Entry<String, Throwable> error : errors.entries()) {
                    if (error.getValue() instanceof IFMLHandledException) {
                        toThrow = error.getValue();
                    } else if (toThrow == null) {
                        toThrow = error.getValue();
                    }
                }
            }
            else
            {
                throw new RuntimeException("The ForgeModLoader state engine is invalid");
            }
            if (toThrow instanceof RuntimeException)
            {
                throw (RuntimeException)toThrow;
            }
            else
            {
                throw new LoaderException(toThrow);
            }
        }
        else if (state != desiredState)
        {
            forceState(desiredState);
        }
    }

    public ModContainer activeContainer()
    {
        return activeContainer != null ? activeContainer : findActiveContainerFromStack();
    }

    @Subscribe
    public void propogateStateMessage(FMLEvent stateEvent)
    {
        if (stateEvent instanceof FMLPreInitializationEvent)
        {
            modObjectList = buildModObjectList();
        }
        ProgressBar bar = ProgressManager.push(stateEvent.description(), activeModList.size(), true);
        for (ModContainer mc : activeModList)
        {
            bar.step(mc.getName());
            sendEventToModContainer(stateEvent, mc);
        }
        ProgressManager.pop(bar);
    }

    private void sendEventToModContainer(FMLEvent stateEvent, ModContainer mc)
    {
        String modId = mc.getModId();
        Collection<String> requirements =  Collections2.transform(mc.getRequirements(),new ArtifactVersionNameFunction());
        for (ArtifactVersion av : mc.getDependencies())
        {
            if (av.getLabel()!= null && requirements.contains(av.getLabel()) && modStates.containsEntry(av.getLabel(),ModState.ERRORED))
            {
                modStates.put(modId, ModState.ERRORED);
                return;
            }
        }
        activeContainer = mc;
        stateEvent.applyModContainer(activeContainer());
        eventChannels.get(modId).post(stateEvent);
        activeContainer = null;
        if (stateEvent instanceof FMLStateEvent)
        {
            if (!errors.containsKey(modId))
            {
                modStates.put(modId, ((FMLStateEvent)stateEvent).getModState());
            }
            else
            {
                modStates.put(modId, ModState.ERRORED);
            }
        }
    }

    public ImmutableBiMap<ModContainer, Object> buildModObjectList()
    {
        ImmutableBiMap.Builder<ModContainer, Object> builder = ImmutableBiMap.<ModContainer, Object>builder();
        for (ModContainer mc : activeModList)
        {
            if (!mc.isImmutable() && mc.getMod()!=null)
            {
                builder.put(mc, mc.getMod());
                List<String> packages = mc.getOwnedPackages();
                for (String pkg : packages)
                {
                    packageOwners.put(pkg, mc);
                }
            }
            if (mc.getMod()==null && !mc.isImmutable() && state!=LoaderState.CONSTRUCTING)
            {
                if (state != LoaderState.CONSTRUCTING)
                {
                    this.errorOccurred(mc, new RuntimeException());
                }
            }
        }
        return builder.build();
    }

    public void errorOccurred(ModContainer modContainer, Throwable exception)
    {
        if (exception instanceof InvocationTargetException)
        {
            errors.put(modContainer.getModId(), ((InvocationTargetException)exception).getCause());
        }
        else
        {
            errors.put(modContainer.getModId(), exception);
        }
    }

    public void printModStates(StringBuilder ret)
    {
        ret.append("\n\tStates:");
        for (ModState state : ModState.values())
            ret.append(" '").append(state.getMarker()).append("' = ").append(state.toString());

        for (ModContainer mc : loader.getModList())
        {
            ret.append("\n\t");
            for (ModState state : modStates.get(mc.getModId()))
                ret.append(state.getMarker());

            ret.append("\t").append(mc.getModId()).append("{").append(mc.getVersion()).append("} [").append(mc.getName()).append("] (").append(mc.getSource().getName()).append(") ");
        }
    }

    public List<ModContainer> getActiveModList()
    {
        return activeModList;
    }

    public ModState getModState(ModContainer selectedMod)
    {
        return Iterables.getLast(modStates.get(selectedMod.getModId()), ModState.AVAILABLE);
    }

    public void distributeStateMessage(Class<?> customEvent)
    {
        try
        {
            masterChannel.post(customEvent.newInstance());
        }
        catch (Exception e)
        {
            throw new LoaderException(e);
        }
    }

    public BiMap<ModContainer, Object> getModObjectList()
    {
        if (modObjectList == null)
        {
            return buildModObjectList();
        }
        return ImmutableBiMap.copyOf(modObjectList);
    }

    public boolean isInState(LoaderState state)
    {
        return this.state == state;
    }

    boolean hasReachedState(LoaderState state) {
        return this.state.ordinal()>=state.ordinal() && this.state!=LoaderState.ERRORED;
    }

    void forceState(LoaderState newState)
    {
        this.state = newState;
    }

    private ModContainer findActiveContainerFromStack()
    {
        for (Class<?> c : getCallingStack())
        {
            int idx = c.getName().lastIndexOf('.');
            if (idx == -1)
            {
                continue;
            }
            String pkg = c.getName().substring(0,idx);
            if (packageOwners.containsKey(pkg))
            {
                return packageOwners.get(pkg).get(0);
            }
        }

        return null;
    }
    private FMLSecurityManager accessibleManager = new FMLSecurityManager();

    class FMLSecurityManager extends SecurityManager
    {
        Class<?>[] getStackClasses()
        {
            return getClassContext();
        }
    }

    Class<?>[] getCallingStack()
    {
        return accessibleManager.getStackClasses();
    }

    LoaderState getState()
    {
        return state;
    }
}