package org.jboss.ejb.client.test.compression;


import org.jboss.ejb.client.annotation.CompressionHint;

/**
 * @author: Jaikiran Pai
 */
@CompressionHint
public interface ClassLevelRequestAndResponseCompressionRemoteView {
    String echo(String msg);
}
