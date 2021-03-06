package org.apache.tinkerpop.gremlin.process.graph.traversal.strategy;

import org.apache.tinkerpop.gremlin.process.Traversal;
import org.apache.tinkerpop.gremlin.process.TraversalEngine;
import org.apache.tinkerpop.gremlin.process.TraversalStrategies;
import org.apache.tinkerpop.gremlin.process.graph.traversal.__;
import org.apache.tinkerpop.gremlin.process.graph.traversal.step.filter.HasTraversalStep;
import org.apache.tinkerpop.gremlin.process.graph.traversal.step.filter.RangeGlobalStep;
import org.apache.tinkerpop.gremlin.process.traversal.DefaultTraversalStrategies;
import org.apache.tinkerpop.gremlin.process.traversal.util.TraversalHelper;
import org.apache.tinkerpop.gremlin.structure.Compare;
import org.apache.tinkerpop.gremlin.structure.Contains;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

/**
 * @author Daniel Kuppitz (http://gremlin.guru)
 * @author Stephen Mallette (http://stephen.genoprime.com)
 */
@RunWith(Enclosed.class)
public class RangeByIsCountStrategyTest {

    @RunWith(Parameterized.class)
    public static class StandardTest extends AbstractRangeByIsCountStrategyTest {

        @Parameterized.Parameters(name = "{0}")
        public static Iterable<Object[]> data() {
            return generateTestParameters();
        }

        @Parameterized.Parameter(value = 0)
        public String name;

        @Parameterized.Parameter(value = 1)
        public BiPredicate predicate;

        @Parameterized.Parameter(value = 2)
        public Object value;

        @Parameterized.Parameter(value = 3)
        public long expectedHighRange;

        @Before
        public void setup() {
            this.traversalEngine = mock(TraversalEngine.class);
            when(this.traversalEngine.getType()).thenReturn(TraversalEngine.Type.STANDARD);
        }

        @Test
        public void shouldApplyStrategy() {
            doTest(predicate, value, expectedHighRange);
        }
    }

    @RunWith(Parameterized.class)
    public static class ComputerTest extends AbstractRangeByIsCountStrategyTest {

        @Parameterized.Parameters(name = "{0}")
        public static Iterable<Object[]> data() {
            return generateTestParameters();
        }

        @Parameterized.Parameter(value = 0)
        public String name;

        @Parameterized.Parameter(value = 1)
        public BiPredicate predicate;

        @Parameterized.Parameter(value = 2)
        public Object value;

        @Parameterized.Parameter(value = 3)
        public long expectedHighRange;

        @Before
        public void setup() {
            this.traversalEngine = mock(TraversalEngine.class);
            when(this.traversalEngine.getType()).thenReturn(TraversalEngine.Type.COMPUTER);
        }

        @Test
        public void shouldApplyStrategy() {
            doTest(predicate, value, expectedHighRange);
        }
    }

    public static class SpecificComputerTest extends AbstractRangeByIsCountStrategyTest {

        @Before
        public void setup() {
            this.traversalEngine = mock(TraversalEngine.class);
            when(this.traversalEngine.getType()).thenReturn(TraversalEngine.Type.COMPUTER);
        }

        @Test
        public void nestedCountEqualsNullShouldLimitToOne() {
            final AtomicInteger counter = new AtomicInteger(0);
            final Traversal traversal = __.out().has(__.outE("created").count().is(0));
            applyRangeByIsCountStrategy(traversal);

            final HasTraversalStep hasStep = TraversalHelper.getStepsOfClass(HasTraversalStep.class, traversal.asAdmin()).stream().findFirst().get();
            final Traversal nestedTraversal = (Traversal) hasStep.getLocalChildren().get(0);
            TraversalHelper.getStepsOfClass(RangeGlobalStep.class, nestedTraversal.asAdmin()).stream().forEach(step -> {
                assertEquals(0, step.getLowRange());
                assertEquals(1, step.getHighRange());
                counter.incrementAndGet();
            });
            assertEquals(1, counter.get());
        }
    }

    private static abstract class AbstractRangeByIsCountStrategyTest {

        protected TraversalEngine traversalEngine;

        void applyRangeByIsCountStrategy(final Traversal traversal) {
            final TraversalStrategies strategies = new DefaultTraversalStrategies();
            strategies.addStrategies(RangeByIsCountStrategy.instance());

            traversal.asAdmin().setStrategies(strategies);
            traversal.asAdmin().applyStrategies();
            traversal.asAdmin().setEngine(traversalEngine);
        }

        public void doTest(final BiPredicate predicate, final Object value, final long expectedHighRange) {
            final AtomicInteger counter = new AtomicInteger(0);
            final Traversal traversal = __.out().count().is(predicate, value);
            applyRangeByIsCountStrategy(traversal);

            final List<RangeGlobalStep> steps = TraversalHelper.getStepsOfClass(RangeGlobalStep.class, traversal.asAdmin());
            assertEquals(1, steps.size());

            steps.forEach(step -> {
                assertEquals(0, step.getLowRange());
                assertEquals(expectedHighRange, step.getHighRange());
                counter.incrementAndGet();
            });

            assertEquals(1, counter.intValue());
        }

        static Iterable<Object[]> generateTestParameters() {

            return Arrays.asList(new Object[][]{
                    {"countEqualsNullShouldLimitToOne", Compare.eq, 0l, 1l},
                    {"countNotEqualsFourShouldLimitToFive", Compare.neq, 4l, 5l},
                    {"countLessThanOrEqualThreeShouldLimitToFour", Compare.lte, 3l, 4l},
                    {"countLessThanThreeShouldLimitToThree", Compare.lt, 3l, 3l},
                    {"countGreaterThanTwoShouldLimitToThree", Compare.gt, 2l, 3l},
                    {"countGreaterThanOrEqualTwoShouldLimitToTwo", Compare.gte, 2l, 2l},
                    {"countInsideTwoAndFourShouldLimitToFour", Compare.inside, Arrays.asList(2l, 4l), 4l},
                    {"countOutsideTwoAndFourShouldLimitToFive", Compare.outside, Arrays.asList(2l, 4l), 5l},
                    {"countWithinTwoSixFourShouldLimitToSeven", Contains.within, Arrays.asList(2l, 6l, 4l), 7l},
                    {"countWithoutTwoSixFourShouldLimitToSix", Contains.without, Arrays.asList(2l, 6l, 4l), 6l}});
        }
    }
}
