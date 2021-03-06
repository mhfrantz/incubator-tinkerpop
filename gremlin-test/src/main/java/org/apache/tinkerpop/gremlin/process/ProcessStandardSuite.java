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
package org.apache.tinkerpop.gremlin.process;

import org.apache.tinkerpop.gremlin.AbstractGremlinSuite;
import org.apache.tinkerpop.gremlin.AbstractGremlinTest;
import org.apache.tinkerpop.gremlin.process.graph.traversal.step.branch.*;
import org.apache.tinkerpop.gremlin.process.graph.traversal.step.filter.*;
import org.apache.tinkerpop.gremlin.process.graph.traversal.step.map.*;
import org.apache.tinkerpop.gremlin.process.graph.traversal.step.sideEffect.*;
import org.apache.tinkerpop.gremlin.process.graph.traversal.step.util.TraversalSideEffectsTest;
import org.apache.tinkerpop.gremlin.process.graph.traversal.strategy.TraversalVerificationStrategyTest;
import org.apache.tinkerpop.gremlin.process.traversal.CoreTraversalTest;
import org.apache.tinkerpop.gremlin.process.traversal.engine.StandardTraversalEngine;
import org.apache.tinkerpop.gremlin.process.util.PathTest;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The {@code ProcessStandardSuite} is a JUnit test runner that executes the Gremlin Test Suite over a
 * {@link org.apache.tinkerpop.gremlin.structure.Graph} implementation.  This specialized test suite and runner is for use
 * by Gremlin implementers to test their {@link org.apache.tinkerpop.gremlin.structure.Graph} implementations.  The
 * {@code ProcessStandardSuite} ensures consistency and validity of the implementations that they test.
 * <p/>
 * To use the {@code ProcessStandardSuite} define a class in a test module.  Simple naming would expect the name of the
 * implementation followed by "ProcessStandardSuite".  This class should be annotated as follows (note that the "Suite"
 * implements {@link org.apache.tinkerpop.gremlin.GraphProvider} as a convenience only. It could be implemented in a
 * separate class file):
 * <p/>
 * <code>
 *
 * @author Stephen Mallette (http://stephen.genoprime.com)
 * @RunWith(ProcessStandardSuite.class)
 * @ProcessStandardSuite.GraphProviderClass(TinkerGraphProcessStandardTest.class) public class TinkerGraphProcessStandardTest implements GraphProvider {
 * }
 * </code>
 * <p/>
 * Implementing {@link org.apache.tinkerpop.gremlin.GraphProvider} provides a way for the {@code ProcessStandardSuite} to
 * instantiate {@link org.apache.tinkerpop.gremlin.structure.Graph} instances from the implementation being tested to inject
 * into tests in the suite.  The ProcessStandardSuite will utilized
 * {@link org.apache.tinkerpop.gremlin.structure.Graph.Features} defined in the suite to determine which tests will be executed.
 * <br/>
 */
public class ProcessStandardSuite extends AbstractGremlinSuite {

    /**
     * This list of tests in the suite that will be executed.  Gremlin developers should add to this list
     * as needed to enforce tests upon implementations.
     */
    private static final Class<?>[] allTests = new Class<?>[]{
            // branch
            BranchTest.Traversals.class,
            ChooseTest.Traversals.class,
            LocalTest.Traversals.class,
            RepeatTest.Traversals.class,
            UnionTest.Traversals.class,

            // filter
            AndTest.Traversals.class,
            CoinTest.Traversals.class,
            CyclicPathTest.Traversals.class,
            DedupTest.Traversals.class,
            ExceptTest.StandardTest.class,
            FilterTest.Traversals.class,
            HasNotTest.Traversals.class,
            HasTest.Traversals.class,
            IsTest.Traversals.class,
            OrTest.Traversals.class,
            RangeTest.Traversals.class,
            RetainTest.Traversals.class,
            SampleTest.Traversals.class,
            SimplePathTest.Traversals.class,
            WhereTest.Traversals.class,

            // map
            BackTest.Traversals.class,
            CoalesceTest.Traversals.class,
            CountTest.Traversals.class,
            FoldTest.Traversals.class,
            MapTest.Traversals.class,
            MatchTest.Traversals.class,
            MaxTest.Traversals.class,
            MeanTest.Traversals.class,
            MinTest.Traversals.class,
            SumTest.Traversals.class,
            OrderTest.Traversals.class,
            org.apache.tinkerpop.gremlin.process.graph.traversal.step.map.PathTest.Traversals.class,
            PropertiesTest.Traversals.class,
            SelectTest.Traversals.class,
            VertexTest.Traversals.class,
            UnfoldTest.Traversals.class,
            ValueMapTest.Traversals.class,

            // sideEffect
            AddEdgeTest.Traversals.class,
            AggregateTest.Traversals.class,
            GroupTest.Traversals.class,
            GroupCountTest.Traversals.class,
            InjectTest.Traversals.class,
            ProfileTest.Traversals.class,
            SackTest.Traversals.class,
            SideEffectCapTest.Traversals.class,
            SideEffectTest.Traversals.class,
            StoreTest.Traversals.class,
            SubgraphTest.Traversals.class,
            TreeTest.Traversals.class,

            // util
            TraversalSideEffectsTest.Traversals.class,

            // compliance
            CoreTraversalTest.class,
            PathTest.class,

            // strategy
            TraversalVerificationStrategyTest.StandardTraversals.class

            // algorithms
            // PageRankVertexProgramTest.class
    };

    /**
     * This list of tests in the suite that will be executed.  Gremlin developers should add to this list
     * as needed to enforce tests upon implementations.
     */
    private static final Class<?>[] testsToExecute;

    static {
        final String override = System.getenv().getOrDefault("gremlin.tests", "");
        if (override.equals(""))
            testsToExecute = allTests;
        else {
            final List<String> filters = Arrays.asList(override.split(","));
            final List<Class<?>> allowed = Stream.of(allTests)
                    .filter(c -> filters.contains(c.getName()))
                    .collect(Collectors.toList());
            testsToExecute = allowed.toArray(new Class<?>[allowed.size()]);
        }
    }

    public ProcessStandardSuite(final Class<?> klass, final RunnerBuilder builder) throws InitializationError {
        super(klass, builder, testsToExecute, testsToExecute);
    }

    public ProcessStandardSuite(final Class<?> klass, final RunnerBuilder builder, final Class<?>[] testsToExecute, final Class<?>[] testsToEnforce) throws InitializationError {
        super(klass, builder, testsToExecute, testsToEnforce);
    }

    public ProcessStandardSuite(final Class<?> klass, final RunnerBuilder builder, final Class<?>[] testsToExecute, final Class<?>[] testsToEnforce, final boolean gremlinFlavorSuite) throws InitializationError {
        super(klass, builder, testsToExecute, testsToEnforce, gremlinFlavorSuite, StandardTraversalEngine.standard);
    }

    @Override
    public boolean beforeTestExecution(final Class<? extends AbstractGremlinTest> testClass) {
        final UseEngine[] useEngines = testClass.getAnnotationsByType(UseEngine.class);
        if (null == useEngines || !Stream.of(useEngines).anyMatch(useEngine -> useEngine.value().equals(TraversalEngine.Type.STANDARD)))
            throw new RuntimeException(String.format("The %s expects all tests to be annotated with @UseEngine(%s) - check %s",
                    ProcessComputerSuite.class.getName(), TraversalEngine.Type.STANDARD, testClass.getName()));
        return super.beforeTestExecution(testClass);
    }
}
