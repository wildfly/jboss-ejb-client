package org.jboss.ejb.client.remoting;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.InflaterInputStream;

/**
 * @author: Jaikiran Pai
 */
class CompressedMessageHandler extends ProtocolMessageHandler {

    private final ChannelAssociation channelAssociation;

    CompressedMessageHandler(final ChannelAssociation channelAssociation) {
        this.channelAssociation = channelAssociation;
    }

    @Override
    protected void processMessage(InputStream inputStream) throws IOException {
        final InputStream inflaterInputStream = new InflaterInputStream(inputStream);
        channelAssociation.processResponse(inflaterInputStream);
    }
}
