/**
 * Copyright (c) 2010-2019 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sql.util;

/**
 * @author lazyman
 */
public interface EntityState {

    /**
     * Tells hibernate {@link org.hibernate.Interceptor} that entity is transient, so that hibernate session
     * doesn't need to verify it using select queries.
     *
     * @return true if entity is transient
     */
    Boolean isTransient();

    void setTransient(Boolean trans);
}
