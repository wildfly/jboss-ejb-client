/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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

/**
 * A selector which selects and returns a node, from among the passed eligible nodes, that can handle a specific
 * deployment within a EJB client context. Typical usage of {@link DeploymentNodeSelector} involves load balancing
 * calls to multiple nodes which can all handle the same deployment. This allows the application to have a deterministic
 * node selection policy while dealing with multiple nodes with same deployment.
 *
 * @author Jaikiran Pai
 */
public interface DeploymentNodeSelector {

    /**
     * Selects and returns a node from among the <code>eligibleNodes</code> to handle the invocation on a deployment
     * represented by the passed <code>appName</code>, <code>moduleName</code> and <code>distinctName</code> combination.
     * Implementations of this method must <b>not</b> return null or any other node name which isn't in the
     * <code>eligibleNodes</code>
     *
     * @param eligibleNodes The eligible nodes which can handle the deployment. Will not be empty.
     * @param appName       The app name of the deployment
     * @param moduleName    The module name of the deployment
     * @param distinctName  The distinct name of the deployment
     * @return
     */
    String selectNode(final String[] eligibleNodes, final String appName, final String moduleName, final String distinctName);
}
