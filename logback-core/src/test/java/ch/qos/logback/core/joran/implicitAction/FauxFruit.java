/**
 * Logback: the reliable, generic, fast and flexible logging framework.
 * Copyright (C) 1999-2026, QOS.ch. All rights reserved.
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
package ch.qos.logback.core.joran.implicitAction;

/**
 * A class which is NOT a {@link Fruit}, used to check that nested components
 * of an incompatible type are never instantiated.
 */
public class FauxFruit {

    static public int COUNT = 0;

    public FauxFruit() {
        COUNT++;
    }

}
