package org.jboss.ejb.client.test.compression;


import org.jboss.ejb.client.annotation.CompressionHint;

/**
 * @author: Jaikiran Pai
 */
@CompressionHint(compressResponse = false)
public interface ClassLevelRequestCompressionRemoteView {
    String echo(String msg);
}
