package org.jboss.ejb.client;

/**
 * @author Stuart Douglas
 */
public class ClientContextSerialization {

    private static final ThreadLocal<Boolean> ACTIVE = new ThreadLocal<Boolean>();
    private static final ThreadLocal<String> CURRENT_CONTEXT = new ThreadLocal<String>();

    public static void activateCurrentContext(String value) {
        ACTIVE.set(true);
        CURRENT_CONTEXT.set(value);
    }

    public static void clearCurrentContext() {
        ACTIVE.remove();
        CURRENT_CONTEXT.remove();
    }

    public static boolean overrideActive() {
        Boolean active = ACTIVE.get();
        return active != null && active;
    }

    public static String currentContextName() {
        return CURRENT_CONTEXT.get();
    }

}
