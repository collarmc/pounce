# pounce

A small event system with preferential dispatch 


## Example
```
class MyEvent {}

class CallerListener {
    @Subscribe(Preference.CALLER)
    public void exec(MyEvent event) {
        System.out.println("event recieved")
    }
}

EventBus eventBus = new EventBus(Runnable::run);

CallerListener listener = new CallerListener();
eventBus.subscribe(listener);

eventBus.publish(new MyEvent());

```
