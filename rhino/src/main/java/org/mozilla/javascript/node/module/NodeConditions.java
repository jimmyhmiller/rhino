/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.node.module;

import java.util.Set;

/** Standard condition sets for Node.js module resolution. */
public final class NodeConditions {

    public static final Set<String> ESM_CONDITIONS = Set.of("node", "import", "default");
    public static final Set<String> CJS_CONDITIONS = Set.of("node", "require", "default");

    private NodeConditions() {}
}
