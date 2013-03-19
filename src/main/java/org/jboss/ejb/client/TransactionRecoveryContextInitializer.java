/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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

/**
 * A {@link EJBClientContextInitializer} which sets up the {@link EJBClientContext} with the relevant information so that the transaction
 * recovery process for EJB XAResource(s) can be functional
 *
 * @author Jaikiran Pai
 */
public class TransactionRecoveryContextInitializer implements EJBClientContextInitializer {
    @Override
    public void initialize(EJBClientContext context) {
        // we register a EJB client context listener which keeps track of the registered EJB receivers, so that those
        // receivers can be used during transaction recovery process for checking any recoverable Xid(s)
        context.registerEJBClientContextListener(RecoveryOnlySerializedEJBXAResource.ReceiverRegistrationListener.INSTANCE);
    }
}
