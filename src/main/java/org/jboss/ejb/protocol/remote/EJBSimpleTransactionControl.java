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

package org.jboss.ejb.protocol.remote;

import java.io.IOException;

import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;

import org.jboss.ejb.client.TransactionID;
import org.jboss.marshalling.Marshalling;
import org.jboss.marshalling.Unmarshaller;
import org.jboss.remoting3.MessageInputStream;
import org.jboss.remoting3.MessageOutputStream;
import org.jboss.remoting3.util.BlockingInvocation;
import org.jboss.remoting3.util.InvocationTracker;
import org.wildfly.transaction.client.spi.SimpleTransactionControl;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
class EJBSimpleTransactionControl implements SimpleTransactionControl {
    private final EJBClientChannel channel;
    private final TransactionID transactionID;

    EJBSimpleTransactionControl(final EJBClientChannel channel) {
        this.channel = channel;
        this.transactionID = channel.allocateUserTransactionID();
    }

    public void commit() throws RollbackException, HeuristicMixedException, HeuristicRollbackException, SecurityException, SystemException {
        executeSimpleInvocation(Protocol.TXN_COMMIT_REQUEST, true);
    }

    public void rollback() throws SecurityException, SystemException {
        executeSimpleInvocation(Protocol.TXN_ROLLBACK_REQUEST, false);
    }

    private void executeSimpleInvocation(int type, boolean withParam) throws SystemException {
        final EJBClientChannel channel = this.channel;
        final InvocationTracker invocationTracker = channel.getInvocationTracker();
        final EJBTransactionOperations.PlainTransactionInvocation invocation = invocationTracker.addInvocation(EJBTransactionOperations.PlainTransactionInvocation::new);
        try (MessageOutputStream os = invocationTracker.allocateMessage(invocation)) {
            os.writeByte(type);
            os.writeShort(invocation.getIndex());
            final byte[] encoded = transactionID.getEncodedForm();
            PackedInteger.writePackedInteger(os, encoded.length);
            os.write(encoded);
            if (withParam) {
                os.writeBoolean(true);
            }
        } catch (IOException e) {
            throw new SystemException();
        }
        try (BlockingInvocation.Response response = invocation.getResponse()) {
            switch (response.getParameter()) {
                case Protocol.TXN_RESPONSE: {
                    final MessageInputStream inputStream = response.getInputStream();
                    boolean flag = inputStream.readBoolean();
                    if (flag) {
                        // unrecognized parameter
                        throw new SystemException();
                    }
                    return;
                }
                case Protocol.APPLICATION_EXCEPTION: {
                    throw readAppException(channel, response);
                }
                default: {
                    throw new SystemException();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            invocation.cancel();
            throw new SystemException();
        } catch (IOException e) {
            throw new SystemException();
        }
    }

    static SystemException readAppException(final EJBClientChannel channel, final BlockingInvocation.Response response) throws SystemException {
        Exception e;
        try (final Unmarshaller unmarshaller = channel.createUnmarshaller()) {
            try (MessageInputStream inputStream = response.getInputStream()) {
                unmarshaller.start(Marshalling.createByteInput(inputStream));
                e = unmarshaller.readObject(Exception.class);
                unmarshaller.finish();
                // The version is probably < 3 else we would not be here
                // drain off attachments so the server doesn't complain
                while (inputStream.read() != -1) {
                    inputStream.skip(Long.MAX_VALUE);
                }
            }
        } catch (IOException | ClassNotFoundException e1) {
            throw new SystemException();
        }
        if (e == null) {
            throw new SystemException();
        }
        try {
            throw e;
        } catch (RuntimeException | SystemException e1) {
            throw e1;
        } catch (Exception e1) {
            final SystemException e2 = new SystemException();
            e2.initCause(e1);
            return e2;
        }
    }

    public <T> T getProviderInterface(final Class<T> providerInterfaceType) {
        return null;
    }
}
