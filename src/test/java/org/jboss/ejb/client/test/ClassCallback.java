package org.jboss.ejb.client.test;

/**
 * A helper class to allow calling an arbitrary Runnable
 */
public class ClassCallback {
    private static volatile Runnable beforeClassCallback;

    public static void beforeClassCallback() {
        if (beforeClassCallback != null) {
            beforeClassCallback.run();
        }
    }

    public static void setBeforeClassCallback(Runnable beforeClassCallback) {
        ClassCallback.beforeClassCallback = beforeClassCallback;
    }
}
