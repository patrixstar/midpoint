/*
 * Copyright (c) 2010-2018 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

/**
 *
 */
package com.evolveum.midpoint.provisioning.impl;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertTrue;

import java.io.File;
import java.util.Collection;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.testng.AssertJUnit;
import org.w3c.dom.Element;

import com.evolveum.midpoint.common.refinery.RefinedResourceSchema;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.provisioning.api.ProvisioningService;
import com.evolveum.midpoint.provisioning.impl.dummy.TestDummyResourceAndSchemaCaching;
import com.evolveum.midpoint.provisioning.impl.mock.SynchronizationServiceMock;
import com.evolveum.midpoint.provisioning.ucf.api.ConnectorInstance;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.PointInTimeType;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.schema.internals.InternalCounters;
import com.evolveum.midpoint.schema.internals.InternalMonitor;
import com.evolveum.midpoint.schema.internals.InternalsConfig;
import com.evolveum.midpoint.schema.processor.ResourceSchema;
import com.evolveum.midpoint.schema.processor.ResourceSchemaImpl;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.schema.util.ResourceTypeUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.test.AbstractIntegrationTest;
import com.evolveum.midpoint.test.asserter.ShadowAsserter;
import com.evolveum.midpoint.util.exception.CommunicationException;
import com.evolveum.midpoint.util.exception.ConfigurationException;
import com.evolveum.midpoint.util.exception.ExpressionEvaluationException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SecurityViolationException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.CachingMetadataType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ResourceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.XmlSchemaType;
import com.evolveum.midpoint.xml.ns._public.resource.capabilities_3.ReadCapabilityType;

/**
 * @author Radovan Semancik
 */
@ContextConfiguration(locations = "classpath:ctx-provisioning-test-main.xml")
@DirtiesContext
public abstract class AbstractProvisioningIntegrationTest extends AbstractIntegrationTest {

    public static final File COMMON_DIR = ProvisioningTestUtil.COMMON_TEST_DIR_FILE;

    protected static final String CSV_CONNECTOR_TYPE = "com.evolveum.polygon.connector.csv.CsvConnector";

    private static final Trace LOGGER = TraceManager.getTrace(AbstractProvisioningIntegrationTest.class);

    @Autowired protected ProvisioningService provisioningService;
    @Autowired protected SynchronizationServiceMock syncServiceMock;

    // Testing connector discovery
    @Autowired protected ConnectorManager connectorManager;

    // Used to make sure that the connector is cached
    @Autowired protected ResourceManager resourceManager;

    // Values used to check if something is unchanged or changed properly
    private Long lastResourceVersion = null;
    private ConnectorInstance lastConfiguredConnectorInstance = null;
    private CachingMetadataType lastCachingMetadata;
    private ResourceSchema lastResourceSchema = null;
    private RefinedResourceSchema lastRefinedResourceSchema;


    @Override
    public void initSystem(Task initTask, OperationResult initResult) throws Exception {
        InternalsConfig.encryptionChecks = false;
        repositoryService.postInit(initResult);         // initialize caches here
        provisioningService.postInit(initResult);
    }

    protected <T extends ObjectType> void assertVersion(PrismObject<T> object, String expectedVersion) {
        assertEquals("Wrong version of "+object, expectedVersion, object.asObjectable().getVersion());
    }

    protected void rememberResourceVersion(String version) {
        lastResourceVersion = parseVersion(version);
    }

    protected void assertResourceVersionIncrement(PrismObject<ResourceType> resource, int expectedIncrement) {
        assertResourceVersionIncrement(resource.getVersion(), expectedIncrement);
    }

    protected void assertResourceVersionIncrement(String currentVersion, int expectedIncrement) {
        long currentVersionLong = parseVersion(currentVersion);
        long actualIncrement = currentVersionLong - lastResourceVersion;
        assertEquals("Unexpected increment in resource version", (long)expectedIncrement, actualIncrement);
        lastResourceVersion = currentVersionLong;
    }

    private long parseVersion(String stringVersion) {
        if (stringVersion == null) {
            AssertJUnit.fail("Version is null");
        }
        if (stringVersion.isEmpty()) {
            AssertJUnit.fail("Version is empty");
        }
        return Long.parseLong(stringVersion);
    }

    protected CachingMetadataType getSchemaCachingMetadata(PrismObject<ResourceType> resource) {
        ResourceType resourceType = resource.asObjectable();
        XmlSchemaType xmlSchemaType = resourceType.getSchema();
        assertNotNull("No schema", xmlSchemaType);
        Element resourceXsdSchemaElementAfter = ResourceTypeUtil.getResourceXsdSchema(resourceType);
        assertNotNull("No schema XSD element", resourceXsdSchemaElementAfter);
        return xmlSchemaType.getCachingMetadata();
    }

    protected void rememberSchemaMetadata(PrismObject<ResourceType> resource) {
        lastCachingMetadata = getSchemaCachingMetadata(resource);
    }

