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

import org.jboss.ejb.client.EJBReceiverContext;

import java.io.DataInput;
import java.io.IOException;

/**
 * User: jpai
 */
class ModuleAvailabilityMessageHandler extends ProtocolMessageHandler {

    private final RemotingConnectionEJBReceiver ejbReceiver;

    private final EJBReceiverContext ejbReceiverContext;

    private EJBModuleIdentifier[] ejbModules;

    ModuleAvailabilityMessageHandler(final RemotingConnectionEJBReceiver ejbReceiver, final EJBReceiverContext ejbReceiverContext) {
        this.ejbReceiver = ejbReceiver;
        this.ejbReceiverContext = ejbReceiverContext;
    }


    @Override
    public void readMessage(DataInput input) throws IOException {
        if (input == null) {
            throw new IllegalArgumentException("Cannot read from null input");
        }
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
    }

    @Override
    public void processMessage() {
        if (this.ejbModules == null) {
            return;
        }
        for (final EJBModuleIdentifier ejbModule : this.ejbModules) {
            this.ejbReceiver.onModuleAvailable(ejbModule.appName, ejbModule.moduleName, ejbModule.distinctName);
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
