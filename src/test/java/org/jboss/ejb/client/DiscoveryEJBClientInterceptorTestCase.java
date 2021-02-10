/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2020 Red Hat, Inc., and individual contributors
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

import org.junit.Assert;
import org.junit.Test;

import java.net.URI;

/**
 * Tests on DiscoveryEJBClientInterceptor
 *
 * @author <a href="mailto:lgao@redhat.com">Lin Gao</a>
 */
public class DiscoveryEJBClientInterceptorTestCase {

    @Test
    public void testBlackList() throws Exception {
        long timeout = 1000L;
        System.setProperty("org.jboss.ejb.client.discovery.blacklist.timeout", timeout + "");
        AbstractInvocationContext context = new AbstractInvocationContext(null, null) {
            @Override
            public void requestRetry() {
            }
        };
        URI destination = new URI("http-remoting://localhost:9443");
        DiscoveryEJBClientInterceptor.addBlackListedDestination(destination);
        Assert.assertTrue(DiscoveryEJBClientInterceptor.isBlackListed(context, destination));
        Assert.assertEquals(1, DiscoveryEJBClientInterceptor.getBlacklist().size());
        Thread.sleep(timeout);
        Assert.assertFalse(DiscoveryEJBClientInterceptor.isBlackListed(context, destination));
        Assert.assertEquals(0, DiscoveryEJBClientInterceptor.getBlacklist().size());
    }

}
