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

package org.jboss.ejb.protocol.remote;

import java.io.IOException;

import jakarta.transaction.HeuristicMixedException;
import jakarta.transaction.HeuristicRollbackException;
import jakarta.transaction.RollbackException;
import jakarta.transaction.SystemException;

import org.jboss.ejb.client.UserTransactionID;
import org.jboss.marshalling.Marshalling;
import org.jboss.marshalling.Unmarshaller;
import org.jboss.remoting3.MessageInputStream;
import org.jboss.remoting3.MessageOutputStream;
import org.jboss.remoting3.util.BlockingInvocation;
import org.jboss.remoting3.util.InvocationTracker;
import org.wildfly.transaction.client._private.Log;
import org.wildfly.transaction.client.provider.remoting.SimpleIdResolver;
import org.wildfly.transaction.client.spi.SimpleTransactionControl;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
class EJBSimpleTransactionControl implements SimpleTransactionControl {
    private final EJBClientChannel channel;
    private final UserTransactionID transactionID;
    private final SimpleIdResolver simpleIdResolver;

    EJBSimpleTransactionControl(final EJBClientChannel channel) {
        this.channel = channel;
        final UserTransactionID transactionID = channel.allocateUserTransactionID();
        this.transactionID = transactionID;
        simpleIdResolver = connection -> {
            if (channel.getChannel().getConnection() != connection) {
                throw Log.log.invalidTransactionConnection();
            }
            return transactionID.getId();
        };
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
        } finally {
            invocationTracker.remove(invocation);
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
        if (providerInterfaceType.isAssignableFrom(SimpleIdResolver.class)) {
            return providerInterfaceType.cast(simpleIdResolver);
        }
        return null;
    }
}
