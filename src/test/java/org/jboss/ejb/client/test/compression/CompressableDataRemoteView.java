package org.jboss.ejb.client.test.compression;

import org.jboss.ejb.client.annotation.DataCompressionHint;

import java.util.zip.Deflater;

/**
 * @author: Jaikiran Pai
 */
public interface CompressableDataRemoteView {


    @DataCompressionHint(data = DataCompressionHint.Data.REQUEST, compressionLevel = Deflater.BEST_COMPRESSION)
    String echoWithRequestCompress(String msg);

    @DataCompressionHint(data = DataCompressionHint.Data.RESPONSE)
    String echoWithResponseCompress(String msg);

    @DataCompressionHint(data = DataCompressionHint.Data.REQUEST_AND_RESPONSE)
    String echoWithRequestAndResponseCompress(String msg);

    String echoWithNoCompress(String msg);
}
