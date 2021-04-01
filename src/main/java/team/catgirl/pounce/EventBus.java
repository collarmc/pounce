package team.catgirl.pounce;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A very simple event bus
 */
public final class EventBus {

    private static final Logger LOGGER = Logger.getLogger(EventBus.class.getName());

    private final ConcurrentHashMap<Class<?>, CopyOnWriteArrayList<ListenerInfo>> listeners = new ConcurrentHashMap<>();
    private final Consumer<Runnable> mainThreadConsumer;

    /**
     * Creates a new EventBus
     * @param mainThreadConsumer to run task on the main thread
     */
    public EventBus(Consumer<Runnable> mainThreadConsumer) {
        this.mainThreadConsumer = mainThreadConsumer;
    }

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
                        && method.isAnnotationPresent(Subscribe.class))
                .forEach(method -> {
                    Parameter parameter = method.getParameters()[0];
                    listeners.compute(parameter.getType(), (eventClass, listenerInfos) -> {
                        listenerInfos = listenerInfos == null ? new CopyOnWriteArrayList<>() : listenerInfos;
                        Subscribe annotation = method.getAnnotation(Subscribe.class);
                        listenerInfos.add(new ListenerInfo(listener, method, eventClass, annotation.value()));
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
    public void dispatch(Object event) {
        List<ListenerInfo> listenerInfos = listeners.get(event.getClass());
        if (listenerInfos != null && !listenerInfos.isEmpty()) {
            dispatchAll(event, listenerInfos);
        } else {
            // If it didn't match, then just send it to a dead event listener that listens to object
            listenerInfos = listeners.get(Object.class);
            if (listenerInfos != null) {
                dispatchAll(event, listenerInfos);
            }
        }
        // Remove weak listeners
        ForkJoinPool.commonPool().submit(() -> {
            listeners.forEachEntry(10, entry -> {
                CopyOnWriteArrayList<ListenerInfo> listeners = entry.getValue();
                listeners.forEach(listenerInfo -> {
                    if (listenerInfo.target.get() == null) {
                        listeners.remove(listenerInfo);
                    }
                });
            });
        });
    }

    private void dispatchAll(Object event, List<ListenerInfo> listenerInfos) {
        listenerInfos.forEach(listenerInfo -> {
            switch (listenerInfo.preference) {
                case MAIN:
                    mainThreadConsumer.accept(() -> dispatch(event, listenerInfo));
                    break;
                case CALLER:
                    dispatch(event, listenerInfo);
                    break;
                case POOL:
                    ForkJoinPool.commonPool().submit(() -> dispatch(event, listenerInfo));
                    break;
            }
        });
    }

    private void dispatch(Object event, ListenerInfo listenerInfo) {
        try {
            Object target = listenerInfo.target.get();
            if (target != null) {
                listenerInfo.method.invoke(target, event);
            }
        } catch (IllegalAccessException | InvocationTargetException e) {
            LOGGER.log(Level.SEVERE, "Problem invoking listener", e);
        }
    }

    private static final class ListenerInfo {
        /** Object reference to the listener */
        public final WeakReference<Object> target;
        /** Listener method to invoke */
        public final Method method;
        /** The event type **/
        public final Class<?> eventType;
        /** Where it will be executed **/
        public final Preference preference;

        public ListenerInfo(Object target,
                            Method method,
                            Class<?> eventType,
                            Preference preference) {
            this.target = new WeakReference<>(target);
            this.method = method;
            this.eventType = eventType;
            this.preference = preference;
        }
    }
}