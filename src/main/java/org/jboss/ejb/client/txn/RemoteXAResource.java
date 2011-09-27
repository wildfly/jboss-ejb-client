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

package org.jboss.ejb.client.txn;

import org.jboss.ejb.client.EJBReceiver;
import org.jboss.ejb.client.remoting.RemotingAttachments;

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class RemoteXAResource implements XAResource {
    private final EJBReceiver<RemotingAttachments> remotingReceiver;

    RemoteXAResource(final EJBReceiver<RemotingAttachments> remotingReceiver) {
        this.remotingReceiver = remotingReceiver;
    }

    public void start(final Xid xid, final int flags) throws XAException {
    }

    public void end(final Xid xid, final int flags) throws XAException {
    }

    public void rollback(final Xid xid) throws XAException {
    }

    public int prepare(final Xid xid) throws XAException {
        return 0;
    }

    public void commit(final Xid xid, final boolean onePhase) throws XAException {
    }

    public void forget(final Xid xid) throws XAException {
    }

    public Xid[] recover(final int flags) throws XAException {
        return new Xid[0];
    }

    public int getTransactionTimeout() throws XAException {
        return 0;
    }

    public boolean setTransactionTimeout(final int seconds) throws XAException {
        return false;
    }

    public boolean isSameRM(final XAResource resource) throws XAException {
        return resource instanceof RemoteXAResource && isSameRM((RemoteXAResource) resource);
    }

    public boolean isSameRM(final RemoteXAResource resource) throws XAException {
        return resource.remotingReceiver == remotingReceiver;
    }
}
