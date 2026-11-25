/*
 * Logback: the reliable, generic, fast and flexible logging framework.
 * Copyright (C) 1999-2024, QOS.ch. All rights reserved.
 *
 * This program and the accompanying materials are dual-licensed under
 * either the terms of the Eclipse Public License v1.0 as published by
 * the Eclipse Foundation
 *
 *   or (per the licensee's choosing)
 *
 * under the terms of the GNU Lesser General Public License version 2.1
 * as published by the Free Software Foundation.
 */
package ch.qos.logback.core.blackbox.boolex;

import ch.qos.logback.core.Context;
import ch.qos.logback.core.ContextBase;
import ch.qos.logback.core.boolex.EvaluationException;
import ch.qos.logback.core.boolex.JaninoEventEvaluatorBase;
import ch.qos.logback.core.status.Status;
import ch.qos.logback.core.status.StatusUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Janino-based event evaluation allows arbitrary code to be executed from a
 * configuration file (CVE-2024-12798). Such evaluators must refuse to start.
 */
public class BlackboxJaninoEventEvaluatorBaseTest {

    static final String SIDE_EFFECT_KEY = "BlackboxJaninoEventEvaluatorBaseTest.sideEffect";

    Context context = new ContextBase();
    StatusUtil checker = new StatusUtil(context);
    StringJaninoEventEvaluator evaluator = new StringJaninoEventEvaluator();

    static class StringJaninoEventEvaluator extends JaninoEventEvaluatorBase<String> {

        @Override
        protected String getDecoratedExpression() {
            return "return " + getExpression() + ";";
        }

        @Override
        protected String[] getParameterNames() {
            return new String[] { "event" };
        }

        @Override
        protected Class<?>[] getParameterTypes() {
            return new Class<?>[] { String.class };
        }

        @Override
        protected Object[] getParameterValues(String event) {
            return new Object[] { event };
        }
    }

    @BeforeEach
    public void setUp() {
        evaluator.setContext(context);
        evaluator.setName("evaluator");
    }

    @AfterEach
    public void tearDown() {
        System.clearProperty(SIDE_EFFECT_KEY);
    }

    @Test
    public void janinoEvaluatorDoesNotStart() {
        evaluator.setExpression("event.equals(\"x\")");
        evaluator.start();

        Assertions.assertFalse(evaluator.isStarted());
        Assertions.assertTrue(checker.containsMatch(Status.ERROR, "Janino-based event evaluation is disabled"));
        Assertions.assertThrows(IllegalStateException.class, () -> evaluator.evaluate("x"));
    }

    @Test
    public void janinoExpressionIsNeverExecuted() {
        Assertions.assertNull(System.getProperty(SIDE_EFFECT_KEY));

        evaluator.setExpression("System.setProperty(\"" + SIDE_EFFECT_KEY + "\", event) == null");
        evaluator.start();
        try {
            evaluator.evaluate("executed");
        } catch (IllegalStateException | EvaluationException e) {
            // expected, the evaluator must not be in started state
        }

        Assertions.assertNull(System.getProperty(SIDE_EFFECT_KEY));
        Assertions.assertFalse(evaluator.isStarted());
    }
}
