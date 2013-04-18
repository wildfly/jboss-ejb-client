package org.jboss.ejb.client.test.compression;

import java.util.zip.Deflater;

import org.jboss.ejb.client.annotation.CompressionHint;

/**
 * @author: Jaikiran Pai
 */
public interface CompressableDataRemoteView {


    @CompressionHint(compressResponse = false, compressionLevel = Deflater.BEST_COMPRESSION)
    String echoWithRequestCompress(String msg);

    @CompressionHint(compressRequest = false)
    String echoWithResponseCompress(String msg);

    @CompressionHint
    String echoWithRequestAndResponseCompress(String msg);

    String echoWithNoCompress(String msg);
}
