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
