package org.jboss.ejb.client.remoting;

import java.io.IOException;

import org.jboss.remoting3.Attachments;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.CloseHandler;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.MessageOutputStream;
import org.junit.Assert;
import org.junit.Test;
import org.xnio.Option;

/**
 * @author Tomas Hofman (thofman@redhat.com)
 */
public class ChannelAssociationTestCase {

    private ChannelAssociation channelAssociation = new ChannelAssociation(null, null, new ChannelStub(), (byte) 1, null, null, null);

    @Test
    public void testGetInvocationIdSkipAsync() {
        // checks that invocation IDs that are already registered are skipped when generating next ID
        Assert.assertEquals(0, channelAssociation.getNextInvocationId());
        channelAssociation.enrollForResult((short) 1);
        channelAssociation.enrollForResult((short) 2);
        Assert.assertEquals(3, channelAssociation.getNextInvocationId());
    }

    @Test
    public void testGetInvocationIdSkipSync() {
        // checks that invocation IDs that are already registered are skipped when generating next ID
        Assert.assertEquals(0, channelAssociation.getNextInvocationId());
        channelAssociation.receiveResponse((short) 1, null);
        channelAssociation.receiveResponse((short) 2, null);
        Assert.assertEquals(3, channelAssociation.getNextInvocationId());
    }

    private static class ChannelStub implements Channel {

        @Override
        public Connection getConnection() {
            return null;
        }

        @Override
        public MessageOutputStream writeMessage() throws IOException {
            return null;
        }

        @Override
        public void writeShutdown() throws IOException {

        }

        @Override
        public void receiveMessage(Receiver handler) {

        }

        @Override
        public boolean supportsOption(Option<?> option) {
            return false;
        }

        @Override
        public <T> T getOption(Option<T> option) {
            return null;
        }

        @Override
        public <T> T setOption(Option<T> option, T value) throws IllegalArgumentException {
            return null;
        }

        @Override
        public void close() throws IOException {

        }

        @Override
        public void awaitClosed() throws InterruptedException {

        }

        @Override
        public void awaitClosedUninterruptibly() {

        }

        @Override
        public void closeAsync() {

        }

        @Override
        public Key addCloseHandler(CloseHandler<? super Channel> handler) {
            return null;
        }

        @Override
        public boolean isOpen() {
            return false;
        }

        @Override
        public Attachments getAttachments() {
            return null;
        }
    }
}
