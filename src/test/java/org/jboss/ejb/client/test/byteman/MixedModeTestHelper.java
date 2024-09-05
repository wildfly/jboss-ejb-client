package org.jboss.ejb.client.test.byteman;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.byteman.rule.Rule;
import org.jboss.byteman.rule.helper.Helper;
import org.jboss.ejb.client.EJBClientContext;
import org.wildfly.discovery.FilterSpec;
import org.wildfly.discovery.ServiceURL;

/**
 * Helper class for the MixedModeServiceURLTestCase.
 *
 * Maintains a map of nodes to ServiceURLs used for test validation.
 *
 * @author rachmato@redhat.com
 */
public class MixedModeTestHelper extends Helper {

    private static final String NODE_LIST_MAP_NAME = "nodeListMap";
    private static final FilterSpec EJB_MODULE_FILTER_SPEC = FilterSpec.hasAttribute(EJBClientContext.FILTER_ATTR_EJB_MODULE);

    public MixedModeTestHelper(Rule rule) {
        super(rule);
    }

    public void createNodeListMap() {
        createLinkMap(NODE_LIST_MAP_NAME);
    }

    public void addServiceURLCacheToMap(String node, List<ServiceURL> list) {
        if (list.size() < 4) {
            System.out.printf("Ignoring partial ServiceURLs: %s%n", list);
            return;
        }
        boolean hasEjbModule = true;
        for (ServiceURL u : list) {
            if (!u.satisfies(EJB_MODULE_FILTER_SPEC)) {
                hasEjbModule = false;
                break;
            }
        }
        if (!hasEjbModule) {
            System.out.printf("Ignoring invalid temp ServiceURLs: %s%n", list);
            return;
        }

        System.out.println("** Adding serviceURL to map: node = " + node + ", list = " + list);
        List<ServiceURL> oldList = (List<ServiceURL>) link(NODE_LIST_MAP_NAME, node, list);
        if (oldList != null) {
            System.out.println("** Overwrite occurred when writing to list map!");
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, List<ServiceURL>> getNodeListMap() {
        System.out.println("** Getting results from Byteman");
        ConcurrentHashMap<String, List<ServiceURL>> results = new ConcurrentHashMap<>();
        // write the entries from the linkMap into a map
        for (Object nameObject: linkNames(NODE_LIST_MAP_NAME)) {
            String node = (String) nameObject;
            List<ServiceURL> list = (List<ServiceURL>) linked(NODE_LIST_MAP_NAME, nameObject);
            results.put(node, list);
        }
        return results;
    }
}
