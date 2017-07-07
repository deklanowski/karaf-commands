package org.deklanowski.karaf.features.internal;

import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 *
 * Adapted from original by Tushar Roy to use with {@link Graph}. See https://github.com/mission-peace/interview
 *
 * Given a directed acyclic graph, do a topological sort on this graph.
 *
 * Do DFS by keeping visited. Put the vertex which are completely explored into a stack.
 * Pop from stack to get sorted order.
 *
 * Space and time complexity is O(n).
 *
 * @author deklanowski
 * @since June 2017
 */
public class TopologicalSort<T> {

    public Deque<T> topSort(Graph<T> graph) {
        Deque<T> stack = new ArrayDeque<>();
        Set<T> visited = new HashSet<>();
        for (T vertex : graph.nodes()) {
            if (visited.contains(vertex)) {
                continue;
            }
            topSortUtil(graph,vertex,stack,visited);
        }
        return stack;
    }

    private void topSortUtil(Graph<T> graph, T vertex, Deque<T> stack,  Set<T> visited) {
        visited.add(vertex);
        for(T childVertex : graph.adjacentNodes(vertex)){
            if(visited.contains(childVertex)){
                continue;
            }
            topSortUtil(graph,childVertex,stack,visited);
        }
        stack.offerFirst(vertex);
    }



    public static void main(String args[]){

        MutableGraph<Integer> graph = GraphBuilder.directed().allowsSelfLoops(false).build();
        graph.putEdge(1, 3);
        graph.putEdge(1, 2);
        graph.putEdge(3, 4);
        graph.putEdge(5, 6);
        graph.putEdge(6, 3);
        graph.putEdge(3, 8);
        graph.putEdge(8, 11);
        
        TopologicalSort<Integer> sort = new TopologicalSort<Integer>();
        Deque<Integer> result = sort.topSort(graph);
        while(!result.isEmpty()){
            System.out.println(result.poll());
        }
    }
}
