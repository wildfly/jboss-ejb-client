/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.ejb.client;

import java.net.URI;

/**
 * A provider for supporting discovery of EJBs which have an affinity type that requires discovery.  Discovery providers
 * will be used for {@link Affinity#NONE} as well as {@link NodeAffinity} and {@link ClusterAffinity}, and possibly
 * also for URI scheme-specific types.
 * <p>
 * Any caching (positive or negative) is the responsibility of the discovery provider.  Similar or equivalent locators
 * may be looked up in succession at any time.  Only the discovery provider has the information necessary to make
 * caching decisions.
 * <p>
 * Discovery providers are generally obligated to return answers as aggressively as possible to minimize possible latency
 * effects.  Answers may be returned synchronously or asynchronously, but any lookup operations that entail a potentially
 * long wait <em>should</em> be run asynchronously so as not to delay other discovery providers.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface EJBDiscoveryProvider {

    /**
     * Locate an EJB identified by the given locator.  The EJBs may be discovered asynchronously; in this case, this method
     * should return directly and resume work in the background.  The given {@link DiscoveryResult} must be used to
     * indicate any located matches, and it must also be used to indicate when the discovery operation is complete (even
     * if no matches are found).  Failure to invoke {@link DiscoveryResult#complete()} will cause invocation to stall
     * indefinitely.  The locator will have an affinity of {@link Affinity#NONE}.
     *
     * @param locator the EJB locator (not {@code null})
     * @param result the discovery result sink (not {@code null})
     */
    void discover(EJBLocator<?> locator, DiscoveryResult result);

    /**
     * The EJB discovery result.
     */
    interface DiscoveryResult {

        /**
         * Indicate that discovery is complete.
         */
        void complete();

        /**
         * Indicate that a matching URI was discovered.
         *
         * @param uri the discovered URI
         */
        void addMatch(URI uri);
    }
}
