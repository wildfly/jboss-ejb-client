package org.jboss.ejb.client.legacy;

/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017, Red Hat, Inc., and individual contributors
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

import org.xnio.OptionMap;

import javax.security.auth.callback.CallbackHandler;

public interface RemotingConnectionConfiguration  {

    /**
     * Returns the {@link OptionMap options} that will be used during connection creation. This method must
     * <b>not</b> return null
     *
     * @return
     */
    OptionMap getConnectionCreationOptions();

    /**
     * Returns the {@link CallbackHandler} that will be used during connection creation. This method must
     * <b>not</b> return null
     *
     * @return
     */
    CallbackHandler getCallbackHandler();

    /**
     * Returns the connection timeout in milliseconds, that will be used during connection creation
     *
     * @return
     */
    long getConnectionTimeout();

    /**
     * Returns the {@link OptionMap options} that will be used during creation of a {@link org.jboss.remoting3.Channel}
     * for the connection
     *
     * @return
     */
    OptionMap getChannelCreationOptions();

    /**
     * If this method returns true, then the EJB client API will try and connect to the destination host "eagerly". when the {@link EJBClientContext}
     * is being created out of the {@link EJBClientConfiguration} to which this connection configuration belongs.
     * <p/>
     * On the other hand, if this method returns false, then the EJB client API will try to connect to the destination host only if no other node/EJBReceiver within the EJB client context
     * can handle a EJB invocation request. i.e. it tries to establish the connection lazily/on-demand.
     *
     * @return
     */
    boolean isConnectEagerly();


    /**
     * Returns the host name/IP address to be used during connection creation. This method must <b>not</b>
     * return null
     *
     * @return
     */
    String getHost();

    /**
     * Returns the port that will be used during connection creation
     *
     * @return
     */
    int getPort();

    /**
     * The protocol to use. Can be remoting, http-remoting or https-remoting
     *
     *
     * @return The protocol
     */
    String getProtocol();

}
