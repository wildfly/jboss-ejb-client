package org.jboss.ejb.client.test.compression;

/**
 * @author: Jaikiran Pai
 */
public class CompressableDataBean implements CompressableDataRemoteView, ClassLevelRequestCompressionRemoteView, ClassLevelRequestAndResponseCompressionRemoteView, ClassLevelResponseCompressionRemoteView, MethodOverrideDataCompressionRemoteView {

    @Override
    public String echoWithRequestCompress(String msg) {
        return msg;
    }

    @Override
    public String echoWithNoExplicitDataCompressionHintOnMethod(String msg) {
        return msg;
    }

    @Override
    public String echoWithResponseCompress(String msg) {
        return msg;
    }

    @Override
    public String echoWithRequestAndResponseCompress(String msg) {
        return msg;
    }

    @Override
    public String echoWithNoCompress(String msg) {
        return msg;
    }

    @Override
    public String echo(String msg) {
        return msg;
    }
}
