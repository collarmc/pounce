package team.catgirl.event;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A very simple event bus
 */
public final class EventBus {

    private static final Logger LOGGER = Logger.getLogger(EventBus.class.getName());

    private final ConcurrentHashMap<Class<? extends Event>, List<ListenerInfo>> listeners = new ConcurrentHashMap<>();

    /**
     * Creates a new EventBus
     */
    public EventBus() {}

    /**
     * Registers a class to receiving events.
     */
    @SuppressWarnings("unchecked")
    public void subscribe(Object listener) {
        List<Class<?>> classes = new LinkedList<>();
        Class<?> currentClass = listener.getClass();
        while (currentClass != null) {
            classes.add(currentClass);
            currentClass = currentClass.getSuperclass();
        }
        classes.stream().flatMap(aClass -> Arrays.stream(aClass.getDeclaredMethods()))
                .filter(method -> method.getParameterCount() == 1
                        && method.isAnnotationPresent(Subscribe.class)
                        && Event.class.isAssignableFrom(method.getParameters()[0].getType()))
                .forEach(method -> {
                    Parameter parameter = method.getParameters()[0];
                    listeners.compute((Class<? extends Event>)parameter.getType(), (eventClass, listenerInfos) -> {
                        listenerInfos = listenerInfos == null ? new LinkedList<>() : listenerInfos;
                        listenerInfos.add(new ListenerInfo(listener, method, eventClass));
                        return listenerInfos;
                    });
                });
    }

    /**
     * Unregisters a listener from receiving events.
     */
    public void unsubscribe(Object listener) {
        listeners.values().stream().flatMap(Collection::stream)
                .filter(listenerInfo -> listenerInfo.target.equals(listener))
                .forEach(listenerInfo -> {
                    listeners.compute(listenerInfo.eventType, (aClass, listenerInfos) -> {
                        if (listenerInfos != null) {
                            listenerInfos.remove(listenerInfo);
                        }
                        return listenerInfos;
                    });
                });
    }

    /**
     * Dispatches an event
     * @param event to dispatch
     */
    public void dispatch(Event event) {
        List<ListenerInfo> listenerInfos = listeners.get(event.getClass());
        if (listenerInfos != null) {
            listenerInfos.forEach(listenerInfo -> {
                if (listenerInfo.async) {
                    ForkJoinPool.commonPool().submit(() -> dispatch(event, listenerInfo));
                } else {
                    dispatch(event, listenerInfo);
                }
            });
        }
    }

    private void dispatch(Event event, ListenerInfo listenerInfo) {
        try {
            listenerInfo.method.invoke(listenerInfo.target, event);
        } catch (IllegalAccessException | InvocationTargetException e) {
            LOGGER.log(Level.SEVERE, "Problem invoking listener", e);
        }
    }

    private static final class ListenerInfo {
        /** Object reference to the listener */
        public final Object target;
        /** Listener method to invoke */
        public final Method method;
        /** The event type **/
        public final Class<? extends Event> eventType;
        /** execute async or on dispatchers thread */
        public final boolean async;

        public ListenerInfo(Object target, Method method, Class<? extends Event> eventType) {
            this.target = target;
            this.method = method;
            this.eventType = eventType;
            this.async = eventType.isAnnotationPresent(AsyncEvent.class);
        }
    }
}