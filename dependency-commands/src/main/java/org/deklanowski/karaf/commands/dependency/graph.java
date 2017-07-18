
package org.deklanowski.karaf.commands.dependency;

import com.google.common.base.MoreObjects;
import com.google.common.graph.*;
import org.apache.karaf.features.Dependency;
import org.apache.karaf.features.Feature;
import org.apache.karaf.features.FeaturesService;
import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.deklanowski.karaf.commands.dependency.internal.DependencySorter;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@Command(scope = "dependency", name = "graph", description = "Feature topology utilities")
@Service
public class graph implements Action {

    @Reference
    private FeaturesService featuresService;

    @Argument(name = "name", description = "Feature name", required = true)
    private String name;

    @Argument(index = 1, name = "version", description = "The version of the feature")
    private String version;

    private final MutableGraph<Node> featureGraph = GraphBuilder.directed().allowsSelfLoops(false).build();

    private final MutableGraph<String> repoGraph = GraphBuilder.directed().allowsSelfLoops(true).build();


    @Override
    public Object execute() throws Exception {
        System.out.printf("Computing feature graph for '%s' '%s':\n", name, (version == null ? "0.0.0" : version));

        buildFeatureGraph(featuresService, name, version);


        final DependencySorter<String> dependencySorter = new DependencySorter<>();


        final List<String> list = dependencySorter.sort(repoGraph);


        System.out.println("\n\nTopological ordering\n\n");
        for (String s : list) {
            System.out.println(s);
        }


        for (Map.Entry<Integer,Set<String>> entry : dependencySorter.getLevelMap().entrySet()) {
            System.out.printf("%d -> %s\n",entry.getKey(),entry.getValue());
        }


//        generateDotOutput(repoGraph);

        return null;

    }



    private <T> void generateDotOutput(Graph<T> graph) {
        System.out.println("digraph G {");
        for (EndpointPair<T> pair : graph.edges()) {
            System.out.printf("\t\"%s\" -> \"%s\";\n",pair.source(), pair.target());
        }
        System.out.println("}");
    }

    /**
     * @param graph: a DAG
     * @return topological ordering starting with most dependent node.
     */
    private <T> List<T> topSort(MutableGraph<T> graph) {

        List<T> result = new ArrayList<>();

        Queue<T> queue = new LinkedList<>();

        Map<T, Integer> nodeDegree = new HashMap<>();

        for (T node : graph.nodes()) {
            int degree = graph.inDegree(node);
            nodeDegree.put(node, degree);
            if (degree == 0) {
                queue.offer(node);
                result.add(node);
            }
        }

        while (!queue.isEmpty()) {

            System.out.printf("Queue: %s\n\n",queue);

            T node = queue.poll();

            Set<T> successors = graph.successors(node);

          //  System.out.printf("node:%s adjacent nodes:%s\n",node,successors);

            for (T successor : successors) {

                int inDegree = nodeDegree.get(successor);

            //    System.out.printf("node:%s inDegree:%d\n",successor,inDegree);

                nodeDegree.put(successor, (inDegree == 0 ? 0 : inDegree-1));

                if ((inDegree - 1) == 0) {
                    queue.offer(successor);
                    result.add(successor);
                }
            }
        }

        return result;
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
     * @throws Exception if something goes wrong
     */
    private void buildFeatureGraph(FeaturesService service, String featureName, String featureVersion) throws Exception {

        Feature[] features = service.getFeatures(featureName, featureVersion);

        for (Feature feature : features) {
            List<Dependency> dependencies = feature.getDependencies();
            for (Dependency dependency : dependencies) {
                String fromRepo = feature.getRepositoryUrl();
                Node from = new Node(featureName, fromRepo);

                String toName = dependency.getName();
                Feature df = service.getFeature(toName, dependency.getVersion());

                if (df == null) {
                    throw new IllegalStateException(String.format("Could not resolve feature for %s",dependency));
                }

                String toRepo = df.getRepositoryUrl();

                Node to = new Node(toName, toRepo);

                featureGraph.putEdge(from, to);
                if (Graphs.hasCycle(featureGraph)) {
                    System.out.printf("Circular dependency detected while adding edge '%s' -> '%s', aborting\n", featureName, toName);
                    return;
                }

                if (!fromRepo.equals(toRepo)) {
                    repoGraph.putEdge(fromRepo, toRepo);
                    if (Graphs.hasCycle(repoGraph)) {
                        System.out.printf("Circular dependency detected while adding edge '%s' -> '%s', aborting\n", featureName, toName);
                        return;
                    }
                }


                buildFeatureGraph(service, toName, dependency.getVersion());
            }
        }
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
