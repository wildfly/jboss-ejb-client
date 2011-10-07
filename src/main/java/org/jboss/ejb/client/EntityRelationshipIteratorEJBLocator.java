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

package org.jboss.ejb.client;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Iterator;
import org.jboss.marshalling.FieldSetter;

/**
 * A locator for an entity EJB relationship collection's iterator.
 *
 * @param <E> the element type
 */
public final class EntityRelationshipIteratorEJBLocator<E> extends Locator<Iterator<E>> {

    private static final long serialVersionUID = 9153633902674519332L;

    private final EntityEJBLocator<?> entityLocator;
    private final String relationshipName;
    private final int generation;
    private final transient int hashCode;

    private static final FieldSetter hashCodeSetter = FieldSetter.get(EntityRelationshipIteratorEJBLocator.class, "hashCode");

    @SuppressWarnings("unchecked")
    EntityRelationshipIteratorEJBLocator(final String relationshipName, final int generation, final EntityEJBLocator<?> entityLocator) {
        super((Class<Iterator<E>>)(Object)Iterator.class);
        this.entityLocator = entityLocator;
        this.relationshipName = relationshipName;
        this.generation = generation;
        hashCode = relationshipName.hashCode() * 13 + (entityLocator.hashCode() * 13 + super.hashCode());
    }

    /**
     * Determine whether this object is equal to another.
     *
     * @param other the other object
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    public boolean equals(final Object other) {
        return other instanceof EntityRelationshipIteratorEJBLocator && equals((EntityRelationshipIteratorEJBLocator<?>) other);
    }

    /**
     * Determine whether this object is equal to another.
     *
     * @param other the other object
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    public boolean equals(final Locator<?> other) {
        return other instanceof EntityRelationshipIteratorEJBLocator && equals((EntityRelationshipIteratorEJBLocator<?>) other);
    }

    /**
     * Determine whether this object is equal to another.
     *
     * @param other the other object
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    public boolean equals(final EntityRelationshipIteratorEJBLocator<?> other) {
        return super.equals(other) && relationshipName.equals(other.relationshipName);
    }

    /**
     * Get the hash code for this instance.
     *
     * @return the hash code for this instance
     */
    public int hashCode() {
        return hashCode;
    }

    /**
     * Get the name of the referenced relationship.
     *
     * @return the name of the referenced relationship
     */
    public String getRelationshipName() {
        return relationshipName;
    }

    /**
     * Get the generation ID of this iterator.
     *
     * @return the generation ID
     */
    public int getGeneration() {
        return generation;
    }

    public String getAppName() {
        return entityLocator.getAppName();
    }

    public String getModuleName() {
        return entityLocator.getModuleName();
    }

    public String getBeanName() {
        return entityLocator.getBeanName();
    }

    public String getDistinctName() {
        return entityLocator.getDistinctName();
    }

    private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
        ois.defaultReadObject();
        hashCodeSetter.setInt(this, relationshipName.hashCode() * 13 + (entityLocator.hashCode() * 13 + super.hashCode()));
    }
}
