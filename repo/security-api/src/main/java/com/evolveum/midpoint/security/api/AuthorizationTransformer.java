/**
 * Copyright (c) 2016 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.security.api;

import java.util.Collection;

/**
 * @author semancik
 *
 */
@FunctionalInterface
public interface AuthorizationTransformer {

    Collection<Authorization> transform(Authorization authorization);

}
