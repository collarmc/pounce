package team.catgirl.pounce;

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

    public static class Event {}

    public static class CallerListener {
        Event event;
        @Subscribe(Preference.CALLER)
        public void caller(Event event) {
            this.event = event;
        }
    }

    public static class MainListener {
        Event event;
        @Subscribe(Preference.MAIN)
        public void main(Event event) {
            this.event = event;
        }
    }

    public static class PoolListener {
        Event event;
        @Subscribe(Preference.POOL)
        public void pool(Event event) {
            this.event = event;
        }
    }
}
