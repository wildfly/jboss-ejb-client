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

package org.jboss.ejb.client.remoting;

import org.jboss.ejb.client.EJBClientInvocationContext;
import org.jboss.ejb.client.EJBClientProxyContext;
import org.jboss.ejb.client.SessionID;

/**
 * The Remoting-specific interceptor for session ID propagation.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class RemotingSessionInterceptor implements RemotingEJBClientInterceptor {

    public void handleInvocation(final EJBClientInvocationContext<? extends RemotingAttachments> context) throws Exception {
        final SessionID sessionID = context.getProxyAttachment(SessionID.SESSION_ID_KEY);
        if (sessionID != null) {
            context.getReceiverSpecific().putPayloadAttachment(0x0000, sessionID.getEncodedForm());
        }
        // pass on the control to the next in chain
        context.sendRequest();
    }

    public Object handleInvocationResult(final EJBClientInvocationContext<? extends RemotingAttachments> context) throws Exception {
        final byte[] attachment = context.getReceiverSpecific().getPayloadAttachment(0x0000);
        if (attachment != null && attachment.length > 0 && attachment[0] != 0) {
            // session was removed
            context.removeProxyAttachment(SessionID.SESSION_ID_KEY);
        }
        return context.getResult();
    }

    public void prepareSerialization(final EJBClientProxyContext<? extends RemotingAttachments> context) {
        final SessionID sessionID = context.getProxyAttachment(SessionID.SESSION_ID_KEY);
        if (sessionID != null) {
            context.getReceiverSpecific().putPayloadAttachment(0x0000, sessionID.getEncodedForm());
        }
    }

    public void postDeserialize(final EJBClientProxyContext<? extends RemotingAttachments> context) {
        final byte[] sessionIdBytes = context.getReceiverSpecific().getPayloadAttachment(0x0000);
        if (sessionIdBytes != null) {
            context.putProxyAttachment(SessionID.SESSION_ID_KEY, SessionID.createSessionID(sessionIdBytes));
        }
    }
}
