package org.jboss.ejb.client;

import org.wildfly.security.permission.AbstractBooleanPermission;

/**
 * Represents permission to invoke an EJB remotely
 *
 * @author Stuart Douglas
 */
public class RemoteEJBPermission extends AbstractBooleanPermission<RemoteEJBPermission> {

    /**
     * Construct a new instance.
     */
    public RemoteEJBPermission() {
    }

    /**
     * Construct a new instance.
     *
     * @param name ignored
     */
    public RemoteEJBPermission(@SuppressWarnings("unused") final String name) {
    }

    /**
     * Construct a new instance.
     *
     * @param name ignored
     * @param actions ignored
     */
    public RemoteEJBPermission(@SuppressWarnings("unused") final String name, @SuppressWarnings("unused") final String actions) {
    }

    private static final RemoteEJBPermission INSTANCE = new RemoteEJBPermission();

    /**
     * Get the instance of this class.
     *
     * @return the instance of this class
     */
    public static RemoteEJBPermission getInstance() {
        return INSTANCE;
    }
}
