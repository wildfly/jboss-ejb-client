package org.jboss.ejb.client.test.interceptor;

import org.jboss.ejb.client.EJBClientContext;
import org.jboss.ejb.client.EJBClientInterceptor;
import org.jboss.ejb.client.EJBClientInvocationContext;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests registration of {@link EJBClientInterceptor}(s) in a {@link EJBClientContext}
 *
 * @author Jaikiran Pai
 */
public class ClientInterceptorRegistrationTestCase {

    /**
     * Tests that multiple interceptor instances cannot be registered with the same priority
     *
     * @throws Exception
     */
    @Test
    public void testDuplicatePriorityRegistration() throws Exception {
        final int priority = 0x99999;
        final EJBClientContext clientContext = EJBClientContext.create();
        final PassThroughInterceptor firstInstanceOfInterceptor = new PassThroughInterceptor();
        clientContext.registerInterceptor(priority, firstInstanceOfInterceptor);
        // try to register again at the same priority, a new instance. This should fail
        try {
            clientContext.registerInterceptor(priority, new PassThroughInterceptor());
            Assert.fail("Registering different instance of client interceptor at the same priority (" + priority + ") was expected to fail, but it didn't");
        } catch (IllegalArgumentException iae) {
            // expected
        }
        // now try to register the same instance of the interceptor again at the same priority. This should pass
        clientContext.registerInterceptor(priority, firstInstanceOfInterceptor);
    }

    private class PassThroughInterceptor implements EJBClientInterceptor {

        @Override
        public void handleInvocation(final EJBClientInvocationContext context) throws Exception {
            context.sendRequest();
        }

        @Override
        public Object handleInvocationResult(final EJBClientInvocationContext context) throws Exception {
            return context.getResult();
        }
    }
}
