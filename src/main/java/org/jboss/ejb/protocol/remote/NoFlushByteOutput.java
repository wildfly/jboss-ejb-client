package org.jboss.ejb.protocol.remote;

import java.io.IOException;

import org.jboss.marshalling.ByteOutput;

/**
 * An output stream that ignores flushes. The marshsaller will flush when it is done, which
 * results in two frames on the wire. By ignoring the flush only one frame is sent for
 * each message.
 *
 * @author Stuart Douglas
 */
class NoFlushByteOutput implements ByteOutput {

    private final ByteOutput delegate;

    NoFlushByteOutput(ByteOutput delegate) {
        this.delegate = delegate;
    }

    @Override
    public void write(int b) throws IOException {
        delegate.write(b);
    }

    @Override
    public void write(byte[] b) throws IOException {
        delegate.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        delegate.write(b, off, len);
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    @Override
    public void flush() throws IOException {
        //ignore
    }
}
