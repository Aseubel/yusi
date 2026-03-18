package com.aseubel;

public class BinaryDataTest {
    public static void main(String[] args) throws Exception {
        Class<?> clazz = Class.forName("com.aliyun.sdk.service.oss2.transport.BinaryData");
        System.out.println("Constructors:");
        for (java.lang.reflect.Constructor<?> c : clazz.getConstructors()) {
            System.out.println(c);
        }
        System.out.println("Methods:");
        for (java.lang.reflect.Method m : clazz.getMethods()) {
            if (java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                System.out.println(m);
            }
        }
    }
}
