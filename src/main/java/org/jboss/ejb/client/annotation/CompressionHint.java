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
 * @author Jaikiran Pai
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface CompressionHint {

    /**
     * True if the request data of an EJB invocation should be compressed. False otherwise. By default this is <code>true</code>.
     */
    boolean compressRequest() default true;

    /**
     * True if the response data of an EJB invocation should be compressed. False otherwise. By default this is <code>true</code>.
     */
    boolean compressResponse() default true;

    /**
     * The compression level to be used while compressing the data. The values can be any of those that are supported by {@link Deflater}. By default the compression level is {@link Deflater#DEFAULT_COMPRESSION}
     */
    int compressionLevel() default Deflater.DEFAULT_COMPRESSION;
}
