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

package org.jboss.ejb.client.annotation;

import java.util.EnumSet;

import javax.ejb.TransactionAttributeType;

/**
 * The transaction policy for the client side of an EJB interface or method.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public enum ClientTransactionPolicy {
    /**
     * Fail with exception when there is no client-side transaction context; propagate the client-side transaction context
     * when it is present.  The target is never invoked without a client-side transaction context.
     * <p>
     * This mode is compatible with the following server-side transaction attribute types:
     * <ul>
     *     <li>{@link TransactionAttributeType#MANDATORY}</li>
     *     <li>{@link TransactionAttributeType#REQUIRED}</li>
     *     <li>{@link TransactionAttributeType#SUPPORTS}</li>
     * </ul>
     * This mode is recommended when the server side method uses {@link TransactionAttributeType#MANDATORY}.
     * <p>
     * This mode may be used with {@link TransactionAttributeType#REQUIRES_NEW} and {@link TransactionAttributeType#NOT_SUPPORTED},
     * but such usage may not make sense in most situations because the transaction that is required to be propagated by
     * the client will always be suspended on the server; in this case, a client-side policy of {@link TransactionAttributeType#NOT_SUPPORTED}
     * is recommended to avoid the useless propagation of the client-side transaction context.
     * <p>
     * Usage of this mode with the following server-side transaction attribute types will always result in an exception,
     * and is therefore not recommended:
     * <ul>
     *     <li>{@link TransactionAttributeType#NEVER}</li>
     * </ul>
     */
    MANDATORY(false, true, true),
    /**
     * Invoke without a transaction if there is no client-side transaction context; propagate the client-side transaction context if it is present.
     * The target may be invoked with or without a client-side transaction context.
     * <p>
     * This is the overall default policy.
     * <p>
     * This mode is compatible with the following server-side transaction attribute types:
     * <ul>
     *     <li>{@link TransactionAttributeType#REQUIRED}</li>
     *     <li>{@link TransactionAttributeType#SUPPORTS}</li>
     *     <li>{@link TransactionAttributeType#NOT_SUPPORTED}</li>
     * </ul>
     * This mode can also be used with {@link TransactionAttributeType#NEVER} and {@link TransactionAttributeType#MANDATORY},
     * however the success of the invocation of such methods will depend on the local transaction state.
     * <p>
     * Unlike {@link TransactionAttributeType#SUPPORTS}, this client-side transaction policy is generally safe to use in
     * all cases, as it still allows the server to decide to create a new transaction according to local policy.
     * <p>
     * This mode may be used with {@link TransactionAttributeType#REQUIRES_NEW}, but such usage may not make sense in
     * most situations because the transaction that is propagated by the client (if any) will always be suspended on the
     * server; in this case, a client-side policy of {@link #NOT_SUPPORTED} is recommended to avoid the useless propagation of
     * the client-side transaction context.
     */
    SUPPORTS(false, false, true),
    /**
     * Invoke without propagating any transaction context whether or not a client-side transaction context is present.
     * The target is always invoked without a client-side transaction context.
     * <p>
     * This mode is compatible with the following server-side transaction attribute types:
     * <ul>
     *     <li>{@link TransactionAttributeType#REQUIRED}</li>
     *     <li>{@link TransactionAttributeType#REQUIRES_NEW}</li>
     *     <li>{@link TransactionAttributeType#SUPPORTS}</li>
     *     <li>{@link TransactionAttributeType#NOT_SUPPORTED}</li>
     * </ul>
     * This mode can also be used with {@link TransactionAttributeType#NEVER}, however, in this case the server will
     * never see the client-side transaction, causing the invocation to effectively use the server-side mode
     * {@link TransactionAttributeType#NOT_SUPPORTED} in this case, which might result in unexpected behavior.
     * <p>
     * Usage of this mode with the following server-side transaction attribute types will always result in an exception,
     * and is therefore not recommended:
     * <ul>
     *     <li>{@link TransactionAttributeType#MANDATORY}</li>
     * </ul>
     */
    NOT_SUPPORTED(false, false, false),
    /**
     * Invoke without propagating any transaction context; if a client-side transaction context is present, an exception
     * is thrown.
     * <p>
     * This mode is compatible with the following server-side transaction attribute types:
     * <ul>
     *     <li>{@link TransactionAttributeType#REQUIRED}</li>
     *     <li>{@link TransactionAttributeType#REQUIRES_NEW}</li>
     *     <li>{@link TransactionAttributeType#SUPPORTS}</li>
     *     <li>{@link TransactionAttributeType#NOT_SUPPORTED}</li>
     * </ul>
     * This mode can also be used with {@link TransactionAttributeType#NEVER}, however, in this case the server will
     * never see the client-side transaction, causing the invocation to effectively use the server-side mode
     * {@link TransactionAttributeType#NOT_SUPPORTED} in this case, which might result in unexpected behavior.
     * <p>
     * Usage of this mode with the following server-side transaction attribute types will always result in an exception,
     * and is therefore not recommended:
     * <ul>
     *     <li>{@link TransactionAttributeType#MANDATORY}</li>
     * </ul>
     */
    NEVER(true, false, false),
    ;

    private final boolean failIfTransactionPresent;
    private final boolean failIfTransactionAbsent;
    private final boolean propagate;

    ClientTransactionPolicy(final boolean failIfTransactionPresent, final boolean failIfTransactionAbsent, final boolean propagate) {
        this.failIfTransactionPresent = failIfTransactionPresent;
        this.failIfTransactionAbsent = failIfTransactionAbsent;
        this.propagate = propagate;
    }

    /**
     * Determine whether this transaction policy causes a failure when a client-side transaction context is present.
     *
     * @return {@code true} if the policy would fail when a transaction is present, {@code false} otherwise
     */
    public boolean failIfTransactionPresent() {
        return failIfTransactionPresent;
    }

    /**
     * Determine whether this transaction policy causes a failure when a client-side transaction context is absent.
     *
     * @return {@code true} if the policy would fail when a transaction is absent, {@code false} otherwise
     */
    public boolean failIfTransactionAbsent() {
        return failIfTransactionAbsent;
    }

    /**
     * Determine whether this transaction policy causes the client-side transaction context (if any) to be propagated
     * to invocations.
     *
     * @return {@code true} if the transaction is propagated by this policy, {@code false} otherwise
     */
    public boolean propagate() {
        return propagate;
    }

    private static final int fullSize = values().length;

    /**
     * Determine whether the given set is fully populated (or "full"), meaning it contains all possible values.
     *
     * @param set the set
     * @return {@code true} if the set is full, {@code false} otherwise
     */
    public static boolean isFull(final EnumSet<ClientTransactionPolicy> set) {
        return set != null && set.size() == fullSize;
    }

    /**
     * Determine whether this instance is equal to one of the given instances.
     *
     * @param v1 the first instance
     * @return {@code true} if one of the instances matches this one, {@code false} otherwise
     */
    public boolean in(final ClientTransactionPolicy v1) {
        return this == v1;
    }

    /**
     * Determine whether this instance is equal to one of the given instances.
     *
     * @param v1 the first instance
     * @param v2 the second instance
     * @return {@code true} if one of the instances matches this one, {@code false} otherwise
     */
    public boolean in(final ClientTransactionPolicy v1, final ClientTransactionPolicy v2) {
        return this == v1 || this == v2;
    }

    /**
     * Determine whether this instance is equal to one of the given instances.
     *
     * @param v1 the first instance
     * @param v2 the second instance
     * @param v3 the third instance
     * @return {@code true} if one of the instances matches this one, {@code false} otherwise
     */
    public boolean in(final ClientTransactionPolicy v1, final ClientTransactionPolicy v2, final ClientTransactionPolicy v3) {
        return this == v1 || this == v2 || this == v3;
    }

    /**
     * Determine whether this instance is equal to one of the given instances.
     *
     * @param values the possible values
     * @return {@code true} if one of the instances matches this one, {@code false} otherwise
     */
    public boolean in(final ClientTransactionPolicy... values) {
        if (values != null) for (ClientTransactionPolicy value : values) {
            if (this == value) return true;
        }
        return false;
    }
}
