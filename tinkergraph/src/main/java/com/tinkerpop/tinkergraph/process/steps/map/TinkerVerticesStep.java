package com.tinkerpop.tinkergraph.process.steps.map;

import com.tinkerpop.gremlin.process.Traversal;
import com.tinkerpop.gremlin.process.steps.map.VerticesStep;
import com.tinkerpop.gremlin.process.util.HolderIterator;
import com.tinkerpop.gremlin.structure.Compare;
import com.tinkerpop.gremlin.structure.Element;
import com.tinkerpop.gremlin.structure.Vertex;
import com.tinkerpop.gremlin.structure.query.util.HasContainer;
import com.tinkerpop.tinkergraph.TinkerGraph;
import com.tinkerpop.tinkergraph.TinkerHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class TinkerVerticesStep extends VerticesStep {

    private final TinkerGraph graph;
    public final List<HasContainer> hasContainers = new ArrayList<>();

    public TinkerVerticesStep(final Traversal traversal, final TinkerGraph graph) {
        super(traversal);
        this.graph = graph;
        this.generateHolderIterator(false);
    }

    public void generateHolderIterator(final boolean trackPaths) {
        this.starts.clear();
        if (trackPaths) {
            this.starts.add(new HolderIterator(this, this.vertices().iterator()));
        } else {
            this.starts.add(new HolderIterator(this.vertices().iterator()));
        }
    }

    private Iterable<Vertex> vertices() {
        stringifyIds();
        final HasContainer indexedContainer = getIndexKey(Vertex.class);
        return ((null == indexedContainer) ?
                TinkerHelper.getVertices(this.graph).parallelStream() :
                TinkerHelper.getVertexIndex(this.graph, indexedContainer.key, indexedContainer.value).parallelStream())
                .filter(v -> HasContainer.testAll((Vertex) v, this.hasContainers))
                .collect(Collectors.<Vertex>toList());
    }

    private HasContainer getIndexKey(final Class<? extends Element> indexedClass) {
        final Set<String> indexedKeys = this.graph.getIndexedKeys(indexedClass);
        return this.hasContainers.stream()
                .filter(c -> indexedKeys.contains(c.key) && c.predicate.equals(Compare.EQUAL))
                .findFirst()
                .orElseGet(() -> null);
    }

    private void stringifyIds() {
        this.hasContainers.stream().filter(h -> h.key.equals(Element.ID)).forEach(h -> {
            final List<String> ids = new ArrayList<>();
            ((List<Object>) h.value).forEach(v -> ids.add(v.toString()));
            h.value = ids;
        });
    }

    // TODO HasContainers with Element.ID (just get the vertices)

}