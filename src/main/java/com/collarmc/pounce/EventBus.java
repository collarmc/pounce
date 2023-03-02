package com.collarmc.pounce;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    private final ExecutorService executorService;

    /**
     * Creates a new EventBus
     * @param mainThreadConsumer to run task on the main thread
     */
    public EventBus(Consumer<Runnable> mainThreadConsumer) {
        this.mainThreadConsumer = mainThreadConsumer;
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Registers an object to receiving events
     */
    public void subscribe(Object listener) {
        doSubscribe(listener, EventBus::createGeneratedListener);
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
    }

    @Override
    public void dispatch(Object event) {
        dispatch(event, e -> {
            LOGGER.warning("Event " + event.getClass().getName() + " was canceled without registering a callback in dispatch");
        });
    }

    private void doSubscribe(Object listener, ListenerCreator creator) {
        Set<Class<?>> classes = new HashSet<>();
        Class<?> currentClass = listener.getClass();
        while (currentClass != null) {
            classes.add(currentClass);
            currentClass = currentClass.getSuperclass();
        }
        classes.stream().flatMap(aClass -> Arrays.stream(aClass.getMethods()))
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
                        if (listenerInfos.stream().noneMatch(li -> Objects.equals(li.target, listenerInfo.target))) {
                            // enforce idempotency (if we're already subscribed don't subscribe again)
                            listenerInfos.add(listenerInfo);
                        }
                        return listenerInfos.stream().sorted((o1, o2) -> Integer.compare(o2.priority, o1.priority)).collect(Collectors.toCollection(ConcurrentLinkedDeque::new));
                    });
                });
    }

    private static ListenerInfo createGeneratedListener(Object listenerInstance, Method listenerMethod, Class<?> eventType, Subscribe subscribe, Preference preference) {
        return new ListenerInfo(
                MethodAccessor.generate(listenerInstance, listenerMethod, eventType),
                listenerInstance,
                listenerMethod,
                eventType,
                preference,
                Cancelable.class.isAssignableFrom(eventType),
                subscribe.priority());
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
                    if (listenerInfo.isCancellable) {
                        // Cancelable events cannot be run in the pool as they are inherently non-async
                        dispatch(event, listenerInfo);
                    } else {
                        executorService.submit(() -> dispatch(event, listenerInfo));
                    }
                    break;
            }
            if (listenerInfo.isCancellable) {
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
            listenerInfo.methodAccessor.executeEvent(event);
        } catch (LinkageError e) {
            LOGGER.log(Level.SEVERE, "Problem invoking listener", e);
        }
    }

    @FunctionalInterface
    private interface ListenerCreator {
        ListenerInfo create(Object listener, Method method, Class<?> eventClass, Subscribe subscribe, Preference preference);
    }

    private static final class ListenerInfo {
        /** Listener accessor **/
        public final MethodAccessor methodAccessor;
        /** Listener reference **/
        public final Object target;
        /** Listener method to invoke */
        public final Method method;
        /** The event type **/
        public final Class<?> eventType;
        /** Where it will be executed **/
        public final Preference preference;
        /** Cancelable */
        public final boolean isCancellable;
        public final int priority;

        public ListenerInfo(MethodAccessor methodAccessor,
                            Object target,
                            Method method,
                            Class<?> eventType,
                            Preference preference,
                            boolean isCancellable,
                            int priority) {
            this.methodAccessor = methodAccessor;
            this.target = target;
            this.method = method;
            this.eventType = eventType;
            this.preference = preference;
            this.isCancellable = isCancellable;
            this.priority = priority;
        }

        public Object getTarget() {
            return target;
        }
    }
}
