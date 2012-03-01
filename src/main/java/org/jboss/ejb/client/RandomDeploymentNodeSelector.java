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

import java.util.Random;

/**
 * A {@link DeploymentNodeSelector} which {@link Random randomly} selects a node name from the eligible nodes
 *
 * @author Jaikiran Pai
 */
class RandomDeploymentNodeSelector implements DeploymentNodeSelector {
    @Override
    public String selectNode(String[] eligibleNodes, String appName, String moduleName, String distinctName) {
        // Just a single node available, so just return it
        if (eligibleNodes.length == 1) {
            return eligibleNodes[0];
        }
        final Random random = new Random();
        final int randomSelection = random.nextInt(eligibleNodes.length);
        return eligibleNodes[randomSelection];
    }
}
