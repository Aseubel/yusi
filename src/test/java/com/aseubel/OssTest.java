package com.aseubel;

public class OssTest {
    public static void main(String[] args) throws Exception {
        Class<?> clazz = Class.forName("com.aliyun.sdk.service.oss2.OSSClient");
        for (java.lang.reflect.Method m : clazz.getMethods()) {
            if (m.getName().toLowerCase().contains("object") || m.getName().toLowerCase().contains("get")) {
                System.out.println("Method: " + m.getName());
                for (Class<?> p : m.getParameterTypes()) {
                    System.out.println("  Param: " + p.getName());
                }
                System.out.println("  Return: " + m.getReturnType().getName());
            }
        }
    }
}