    protected void assertSchemaMetadataUnchanged(PrismObject<ResourceType> resource) {
        CachingMetadataType current = getSchemaCachingMetadata(resource);
        assertEquals("Schema caching metadata changed", lastCachingMetadata, current);
    }

    protected void rememberResourceSchema(ResourceSchema resourceSchema) {
        lastResourceSchema = resourceSchema;
    }

    protected void assertResourceSchemaUnchanged(ResourceSchema currentResourceSchema) {
        // We really want == there. We want to make sure that this is actually the same instance and that
        // it was properly cached
        assertTrue("Resource schema has changed", lastResourceSchema == currentResourceSchema);
    }

    protected void rememberRefinedResourceSchema(RefinedResourceSchema rResourceSchema) {
        lastRefinedResourceSchema = rResourceSchema;
    }

    protected void assertRefinedResourceSchemaUnchanged(RefinedResourceSchema currentRefinedResourceSchema) {
        // We really want == there. We want to make sure that this is actually the same instance and that
        // it was properly cached
        assertTrue("Refined resource schema has changed", lastRefinedResourceSchema == currentRefinedResourceSchema);
    }

    protected void assertHasSchema(PrismObject<ResourceType> resource, String desc) throws SchemaException {
        ResourceType resourceType = resource.asObjectable();
        display("Resource "+desc, resourceType);

        XmlSchemaType xmlSchemaTypeAfter = resourceType.getSchema();
        assertNotNull("No schema in "+desc, xmlSchemaTypeAfter);
        Element resourceXsdSchemaElementAfter = ResourceTypeUtil.getResourceXsdSchema(resourceType);
        assertNotNull("No schema XSD element in "+desc, resourceXsdSchemaElementAfter);

        String resourceXml = prismContext.serializeObjectToString(resource, PrismContext.LANG_XML);
//        display("Resource XML", resourceXml);

        CachingMetadataType cachingMetadata = xmlSchemaTypeAfter.getCachingMetadata();
        assertNotNull("No caching metadata in "+desc, cachingMetadata);
        assertNotNull("No retrievalTimestamp in "+desc, cachingMetadata.getRetrievalTimestamp());
        assertNotNull("No serialNumber in "+desc, cachingMetadata.getSerialNumber());

        Element xsdElement = ObjectTypeUtil.findXsdElement(xmlSchemaTypeAfter);
        ResourceSchema parsedSchema = ResourceSchemaImpl.parse(xsdElement, resource.toString(), prismContext);
        assertNotNull("No schema after parsing in "+desc, parsedSchema);
    }

    protected void rememberConnectorInstance(PrismObject<ResourceType> resource) throws ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException {
        OperationResult result = new OperationResult(TestDummyResourceAndSchemaCaching.class.getName() + ".rememberConnectorInstance");
        rememberConnectorInstance(resourceManager.getConfiguredConnectorInstanceFromCache(resource, ReadCapabilityType.class, false, result));
    }

    protected void rememberConnectorInstance(ConnectorInstance currentConnectorInstance) throws ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException {
        LOGGER.debug("Remembering connector instance {}", currentConnectorInstance);
        lastConfiguredConnectorInstance = currentConnectorInstance;
    }

    protected void assertConnectorInstanceUnchanged(PrismObject<ResourceType> resource) throws ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException {
        if (lastConfiguredConnectorInstance == null) {
            return;
        }
        OperationResult result = new OperationResult(TestDummyResourceAndSchemaCaching.class.getName()
                + ".assertConnectorInstanceUnchanged");
        ConnectorInstance currentConfiguredConnectorInstance = resourceManager.getConfiguredConnectorInstanceFromCache(
                resource, ReadCapabilityType.class, false, result);
        assertTrue("Connector instance has changed", lastConfiguredConnectorInstance == currentConfiguredConnectorInstance);
    }

    protected void assertConnectorInstanceChanged(PrismObject<ResourceType> resource) throws ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException {
        OperationResult result = new OperationResult(TestDummyResourceAndSchemaCaching.class.getName()
                + ".rememberConnectorInstance");
        ConnectorInstance currentConfiguredConnectorInstance = resourceManager.getConfiguredConnectorInstanceFromCache(
                resource, ReadCapabilityType.class, false, result);
        assertTrue("Connector instance has NOT changed", lastConfiguredConnectorInstance != currentConfiguredConnectorInstance);
        lastConfiguredConnectorInstance = currentConfiguredConnectorInstance;
    }

