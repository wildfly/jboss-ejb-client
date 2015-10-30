/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Spliterator;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class IndexedList<T> extends AbstractList<T> {

    private final ArrayList<T> arrayList;
    private final HashSet<T> hashSet;

    IndexedList(final ArrayList<T> arrayList, final HashSet<T> hashSet) {
        this.arrayList = arrayList;
        this.hashSet = hashSet;
    }

    static <T> List<T> indexedListOf(Collection<T> list) {
        if (list.size() == 0) {
            return Collections.emptyList();
        } else if (list.size() == 1) {
            return Collections.singletonList(list.iterator().next());
        } else {
            final ArrayList<T> al = new ArrayList<>(list);
            final HashSet<T> index = new HashSet<T>(al);
            return new IndexedList<>(al, index);
        }
    }

    public boolean contains(final Object o) {
        return hashSet.contains(o);
    }

    public int size() {
        return arrayList.size();
    }

    public boolean isEmpty() {
        return arrayList.isEmpty();
    }

    public int indexOf(final Object o) {
        return arrayList.indexOf(o);
    }

    public int lastIndexOf(final Object o) {
        return arrayList.lastIndexOf(o);
    }

    public Object[] toArray() {
        return arrayList.toArray();
    }

    public <T> T[] toArray(final T[] a) {
        return arrayList.toArray(a);
    }

    public T get(final int index) {
        return arrayList.get(index);
    }

    public void forEach(final Consumer<? super T> action) {
        arrayList.forEach(action);
    }

    public Spliterator<T> spliterator() {
        return arrayList.spliterator();
    }
}
