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

import org.jboss.remoting3.MessageInputStream;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;

/**
 * Responsible for parsing module availability and unavailability messages from a stream, as per the EJB remoting client
 * protocol specification
 * <p/>
 * User: Jaikiran Pai
 */
class ModuleAvailabilityMessageHandler extends ProtocolMessageHandler {

    private final RemotingConnectionEJBReceiver ejbReceiver;

    enum ModuleReportType {
        MODULE_AVAILABLE,
        MODULE_UNAVAILABLE
    }

    private final ModuleReportType type;

    ModuleAvailabilityMessageHandler(final RemotingConnectionEJBReceiver ejbReceiver, final ModuleReportType type) {
        this.ejbReceiver = ejbReceiver;
        this.type = type;
    }


    /**
     * Processes the passed <code>messageInputStream</code> for module availability and/or module unavailability
     * report. This method then let's the {@link RemotingConnectionEJBReceiver} know about the module availability/unavailability
     *
     * @param messageInputStream The message input stream
     * @throws IOException If there's a problem while reading the stream
     */
    @Override
    protected void processMessage(final MessageInputStream messageInputStream) throws IOException {
        if (messageInputStream == null) {
            throw new IllegalArgumentException("Cannot read from null stream");
        }
        EJBModuleIdentifier ejbModules[] = null;
        try {
            final DataInput input = new DataInputStream(messageInputStream);
            // read the count
            final int count = PackedInteger.readPackedInteger(input);
            ejbModules = new EJBModuleIdentifier[count];
            for (int i = 0; i < ejbModules.length; i++) {
                // read the app name
                String appName = input.readUTF();
                if (appName.isEmpty()) {
                    appName = null;
                }
                // read the module name
                final String moduleName = input.readUTF();
                // read distinct name
                String distinctName = input.readUTF();
                if (distinctName.isEmpty()) {
                    distinctName = null;
                }
                ejbModules[i] = new EJBModuleIdentifier(appName, moduleName, distinctName);
            }
        } finally {
            messageInputStream.close();
        }
        switch (this.type) {
            case MODULE_AVAILABLE:
                for (final EJBModuleIdentifier ejbModule : ejbModules) {
                    this.ejbReceiver.moduleAvailable(ejbModule.appName, ejbModule.moduleName, ejbModule.distinctName);
                }
                break;
            case MODULE_UNAVAILABLE:
                for (final EJBModuleIdentifier ejbModule : ejbModules) {
                    this.ejbReceiver.moduleUnavailable(ejbModule.appName, ejbModule.moduleName, ejbModule.distinctName);
                }
                break;
        }


    }

    private class EJBModuleIdentifier {
        private final String appName;

        private final String moduleName;

        private final String distinctName;

        EJBModuleIdentifier(final String appname, final String moduleName, final String distinctName) {
            this.appName = appname;
            this.moduleName = moduleName;
            this.distinctName = distinctName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            EJBModuleIdentifier that = (EJBModuleIdentifier) o;

            if (appName != null ? !appName.equals(that.appName) : that.appName != null) return false;
            if (distinctName != null ? !distinctName.equals(that.distinctName) : that.distinctName != null)
                return false;
            if (!moduleName.equals(that.moduleName)) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = appName != null ? appName.hashCode() : 0;
            result = 31 * result + moduleName.hashCode();
            result = 31 * result + (distinctName != null ? distinctName.hashCode() : 0);
            return result;
        }
    }
}
