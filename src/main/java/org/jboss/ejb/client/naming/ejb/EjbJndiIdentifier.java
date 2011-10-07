/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, Red Hat Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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
package org.jboss.ejb.client.naming.ejb;

import java.util.Map;

/**
 * @author Stuart Douglas
 */
class EjbJndiIdentifier {
    private final String application;
    private final String module;
    private final String distinctName;
    private final String ejbName;
    private final String viewName;
    private final Map<String, String> options;

    EjbJndiIdentifier(final String application, final String module, final String distinctName, final String ejbName, final String viewName, final Map<String, String> options) {
        this.application = application;
        this.module = module;
        this.distinctName = distinctName;
        this.ejbName = ejbName;
        this.viewName = viewName;
        this.options = options;
    }

    public String getApplication() {
        return application;
    }

    public String getDistinctName() {
        return distinctName;
    }

    public String getEjbName() {
        return ejbName;
    }

    public String getModule() {
        return module;
    }

    public Map<String, String> getOptions() {
        return options;
    }

    public String getViewName() {
        return viewName;
    }
}
