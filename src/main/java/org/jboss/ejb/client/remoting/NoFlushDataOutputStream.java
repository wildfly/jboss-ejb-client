package org.jboss.ejb.client.remoting;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Extended version of DataOutputStream that does not flush before close
 *
 * @author Stuart Douglas
 */
class NoFlushDataOutputStream extends DataOutputStream {

    /**
     * Creates a new data output stream to write data to the specified
     * underlying output stream. The counter <code>written</code> is
     * set to zero.
     *
     * @param out the underlying output stream, to be saved for later
     *            use.
     * @see java.io.FilterOutputStream#out
     */
    public NoFlushDataOutputStream(OutputStream out) {
        super(out);
    }

    @Override
    public void close() throws IOException {
        this.out.close();
    }
}
