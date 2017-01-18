/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
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

package org.jboss.ejb.server;

import javax.transaction.SystemException;
import javax.transaction.Transaction;

/**
 * An EJB session-open request.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface SessionOpenRequest extends Request {
    /**
     * Determine if the request has a transaction.
     *
     * @return {@code true} if there is a transaction context with this request
     */
    boolean hasTransaction();

    /**
     * Get the inflowed transaction of the request.  This should not be called unless it is desired to actually inflow
     * the transaction; doing so without using the transaction will cause needless work for the transaction coordinator.
     * To perform transaction checks, use {@link #hasTransaction()} first.  This method should only be called one time
     * as it will inflow the transaction when called.
     * <p>
     * If a transaction is present but transaction inflow has failed, a {@link SystemException} is thrown.  In this case,
     * the invocation should fail.
     * <p>
     * It is the caller's responsibility to check the status of the returned transaction to ensure that it is in an
     * active state; failure to do so can result in undesirable behavior.
     *
     * @return the transaction, or {@code null} if there is none for the request
     * @throws SystemException if inflowing the transaction failed
     * @throws IllegalStateException if this method is called more than one time
     */
    Transaction getTransaction() throws SystemException, IllegalStateException;

}
