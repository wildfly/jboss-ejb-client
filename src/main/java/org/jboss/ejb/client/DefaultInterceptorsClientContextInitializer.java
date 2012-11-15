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

/**
 * A {@link EJBClientContextInitializer} which sets up the default interceptors for the {@link EJBClientContext client context}
 *
 * @author Jaikiran Pai
 */
public class DefaultInterceptorsClientContextInitializer implements EJBClientContextInitializer {

    private static final int RECEIVER_INTERCEPTOR = 0x10000;
    private static final int TRANSACTION_INTERCEPTOR = 0x20000;
    private static final int EJB_HOME_CREATE_METHOD_INVOCATION_INTERCEPTOR = 0x30000;

    @Override
    public void initialize(EJBClientContext context) {
        // setup the interceptors
        context.registerInterceptor(TRANSACTION_INTERCEPTOR, new TransactionInterceptor());
        context.registerInterceptor(RECEIVER_INTERCEPTOR, new ReceiverInterceptor());
        context.registerInterceptor(EJB_HOME_CREATE_METHOD_INVOCATION_INTERCEPTOR, new EJBHomeCreateInterceptor());
    }
}
