package com.collarmc.pounce;

import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class EventBusTest {
    @Test
    public void caller() {
        CallerListener listener = new CallerListener();
        EventBus eventBus = new EventBus(Runnable::run);
        eventBus.subscribe(listener);
        Event e = new Event();
        eventBus.dispatch(e);
        Assert.assertEquals(e, listener.event);
        eventBus.unsubscribe(listener);
    }

    @Test
    public void main() {
        MainListener listener = new MainListener();
        EventBus eventBus = new EventBus(Runnable::run);
        eventBus.subscribe(listener);
        Event e = new Event();
        eventBus.dispatch(e);
        Assert.assertEquals(e, listener.event);
        eventBus.unsubscribe(listener);
    }

    @Test
    public void pool() throws InterruptedException {
        PoolListener listener = new PoolListener();
        EventBus eventBus = new EventBus(Runnable::run);
        eventBus.subscribe(listener);
        Event e = new Event();
        eventBus.dispatch(e);
        Thread.sleep(TimeUnit.SECONDS.toMillis(1));
        Assert.assertEquals(e, listener.event);
        eventBus.unsubscribe(listener);
    }

    @Test
    public void cancelable() {
        CancelableListener listener = new CancelableListener();
        EventBus eventBus = new EventBus(Runnable::run);
        eventBus.subscribe(listener);
        CancelableEvent e = new CancelableEvent();
        eventBus.dispatch(e);
        Assert.assertEquals(10, listener.value);
        eventBus.unsubscribe(listener);
    }

    @Test
    public void deadEvents() {
        EventBus eventBus = new EventBus(Runnable::run);
        DeadListener listener = new DeadListener();
        eventBus.subscribe(listener);
        Event e = new Event();
        eventBus.dispatch(e);
        Assert.assertEquals(e, listener.event);
        eventBus.unsubscribe(listener);
    }

    @Test
    public void subscribeIdempotencyTest() {
        EventBus eventBus = new EventBus(Runnable::run);
        final CountingListener countingListener = new CountingListener();
        eventBus.subscribe(countingListener);
        eventBus.subscribe(countingListener);
        Event e = new Event();
        eventBus.dispatch(e);
        Assert.assertEquals(1, countingListener.value.get());
        eventBus.unsubscribe(countingListener);
    }

    public static class Event {}

    public static class CallerListener {
        Event event;
        @Subscribe(Preference.CALLER)
        public void exec(Event event) {
            this.event = event;
        }
    }

    public static class MainListener {
        Event event;
        @Subscribe(Preference.MAIN)
        public void exec(Event event) {
            this.event = event;
        }
    }

    public static class PoolListener {
        Event event;
        @Subscribe(Preference.POOL)
        public void exec(Event event) {
            this.event = event;
        }
    }

    public static class DeadListener {
        Object event;
        @Subscribe(Preference.CALLER)
        public void exec(Object event) {
            this.event = event;
        }
    }

    public static class CancelableListener {
        int value = 0;

        @Subscribe(value = Preference.CALLER)
        public void call1(CancelableEvent event) {
            value = value + 10;
            event.cancel();
        }

        @Subscribe(value = Preference.CALLER)
        public void call2(CancelableEvent event) {
            value = value + 10;
            event.cancel();
        }
    }

    public static class CountingListener {
        final AtomicInteger value = new AtomicInteger();

        @Subscribe(value = Preference.CALLER)
        public void onEvent(Object event) {
            value.incrementAndGet();
        }
    }

    public static class CancelableEvent implements Cancelable {}
}
