/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.interpreterv2;

/** Represents the state of a generator function. */
public class GeneratorState {
    public int operation;
    public Object value;
    public RuntimeException returnedException;

    public GeneratorState(int operation, Object value) {
        this.operation = operation;
        this.value = value;
    }
}
