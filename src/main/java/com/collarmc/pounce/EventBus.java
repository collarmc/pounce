package com.collarmc.pounce;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * A very simple event bus
 */
public final class EventBus {

    private static final Logger LOGGER = Logger.getLogger(EventBus.class.getName());

    private final ConcurrentHashMap<Class<?>, List<ListenerInfo>> listeners = new ConcurrentHashMap<>();
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
    public void subscribe(Object listener) {
        List<Class<?>> classes = new LinkedList<>();
        Class<?> currentClass = listener.getClass();
        while (currentClass != null) {
            classes.add(currentClass);
            currentClass = currentClass.getSuperclass();
        }
        classes.stream().flatMap(aClass -> Arrays.stream(aClass.getDeclaredMethods()))
                .filter(method -> method.getParameterCount() == 1 && method.isAnnotationPresent(Subscribe.class))
                .forEach(method -> {
                    Parameter parameter = method.getParameters()[0];
                    listeners.compute(parameter.getType(), (eventClass, listenerInfos) -> {
                        listenerInfos = listenerInfos == null ? new LinkedList<>() : listenerInfos;
                        if (!method.isAccessible()) {
                            method.setAccessible(true);
                        }
                        Subscribe subscribe = method.getAnnotation(Subscribe.class);
                        EventInfo eventInfo = eventClass.getAnnotation(EventInfo.class);
                        Preference preference = eventInfo != null && eventInfo.preference() != null ? eventInfo.preference() : subscribe.value();
                        ListenerInfo listenerInfo = new ListenerInfo(
                                listener,
                                method,
                                eventClass,
                                preference,
                                Cancelable.class.isAssignableFrom(eventClass),
                                subscribe.priority());
                        listenerInfos.add(listenerInfo);
                        return listenerInfos.stream().sorted((o1, o2) -> Integer.compare(o2.priority, o1.priority)).collect(Collectors.toList());
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
                List<ListenerInfo> listeners = entry.getValue();
                listeners.forEach(listenerInfo -> {
                    if (listenerInfo.target.get() == null) {
                        listeners.remove(listenerInfo);
                    }
                });
            });
        });
    }

    private void dispatchAll(Object event, List<ListenerInfo> listenerInfos) {
        for (ListenerInfo listenerInfo : listenerInfos) {
            switch (listenerInfo.preference) {
                case MAIN:
                    mainThreadConsumer.accept(() -> dispatch(event, listenerInfo));
                    break;
                case CALLER:
                    dispatch(event, listenerInfo);
                    break;
                case POOL:
                    if (listenerInfo.isCancelable) {
                        // Cancelable events cannot be run in the pool as they are inherently non-async
                        dispatch(event, listenerInfo);
                    } else {
                        ForkJoinPool.commonPool().submit(() -> dispatch(event, listenerInfo));
                    }
                    break;
            }
            if (listenerInfo.isCancelable) {
                Cancelable cancelable = (Cancelable) event;
                if (cancelable.isCanceled()) {
                    break;
                }
            }
        }
        if (event instanceof Cancelable) {
            CancelableState.clear((Cancelable) event);
        }
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
        /** Cancelable */
        public final boolean isCancelable;
        public final int priority;

        public ListenerInfo(Object target,
                            Method method,
                            Class<?> eventType,
                            Preference preference,
                            boolean isCancelable,
                            int priority) {
            this.target = new WeakReference<>(target);
            this.method = method;
            this.eventType = eventType;
            this.preference = preference;
            this.isCancelable = isCancelable;
            this.priority = priority;
        }
    }
}