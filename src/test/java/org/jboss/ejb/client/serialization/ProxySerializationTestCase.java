/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010 Red Hat, Inc., and individual contributors
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
 * Tests that validate that an Enterprise Bean proxy can be serialized using JBoss Marshalling.
 *
 * @author Stuart Douglas
 */
public class ProxySerializationTestCase {

    /**
     * Tests that marshalling/unmarshalling an EJB proxy to and from a byte stream does not affect
     * the validity of the proxy.
     *
     * @throws IOException
     * @throws ClassNotFoundException
     */
    @Test
    public void testProxySerialization() throws IOException, ClassNotFoundException {

        // create a sample EJB client proxy
        final StatelessEJBLocator<SimpleInterface> locator = new StatelessEJBLocator<SimpleInterface>(SimpleInterface.class, "a", "m", "b", "d");
        final Object proxy = EJBClient.createProxy(locator);

        // configure a JBoss Marshalling marshaller and marshall the proxy into a byte array, "bytes"
        final MarshallingConfiguration marshallingConfiguration = new MarshallingConfiguration();
        marshallingConfiguration.setVersion(2);
        org.jboss.marshalling.MarshallerFactory factory = new RiverMarshallerFactory();
        final Marshaller marshaller = factory.createMarshaller(marshallingConfiguration);
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        marshaller.start(new OutputStreamByteOutput(bytes));
        marshaller.writeObject(proxy);
        marshaller.finish();

        // configure a JBoss Marshalling unmarshaller and unmarshal the byte array, "bytes", into an Object
        Unmarshaller unmarshaller = factory.createUnmarshaller(marshallingConfiguration);
        ByteArrayInputStream in = new ByteArrayInputStream(bytes.toByteArray());
        unmarshaller.start(new InputStreamByteInput(in));
        Object deserialized = unmarshaller.readObject();

        // check for equality of the original proxy and the marshalled/unmarshalled proxy
        Assert.assertEquals(proxy, deserialized);
    }

}
