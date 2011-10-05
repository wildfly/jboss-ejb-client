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

import org.junit.Assert;
import org.junit.Test;

import java.util.Iterator;

/**
 * Test {@link IntKeyMap}
 * <p/>
 * User: Jaikiran Pai
 */
public class IntKeyMapTestCase {

    /**
     * Test that the iterator returned by {@link org.jboss.ejb.client.remoting.IntKeyMap#iterator()} works
     * as per the API contracts of {@link Iterator}
     *
     * @throws Exception
     */
    @Test
    public void testIterator() throws Exception {
        final IntKeyMap<String> intKeyMap = new IntKeyMap();
        // Add some entries to the map
        final int NUM_ENTRIES = 5;
        for (int i = 0; i < NUM_ENTRIES; i++) {
            intKeyMap.put(i, String.valueOf(i));
        }
        // check the size
        Assert.assertEquals("Unexpected IntKeyMap size", NUM_ENTRIES, intKeyMap.size());
        // test isEmpty API
        Assert.assertFalse("IntKeyMap should not have been empty", intKeyMap.isEmpty());

        // get the iterator
        final Iterator<IntKeyMap.Entry<String>> iterator = intKeyMap.iterator();
        // test the hasNext API
        Assert.assertTrue("IntKeyMap's iterator#hasNext should have returned true", iterator.hasNext());
        Assert.assertTrue("IntKeyMap's iterator#hasNext should have returned true (second time)", iterator.hasNext());
        // test the next() API (and hasNext() API) on the iterator
        for (int i = 0; i < NUM_ENTRIES; i++) {
            if (i != NUM_ENTRIES - 1) {
                Assert.assertTrue("IntKeyMap's iterator#hasNext should have returned true while iterating", iterator.hasNext());
            }
            final IntKeyMap.Entry<String> next = iterator.next();
            final int key = next.getKey();
            final String val = next.getValue();
            final String expectedVal = String.valueOf(key);
            Assert.assertEquals("Unexpected value for key: " + key, expectedVal, val);
        }
        // test hasNext() one more time
        Assert.assertFalse("IntKeyMap's iterator#hasNext should have returned false", iterator.hasNext());

    }
}
