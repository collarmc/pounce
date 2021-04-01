package team.catgirl.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.ForkJoinPool;

/**
 * Events annotated with this type are executed on {@link ForkJoinPool#commonPool()}
 * rather than the dispatchers thread
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AsyncEvent {
}