    protected void assertSteadyResource() throws ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException {
        assertCounterIncrement(InternalCounters.RESOURCE_SCHEMA_FETCH_COUNT, 0);
        assertCounterIncrement(InternalCounters.CONNECTOR_CAPABILITIES_FETCH_COUNT, 0);
        assertCounterIncrement(InternalCounters.CONNECTOR_SCHEMA_PARSE_COUNT, 0);
        assertCounterIncrement(InternalCounters.CONNECTOR_INSTANCE_INITIALIZATION_COUNT, 0);
        assertCounterIncrement(InternalCounters.CONNECTOR_INSTANCE_CONFIGURATION_COUNT, 0);
        assertCounterIncrement(InternalCounters.RESOURCE_SCHEMA_PARSE_COUNT, 0);
        PrismObject<ResourceType> resource = getResource();
        if (resource != null) {
            assertResourceVersionIncrement(resource, 0);
            assertSchemaMetadataUnchanged(resource);
            assertConnectorInstanceUnchanged(resource);
        }

        display("Resource cache", InternalMonitor.getResourceCacheStats());
        // We do not assert hits, there may be a lot of them
        assertResourceCacheMissesIncrement(0);
    }

    protected PrismObject<ResourceType> getResource() {
        return null;
    }

    protected void assertProvisioningNotFound(String oid) throws CommunicationException, SchemaException, ConfigurationException, SecurityViolationException, ExpressionEvaluationException {
        Task task = createTask("assertProvisioningNotFound");
        OperationResult result = task.getResult();
        try {
            provisioningService.getObject(ShadowType.class, oid, null, task, result);
            assertNotReached();
        } catch (ObjectNotFoundException e) {
            // expected
        }
        assertFailure(result);
    }

    protected void assertProvisioningFutureNotFound(String oid) throws CommunicationException, SchemaException, ConfigurationException, SecurityViolationException, ExpressionEvaluationException {
        Task task = createTask("assertProvisioningFutureNotFound");
        OperationResult result = task.getResult();
        Collection<SelectorOptions<GetOperationOptions>> options =  SelectorOptions.createCollection(GetOperationOptions.createPointInTimeType(PointInTimeType.FUTURE));
        try {
            provisioningService.getObject(ShadowType.class, oid, options, task, result);
            assertNotReached();
        } catch (ObjectNotFoundException e) {
            // expected
        }
        assertFailure(result);
    }

    protected ShadowAsserter<Void> assertShadowProvisioning(String oid) throws ObjectNotFoundException, CommunicationException, SchemaException, ConfigurationException, SecurityViolationException, ExpressionEvaluationException {
        Task task = createTask("assertShadowProvisioning");
        OperationResult result = task.getResult();
        PrismObject<ShadowType> shadow = provisioningService.getObject(ShadowType.class, oid, null, task, result);
        assertSuccess(result);
        ShadowAsserter<Void> asserter = ShadowAsserter.forShadow(shadow, "provisioning");
        asserter
            .display();
        return asserter;
    }

    protected ShadowAsserter<Void> assertShadowNoFetch(String oid) throws ObjectNotFoundException, CommunicationException, SchemaException, ConfigurationException, SecurityViolationException, ExpressionEvaluationException {
        Task task = createTask("assertShadowProvisioning");
        OperationResult result = task.getResult();
        Collection<SelectorOptions<GetOperationOptions>> options =  SelectorOptions.createCollection(GetOperationOptions.createNoFetch());
        PrismObject<ShadowType> shadow = provisioningService.getObject(ShadowType.class, oid, options, task, result);
        assertSuccess(result);
        ShadowAsserter<Void> asserter = ShadowAsserter.forShadow(shadow, "noFetch");
        asserter
            .display();
        return asserter;
    }

    protected ShadowAsserter<Void> assertShadowFuture(String oid) throws ObjectNotFoundException, CommunicationException, SchemaException, ConfigurationException, SecurityViolationException, ExpressionEvaluationException {
        Task task = createTask("assertShadowFuture");
        OperationResult result = task.getResult();
        Collection<SelectorOptions<GetOperationOptions>> options =  SelectorOptions.createCollection(GetOperationOptions.createPointInTimeType(PointInTimeType.FUTURE));
        PrismObject<ShadowType> shadow = provisioningService.getObject(ShadowType.class, oid, options, task, result);
        assertSuccess(result);
        ShadowAsserter<Void> asserter = ShadowAsserter.forShadow(shadow, "future");
        asserter
            .display();
        return asserter;
    }

    protected ShadowAsserter<Void> assertShadowFutureNoFetch(String oid) throws ObjectNotFoundException, CommunicationException, SchemaException, ConfigurationException, SecurityViolationException, ExpressionEvaluationException {
        Task task = createTask("assertShadowFutureNoFetch");
        OperationResult result = task.getResult();
        GetOperationOptions rootOptions = GetOperationOptions.createPointInTimeType(PointInTimeType.FUTURE);
        rootOptions.setNoFetch(true);
        Collection<SelectorOptions<GetOperationOptions>> options =  SelectorOptions.createCollection(rootOptions);
        PrismObject<ShadowType> shadow = provisioningService.getObject(ShadowType.class, oid, options, task, result);
        assertSuccess(result);
        ShadowAsserter<Void> asserter = ShadowAsserter.forShadow(shadow, "future,noFetch");
        asserter
            .display();
        return asserter;
    }
}
