/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
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

package org.jboss.ejb.protocol.remote;

import java.io.Serializable;

/**
 * Allow safe deserialization of stack trace elements from Java 8 and earlier (4-field variant).
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class StackTraceElement4 implements Serializable {
    // keep this exact UID for JDK compatibility
    private static final long serialVersionUID = 6992337162326171013L;

    private String declaringClass;
    private String methodName;
    private String fileName;
    private int    lineNumber;

    StackTraceElement4() {
    }

    Object readResolve() {
        return new StackTraceElement(declaringClass, methodName, fileName, lineNumber);
    }
}
