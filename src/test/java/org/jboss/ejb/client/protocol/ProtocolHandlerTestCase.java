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

package org.jboss.ejb.client.protocol;

import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Method;

/**
 * User: jpai
 */
public class ProtocolHandlerTestCase {

    private ProtocolHandler protocolHandler;

    @Before
    public void beforeTest() {
        this.protocolHandler = new VersionZeroProtocolHandler("java-serial");
    }

    @Test
    public void testMethodInvocationRequestResponse() throws Exception {
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final DataOutputStream dos = new DataOutputStream(bos);
        final Method method = Dummy.class.getMethod("echo", new Class<?>[] {String.class});
        this.protocolHandler.writeMethodInvocationRequest(dos, (short) 0, "app", "module", "bean", "view", method,
                new Object[] {"some message"}, null);

        dos.flush();
        final byte[] request = bos.toByteArray();
        final ByteArrayInputStream ios = new ByteArrayInputStream(request);
//        byte data;
//        while ((data = (byte) ios.read()) != -1) {
//            System.out.println("Byte is 0x" + Integer.toHexString(data));
//        }
//        System.out.println("Byte length is " + request.length);

        final ByteArrayInputStream inputStreamForProtocolHandler = new ByteArrayInputStream(request);
        final DataInputStream dis = new DataInputStream(inputStreamForProtocolHandler);
        final byte header = dis.readByte();
        //final MethodInvocationRequest methodInvocationRequest = this.protocolHandler.readMethodInvocationRequest(dis, new DummyEjbViewResolver());
    }

    private class Dummy {
        public String echo(String msg) {
            return msg;
        }
    }
}
