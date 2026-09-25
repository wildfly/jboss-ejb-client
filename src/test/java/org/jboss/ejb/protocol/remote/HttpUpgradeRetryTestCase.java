/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2026 Red Hat, Inc., and individual contributors
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

import static org.junit.Assert.assertFalse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Security;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.ejb.client.Affinity;
import org.jboss.ejb.client.EJBClient;
import org.jboss.ejb.client.EJBClientConnection;
import org.jboss.ejb.client.EJBClientContext;
import org.jboss.ejb.client.EJBIdentifier;
import org.jboss.ejb.client.StatelessEJBLocator;
import org.jboss.ejb.client.test.common.Echo;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.wildfly.security.WildFlyElytronProvider;

public class HttpUpgradeRetryTestCase {

    private static final String MAGIC = "CF70DEB8-70F9-4FBA-8B4F-DFC3E723B4CD";
    private static final String SEC_KEY_HEADER = "Sec-JbossRemoting-Key: ";
    private static final String SEC_ACCEPT_HEADER = "Sec-JbossRemoting-Accept";

    private static final int CALLER_THREADS = 1;
    private static int MAX_EXPECTED_CONNECTIONS = 50;
    private static final int MEASURE_WINDOW_MS = 2000;
    private static final String APP = "test-app";
    private static final String MODULE = "test-module";
    private static final String DISTINCT = "";
    private static final String BEAN = "StatelessEchoBean";


    private static String providerName;

    @Rule
    public TestName name = new TestName();

    @BeforeClass
    public static void doBeforeClass() {
        final WildFlyElytronProvider provider = new WildFlyElytronProvider();
        Security.addProvider(provider);
        providerName = provider.getName();
    }

    @AfterClass
    public static void doAfterClass() {
        Security.removeProvider(providerName);
    }

    @Before
    public void doBefore() {
        System.gc();
        System.runFinalization();
    }

    @After
    public void doAfter() {
        System.gc();
        System.runFinalization();
    }

    @Test(timeout = 30_000)
    public void testZeroBackoffTightRetryLoopViaEjbClientProxy() throws Exception {
        final AtomicInteger totalAttempts = new AtomicInteger();

        try (ServerSocket serverSocket = new ServerSocket(0)) {
            final int port = serverSocket.getLocalPort();

            Thread serverThread = new Thread(new ServerAcceptLoop(serverSocket, totalAttempts), "server-thread");
            serverThread.setDaemon(true);
            serverThread.start();

            final URI serverUri = new URI("remote+http", null, "localhost", port, "/", null, null);

            final EJBClientContext ejbClientContext = new EJBClientContext.Builder()
                    .addTransportProvider(new RemoteTransportProvider())
                    .addClientConnection(new EJBClientConnection.Builder()
                            .setDestination(serverUri)
                            .build())
                    .build();

            final StatelessEJBLocator<Echo> locator = StatelessEJBLocator.create(
                    Echo.class,
                    new EJBIdentifier(APP, MODULE, BEAN, DISTINCT),
                    Affinity.forUri(serverUri));
            final Echo proxy = EJBClient.createProxy(locator);

            final CountDownLatch stopLatch = new CountDownLatch(CALLER_THREADS);

            for (int i = 0; i < CALLER_THREADS; i++) {
                Thread callerThread = new Thread(
                        new EjbCaller(ejbClientContext, proxy, stopLatch),
                        "caller-thread-" + i);
                callerThread.setDaemon(true);
                callerThread.start();
            }

            stopLatch.await(MEASURE_WINDOW_MS + 5000, TimeUnit.MILLISECONDS);

            System.out.printf("Total connection attempts in %dms: %d (%.1f/sec)%n",
                    MEASURE_WINDOW_MS, totalAttempts.get(),
                    totalAttempts.get() * 1000.0 / MEASURE_WINDOW_MS);

            assertFalse(
                    "Expected many connection attempts via EJB client with zero-backoff retry, got: " + totalAttempts.get(),
                    totalAttempts.get() > MAX_EXPECTED_CONNECTIONS);
        }
    }

    /**
     * Accepts incoming connections and hands each one to a ConnectionHandler.
     */
    private static class ServerAcceptLoop implements Runnable {
        private final ServerSocket serverSocket;
        private final AtomicInteger totalAttempts;

        ServerAcceptLoop(ServerSocket serverSocket, AtomicInteger totalAttempts) {
            this.serverSocket = serverSocket;
            this.totalAttempts = totalAttempts;
        }

        @Override
        public void run() {
            while (!serverSocket.isClosed()) {
                try {
                    Socket s = serverSocket.accept();
                    totalAttempts.incrementAndGet();
                    Thread handler = new Thread(new ConnectionHandler(s), "server-handler");
                    handler.setDaemon(true);
                    handler.start();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * Completes the HTTP upgrade handshake (sends HTTP 101 with the correct
     * Sec-JbossRemoting-Accept header) and then immediately closes the socket.
     * The client's RemotingHandshakeChecker accepts the 101, but the remoting
     * SASL handshake fails because the connection is closed → ConnectionInfo
     * .handleFailed() fires → zero-backoff retry.
     */
    private static class ConnectionHandler implements Runnable {
        private final Socket socket;

        ConnectionHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (Socket sock = socket) {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8));
                String secKey = null;
                String line;
                while ((line = reader.readLine()) != null && !line.isEmpty()) {
                    if (line.startsWith(SEC_KEY_HEADER)) {
                        secKey = line.substring(SEC_KEY_HEADER.length()).trim();
                    }
                }
                if (secKey == null) return;
                String concat = secKey + MAGIC;
                MessageDigest digest = MessageDigest.getInstance("SHA1");
                digest.update(concat.getBytes(StandardCharsets.UTF_8));
                String accept = Base64.getEncoder().encodeToString(digest.digest());
                sock.getOutputStream().write((
                        "HTTP/1.1 101 Switching Protocols\r\n" +
                        "Upgrade: jboss-remoting\r\n" +
                        "Connection: Upgrade\r\n" +
                        SEC_ACCEPT_HEADER + ": " + accept + "\r\n" +
                        "\r\n").getBytes(StandardCharsets.UTF_8));
                sock.getOutputStream().flush();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Invokes an EJB proxy repeatedly with no delay between retries, simulating
     * the EJB client's zero-backoff retry behaviour seen in production.
     */
    private static class EjbCaller implements Runnable {
        private final EJBClientContext ejbClientContext;
        private final Echo proxy;
        private final CountDownLatch stopLatch;

        EjbCaller(EJBClientContext ejbClientContext, Echo proxy, CountDownLatch stopLatch) {
            this.ejbClientContext = ejbClientContext;
            this.proxy = proxy;
            this.stopLatch = stopLatch;
        }

        @Override
        public void run() {
            EJBClientContext.getContextManager().setThreadDefault(ejbClientContext);
            long deadline = System.currentTimeMillis() + MEASURE_WINDOW_MS;
            while (System.currentTimeMillis() < deadline) {
                try {
                    proxy.echo("ping");
                } catch (Exception ignored) {
                }
            }
            stopLatch.countDown();
        }
    }
}
