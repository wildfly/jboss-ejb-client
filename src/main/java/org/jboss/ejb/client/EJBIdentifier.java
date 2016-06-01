/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.ejb.client;

import java.io.Serializable;
import java.util.Objects;

import org.wildfly.common.Assert;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class EJBIdentifier implements Serializable {
    private static final long serialVersionUID = 7065644117552778408L;

    private final String appName;
    private final String moduleName;
    private final String beanName;
    private final String distinctName;
    private transient int hashCode;

    public EJBIdentifier(final String appName, final String moduleName, final String beanName, final String distinctName) {
        Assert.checkNotNullParam("appName", appName);
        Assert.checkNotNullParam("moduleName", moduleName);
        Assert.checkNotNullParam("beanName", beanName);
        Assert.checkNotNullParam("distinctName", distinctName);
        this.appName = appName;
        this.moduleName = moduleName;
        this.beanName = beanName;
        this.distinctName = distinctName;
    }

    public String getAppName() {
        return appName;
    }

    public String getModuleName() {
        return moduleName;
    }

    public String getBeanName() {
        return beanName;
    }

    public String getDistinctName() {
        return distinctName;
    }

    public boolean equals(final Object other) {
        return other instanceof EJBIdentifier && equals((EJBIdentifier) other);
    }

    public boolean equals(final EJBIdentifier other) {
        return other != null && (
            other == this ||
                other.hashCode() == hashCode() &&
                Objects.equals(appName, other.appName) &&
                Objects.equals(moduleName, other.moduleName) &&
                Objects.equals(beanName, other.beanName) &&
                Objects.equals(distinctName, other.distinctName)
        );
    }

    public int hashCode() {
        int hashCode = this.hashCode;
        if (hashCode != 0) {
            return hashCode;
        }
        hashCode = Objects.hashCode(appName) + 13 * (Objects.hashCode(moduleName) + 13 * (Objects.hashCode(beanName) + 13 * Objects.hashCode(distinctName)));
        return this.hashCode = hashCode == 0 ? 1 : hashCode;
    }

    public String toString() {
        final String distinctName = getDistinctName();
        if (distinctName == null || distinctName.isEmpty()) {
            return String.format("%s/%s/%s", getAppName(), getModuleName(), getBeanName());
        } else {
            return String.format("%s/%s/%s/%s", getAppName(), getModuleName(), getBeanName(), distinctName);
        }
    }
}
