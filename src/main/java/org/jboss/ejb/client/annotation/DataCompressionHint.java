package org.jboss.ejb.client.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.zip.Deflater;

/**
 * A hint to the EJB client API that the data being communicated between the EJB client and the server via the {@link org.jboss.ejb.client.EJBReceiver EJBReceiver(s)} should be compressed.
 * <p/>
 * This is just a hint and it's ultimately the EJB client API implementation and/or the EJBReceiver(s) which decide whether or not the data will be compressed.
 *
 * @author: Jaikiran Pai
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface DataCompressionHint {

    enum Data {
        REQUEST,
        RESPONSE,
        REQUEST_AND_RESPONSE
    }

    /**
     * The data that should be compressed. It can either be the {@link Data#REQUEST request}, {@link Data#RESPONSE response} or both the {@link Data#REQUEST_AND_RESPONSE request and response} of
     * a EJB invocation. By default both request and response data is compressed, when the {@link DataCompressionHint} is honoured.
     *
     * @return
     */
    Data data() default org.jboss.ejb.client.annotation.DataCompressionHint.Data.REQUEST_AND_RESPONSE; // fully qualified classname for the enum is required as a workaround for Sun/Oracle compiler bug http://bugs.sun.com/view_bug.do?bug_id=6512707

    /**
     * The compression level to be used while compressing the data. The values can be any of those that are supported by {@link Deflater}. By default the compression level is {@link Deflater#DEFAULT_COMPRESSION}
     *
     * @return
     */
    int compressionLevel() default Deflater.DEFAULT_COMPRESSION;
}
