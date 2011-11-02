/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, Red Hat Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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
package org.jboss.ejb.client.serialization;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.jboss.ejb.client.EJBClient;
import org.jboss.ejb.client.SimpleInterface;
import org.jboss.ejb.client.StatelessEJBLocator;
import org.jboss.marshalling.InputStreamByteInput;
import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.OutputStreamByteOutput;
import org.jboss.marshalling.Unmarshaller;
import org.jboss.marshalling.river.RiverMarshallerFactory;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests that an EJB proxy can be serialized
 *
 * @author Stuart Douglas
 */
public class ProxySerializationTestCase {

    @Test
    public void testProxySerialization() throws IOException, ClassNotFoundException {
        final StatelessEJBLocator<SimpleInterface> locator = new StatelessEJBLocator<SimpleInterface>(SimpleInterface.class, "a", "m", "b", "d");
        final Object proxy = EJBClient.createProxy(locator);
        final MarshallingConfiguration marshallingConfiguration = new MarshallingConfiguration();
        marshallingConfiguration.setVersion(2);
        org.jboss.marshalling.MarshallerFactory factory = new RiverMarshallerFactory();
        final Marshaller marshaller = factory.createMarshaller(marshallingConfiguration);
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        marshaller.start(new OutputStreamByteOutput(bytes));
        marshaller.writeObject(proxy);
        marshaller.finish();
        Unmarshaller unmarshaller = factory.createUnmarshaller(marshallingConfiguration);
        ByteArrayInputStream in = new ByteArrayInputStream(bytes.toByteArray());
        unmarshaller.start(new InputStreamByteInput(in));
        Object deserialized = unmarshaller.readObject();
        Assert.assertEquals(proxy, deserialized);
    }

}
