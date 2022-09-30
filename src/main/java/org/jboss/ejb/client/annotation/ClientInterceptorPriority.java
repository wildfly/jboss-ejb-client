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

package org.jboss.ejb.client.annotation;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * The priority of the client interceptor.  If no priority is specified, then {@link #APPLICATION} is used.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@Target(TYPE)
@Retention(RUNTIME)
public @interface ClientInterceptorPriority {
    int value();

    /**
     * The starting range value for application interceptors.
     */
    int APPLICATION = 0;

    /**
     * The ending range value for application interceptors.
     */
    int APPLICATION_END = APPLICATION + 99_999;

    /**
     * The starting range value for library "before" interceptors.
     */
    int LIBRARY_BEFORE = -100_000;

    /**
     * The ending range value for library "before" interceptors.
     */
    int LIBRARY_BEFORE_END = LIBRARY_BEFORE + 99_999;

    /**
     * The starting range value for provided JBoss "before" interceptors.
     */
    int JBOSS_BEFORE = -200_000;

    /**
     * The ending range value for provided JBoss "before" interceptors.
     */
    int JBOSS_BEFORE_END = JBOSS_BEFORE + 99_999;

    /**
     * The starting range value for library "after" interceptors.
     */
    int LIBRARY_AFTER = 100_000;

    /**
     * The ending range value for library "after" interceptors.
     */
    int LIBRARY_AFTER_END = LIBRARY_AFTER + 99_999;

    /**
     * The starting range value for provided JBoss "after" interceptors.
     */
    int JBOSS_AFTER = 200_000;

    /**
     * The ending range value for provided JBoss "before" interceptors.
     */
    int JBOSS_AFTER_END = JBOSS_AFTER + 99_999;
}
