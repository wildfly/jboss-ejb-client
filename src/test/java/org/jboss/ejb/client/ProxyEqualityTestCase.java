/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.ejb.client;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests which validate the test for proxy equality works for proxies created by the EJB Client API.
 *
 * Proxy equality is based on module name, use of an EJBINvocationHandler and viewType.
 *
 * @author Stuart Douglas
 */
public class ProxyEqualityTestCase {

    /**
     * A test for proxy equality, inequality and use of the correct invocation handler.
     */
    @Test
    public void testClientProxyEquality() {
        // create a proxy with appName = a, moduleName = m, beanName = b and distinctName = d
        final StatelessEJBLocator<SimpleInterface> locatorA = new StatelessEJBLocator<SimpleInterface>(SimpleInterface.class, "a", "m", "b", "d");
        SimpleInterface proxyA = EJBClient.createProxy(locatorA);

        final StatelessEJBLocator<SimpleInterface> locatorB = new StatelessEJBLocator<SimpleInterface>(SimpleInterface.class, "a", "m", "b", "d");
        SimpleInterface proxyB = EJBClient.createProxy(locatorB);

        final StatelessEJBLocator<SimpleInterface> locatorC = new StatelessEJBLocator<SimpleInterface>(SimpleInterface.class, "a", "m", "b", "other");
        SimpleInterface proxyC = EJBClient.createProxy(locatorC);

        // validate proxy equality
        Assert.assertTrue(proxyA.equals(proxyB));
        Assert.assertEquals(proxyA.hashCode(), proxyB.hashCode());

        // valite proxy inequality
        Assert.assertFalse(proxyA.equals(proxyC));
        Assert.assertTrue(proxyA.hashCode() != proxyC.hashCode());

        // validate the proxy was created from EJB Client API
        Assert.assertTrue(EJBClient.isEJBProxy(proxyA));
        Assert.assertTrue(EJBClient.isEJBProxy(proxyB));
        Assert.assertTrue(EJBClient.isEJBProxy(proxyC));

        InvocationHandler invocationHandler1 = (proxy, method, args) -> method.invoke(proxy, args);
        final Object proxy1 = Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Runnable.class}, invocationHandler1);
        Assert.assertFalse(EJBClient.isEJBProxy(proxy1));
    }
}
