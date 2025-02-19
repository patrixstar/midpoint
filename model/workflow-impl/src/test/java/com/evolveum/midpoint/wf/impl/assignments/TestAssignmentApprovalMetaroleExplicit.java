/*
 * Copyright (c) 2010-2019 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.wf.impl.assignments;

/**
 * Tests assigning of roles 1..3 with explicitly assigned metaroles (with policy rules).
 *
 * @author mederly
 */
@SuppressWarnings("Duplicates")
public class TestAssignmentApprovalMetaroleExplicit extends AbstractTestAssignmentApproval {

    @Override
    protected String getRoleOid(int number) {
        switch (number) {
            case 1: return roleRole1bOid;
            case 2: return roleRole2bOid;
            case 3: return roleRole3bOid;
            case 4: return roleRole4bOid;
            case 10: return roleRole10bOid;
            default: throw new IllegalArgumentException("Wrong role number: " + number);
        }
    }

    @Override
    protected String getRoleName(int number) {
        switch (number) {
            case 1: return "Role1b";
            case 2: return "Role2b";
            case 3: return "Role3b";
            case 4: return "Role4b";
            case 10: return "Role10b";
            default: throw new IllegalArgumentException("Wrong role number: " + number);
        }
    }
}
