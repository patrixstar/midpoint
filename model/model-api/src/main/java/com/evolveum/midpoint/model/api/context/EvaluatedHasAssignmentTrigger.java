/*
 * Copyright (c) 2010-2017 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.model.api.context;

import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.util.LocalizableMessage;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author mederly
 */
public class EvaluatedHasAssignmentTrigger extends EvaluatedPolicyRuleTrigger<HasAssignmentPolicyConstraintType> {

    public EvaluatedHasAssignmentTrigger(@NotNull PolicyConstraintKindType kind, @NotNull HasAssignmentPolicyConstraintType constraint,
            LocalizableMessage message, LocalizableMessage shortMessage) {
        super(kind, constraint, message, shortMessage, false);
    }

    @Override
    public EvaluatedHasAssignmentTriggerType toEvaluatedPolicyRuleTriggerType(PolicyRuleExternalizationOptions options,
            PrismContext prismContext) {
        EvaluatedHasAssignmentTriggerType rv = new EvaluatedHasAssignmentTriggerType();
        fillCommonContent(rv);
        return rv;
    }
}
