package org.jboss.ejb.client;

/**
 * @author: Jaikiran Pai
 */
public interface CloseTask {

    void close(boolean closeAsync);
}
