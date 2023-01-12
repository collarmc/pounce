package com.collarmc.pounce;

import org.joor.Reflect;

import java.lang.reflect.Method;

public abstract class MethodAccessor {
    private static int ID = 0;
    public static MethodAccessor generate(final Object handler, final Method handlerMethod, final Class<?> eventClass) {
        final String className = "MethodAccessor$" + handler.getClass().getSimpleName() + "I" + handlerMethod.getName() + "ID" + ID++;
        final String fullClassName = "pounce.generated." + className;
        final String source = "package pounce.generated;\n\n"
                + "public final class " + className + " extends " + MethodAccessor.class.getName() + " {\n"
                + "    public final Object handlerInstance;\n"
                + "    public " + className + "(final Object h) {\n"
                + "        this.handlerInstance = h;\n"
                + "    }\n"
                + "    public void executeEvent(final Object event) {\n"
                + "        ((" + handler.getClass().getName().replace("$", ".") + ") this.handlerInstance)." + handlerMethod.getName() + "((" + eventClass.getName().replace("$", ".") +") event);\n"
                + "    }\n"
                + "}";
        return Reflect.compile(fullClassName, source).create(handler).get();
    }

    public abstract void executeEvent(final Object event);

}
