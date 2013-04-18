package org.jboss.ejb.client.test.compression;


import org.jboss.ejb.client.annotation.CompressionHint;

/**
 * @author: Jaikiran Pai
 */
@CompressionHint(compressRequest = false)
public interface ClassLevelResponseCompressionRemoteView {
    String echo(String msg);
}
