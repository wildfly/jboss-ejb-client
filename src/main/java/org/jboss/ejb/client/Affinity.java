/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

import java.io.Serializable;

/**
 * The affinity specification for an EJB proxy.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class Affinity implements Serializable {

    private static final long serialVersionUID = -2985180758368879373L;

    /**
     * The specification for no particular affinity.
     */
    public static final Affinity NONE = new NoAffinity();

    /**
     * Key which will be used in the invocation context data for passing around the weak affinity
     * associated with a EJB
     */
    public static final String WEAK_AFFINITY_CONTEXT_KEY = "jboss.ejb.weak.affinity";

    Affinity() {
    }

    abstract EJBReceiverContext requireReceiverContext(EJBClientContext clientContext);

    static class NoAffinity extends Affinity {

        private static final long serialVersionUID = -2052559528672779420L;

        EJBReceiverContext requireReceiverContext(final EJBClientContext clientContext) {
            return null;
        }

        public int hashCode() {
            return 17;
        }

        public boolean equals(final Object obj) {
            return obj == this;
        }

        protected Object readResolve() {
            return NONE;
        }
    }
}
