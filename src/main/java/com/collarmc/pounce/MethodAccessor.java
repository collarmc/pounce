package com.collarmc.pounce;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;

import static org.objectweb.asm.Opcodes.*;

public abstract class MethodAccessor {
    private static int ID = 0;
    public static MethodAccessor generate(final Object handler, final Method handlerMethod, final Class<?> eventClass) {
        // inspired from Forge's ASMEventHandler
        // It would be nice to use a direct compiler here from generated source code
        // ...however some users may be running on a JRE which does not have a compiler
        // this limits us to generating bytecode directly.
        // There may be some library that could handle this in a more elegant way, will keep a lookout
        try {
            final String className = "MethodAccessor$" + handler.getClass().getSimpleName() + "I" + handlerMethod.getName() + "ID" + ID++;
            final String fullClassName = "pounce.generated." + className;
            final String desc = fullClassName.replace(".", "/");
            final ClassWriter classWriter = new ClassWriter(0);

            classWriter.visit(V1_8, ACC_PUBLIC | ACC_SUPER, desc, null, "com/collarmc/pounce/MethodAccessor", null);
            classWriter.visitSource(".dynamic", null);
            classWriter.visitField(ACC_PUBLIC, "handlerInstance", "Ljava/lang/Object;", null, null).visitEnd();
            MethodVisitor methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "(Ljava/lang/Object;)V", null, null);
            methodVisitor.visitCode();
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(INVOKESPECIAL, Type.getType(MethodAccessor.class).getInternalName(), "<init>", "()V", false);
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitFieldInsn(PUTFIELD, desc, "handlerInstance", "Ljava/lang/Object;");
            methodVisitor.visitInsn(RETURN);
            methodVisitor.visitMaxs(2, 2);
            methodVisitor.visitEnd();
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "executeEvent", Type.getMethodDescriptor(MethodAccessor.class.getDeclaredMethod("executeEvent", Object.class)), null, null);
            methodVisitor.visitCode();
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitFieldInsn(GETFIELD, desc, "handlerInstance", "Ljava/lang/Object;");
            methodVisitor.visitTypeInsn(CHECKCAST, handler.getClass().getName().replace('.', '/'));
            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitTypeInsn(CHECKCAST, eventClass.getName().replace('.', '/'));
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(handlerMethod.getDeclaringClass()), handlerMethod.getName(), Type.getMethodDescriptor(handlerMethod), false);
            methodVisitor.visitInsn(RETURN);
            methodVisitor.visitMaxs(2, 2);
            methodVisitor.visitEnd();
            classWriter.visitEnd();
            final ASMClassLoader[] classLoader = new ASMClassLoader[1];
            AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
                classLoader[0] = new ASMClassLoader();
                return null;
            });
            final Class<?> accessorClass =  classLoader[0].define(fullClassName, classWriter.toByteArray());
            return (MethodAccessor) accessorClass.getConstructor(Object.class).newInstance(handler);
        } catch (final Error | NoSuchMethodException | InstantiationException | IllegalAccessException |
                       InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public abstract void executeEvent(final Object event);

    private static class ASMClassLoader extends ClassLoader
    {
        private ASMClassLoader()
        {
            super(ASMClassLoader.class.getClassLoader());
        }

        public Class<?> define(String name, byte[] data)
        {
            return defineClass(name, data, 0, data.length);
        }
    }
}
