package org.jboss.ejb.client;

/**
 * A callback interested in a cluster affinity.
 *
 * @author Jason T. Greene
 */
interface ClusterAffinityInterest {
    AttachmentKey<ClusterAffinityInterest> KEY = new AttachmentKey<>();

    void notifyAssignment(ClusterAffinity affinity);
}
