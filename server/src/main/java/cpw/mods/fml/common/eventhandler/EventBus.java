package cpw.mods.fml.common.eventhandler;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

import co.aikar.timings.Timing;
import co.aikar.timings.Timings;
import io.github.crucible.CrucibleTimings;
import net.minecraft.server.MinecraftServer;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.MapMaker;
import com.google.common.reflect.TypeToken;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;

public class EventBus implements IEventExceptionHandler
{
    private static int maxID = 0;

    private ConcurrentHashMap<Object, ArrayList<IEventListener>> listeners = new ConcurrentHashMap<Object, ArrayList<IEventListener>>();
    private Map<Object,ModContainer> listenerOwners = new MapMaker().weakKeys().weakValues().makeMap();
    private final int busID = maxID++;
    private IEventExceptionHandler exceptionHandler;

    public EventBus()
    {
        ListenerList.resize(busID + 1);
        exceptionHandler = this;
    }

    public EventBus(@Nonnull IEventExceptionHandler handler)
    {
        this();
        Preconditions.checkArgument(handler != null, "EventBus exception handler can not be null");
        exceptionHandler = handler;
    }

    public void register(Object target)
    {
        if (listeners.containsKey(target))
        {
            return;
        }

        ModContainer activeModContainer = Loader.instance().activeModContainer();
        if (activeModContainer == null) {
            activeModContainer = Loader.instance().getMinecraftModContainer();
        }
        listenerOwners.put(target, activeModContainer);
        Set<? extends Class<?>> supers = TypeToken.of(target.getClass()).getTypes().rawTypes();
        for (Method method : target.getClass().getMethods())
        {
            for (Class<?> cls : supers)
            {
                try
                {
                    Method real = cls.getDeclaredMethod(method.getName(), method.getParameterTypes());
                    if (real.isAnnotationPresent(SubscribeEvent.class))
                    {
                        Class<?>[] parameterTypes = method.getParameterTypes();
                        if (parameterTypes.length != 1)
                        {
                            throw new IllegalArgumentException(
                                "Method " + method + " has @SubscribeEvent annotation, but requires " + parameterTypes.length +
                                " arguments.  Event handler methods must require a single argument."
                            );
                        }

                        Class<?> eventType = parameterTypes[0];

                        if (!Event.class.isAssignableFrom(eventType))
                        {
                            throw new IllegalArgumentException("Method " + method + " has @SubscribeEvent annotation, but takes a argument that is not an Event " + eventType);
                        }

                        register(eventType, target, method, activeModContainer);
                        break;
                    }
                }
                catch (NoSuchMethodException e)
                {
                    ;
                }
            }
        }
    }

    private void register(Class<?> eventType, Object target, Method method, ModContainer owner)
    {
        try
        {
            Constructor<?> ctr = eventType.getConstructor();
            ctr.setAccessible(true);
            Event event = (Event)ctr.newInstance();
            ASMEventHandler listener = new ASMEventHandler(target, method, owner);
            event.getListenerList().register(busID, listener.getPriority(), listener);

            ArrayList<IEventListener> others = listeners.get(target);
            if (others == null)
            {
                others = new ArrayList<IEventListener>();
                listeners.put(target, others);
            }
            others.add(listener);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public void unregister(Object object)
    {
        ArrayList<IEventListener> list = listeners.remove(object);
        if (list == null)
            return;
        for (IEventListener listener : list)
        {
            ListenerList.unregisterAll(busID, listener);
        }
    }

    public boolean post(Event event)
    {
        if (MinecraftServer.serverStarted && Timings.isTimingsEnabled()) { //Only use timings after the startup and if Timings is Enabled.
            Timing eventTiming = CrucibleTimings.getEventTiming(event);
            eventTiming.startTiming();
            IEventListener[] listeners = event.getListenerList().getListeners(busID);
            int index = 0;
            try
            {
                for (; index < listeners.length; index++)
                {
                    Timing listenerTimings = CrucibleTimings.getListenerTiming(listeners[index],eventTiming); //Crucible
                    listenerTimings.startTiming();
                    listeners[index].invoke(event);
                    listenerTimings.stopTiming();
                }
            }
            catch (Throwable throwable)
            {
                eventTiming.stopTiming();
                exceptionHandler.handleException(this, event, listeners, index, throwable);
                Throwables.propagate(throwable);
            }
            eventTiming.stopTiming();
        }
        else { //Original code.
            IEventListener[] listeners = event.getListenerList().getListeners(busID);
            int index = 0;
            try
            {
                for (; index < listeners.length; index++)
                {
                    listeners[index].invoke(event);
                }
            }
            catch (Throwable throwable)
            {
                exceptionHandler.handleException(this, event, listeners, index, throwable);
                Throwables.throwIfUnchecked(throwable);
            }
        }
        return (event.isCancelable() && event.isCanceled());
    }

    @Override
    public void handleException(EventBus bus, Event event, IEventListener[] listeners, int index, Throwable throwable) {}
}