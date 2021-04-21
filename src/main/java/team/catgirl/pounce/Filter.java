package team.catgirl.pounce;

public abstract class Filter<T> {
    public abstract boolean match(T event);
}
