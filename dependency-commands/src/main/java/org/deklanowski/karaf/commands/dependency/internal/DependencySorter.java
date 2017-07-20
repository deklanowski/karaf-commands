package org.deklanowski.karaf.commands.dependency.internal;

import com.google.common.base.Preconditions;
import com.google.common.graph.*;
import com.google.common.graph.Graph;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.regex.Pattern.compile;

/**
 * This uses a BFS topological sort with graph depth tracking to sort dependencies into
 * levels. Levels identify those sets of dependencies that can be independently handled.
 * <p>
 * The BFS algorithm is according to Kahn (1962)
 * <p>
 * The slick solution to graph depth/level tracking courtesy of
 * https://stackoverflow.com/questions/31247634/how-to-keep-track-of-depth-in-breadth-first-search/31248992#31248992
 * <p>
 * I use {@link Graph}, the input graph instance must be a DAG.
 *
 * @param <T>
 * @author deklanowski
 * @since June 2017
 */
public class DependencySorter<T> {

    /**
     * Maps levels to nodes at that level. Higher level dependencies
     * must be addressed first.
     */
    private Map<Integer, Set<T>> levelMap = new HashMap<>();

    /**
     * Chattiness level
     */
    private final boolean verbose;


    /** Patterns to determine nodes which should be placed in the bottom-most dependency level */
    private final Pattern excludePattern;


    public DependencySorter() {
        this.verbose = false;
        this.excludePattern = compile("org\\.apache|org\\.code-house|org\\.deklanowski");
    }

    public DependencySorter(String excludePattern, boolean verbose) {
        excludePattern = Preconditions.checkNotNull(excludePattern, "exclude pattern should be a non-empty string");
        this.excludePattern = compile(excludePattern);
        this.verbose = verbose;
    }

    /**
     * @param graph dependency graph, must be a DAG
     * @return topologically sorted nodes according to BFS
     */
    public List<T> sort(Graph<T> graph) {

        Preconditions.checkArgument(graph.isDirected() && !Graphs.hasCycle(graph), "Input graph must be directed and acyclic.");

        // clear state
        this.levelMap = new HashMap<>();

        // sorted output
        List<T> result = new ArrayList<>();

        Queue<T> queue = new LinkedList<>();

        // We don't mutate the graph itself so we
        // track visitations and attendant changes to
        // in-degree in a separate map.
        //
        Map<T, Integer> nodeDegree = new HashMap<>();

        // Top of the dependency tree
        int level = 1;

        // Find starter nodes
        for (T node : graph.nodes()) {
            int inDegree = graph.inDegree(node);
            nodeDegree.put(node, inDegree);
            if (inDegree == 0) {
                queue.add(node);
                result.add(node);
            }
        }

        queue.add(null); // this marks end of level 1, which contains all starter nodes

        if (verbose) {
            System.out.printf("Level %d queue=%s\n", level, queue);
        }

        addToLevelMap(level, queue);

        while (!queue.isEmpty()) {
            T node = queue.poll();

            // Check whether we need to increment the level
            if (node == null) {
                queue.add(null);
                if (queue.peek() == null) {
                    if (verbose) {
                        System.out.println("Two consecutive nulls encountered, all nodes visited.");
                    }
                    break;
                } else {
                    level++;
                    if (verbose) {
                        System.out.printf("Level %d queue=%s\n", level, queue);
                    }
                    addToLevelMap(level, queue);
                    continue;
                }
            }


            Set<T> successors = graph.successors(node);

            if (verbose) {
                System.out.printf("node:%s successors :%s\n", node, successors);
            }

            for (T successor : successors) {

                int degree = nodeDegree.get(successor);

                nodeDegree.put(successor, (degree == 0 ? 0 : degree - 1));

                if (shouldAddToQueue(degree)) {
                    if (verbose) {
                        System.out.printf("Adding %s to queue and output\n", successor);
                    }
                    queue.offer(successor);
                    result.add(successor);
                }
            }
        }


        // find all nodes with no successors, these can be moved to the bottom-most level
        // regardless of what their original computed level is.
        //
        Set<T> bottomFeeders = new HashSet<>();

        for (Map.Entry<Integer, Set<T>> entry : levelMap.entrySet()) {
            Iterator<T> nodeIter = entry.getValue().iterator();
            while (nodeIter.hasNext()) {
                T node = nodeIter.next();
                if (isBottomFeeder(graph, node)) {
                    bottomFeeders.add(node);
                    nodeIter.remove();
                }
            }
        }


        if (verbose) {
            System.out.println("Bottom feeders: " + bottomFeeders);
        }

        bottomFeeders.addAll(levelMap.get(levelMap.size()));
        levelMap.put(levelMap.size(), bottomFeeders);

        return result;
    }


