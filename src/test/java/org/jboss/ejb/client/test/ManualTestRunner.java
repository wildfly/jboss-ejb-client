package org.jboss.ejb.client.test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.jboss.ejb.client.serialization.ProxySerializationTestCase;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

// Manually launches EJB tests (e.g. for Xbootclasspath testing)
public class ManualTestRunner {
    public static void main(String[] args) throws Exception {
        String summary = args.length > 0 ? args[0] : null;
        if (summary == null || summary.length() == 0) {
            summary = "Manual Test Run";
        }
        System.out.println("===========================");
        System.out.printf(" %s \n", summary);
        System.out.println("===========================");

        ClassCallback.setBeforeClassCallback(ManualTestRunner::reloadConfiguration);

        Result result = JUnitCore.runClasses(JBossEJBPropertiesTestCase.class, ClusteredInvocationTestCase.class, SimpleInvocationTestCase.class, ProxySerializationTestCase.class, WildflyClientXMLTestCase.class);

        System.out.println("Failed: " + result.getFailureCount() + " Ignored: " + result.getIgnoreCount() +
                           " Succeeded: " + (result.getRunCount() - result.getFailureCount() - result.getIgnoreCount()));
        for (Failure failure: result.getFailures()) {
            System.out.println(failure.getDescription());
            System.out.println(failure.getTrace());
        }

        System.exit(result.wasSuccessful() ? 0 : 1);
    }

     private static void reloadConfiguration()  {
        try {
            // Force reconfiguration so that one test class doesn't pollute the other 
            // (since we are running them all in one JVM)
            Class<?> clazz = Class.forName("org.jboss.ejb.client.ConfigurationBasedEJBClientContextSelector");
            Method init = clazz.getDeclaredMethod("loadConfiguration");
            init.setAccessible(true);
            Object o = init.invoke(null);
            Field field = clazz.getDeclaredField("configuredContext");
            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);

            field.setAccessible(true);
            field.set(null, o);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
     }
}

