/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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

import org.jboss.ejb.client.DefaultCallbackHandler;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Property;
import org.xnio.Sequence;

import javax.security.auth.callback.CallbackHandler;

/**
 * @author Jaikiran Pai
 */
public class RemotingConnectionUtil {

    private static final String JBOSS_SASL_LOCAL_USER_QUIET_AUTH_PROP = "jboss.sasl.local-user.quiet-auth";

    /**
     * Adds the <code>jboss.sasl.local-user.quiet-auth</code> {@link Property property} to the {@link Options#SASL_PROPERTIES}
     * in the passed {@link OptionMap connectionCreationOptions} if
     * <ul>
     * <li>The passed <code>callbackHandler</code> is of type {@link DefaultCallbackHandler}</li>
     * <li><b>and</b> the passed {@link OptionMap connectionCreationOptions} doesn't already contain the {@link Options#SASL_PROPERTIES}
     * with the <code>jboss.sasl.local-user.quiet-auth</code></li>
     * </ul>
     *
     * @param callbackHandler           The callback handler
     * @param connectionCreationOptions The connection creation options
     * @return Returns the {@link OptionMap} with the jboss.sasl.local-user.quiet-auth {@link Property} set to true
     *         for the {@link Options#SASL_PROPERTIES} option, if the necessary conditions, mentioned earlier, are met. Else, returns back
     *         the passed <code>connectionCreationOptions</code>
     */
    public static OptionMap addSilentLocalAuthOptionsIfApplicable(final CallbackHandler callbackHandler, final OptionMap connectionCreationOptions) {
        // if the CallbackHandler isn't of type DefaultCallbackHandler then it means that the
        // user either specified a username or a CallbackHandler of his own. In both such cases, we do
        // *not* want to enable silent local auth and instead let the CallbackHandler be invoked and let
        // it pass the credentials
        if (!(callbackHandler instanceof DefaultCallbackHandler)) {
            return connectionCreationOptions;
        }
        // Neither a username nor a CallbackHandler was specified by the user, so let's
        // enable silent local auth *if* the user hasn't already overidden that property
        final Sequence<Property> existingSaslProps = connectionCreationOptions.get(Options.SASL_PROPERTIES);
        if (existingSaslProps != null) {
            for (Property prop : existingSaslProps) {
                if (prop.getKey().equals(JBOSS_SASL_LOCAL_USER_QUIET_AUTH_PROP)) {
                    return connectionCreationOptions;
                }
            }
            // the jboss.sasl.local-user.quiet-auth wasn't set by the user, so we add it
            // to the existing sasl properties
            existingSaslProps.add(Property.of(JBOSS_SASL_LOCAL_USER_QUIET_AUTH_PROP, "true"));
            return connectionCreationOptions;
        }
        // copy all the connection creation options and add the jboss.sasl.local-user.quiet-auth property
        // to the sasl property option
        final OptionMap.Builder updatedConnectionOptsBuilder = OptionMap.builder().addAll(connectionCreationOptions);
        updatedConnectionOptsBuilder.set(Options.SASL_PROPERTIES, Sequence.of(Property.of(JBOSS_SASL_LOCAL_USER_QUIET_AUTH_PROP, "true")));
        return updatedConnectionOptsBuilder.getMap();
    }

}
