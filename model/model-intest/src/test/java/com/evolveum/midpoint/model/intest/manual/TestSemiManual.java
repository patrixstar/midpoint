/*
 * Copyright (c) 2010-2018 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

/**
 *
 */
package com.evolveum.midpoint.model.intest.manual;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertNull;

import java.io.File;

import javax.xml.namespace.QName;

import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ContextConfiguration;
import org.testng.AssertJUnit;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;
import org.w3c.dom.Element;

import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismProperty;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.PointInTimeType;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.schema.constants.MidPointConstants;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.test.asserter.ShadowAsserter;
import com.evolveum.midpoint.test.asserter.UserAsserter;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ActivationStatusType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.OperationResultStatusType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.PendingOperationExecutionStatusType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.PendingOperationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowKindType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.UserType;
import com.evolveum.prism.xml.ns._public.types_3.PolyStringType;

/**
 * @author Radovan Semancik
 */
@ContextConfiguration(locations = {"classpath:ctx-model-intest-test-main.xml"})
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
@Listeners({ com.evolveum.midpoint.tools.testng.AlphabeticalMethodInterceptor.class })
public class TestSemiManual extends AbstractDirectManualResourceTest {

    private static final Trace LOGGER = TraceManager.getTrace(TestSemiManual.class);

    protected static final String ATTR_DISABLED = "disabled";
    protected static final QName ATTR_DISABLED_QNAME = new QName(MidPointConstants.NS_RI, ATTR_DISABLED);

    @Override
    public void initSystem(Task initTask, OperationResult initResult) throws Exception {
        super.initSystem(initTask, initResult);
    }

    @Override
    protected BackingStore createBackingStore() {
        return new CsvBackingStore();
    }

    @Override
    protected String getResourceOid() {
        return RESOURCE_SEMI_MANUAL_OID;
    }

    @Override
    protected File getResourceFile() {
        return RESOURCE_SEMI_MANUAL_FILE;
    }

    @Override
    protected String getRoleOneOid() {
        return ROLE_ONE_SEMI_MANUAL_OID;
    }

    @Override
    protected File getRoleOneFile() {
        return ROLE_ONE_SEMI_MANUAL_FILE;
    }

    @Override
    protected String getRoleTwoOid() {
        return ROLE_TWO_SEMI_MANUAL_OID;
    }

    @Override
    protected File getRoleTwoFile() {
        return ROLE_TWO_SEMI_MANUAL_FILE;
    }

    @Override
    protected boolean hasMultivalueInterests() {
        return false;
    }

    @Override
    protected void assertResourceSchemaBeforeTest(Element resourceXsdSchemaElementBefore) {
        AssertJUnit.assertNull("Resource schema sneaked in before test connection", resourceXsdSchemaElementBefore);
    }

    @Override
    protected int getNumberOfAccountAttributeDefinitions() {
        return 5;
    }

