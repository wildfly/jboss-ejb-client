package org.jboss.ejb.client;

import org.junit.Assert;
import org.junit.Test;
import org.wildfly.naming.client.ProviderEnvironment;
import org.wildfly.naming.client.SimpleName;
import org.wildfly.naming.client.util.FastHashtable;

import javax.naming.InvalidNameException;
import javax.naming.NamingException;

/**
 * A set of tests which validate that invocation.timeout values set in the properties map of a JNDI context
 * get propagated to the proxies created from that JNDI context.
 *
 * @author unknown
 */
public class EJBRootContextTestCase {

    private static final String LOOKUP_NAME = "appName/moduleName/distinctName!org.jboss.ejb.client.test.common.Echo";

    /**
     * Test which validates that an integer-valued invocation.timeout property set in the properties map for
     * a JNDI context gets passed through to a proxy created from that JNDI context.
     *
     * @throws NamingException
     */
    @Test
    public void testInvocationTimeoutEnvPropertyInteger() throws NamingException {
        FastHashtable<String, Object> env = new FastHashtable<>();
        env.put("invocation.timeout", 100);

        Object proxy = invokeContextLookup(env);

        Assert.assertEquals(100, EJBInvocationHandler.forProxy(proxy).getInvocationTimeout());
    }

    /**
     * Test which validates that a string-valued invocation.timeout property set in the properties map for
     * a JNDI context gets passed through to a proxy created from that JNDI context.
     *
     * @throws NamingException
     */
    @Test
    public void testInvocationTimeoutEnvPropertyString() throws NamingException {
        FastHashtable<String, Object> env = new FastHashtable<>();
        env.put("invocation.timeout", "100");

        Object proxy = invokeContextLookup(env);

        Assert.assertEquals(100, EJBInvocationHandler.forProxy(proxy).getInvocationTimeout());
    }

    /**
     * Test which validates that a long-valued invocation.timeout property set in the properties map for
     * a JNDI context gets passed through to a proxy created from that JNDI context.
     *
     * @throws NamingException
     */
    @Test
    public void testInvocationTimeoutEnvPropertyLong() throws NamingException {
        FastHashtable<String, Object> env = new FastHashtable<>();
        env.put("invocation.timeout", 100L);

        Object proxy = invokeContextLookup(env);

        Assert.assertEquals(100, EJBInvocationHandler.forProxy(proxy).getInvocationTimeout());
    }

    /**
     * Test which validates that the default invocation.timeout property for a JNDI context is -1.
     *
     * @throws NamingException
     */
    @Test
    public void testInvocationTimeoutEnvPropertyEmpty() throws NamingException {
        FastHashtable<String, Object> env = new FastHashtable<>();

        Object proxy = invokeContextLookup(env);

        Assert.assertEquals(-1, EJBInvocationHandler.forProxy(proxy).getInvocationTimeout());
    }

    private Object invokeContextLookup(FastHashtable<String, Object> env) throws NamingException {
        EJBRootContext context = new EJBRootContext(null, env, new ProviderEnvironment.Builder().build());
        return context.lookupNative(new SimpleName(LOOKUP_NAME));
    }
}
