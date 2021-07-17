package com.collarmc.pounce;

import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

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
    public void privateListener() {
        PrivateListener listener = new PrivateListener();
        EventBus eventBus = new EventBus(Runnable::run);
        eventBus.subscribe(listener);
        Event e = new Event();
        eventBus.dispatch(e);
        Assert.assertEquals(e, listener.event);
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

    public static class PrivateListener {
        Object event;
        @Subscribe(Preference.CALLER)
        private void exec(Object event) {
            this.event = event;
        }
    }
}