    /**
     * Trying to assign an account that already exists in the backing store.
     * But midPoint does not know about it.
     * MID-4002
     */
    @Test
    public void test700AssignAccountJackExisting() throws Exception {
        final String TEST_NAME = "test700AssignAccountJack";
        displayTestTitle(TEST_NAME);
        // GIVEN
        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        if (accountJackOid != null) {
            PrismObject<ShadowType> shadowRepoBefore = repositoryService.getObject(ShadowType.class, accountJackOid, null, result);
            display("Repo shadow before", shadowRepoBefore);
            assertPendingOperationDeltas(shadowRepoBefore, 0);
        }

        backingStoreAddJack();

        clock.overrideDuration("PT5M");

        accountJackReqestTimestampStart = clock.currentTimeXMLGregorianCalendar();

        // WHEN
        displayWhen(TEST_NAME);
        assignAccountToUser(USER_JACK_OID, getResourceOid(), null, task, result);

        // THEN
        displayThen(TEST_NAME);
        display("result", result);
        assertSuccess(result, 2);
        assertNull("Unexpected ticket in result", result.getAsynchronousOperationReference());

        accountJackReqestTimestampEnd = clock.currentTimeXMLGregorianCalendar();

        PrismObject<UserType> userAfter = getUser(USER_JACK_OID);
        display("User after", userAfter);
        accountJackOid = getSingleLinkOid(userAfter);

        PrismObject<ShadowType> shadowRepo = repositoryService.getObject(ShadowType.class, accountJackOid, null, result);
        display("Repo shadow", shadowRepo);
        assertPendingOperationDeltas(shadowRepo, 0);
        assertShadowExists(shadowRepo, true);
        assertNoShadowPassword(shadowRepo);

        PrismObject<ShadowType> shadowModel = modelService.getObject(ShadowType.class,
                accountJackOid, null, task, result);

        display("Model shadow", shadowModel);
        ShadowType shadowTypeProvisioning = shadowModel.asObjectable();
        assertShadowName(shadowModel, USER_JACK_USERNAME);
        assertEquals("Wrong kind (provisioning)", ShadowKindType.ACCOUNT, shadowTypeProvisioning.getKind());
        assertAttribute(shadowModel, ATTR_USERNAME_QNAME, USER_JACK_USERNAME);
        assertAttribute(shadowModel, ATTR_FULLNAME_QNAME, USER_JACK_FULL_NAME);
        assertShadowActivationAdministrativeStatus(shadowModel, ActivationStatusType.ENABLED);
        assertShadowExists(shadowModel, true);

        assertPendingOperationDeltas(shadowModel, 0);
    }

    /**
     * MID-4002
     */
    @Test
    public void test710UnassignAccountJack() throws Exception {
        final String TEST_NAME = "test710UnassignAccountJack";
        displayTestTitle(TEST_NAME);
        // GIVEN
        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        clock.overrideDuration("PT5M");

        accountJackReqestTimestampStart = clock.currentTimeXMLGregorianCalendar();

        // WHEN
        displayWhen(TEST_NAME);
        unassignAccountFromUser(USER_JACK_OID, getResourceOid(), null, task, result);

        // THEN
        displayThen(TEST_NAME);
        display("result", result);
        jackLastCaseOid = assertInProgress(result);

        PrismObject<UserType> userAfter = getUser(USER_JACK_OID);
        display("User after", userAfter);
        accountJackOid = getSingleLinkOid(userAfter);

        accountJackReqestTimestampEnd = clock.currentTimeXMLGregorianCalendar();

        PrismObject<ShadowType> shadowRepo = repositoryService.getObject(ShadowType.class, accountJackOid, null, result);
        display("Repo shadow", shadowRepo);

        assertPendingOperationDeltas(shadowRepo, 1);
        PendingOperationType pendingOperation = findPendingOperation(shadowRepo, OperationResultStatusType.IN_PROGRESS);
        assertPendingOperation(shadowRepo, pendingOperation, accountJackReqestTimestampStart, accountJackReqestTimestampEnd);
        assertNotNull("No ID in pending operation", pendingOperation.getId());

        PrismObject<ShadowType> shadowModel = modelService.getObject(ShadowType.class,
                accountJackOid, null, task, result);

        display("Model shadow", shadowModel);
        ShadowType shadowTypeProvisioning = shadowModel.asObjectable();
        assertShadowName(shadowModel, USER_JACK_USERNAME);
        assertEquals("Wrong kind (provisioning)", ShadowKindType.ACCOUNT, shadowTypeProvisioning.getKind());
        assertShadowPassword(shadowModel);

        assertPendingOperationDeltas(shadowModel, 1);
        pendingOperation = findPendingOperation(shadowModel, OperationResultStatusType.IN_PROGRESS);
        assertPendingOperation(shadowModel, pendingOperation, accountJackReqestTimestampStart, accountJackReqestTimestampEnd);

        assertUnassignedFuture(assertModelShadowFuture(accountJackOid), true);

        assertCaseState(jackLastCaseOid, SchemaConstants.CASE_STATE_OPEN);
    }

