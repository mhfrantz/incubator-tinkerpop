/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.tinkerpop.gremlin.structure.strategy;

import org.apache.tinkerpop.gremlin.process.Step;
import org.apache.tinkerpop.gremlin.process.Traverser;
import org.apache.tinkerpop.gremlin.process.graph.traversal.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.graph.traversal.step.filter.HasStep;
import org.apache.tinkerpop.gremlin.process.graph.traversal.DefaultGraphTraversal;
import org.apache.tinkerpop.gremlin.process.graph.util.HasContainer;
import org.apache.tinkerpop.gremlin.process.traversal.engine.StandardTraversalEngine;
import org.apache.tinkerpop.gremlin.process.traverser.util.DefaultTraverserGeneratorFactory;
import org.apache.tinkerpop.gremlin.process.traversal.util.TraversalHelper;
import org.apache.tinkerpop.gremlin.structure.Contains;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Element;
import org.apache.tinkerpop.gremlin.structure.Property;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.apache.tinkerpop.gremlin.structure.util.ElementHelper;
import org.apache.tinkerpop.gremlin.structure.util.StringFactory;
import org.apache.tinkerpop.gremlin.util.StreamFactory;
import org.apache.tinkerpop.gremlin.util.function.TriFunction;
import org.apache.tinkerpop.gremlin.util.iterator.IteratorUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * A {@link GraphStrategy} which enables support for logical graph partitioning where the Graph can be blinded to
 * different parts of the total {@link org.apache.tinkerpop.gremlin.structure.Graph}.  Note that the {@code partitionKey}
 * is hidden by this strategy.  Use the base {@link org.apache.tinkerpop.gremlin.structure.Graph} to access that.
 *
 * @author Stephen Mallette (http://stephen.genoprime.com)
 * @author Joshua Shinavier (http://fortytwo.net)
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class PartitionStrategy implements GraphStrategy {

    private String writePartition;
    private final String partitionKey;
    private final Set<String> readPartitions = new HashSet<>();

    private PartitionStrategy(final String partitionKey, final String partition) {
        this.writePartition = partition;
        this.addReadPartition(partition);
        this.partitionKey = partitionKey;
    }

    public String getWritePartition() {
        return this.writePartition;
    }

    public void setWritePartition(final String writePartition) {
        this.writePartition = writePartition;
    }

    public String getPartitionKey() {
        return this.partitionKey;
    }

    public Set<String> getReadPartitions() {
        return Collections.unmodifiableSet(this.readPartitions);
    }

    public void removeReadPartition(final String readPartition) {
        this.readPartitions.remove(readPartition);
    }

    public void addReadPartition(final String readPartition) {
        this.readPartitions.add(readPartition);
    }

    public void clearReadPartitions() {
        this.readPartitions.clear();
    }

    ///////////// old subgraph strategy methods
    @Override
    public UnaryOperator<BiFunction<Direction, String[], Iterator<Vertex>>> getVertexIteratorsVertexIteratorStrategy(final StrategyContext<StrategyVertex> ctx, final GraphStrategy composingStrategy) {
        return (f) -> (direction, labels) -> StreamFactory
                .stream(ctx.getCurrent().getBaseVertex().iterators().edgeIterator(direction, labels))
                .filter(this::testEdge)
                .map(edge -> otherVertex(direction, ctx.getCurrent(), edge)).iterator();
        // TODO: Note that we are not doing f.apply() like the other methods. Is this bad?
        // by not calling f.apply() to get the iterator, we're possibly bypassing strategy methods that
        // could have been sequenced
    }

    @Override
    public UnaryOperator<BiFunction<Direction, String[], Iterator<Edge>>> getVertexIteratorsEdgeIteratorStrategy(final StrategyContext<StrategyVertex> ctx, final GraphStrategy composingStrategy) {
        return (f) -> (direction, labels) -> IteratorUtils.filter(f.apply(direction, labels), this::testEdge);
    }

    @Override
    public UnaryOperator<Function<Direction, Iterator<Vertex>>> getEdgeIteratorsVertexIteratorStrategy(final StrategyContext<StrategyEdge> ctx, final GraphStrategy composingStrategy) {
        return (f) -> direction -> IteratorUtils.filter(f.apply(direction), this::testVertex);
    }

    @Override
    public UnaryOperator<Function<Object[], GraphTraversal<Vertex, Vertex>>> getGraphVStrategy(final StrategyContext<StrategyGraph> ctx, final GraphStrategy composingStrategy) {
        return (f) -> ids -> {
            final GraphTraversal<Vertex, Vertex> traversal = this.generateTraversal(ctx.getStrategyGraph().getBaseGraph());
            traversal.asAdmin().getStrategies().setTraverserGeneratorFactory(DefaultTraverserGeneratorFactory.instance());
            TraversalHelper.insertTraversal(0, f.apply(ids).has(this.partitionKey, Contains.within, getReadPartitions()).asAdmin(), traversal.asAdmin());
            return traversal.filter(vertex -> testVertex(vertex.get()));
        };
    }

    @Override
    public UnaryOperator<Function<Object[], GraphTraversal<Edge, Edge>>> getGraphEStrategy(final StrategyContext<StrategyGraph> ctx, final GraphStrategy composingStrategy) {
        return (f) -> ids -> {
            final GraphTraversal<Edge, Edge> traversal = this.generateTraversal(ctx.getStrategyGraph().getBaseGraph());
            traversal.asAdmin().getStrategies().setTraverserGeneratorFactory(DefaultTraverserGeneratorFactory.instance());
            TraversalHelper.insertTraversal(0, f.apply(ids).has(this.partitionKey, Contains.within, getReadPartitions()).asAdmin(), traversal.asAdmin());
            return traversal.filter(edge -> testEdge(edge.get()));
        };
    }

    private boolean testElement(final Element element) {
        final Property<String> property = element instanceof StrategyElement ?
                ((StrategyElement) element).getBaseElement().property(this.partitionKey) :
                element.property(this.partitionKey);
        return property.isPresent() && this.readPartitions.contains(property.value());
    }

    private boolean testVertex(final Vertex vertex) {
        return testElement(vertex);
    }

    private boolean testEdge(final Edge edge) {
        // the edge must pass the edge predicate, and both of its incident vertices must also pass the vertex predicate
        // inV() and/or outV() will be empty if they do not.  it is sometimes the case that an edge is unwrapped
        // in which case it may not be filtered.  in such cases, the vertices on such edges should be tested.
        return testElement(edge)
                && (edge instanceof StrategyWrapped ? edge.iterators().vertexIterator(Direction.IN).hasNext() && edge.iterators().vertexIterator(Direction.OUT).hasNext()
                : testVertex(edge.iterators().vertexIterator(Direction.IN).next()) && testVertex(edge.iterators().vertexIterator(Direction.OUT).next()));
    }

    private static final Vertex otherVertex(final Direction direction, final Vertex start, final Edge edge) {
        if (direction.equals(Direction.BOTH)) {
            final Vertex inVertex = edge.iterators().vertexIterator(Direction.IN).next();
            return ElementHelper.areEqual(start, inVertex) ?
                    edge.iterators().vertexIterator(Direction.OUT).next() :
                    inVertex;
        } else {
            return edge.iterators().vertexIterator(direction.opposite()).next();
        }
    }

    private final <S, E> GraphTraversal<S, E> generateTraversal(final Object emanatingObject) {
        return new DefaultGraphTraversal<S, E>(emanatingObject) {
            @Override
            public GraphTraversal<S, Vertex> to(final Direction direction, final String... edgeLabels) {
                return direction.equals(Direction.BOTH) ?
                        this.toE(direction, edgeLabels).otherV() :
                        this.toE(direction, edgeLabels).toV(direction.opposite());
            }

            @Override
            public GraphTraversal<S, Edge> toE(final Direction direction, final String... edgeLabels) {
                return super.toE(direction, edgeLabels).has(getPartitionKey(), Contains.within, getReadPartitions()).filter(edge -> testEdge(edge.get()));
            }

            @Override
            public GraphTraversal<S, Vertex> toV(final Direction direction) {
                return super.toV(direction).has(getPartitionKey(), Contains.within, getReadPartitions());
            }

            @Override
            public GraphTraversal<S, Vertex> otherV() {
                return super.otherV().has(getPartitionKey(), Contains.within, getReadPartitions());
            }

            @Override
            public GraphTraversal<S, E> has(final String key, final BiPredicate predicate, final Object value) {
                final HasContainer hasContainer = new HasContainer(key, predicate, value);
                final HasStep<Element> hasStep = new HasStep(this, hasContainer) {
                    @Override
                    protected boolean filter(final Traverser.Admin traverser) {
                        return hasContainer.test(((StrategyElement) traverser.get()).getBaseElement());
                    }
                };
                return this.asAdmin().addStep((Step) hasStep);
            }
        };
    }

    ///////////////////////////

    @Override
    public <V> UnaryOperator<Function<String[], Iterator<VertexProperty<V>>>> getVertexIteratorsPropertyIteratorStrategy(final StrategyContext<StrategyVertex> ctx, final GraphStrategy composingStrategy) {
        return (f) -> (keys) -> IteratorUtils.filter(f.apply(keys), property -> {
            return !this.partitionKey.equals(property.key());
        });
    }

    @Override
    public <V> UnaryOperator<Function<String[], Iterator<V>>> getVertexIteratorsValueIteratorStrategy(final StrategyContext<StrategyVertex> ctx, final GraphStrategy composingStrategy) {
        return (f) -> (keys) -> IteratorUtils.map(ctx.getCurrent().iterators().<V>propertyIterator(keys), vertexProperty -> vertexProperty.value());
    }

    @Override
    public UnaryOperator<Supplier<Set<String>>> getVertexKeysStrategy(final StrategyContext<StrategyVertex> ctx, final GraphStrategy composingStrategy) {
        return (f) -> () -> IteratorUtils.fill(IteratorUtils.filter(f.get().iterator(), key -> {
            return !this.partitionKey.equals(key);
        }), new HashSet<>());
    }

    @Override
    public <V> UnaryOperator<Function<String, VertexProperty<V>>> getVertexGetPropertyStrategy(final StrategyContext<StrategyVertex> ctx, final GraphStrategy composingStrategy) {
        return (f) -> k -> {
            return k.equals(this.partitionKey) ? VertexProperty.<V>empty() : f.apply(k);
        };
    }

    @Override
    public <V> UnaryOperator<Function<String, Property<V>>> getEdgeGetPropertyStrategy(StrategyContext<StrategyEdge> ctx, GraphStrategy composingStrategy) {
        return (f) -> k -> {
            return k.equals(this.partitionKey) ? Property.<V>empty() : f.apply(k);
        };
    }

    @Override
    public <V> UnaryOperator<Function<String[], Iterator<Property<V>>>> getEdgeIteratorsPropertyIteratorStrategy(final StrategyContext<StrategyEdge> ctx, final GraphStrategy composingStrategy) {
        return (f) -> (keys) -> IteratorUtils.filter(f.apply(keys), property -> {
            return !this.partitionKey.equals(property.key());
        });
    }

    @Override
    public <V> UnaryOperator<Function<String[], Iterator<V>>> getEdgeIteratorsValueIteratorStrategy(final StrategyContext<StrategyEdge> ctx, final GraphStrategy composingStrategy) {
        return (f) -> (keys) -> IteratorUtils.map(ctx.getCurrent().iterators().<V>propertyIterator(keys), property -> property.value());
    }

    @Override
    public UnaryOperator<Supplier<Set<String>>> getEdgeKeysStrategy(final StrategyContext<StrategyEdge> ctx, final GraphStrategy composingStrategy) {
        return (f) -> () -> IteratorUtils.fill(IteratorUtils.filter(f.get().iterator(), key -> {
            return !this.partitionKey.equals(key);
        }), new HashSet<>());
    }

    @Override
    public UnaryOperator<Function<Object[], Vertex>> getAddVertexStrategy(final StrategyContext<StrategyGraph> ctx, final GraphStrategy composingStrategy) {
        return (f) -> (keyValues) -> f.apply(this.addKeyValues(keyValues));
    }

    @Override
    public UnaryOperator<TriFunction<String, Vertex, Object[], Edge>> getAddEdgeStrategy(final StrategyContext<StrategyVertex> ctx, final GraphStrategy composingStrategy) {
        return (f) -> (label, v, keyValues) -> f.apply(label, v, this.addKeyValues(keyValues));
    }

    private final Object[] addKeyValues(final Object[] keyValues) {
        final Object[] keyValuesExtended = Arrays.copyOf(keyValues, keyValues.length + 2);
        keyValuesExtended[keyValues.length] = this.partitionKey;
        keyValuesExtended[keyValues.length + 1] = this.writePartition;
        return keyValuesExtended;
    }

    @Override
    public String toString() {
        return StringFactory.graphStrategyString(this);
    }

    public static Builder build() {
        return new Builder();
    }

    public static class Builder {
        private String startPartition = "default";
        private String partitionKey = "_partition";

        private Builder() {
        }

        /**
         * The initial partition to filter by. If this value is not set, it will be defaulted to "default".
         */
        public Builder startPartition(final String startPartition) {
            if (null == startPartition) throw new IllegalArgumentException("The startPartition cannot be null");
            this.startPartition = startPartition;
            return this;
        }

        /**
         * The name of the partition key.  If this is not set, then the value is defaulted to "_partition".
         */
        public Builder partitionKey(final String partitionKey) {
            if (null == partitionKey) throw new IllegalArgumentException("The partitionKey cannot be null");
            this.partitionKey = partitionKey;
            return this;
        }

        public PartitionStrategy create() {
            return new PartitionStrategy(partitionKey, startPartition);
        }
    }
}