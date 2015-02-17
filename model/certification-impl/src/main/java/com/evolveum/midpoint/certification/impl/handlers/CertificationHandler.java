/*
 * Copyright (c) 2010-2015 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.evolveum.midpoint.certification.impl.handlers;

import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.CommunicationException;
import com.evolveum.midpoint.util.exception.ConfigurationException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SecurityViolationException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AccessCertificationRunType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AccessCertificationTypeType;

/**
 * @author mederly
 */
public interface CertificationHandler {

    /**
     * Creates a new Certification Run from a Certification Type and (prototype) Certification Run.
     * By default, merges all the data from type and run (specific data from the run take precedence).
     * Individual handlers can modify this behavior if needed.
     *
     * @param certificationTypeType
     * @param runType
     * @param task
     * @param result
     * @return
     * @throws SecurityViolationException
     */
    AccessCertificationRunType createCertificationRunType(AccessCertificationTypeType certificationTypeType, AccessCertificationRunType runType, Task task, OperationResult result) throws SecurityViolationException;

    /**
     * Records the certification run starting. First of all, it sets the following for each relevant object/assignment:
     *
     *  certificationStartedTimestamp := current time
     *  certificationRunRef := current certification
     *  certificationStatus := AWAITING_DECISION
     *  certifierToDecideRef := certifier as determined by certification type
     *
     * @param runType
     * @param task
     * @param result
     */
    void recordRunStarted(AccessCertificationRunType runType, Task task, OperationResult result) throws SchemaException, SecurityViolationException, ObjectNotFoundException, CommunicationException, ConfigurationException;
}
