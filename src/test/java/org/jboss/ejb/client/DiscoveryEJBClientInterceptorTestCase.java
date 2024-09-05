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

import java.net.URI;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for the DiscoveryEJBClientInterceptor
 *
 * @author <a href="mailto:lgao@redhat.com">Lin Gao</a>
 */
public class DiscoveryEJBClientInterceptorTestCase {

    /**
     * A test for the "blocklist" feature of the DiscoveryEJBClientInterceptor which allows invocation targets
     * to be added to a blocklist. Presence of a URL on the blocklist eans that they should currently be excluded
     * from discovery request results. The blocklist has an associated timeout; blocklisted entries are cleared
     * once their time in the blocklist has passed the timeout to avoid being blocklisted forever.
     *
     * THis test validates the addition of a URL to the blicklist, as well as its expiration from the blocklist.
     *
     * @throws Exception
     */
    @Test
    public void testBlocklist() throws Exception {
        long timeout = 1000L;
        System.setProperty(SystemProperties.DISCOVERY_BLOCKLIST_TIMEOUT, timeout + "");
        AbstractInvocationContext context = new AbstractInvocationContext(null, null, null) {
            @Override
            public void requestRetry() {
            }
        };
        URI destination = new URI("http-remoting://localhost:9443");
        DiscoveryEJBClientInterceptor.addBlocklistedDestination(destination);
        Assert.assertTrue(DiscoveryEJBClientInterceptor.isBlocklisted(context, destination));
        Assert.assertEquals(1, DiscoveryEJBClientInterceptor.getBlocklist().size());

        // If sleeping for just the timeout duration, the lifespan of the blocklist may equal to, but not greater than,
        // the configured timeout, and so will not be removed yet. So sleep a bit longer.
        Thread.sleep(timeout * 2);
        Assert.assertFalse(DiscoveryEJBClientInterceptor.isBlocklisted(context, destination));
        Assert.assertEquals(0, DiscoveryEJBClientInterceptor.getBlocklist().size());
    }

}