    /**
     * Nodes which match the following criteria are bottom feeders:
     * 1. no children
     * 2. node name matches the exclude pattern
     * 3. only have children whose names match the exclude pattern
     *
     * {@link #excludePattern}
     * @param graph the graph
     * @param node node to test
     * @return true if bottom feeders
     */
    private boolean isBottomFeeder(Graph<T> graph, T node) {
        Set<T> successors = graph.successors(node);

        if (successors.isEmpty()) {
            return true;
        } else if (excludePattern.matcher(node.toString()).find()) {
            return true;
        } else for (T successor : successors) {
            if (!excludePattern.matcher(successor.toString()).find()) {
                return false;
            }
        }

        return true;
    }

    /**
     * Add nodes to level map, remove marker nulls.
     *
     * @param level graph depth
     * @param queue current node queue
     */
    private void addToLevelMap(int level, Queue<T> queue) {
        Set<T> nodes = new HashSet<>(queue);
        nodes.remove(null); // remove our marker nulls
        levelMap.put(level, nodes);
    }


    /**
     * @param inDegree the degree of the node being processed
     * @return true if decrementing the degree gives an in-degree of 0
     */
    private boolean shouldAddToQueue(int inDegree) {
        return (inDegree - 1) == 0;
    }


    /**
     * Print out node levels
     *
     * @param <T> node type
     */
    public <T> void displayDependencyLevels() {
        this.levelMap.forEach((k, v) -> System.out.println(k + " -> " + v));
    }


    /**
     * Simple DOT output for visualisation
     *
     * @param <T>         node type
     * @param graph       the graph instance
     * @param nodePattern a simple filter to identify certain nodes for styling actions
     */
    @SuppressWarnings("unchecked")
    public <T> void generateDotOutput(Graph<T> graph, String nodePattern) {

        System.out.println("digraph G {\ngraph [style=\"rounded, filled\", fontsize=10];\nrankdir=LR;\nconcentrate=true;\nnode [shape=box, style=\"rounded,filled\"]\n\n");


        Pattern nodePatternRegex = compile(nodePattern);


        Set<T> nodes = (Set<T>) levelMap.values()
                .stream()
                .flatMap(Set::stream)
                .collect(Collectors.toSet());


        nodes
                .stream()
                .filter(node -> nodePatternRegex.matcher(node.toString()).find())
                .forEach(node -> System.out.printf("\"%s\" [ color=khaki3 ];\n", node));


        System.out.println();


        // group levels in a subgraph
        for (Integer level : this.levelMap.keySet()) {
            System.out.printf("\tsubgraph cluster_%d { label=\"Level %d\"; shape=box; style=rounded; node [style=rounded];\n", level, level);

            levelMap.get(level)
                    .forEach(node -> System.out.printf("\"%s\" ", node));

            System.out.println("}\n");
        }

        graph.edges()
                .forEach(pair -> System.out.printf("\t\"%s\" -> \"%s\";\n", pair.source(), pair.target()));

        System.out.println("}");
    }


    public static void main(String[] args) {
        final MutableGraph<String> g = GraphBuilder.directed().allowsSelfLoops(false).build();


        g.putEdge("com.example.A", "com.example.B");
        g.putEdge("com.example.A", "com.example.C");
        g.putEdge("com.example.A", "org.code-house");

        g.putEdge("com.example.B", "com.example.D");
        g.putEdge("com.example.B", "com.example.E");

        g.putEdge("com.example.C", "com.example.F");
        g.putEdge("com.example.C", "com.example.G");
        g.putEdge("com.example.F", "org.apache.B");

        g.putEdge("com.example.C", "org.deklanowski.A");
        g.putEdge("com.example.C", "org.apache.B");

        g.putEdge("org.apache.B", "org.apache.C");


        DependencySorter<String> dependencySorter = new DependencySorter<>();
        List<String> list = dependencySorter.sort(g);

        System.out.println("Topologically sorted, for dependency build order reverse the list");
        System.out.println(list);
        System.out.println();


        dependencySorter.generateDotOutput(g, "com.example|org.deklan");


        System.out.println("\nLevel Map:");

        dependencySorter.displayDependencyLevels();
    }


}

