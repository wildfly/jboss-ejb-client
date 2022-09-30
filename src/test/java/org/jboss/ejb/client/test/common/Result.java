/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2019 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
