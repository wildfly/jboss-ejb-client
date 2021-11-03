package org.jboss.ejb.client.test.byteman;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.byteman.rule.Rule;
import org.jboss.byteman.rule.helper.Helper;
import org.wildfly.discovery.ServiceURL;

public class MixedModeTestHelper extends Helper {

    private static final String NODE_LIST_MAP_NAME = "nodeListMap";

    public MixedModeTestHelper(Rule rule) {
        super(rule);
    }

    public void createNodeListMap() {
        createLinkMap(NODE_LIST_MAP_NAME);
    }

    public void addServiceURLCacheToMap(String node, List<ServiceURL> list) {
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
