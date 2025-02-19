/*
 * Copyright (c) 2010-2019 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.common.configuration.api;

/**
 *  This is an attempt to provide more typed access to config.xml file content.
 *
 *  EXPERIMENTAL.
 */
public interface SystemConfigurationSection {

    String getJmap();

    String getLogFile();
}
