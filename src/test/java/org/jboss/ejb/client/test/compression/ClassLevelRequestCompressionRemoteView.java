package org.jboss.ejb.client.test.compression;


import org.jboss.ejb.client.annotation.DataCompressionHint;

/**
 * @author: Jaikiran Pai
 */
@DataCompressionHint(data = DataCompressionHint.Data.REQUEST)
public interface ClassLevelRequestCompressionRemoteView {
    String echo(String msg);
}
