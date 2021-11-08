package org.jboss.ejb.protocol.remote;

import java.io.IOException;
import java.io.OutputStream;

import org.jboss.remoting3.MessageOutputStream;

/**
 * Extended version of DataOutputStream that does not flush before close
 *
 * @author Stuart Douglas
 */
class WrapperMessageOutputStream extends MessageOutputStream {

    private final MessageOutputStream underlyingMessage;
    private final OutputStream delegate;

    WrapperMessageOutputStream(MessageOutputStream underlyingMessage, OutputStream delegate) {
        this.underlyingMessage = underlyingMessage;
        this.delegate = delegate;
    }

    @Override
    public void write(int b) throws IOException {
        delegate.write(b);
    }

    @Override
    public void flush() throws IOException {
        delegate.flush();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    @Override
    public MessageOutputStream cancel() {
        return underlyingMessage.cancel();
    }

    @Override
    public void write(byte[] b) throws IOException {
        delegate.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        delegate.write(b, off, len);
    }
}
