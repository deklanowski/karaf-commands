
package org.deklanowski.karaf.commands.dependency;

import com.google.common.base.MoreObjects;
import com.google.common.graph.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.karaf.features.Dependency;
import org.apache.karaf.features.Feature;
import org.apache.karaf.features.FeaturesService;
import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.deklanowski.karaf.commands.dependency.internal.DependencySorter;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

@Command(scope = "dependency", name = "graph", description = "Generates a dependency levelized output of feature repositories for the specified feature")
@Service
public class graph implements Action {

    @Reference
    private FeaturesService featuresService;

    @Argument(name = "name", description = "Feature name", required = true)
    private String name;

    @Argument(index = 1, name = "version", description = "The version of the feature")
    private String version;

    @Option(name = "--dot", description = "Just generate simple DOT output of feature repository graph for GraphViz")
    private boolean dot;

    @Option(name = "--verbose", description = "Blah blah blah")
    private boolean verbose;

    private final MutableGraph<Node> featureGraph = GraphBuilder.directed().allowsSelfLoops(false).build();

    private final MutableGraph<String> repoGraph = GraphBuilder.directed().allowsSelfLoops(true).build();


    @SuppressWarnings("unused")
    @Override
    public Object execute() throws Exception {
        System.out.printf("Computing feature graph for '%s' '%s':\n", name, (version == null ? "0.0.0" : version));

        buildFeatureGraph(featuresService, name, version);


        final DependencySorter<String> dependencySorter = new DependencySorter<>(verbose);

        final List<String> list = dependencySorter.sort(repoGraph);

        Map<Integer, Set<String>> levelMap = dependencySorter.getLevelMap();


        if (dot) {
            generateDotOutput(repoGraph, levelMap);
            return null;
        }


        for (Map.Entry<Integer,Set<String>> entry : levelMap.entrySet()) {
            System.out.printf("%d -> %s\n",entry.getKey(),entry.getValue());
        }



        return null;

    }


    /**
     * Simple DOT output for visualisation
     * @param <T> node type
     * @param graph the graph instance
     * @param levelMap
     */
    private <T> void generateDotOutput(Graph<T> graph, Map<Integer,Set<String>> levelMap) {
        System.out.println("digraph G {\ngraph [style=\"rounded, filled\", fontsize=10];\nrankdir=LR;\nnode [shape=box, style=\"rounded,filled\"]\n\n");

        for (Set<String> nodes : levelMap.values()) {
            for (String node : nodes) {
                if (node.contains("com.ipfli")) {
                    System.out.printf("\"%s\" [ color=darkseagreen ];\n", node);
                }
            }
        }

        System.out.println();

        // group levels in a subgraph
        for (Map.Entry<Integer,Set<String>> entry : levelMap.entrySet()) {
            System.out.printf("\tsubgraph cluster_%d { label=\"Level %d\"; shape=box; style=rounded; node [style=rounded];\n",entry.getKey(),entry.getKey());
            int i=0;
            Set<String> nodes = entry.getValue();
            for (String node : nodes) {
                System.out.printf("\"%s\"",node);
                i++;
                if (i < nodes.size()) {
                    System.out.print(",");
                }
            }
            System.out.println("}\n");
        }

        for (EndpointPair<T> pair : graph.edges()) {
            System.out.printf("\t\"%s\" -> \"%s\";\n",pair.source(), pair.target());
        }
        System.out.println("}");
    }


    /**
     * For test support
     *
     * @param featuresService the service
     */
    void setFeaturesService(FeaturesService featuresService) {
        this.featuresService = featuresService;
    }

    /**
     * test
     *
     * @param version feature version
     */
    void setVersion(String version) {
        this.version = version;
    }

    /**
     * test
     *
     * @param name feature name
     */
    void setName(String name) {
        this.name = name;
    }

    private static final AtomicLong id = new AtomicLong(0);

    /**
     * Builds a dependency graph of features starting with the specified root feature.
     *
     * @param service        feature admin service
     * @param featureName    root feature
     * @param featureVersion root feature version
     * @throws Exception if cycles are detected while building the graph(s)
     */
    private void buildFeatureGraph(FeaturesService service, String featureName, String featureVersion) throws Exception {

        Feature[] features = service.getFeatures(featureName, featureVersion);

        for (Feature feature : features) {
            List<Dependency> dependencies = feature.getDependencies();

            for (Dependency dependency : dependencies) {

                String fromRepo = feature.getRepositoryUrl();

                if (StringUtils.isBlank(fromRepo)) {
                    throw new IllegalStateException(String.format("Repository URL for source %s is empty",feature));
                }

                Node from = new Node(featureName, fromRepo);

                String toName = dependency.getName();

                Feature df = service.getFeature(toName, dependency.getVersion());

                if (df == null) {
                    throw new IllegalStateException(String.format("Could not resolve feature for %s, you might need to update your local maven repository",dependency));
                }

                String toRepo = df.getRepositoryUrl();

                if (StringUtils.isBlank(fromRepo)) {
                    throw new IllegalStateException(String.format("Repository URL for target %s is empty",feature));
                }


                Node to = new Node(toName, toRepo);

                featureGraph.putEdge(from, to);
                if (Graphs.hasCycle(featureGraph)) {
                    throw new IllegalStateException(String.format("Circular dependency detected while adding edge '%s' -> '%s', aborting\n", featureName, toName));
                }

                if (!fromRepo.equals(toRepo)) {
                    repoGraph.putEdge(compactRepoUrl(fromRepo), compactRepoUrl(toRepo));
                    if (Graphs.hasCycle(repoGraph)) {
                        throw new IllegalStateException(String.format("Circular dependency detected while adding edge '%s' -> '%s', aborting\n", fromRepo, toRepo));
                    }
                }

                buildFeatureGraph(service, toName, dependency.getVersion());
            }
        }
    }


    /**
     * Strip out unnecessary text for better readability
     * @param repoUrl feature repository URL
     * @return shortened form
     */
    private String compactRepoUrl(String repoUrl) {
        return repoUrl.replace("mvn:","").replace("/xml/features","");
    }


    private static class Node {

        private final String name;
        private final String repo;

        Node(String name, String repo) {
            this.name = name;
            this.repo = repo;
        }

        public String getName() {
            return name;
        }

        public String getRepo() {
            return repo;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Node node = (Node) o;

            return name != null ? name.equals(node.name) : node.name == null;
        }

        @Override
        public int hashCode() {
            return name != null ? name.hashCode() : 0;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("name", name)
                    .add("repo", repo)
                    .toString();
        }
    }
}
