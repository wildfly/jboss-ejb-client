package org.jboss.ejb.client.test.compression;


import org.jboss.ejb.client.annotation.DataCompressionHint;

/**
 * @author: Jaikiran Pai
 */
@DataCompressionHint(data = DataCompressionHint.Data.REQUEST_AND_RESPONSE)
public interface ClassLevelRequestAndResponseCompressionRemoteView {
    String echo(String msg);
}
