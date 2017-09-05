package org.jboss.ejb.client.test.common;

import java.io.Serializable;

/**
 * /**
 * A wrapper for a return value that includes the node on which the result was generated.
 * @author Paul Ferraro
 */

public class Result<T> implements Serializable {
    private static final long servialVersionUID = 12345L ;
    private final T value;
    private final String node;

    public Result(T value, String node) {
        this.value = value;
        this.node = node;
    }

    public T getValue() {
        return value;
    }

    public String getNode() {
        return node;
    }
}