    /**
     * MID-4002
     */
    @Test
    public void test712CloseCaseAndRecomputeJack() throws Exception {
        final String TEST_NAME = "test712CloseCaseAndRecomputeJack";
        displayTestTitle(TEST_NAME);
        // GIVEN
        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        backingStoreDeleteJack();

        closeCase(jackLastCaseOid);

        accountJackCompletionTimestampStart = clock.currentTimeXMLGregorianCalendar();

        // WHEN
        displayWhen(TEST_NAME);
        // We need reconcile and not recompute here. We need to fetch the updated case status.
        reconcileUser(USER_JACK_OID, task, result);

        // THEN
        displayThen(TEST_NAME);
        display("result", result);
        assertSuccess(result);

        accountJackCompletionTimestampEnd = clock.currentTimeXMLGregorianCalendar();

        ShadowAsserter<Void> shadowRepoAsserter = assertRepoShadow(accountJackOid)
            .pendingOperations()
                .singleOperation()
                    .assertRequestTimestamp(accountJackReqestTimestampStart, accountJackReqestTimestampEnd)
                    .assertExecutionStatus(PendingOperationExecutionStatusType.COMPLETED)
                    .assertResultStatus(OperationResultStatusType.SUCCESS)
                    .assertCompletionTimestamp(accountJackCompletionTimestampStart, accountJackCompletionTimestampEnd)
                .end()
            .end();
        assertUnassignedShadow(shadowRepoAsserter, true, null);

        ShadowAsserter<Void> shadowModelAsserter = assertModelShadow(accountJackOid)
            .assertName(USER_JACK_USERNAME)
            .assertKind(ShadowKindType.ACCOUNT)
            .pendingOperations()
                .singleOperation()
                    .assertRequestTimestamp(accountJackReqestTimestampStart, accountJackReqestTimestampEnd)
                    .assertExecutionStatus(PendingOperationExecutionStatusType.COMPLETED)
                    .assertResultStatus(OperationResultStatusType.SUCCESS)
                    .assertCompletionTimestamp(accountJackCompletionTimestampStart, accountJackCompletionTimestampEnd)
                .end()
            .end();
        assertUnassignedShadow(shadowModelAsserter, true, ActivationStatusType.DISABLED);

        assertUnassignedFuture(assertModelShadowFuture(accountJackOid), false);

        assertCaseState(jackLastCaseOid, SchemaConstants.CASE_STATE_CLOSED);
    }

    /**
     * MID-4002
     */
    @Test
    public void test717RecomputeJackAfter130min() throws Exception {
        final String TEST_NAME = "test717RecomputeJackAfter130min";
        displayTestTitle(TEST_NAME);
        // GIVEN
        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        clock.overrideDuration("PT130M");

        // WHEN
        displayWhen(TEST_NAME);
        // We need reconcile and not recompute here. We need to fetch the updated case status.
        reconcileUser(USER_JACK_OID, task, result);

        // THEN
        displayThen(TEST_NAME);
        display("result", result);
        assertSuccess(result);

        UserAsserter<Void> userAfterAsserter = assertUserAfter(USER_JACK_OID);
        userAfterAsserter.displayWithProjections();
        assertDeprovisionedTimedOutUser(userAfterAsserter, accountJackOid);

        assertCaseState(jackLastCaseOid, SchemaConstants.CASE_STATE_CLOSED);
    }

    /**
     * Put everything in a clean state so we can start over.
     */
    @Test
    public void test719CleanUp() throws Exception {
        final String TEST_NAME = "test719CleanUp";
        displayTestTitle(TEST_NAME);

        cleanupUser(TEST_NAME, USER_JACK_OID, USER_JACK_USERNAME, accountJackOid);
    }

    @Override
    protected void assertShadowPassword(PrismObject<ShadowType> shadow) {
        // CSV password is readable
        PrismProperty<PolyStringType> passValProp = shadow.findProperty(SchemaConstants.PATH_PASSWORD_VALUE);
        assertNotNull("No password value property in "+shadow+": "+passValProp, passValProp);
    }

    @Override
    protected void assertUnassignedShadow(ShadowAsserter<?> shadowModelAsserter, boolean backingStoreUpdated, ActivationStatusType expectAlternativeActivationStatus) {
        if (backingStoreUpdated) {
            shadowModelAsserter.assertTombstone();
        } else {
            shadowModelAsserter.assertCorpse();
        }
    }
}
