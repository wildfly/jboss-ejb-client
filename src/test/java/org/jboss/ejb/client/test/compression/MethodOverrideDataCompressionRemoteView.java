package org.jboss.ejb.client.test.compression;


import org.jboss.ejb.client.annotation.DataCompressionHint;

/**
 * @author: Jaikiran Pai
 */
@DataCompressionHint(data = DataCompressionHint.Data.REQUEST_AND_RESPONSE)
public interface MethodOverrideDataCompressionRemoteView {

    @DataCompressionHint(data = DataCompressionHint.Data.RESPONSE)
    String echoWithResponseCompress(final String msg);

    @DataCompressionHint(data = DataCompressionHint.Data.REQUEST)
    String echoWithRequestCompress(final String msg);

    String echoWithNoExplicitDataCompressionHintOnMethod(String msg);
}
