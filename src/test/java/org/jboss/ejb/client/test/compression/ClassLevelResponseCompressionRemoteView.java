package org.jboss.ejb.client.test.compression;


import org.jboss.ejb.client.annotation.DataCompressionHint;

/**
 * @author: Jaikiran Pai
 */
@DataCompressionHint(data = DataCompressionHint.Data.RESPONSE)
public interface ClassLevelResponseCompressionRemoteView {
    String echo(String msg);
}
