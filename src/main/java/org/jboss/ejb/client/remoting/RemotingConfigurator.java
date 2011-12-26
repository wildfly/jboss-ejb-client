/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

package org.jboss.ejb.client.remoting;


import org.jboss.logging.Logger;
import org.xnio.Option;
import org.xnio.OptionMap;

import java.util.Properties;

/**
 * @author Jaikiran Pai
 */
abstract class RemotingConfigurator {

    private static final Logger logger = Logger.getLogger(RemotingConfigurator.class);

    protected static OptionMap getOptionMapFromProperties(final Properties properties, final String propertyPrefix, final ClassLoader classLoader) {
        final OptionMap.Builder optionMapBuilder = OptionMap.builder().parseAll(properties, propertyPrefix, classLoader);
        final OptionMap optionMap = optionMapBuilder.getMap();
        logger.debug(propertyPrefix + " has the following options " + optionMap);
        return optionMap;
    }

    /**
     * Merges the passed <code>defaults</code> and the <code>overrides</code> to return a combined
     * {@link OptionMap}. If the passed <code>overrides</code> has a {@link org.xnio.Option} for
     * which matches the one in <code>defaults</code> then the default option value is ignored and instead the
     * overridden one is added to the combined {@link OptionMap}. If however, the <code>overrides</code> doesn't
     * contain a option which is present in the <code>defaults</code>, then the default option is added to the
     * combined {@link OptionMap}
     *
     * @param defaults  The default options
     * @param overrides The overridden options
     * @return
     */
    protected static OptionMap mergeWithDefaults(final OptionMap defaults, final OptionMap overrides) {
        // copy all the overrides
        final OptionMap.Builder combinedOptionsBuilder = OptionMap.builder().addAll(overrides);
        // Skip all the defaults which have been overridden and just add the rest of the defaults
        // to the combined options
        for (final Option defaultOption : defaults) {
            if (combinedOptionsBuilder.getMap().contains(defaultOption)) {
                continue;
            }
            final Object defaultValue = defaults.get(defaultOption);
            combinedOptionsBuilder.set(defaultOption, defaultValue);
        }
        final OptionMap combinedOptions = combinedOptionsBuilder.getMap();
        if (logger.isTraceEnabled()) {
            logger.trace("Options " + overrides + " have been merged with defaults " + defaults + " to form " + combinedOptions);
        }
        return combinedOptions;
    }

}
