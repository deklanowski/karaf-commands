
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

    @Option(name = "--feature", description = "Select feature graph (default is repository graph)")
    private boolean feature;

    @Option(name = "--verbose", description = "Blah blah blah")
    private boolean verbose;

    @Option(name = "--nodePattern", description = "Simple string matcher for node names for the application of styling actions in DOT output")
    private String nodePattern;


    private final MutableGraph<Node> featureGraph = GraphBuilder.directed().allowsSelfLoops(false).build();

    private final MutableGraph<String> repoGraph = GraphBuilder.directed().allowsSelfLoops(true).build();


    @SuppressWarnings("unused")
    @Override
    public Object execute() throws Exception {

        if (verbose) {
            System.out.printf("Computing feature graph for '%s' '%s':\n", name, (version == null ? "0.0.0" : version));
        }

        buildFeatureGraph(featuresService, name, version);

        if (feature) {
            final DependencySorter<Node> sorter = new DependencySorter<>(verbose);
            final List<Node> list = sorter.sort(featureGraph);

            if (dot) {
                sorter.generateDotOutput(featureGraph, nodePattern );
            } else {
                sorter.displayDependencyLevels();
            }

        } else {
            final DependencySorter<String> sorter = new DependencySorter<>(verbose);
            final List<String> list = sorter.sort(repoGraph);

            if (dot) {
                sorter.generateDotOutput(repoGraph, nodePattern );
            } else {
                sorter.displayDependencyLevels();
            }
        }

        return null;
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
                    throw new IllegalStateException(String.format("Repository URL for source %s is empty", feature));
                }

                Node from = new Node(featureName, fromRepo);

                String toName = dependency.getName();

                Feature df = service.getFeature(toName, dependency.getVersion());

                if (df == null) {
                    throw new IllegalStateException(String.format("Could not resolve feature for %s, you might need to update your local maven repository", dependency));
                }

                String toRepo = df.getRepositoryUrl();

                if (StringUtils.isBlank(fromRepo)) {
                    throw new IllegalStateException(String.format("Repository URL for target %s is empty", feature));
                }


                Node to = new Node(toName, toRepo);

                featureGraph.putEdge(from, to);
                if (verbose) {
                    System.out.printf("Adding edge to feature graph %s -> %s\n", from, to);
                }

                if (Graphs.hasCycle(featureGraph)) {
                    throw new IllegalStateException(String.format("Circular dependency detected while adding edge '%s' -> '%s', aborting\n", featureName, toName));
                }

                if (!fromRepo.equals(toRepo)) {

                    String nodeU = compactRepoUrl(fromRepo);
                    String nodeV = compactRepoUrl(toRepo);

                    if (verbose) {
                        System.out.printf("Adding edge to feature repository graph %s -> %s\n", nodeU, nodeV);
                    }

                    repoGraph.putEdge(nodeU, nodeV);
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
     *
     * @param repoUrl feature repository URL
     * @return shortened form
     */
    private String compactRepoUrl(String repoUrl) {
        return repoUrl.replace("mvn:", "").replace("/xml/features", "");
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
