package com.collarmc.pounce;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * A very simple event bus
 */
public final class EventBus implements EventDispatcher {

    private static final Logger LOGGER = Logger.getLogger(EventBus.class.getName());

    private final ConcurrentHashMap<Class<?>, ConcurrentLinkedDeque<ListenerInfo>> listeners = new ConcurrentHashMap<>();
    private final Consumer<Runnable> mainThreadConsumer;

    /**
     * Creates a new EventBus
     * @param mainThreadConsumer to run task on the main thread
     */
    public EventBus(Consumer<Runnable> mainThreadConsumer) {
        this.mainThreadConsumer = mainThreadConsumer;
    }

    /**
     * Registers an object to receiving events using a weakly held reference
     * This is the same as {@link #subscribeWeakly(Object)}
     */
    public void subscribe(Object listener) {
        subscribeWeakly(listener);
    }

    /**
     * Registers an object to receiving events using a weakly held reference
     */
    public void subscribeWeakly(Object listener) {
        doSubscribe(listener, EventBus::createWeakListener);
    }

    /**
     * Registers an object to receiving events using a strongly held reference
     * To release the reference, you must call {@link #unsubscribe(Object)}
     */
    public void subscribeStrongly(Object listener) {
        doSubscribe(listener, EventBus::createStrongListener);
    }

    /**
     * Unregisters a listener from receiving events.
     */
    public void unsubscribe(Object listener) {
        listeners.values().stream()
                .flatMap(Collection::stream)
                .forEach(listenerInfo -> {
                    Object target = listenerInfo.getTarget();
                    if (listener.equals(target)) {
                        listeners.compute(listenerInfo.eventType, (aClass, listenerInfos) -> {
                            if (listenerInfos != null) {
                                listenerInfos.remove(listenerInfo);
                            }
                            return listenerInfos;
                        });
                    }
                });
    }

    @Override
    public void dispatch(Object event, CancelableCallback callback) {
        ConcurrentLinkedDeque<ListenerInfo> listenerInfos = listeners.get(event.getClass());
        if (listenerInfos != null && !listenerInfos.isEmpty()) {
            dispatchAll(event, listenerInfos, callback);
        } else {
            // If it didn't match, then just send it to a dead event listener that listens to object
            listenerInfos = listeners.get(Object.class);
            if (listenerInfos != null) {
                dispatchAll(event, listenerInfos, callback);
            }
        }
        // Remove weak listeners
        ForkJoinPool.commonPool().submit(() -> {
            listeners.forEachEntry(10, entry -> {
                ConcurrentLinkedDeque<ListenerInfo> listeners = entry.getValue();
                listeners.forEach(listenerInfo -> {
                    if (listenerInfo.strongTarget == null && listenerInfo.weakTarget.get() == null) {
                        listeners.remove(listenerInfo);
                    }
                });
            });
        });
    }

    @Override
    public void dispatch(Object event) {
        dispatch(event, e -> {
            LOGGER.warning("Event " + event.getClass().getName() + " was canceled without registering a callback in dispatch");
        });
    }

    private void doSubscribe(Object listener, ListenerCreator creator) {
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
                        listenerInfos = listenerInfos == null ? new ConcurrentLinkedDeque<>() : listenerInfos;
                        if (!method.isAccessible()) {
                            method.setAccessible(true);
                        }
                        Subscribe subscribe = method.getAnnotation(Subscribe.class);
                        EventInfo eventInfo = eventClass.getAnnotation(EventInfo.class);
                        Preference preference = eventInfo != null && eventInfo.preference() != null ? eventInfo.preference() : subscribe.value();
                        ListenerInfo listenerInfo = creator.create(listener, method, eventClass, subscribe, preference);
                        listenerInfos.add(listenerInfo);
                        return listenerInfos.stream().sorted((o1, o2) -> Integer.compare(o2.priority, o1.priority)).collect(Collectors.toCollection(ConcurrentLinkedDeque::new));
                    });
                });
    }

    private void dispatchAll(Object event, ConcurrentLinkedDeque<ListenerInfo> listenerInfos, CancelableCallback callback) {
        for (ListenerInfo listenerInfo : listenerInfos) {
            switch (listenerInfo.preference) {
                case MAIN:
                    mainThreadConsumer.accept(() -> dispatch(event, listenerInfo));
                    break;
                case DISPATCH:
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
                if (CancelableState.isCanceled(cancelable)) {
                    callback.canceled(event);
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
            Object target = listenerInfo.getTarget();
            if (target != null) {
                listenerInfo.method.invoke(target, event);
            }
        } catch (IllegalAccessException | InvocationTargetException e) {
            LOGGER.log(Level.SEVERE, "Problem invoking listener", e);
        }
    }

    private static ListenerInfo createStrongListener(Object listener, Method method, Class<?> eventClass, Subscribe subscribe, Preference preference) {
        return new ListenerInfo(
                null,
                listener,
                method,
                eventClass,
                preference,
                Cancelable.class.isAssignableFrom(eventClass),
                subscribe.priority());
    }

    private static ListenerInfo createWeakListener(Object listener, Method method, Class<?> eventClass, Subscribe subscribe, Preference preference) {
        return new ListenerInfo(
                null,
                listener,
                method,
                eventClass,
                preference,
                Cancelable.class.isAssignableFrom(eventClass),
                subscribe.priority());
    }

    @FunctionalInterface
    private interface ListenerCreator {
        ListenerInfo create(Object listener, Method method, Class<?> eventClass, Subscribe subscribe, Preference preference);
    }

    private static final class ListenerInfo {
        /** Object reference to the listener */
        private final WeakReference<Object> weakTarget;
        private final Object strongTarget;
        /** Listener method to invoke */
        public final Method method;
        /** The event type **/
        public final Class<?> eventType;
        /** Where it will be executed **/
        public final Preference preference;
        /** Cancelable */
        public final boolean isCancelable;
        public final int priority;

        public ListenerInfo(WeakReference<Object> weakTarget,
                            Object strongTarget,
                            Method method,
                            Class<?> eventType,
                            Preference preference,
                            boolean isCancelable,
                            int priority) {
            this.weakTarget = weakTarget;
            this.strongTarget = strongTarget;
            this.method = method;
            this.eventType = eventType;
            this.preference = preference;
            this.isCancelable = isCancelable;
            this.priority = priority;
        }

        public Object getTarget() {
            return weakTarget == null ? strongTarget : weakTarget.get();
        }
    }
}