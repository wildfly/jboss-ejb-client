/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
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
