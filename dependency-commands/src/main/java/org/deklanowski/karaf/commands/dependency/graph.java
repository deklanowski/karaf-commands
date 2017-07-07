
package org.deklanowski.karaf.commands.dependency;

import com.google.common.graph.GraphBuilder;
import com.google.common.graph.Graphs;
import com.google.common.graph.MutableGraph;
import org.apache.karaf.features.Dependency;
import org.apache.karaf.features.Feature;
import org.apache.karaf.features.FeaturesService;
import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.deklanowski.karaf.commands.dependency.internal.TopologicalSort;

import java.util.Deque;
import java.util.List;

@Command(scope = "dependency", name = "graph", description = "Feature topology utilities")
@Service
public class graph implements Action {

    @Reference
    private FeaturesService featuresService;

    @Option(name = "-v", aliases = {"--version"}, description = "Feature version")
    private String version;

    @Argument(name = "name", description = "Feature name", required = true)
    private String name;


    private final MutableGraph<String> directedGraph = GraphBuilder.directed().allowsSelfLoops(false).build();


    @Override
    public Object execute() throws Exception {
        System.out.printf("Computing feature graph for '%s' '%s':\n",name,(version == null ? "0.0.0" : version));

        buildFeatureGraph(featuresService, name, version);

        TopologicalSort<String> sort = new TopologicalSort<>();

        Deque<String> result = sort.topSort(directedGraph);
        while (!result.isEmpty()) {
            String node = result.poll();
            System.out.printf("%-50s -> %s\n", node, directedGraph.successors(node));
        }

        System.out.printf("%d nodes, %d edges.\n", directedGraph.nodes().size(), directedGraph.edges().size());
        return null;

    }


    /**
     * For test support
     * @param featuresService the service
     */
    void setFeaturesService(FeaturesService featuresService) {
        this.featuresService = featuresService;
    }

    /**
     * test
     * @param version feature version
     */
    void setVersion(String version) {
        this.version = version;
    }

    /**
     * test
     * @param name feature name
     */
    void setName(String name) {
        this.name = name;
    }

    /**
     * Builds a dependency graph of features starting with the specified root feature.
     *
     * @param service          feature admin service
     * @param featureName    root feature
     * @param featureVersion root feature version
     * @throws Exception if something goes wrong
     */
    private void buildFeatureGraph(FeaturesService service, String featureName, String featureVersion) throws Exception {

        Feature[] features = service.getFeatures(featureName, featureVersion);

        for (Feature feature : features) {
            List<Dependency> dependencies = feature.getDependencies();
            for (Dependency dependency : dependencies) {
                String toName = dependency.getName();
                directedGraph.putEdge(feature.getName(), toName);
                if (Graphs.hasCycle(directedGraph)) {
                    System.out.printf("Circular dependency detected while adding edge '%s' -> '%s', aborting\n",featureName,toName);
                    return;
                }
                buildFeatureGraph(service, toName, dependency.getVersion());
            }
        }
    }
}
