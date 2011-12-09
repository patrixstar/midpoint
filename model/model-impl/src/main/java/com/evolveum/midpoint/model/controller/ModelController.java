/*
 * Copyright (c) 2011 Evolveum
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://www.opensource.org/licenses/cddl1 or
 * CDDLv1.0.txt file in the source code distribution.
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 *
 * Portions Copyrighted 2011 [name of copyright owner]
 */
package com.evolveum.midpoint.model.controller;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.xml.bind.JAXBException;
import javax.xml.namespace.QName;

import org.apache.commons.lang.NotImplementedException;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.evolveum.midpoint.common.Utils;
import com.evolveum.midpoint.common.refinery.RefinedResourceSchema;
import com.evolveum.midpoint.common.refinery.ResourceAccountType;
import com.evolveum.midpoint.model.AccountSyncContext;
import com.evolveum.midpoint.model.ChangeExecutor;
import com.evolveum.midpoint.model.SyncContext;
import com.evolveum.midpoint.model.api.ModelService;
import com.evolveum.midpoint.model.api.hooks.ChangeHook;
import com.evolveum.midpoint.model.api.hooks.HookOperationMode;
import com.evolveum.midpoint.model.api.hooks.HookRegistry;
import com.evolveum.midpoint.model.importer.ImportAccountsFromResourceTaskHandler;
import com.evolveum.midpoint.model.importer.ObjectImporter;
import com.evolveum.midpoint.model.synchronizer.UserSynchronizer;
import com.evolveum.midpoint.provisioning.api.ProvisioningService;
import com.evolveum.midpoint.repo.api.RepositoryService;
import com.evolveum.midpoint.repo.cache.RepositoryCache;
import com.evolveum.midpoint.schema.ResultArrayList;
import com.evolveum.midpoint.schema.ResultList;
import com.evolveum.midpoint.schema.SchemaRegistry;
import com.evolveum.midpoint.schema.XsdTypeConverter;
import com.evolveum.midpoint.schema.constants.ObjectTypes;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.delta.ObjectDelta;
import com.evolveum.midpoint.schema.exception.CommunicationException;
import com.evolveum.midpoint.schema.exception.ConsistencyViolationException;
import com.evolveum.midpoint.schema.exception.ExpressionEvaluationException;
import com.evolveum.midpoint.schema.exception.ObjectAlreadyExistsException;
import com.evolveum.midpoint.schema.exception.ObjectNotFoundException;
import com.evolveum.midpoint.schema.exception.SchemaException;
import com.evolveum.midpoint.schema.exception.SystemException;
import com.evolveum.midpoint.schema.holder.XPathHolder;
import com.evolveum.midpoint.schema.processor.ChangeType;
import com.evolveum.midpoint.schema.processor.MidPointObject;
import com.evolveum.midpoint.schema.processor.Schema;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.result.OperationResultStatus;
import com.evolveum.midpoint.schema.util.DebugUtil;
import com.evolveum.midpoint.schema.util.JAXBUtil;
import com.evolveum.midpoint.schema.util.ObjectResolver;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.schema.util.ResourceObjectShadowUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.task.api.TaskManager;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_1.AccountShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ConnectorHostType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ConnectorType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ImportOptionsType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ObjectModificationType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ObjectReferenceType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.PagingType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.PropertyAvailableValuesListType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.PropertyModificationType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.PropertyModificationTypeType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.PropertyReferenceListType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.QueryType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ResourceObjectShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ResourceType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.UserType;

/**
 * This used to be an interface, but it was switched to class for simplicity. I don't expect that
 * the implementation of the controller will be ever replaced. In extreme case the whole Model will
 * be replaced by a different implementation, but not just the controller.
 * <p/>
 * However, the common way to extend the functionality will be the use of hooks that are implemented
 * here.
 * <p/>
 * Great deal of code is copied from the old ModelControllerImpl.
 *
 * @author lazyman
 * @author Radovan Semancik
 */
@Component
public class ModelController implements ModelService {

    // Constants for OperationResult
    public static final String CLASS_NAME_WITH_DOT = ModelController.class.getName() + ".";
    public static final String SEARCH_OBJECTS_IN_REPOSITORY = CLASS_NAME_WITH_DOT + "searchObjectsInRepository";
    public static final String SEARCH_OBJECTS_IN_PROVISIONING = CLASS_NAME_WITH_DOT + "searchObjectsInProvisioning";
    public static final String ADD_OBJECT_WITH_EXCLUSION = CLASS_NAME_WITH_DOT + "addObjectWithExclusion";
    public static final String MODIFY_OBJECT_WITH_EXCLUSION = CLASS_NAME_WITH_DOT + "modifyObjectWithExclusion";
    public static final String CHANGE_ACCOUNT = CLASS_NAME_WITH_DOT + "changeAccount";

    public static final String GET_SYSTEM_CONFIGURATION = CLASS_NAME_WITH_DOT + "getSystemConfiguration";
    public static final String RESOLVE_USER_ATTRIBUTES = CLASS_NAME_WITH_DOT + "resolveUserAttributes";
    public static final String RESOLVE_ACCOUNT_ATTRIBUTES = CLASS_NAME_WITH_DOT + "resolveAccountAttributes";
    public static final String CREATE_ACCOUNT = CLASS_NAME_WITH_DOT + "createAccount";
    public static final String UPDATE_ACCOUNT = CLASS_NAME_WITH_DOT + "updateAccount";
    public static final String PROCESS_USER_TEMPLATE = CLASS_NAME_WITH_DOT + "processUserTemplate";

    private static final Trace LOGGER = TraceManager.getTrace(ModelController.class);

    @Autowired(required = true)
    private UserSynchronizer userSynchronizer;

    @Autowired(required = true)
    private SchemaRegistry schemaRegistry;

    @Autowired(required = true)
    private ProvisioningService provisioning;

    @Autowired(required = true)
    private ObjectResolver objectResolver;

    @Autowired(required = true)
    @Qualifier("cacheRepositoryService")
    private transient RepositoryService cacheRepositoryService;
    
    @Autowired
    private ChangeExecutor changeExecutor;

    @Autowired(required = true)
    private transient ImportAccountsFromResourceTaskHandler importAccountsFromResourceTaskHandler;

    @Autowired(required = true)
    private transient ObjectImporter objectImporter;

    @Autowired(required = false)
    private HookRegistry hookRegistry;
    
    @Autowired(required = true)
    private TaskManager taskManager;

    @Override
    public <T extends ObjectType> T getObject(Class<T> clazz, String oid, PropertyReferenceListType resolve,
                                              OperationResult result) throws ObjectNotFoundException, SchemaException {
        Validate.notEmpty(oid, "Object oid must not be null or empty.");
        Validate.notNull(result, "Operation result must not be null.");
        Validate.notNull(clazz, "Object class must not be null.");
        RepositoryCache.enter();

        T object = null;
        try {
            OperationResult subResult = result.createSubresult(GET_OBJECT);
            subResult.addParams(new String[]{"oid", "resolve", "class"}, oid, resolve, clazz);

            ObjectReferenceType ref = new ObjectReferenceType();
            ref.setOid(oid);
            ref.setType(ObjectTypes.getObjectType(clazz).getTypeQName());
            object = (T) objectResolver.resolve(ref, "getObject", subResult);

            //todo will be fixed after another interface cleanup
            //fix for resolving object properties.
            resolveObjectAttributes(object, resolve, subResult);
        } finally {
            RepositoryCache.exit();
        }
        return object;
    }

    protected void resolveObjectAttributes(ObjectType object, PropertyReferenceListType resolve,
                                           OperationResult result) {
        if (object == null || resolve == null) {
            return;
        }

        if (object instanceof UserType) {
            resolveUserAttributes((UserType) object, resolve, result);
        } else if (object instanceof AccountShadowType) {
            resolveAccountAttributes((AccountShadowType) object, resolve, result);
        }
    }

    private void resolveUserAttributes(UserType user, PropertyReferenceListType resolve,
                                       OperationResult result) {
        if (!Utils.haveToResolve("Account", resolve)) {
            return;
        }

        List<ObjectReferenceType> refToBeDeleted = new ArrayList<ObjectReferenceType>();
        for (ObjectReferenceType accountRef : user.getAccountRef()) {
            OperationResult subResult = result.createSubresult(RESOLVE_USER_ATTRIBUTES);
            subResult.addParams(new String[]{"user", "accountRef"}, user, accountRef);
            try {
                AccountShadowType account = getObject(AccountShadowType.class, accountRef.getOid(), resolve,
                        subResult);
                user.getAccount().add(account);
                refToBeDeleted.add(accountRef);
                subResult.recordSuccess();
            } catch (Exception ex) {
                LoggingUtils.logException(LOGGER, "Couldn't resolve account with oid {}", ex,
                        accountRef.getOid());
                subResult.recordFatalError(
                        "Couldn't resolve account with oid '" + accountRef.getOid() + "'.", ex);
            } finally {
                subResult.computeStatus("Couldn't resolve account with oid '" + accountRef.getOid() + "'.");
            }
        }
        user.getAccountRef().removeAll(refToBeDeleted);
    }

    private void resolveAccountAttributes(AccountShadowType account, PropertyReferenceListType resolve,
                                          OperationResult result) {
        if (!Utils.haveToResolve("Resource", resolve)) {
            return;
        }

        ObjectReferenceType reference = account.getResourceRef();
        if (reference == null || StringUtils.isEmpty(reference.getOid())) {
            LOGGER.debug("Skipping resolving resource for account {}, resource reference is null or "
                    + "doesn't contain oid.", new Object[]{account.getName()});
            return;
        }
        OperationResult subResult = result.createSubresult(RESOLVE_ACCOUNT_ATTRIBUTES);
        subResult.addParams(new String[]{"account", "resolve"}, account, resolve);
        try {
            ResourceType resource = getObject(ResourceType.class, account.getResourceRef().getOid(), resolve,
                    result);
            account.setResource(resource);
            account.setResourceRef(null);
            subResult.recordSuccess();
        } catch (Exception ex) {
            LoggingUtils
                    .logException(LOGGER, "Couldn't resolve resource with oid {}", ex, reference.getOid());
            subResult
                    .recordFatalError("Couldn't resolve resource with oid '" + reference.getOid() + "'.", ex);
        } finally {
            subResult.computeStatus("Couldn't resolve resource with oid '" + reference.getOid() + "'.");
        }
    }

    @Override
    public <T extends ObjectType> String addObject(T object, OperationResult parentResult)
            throws ObjectAlreadyExistsException, ObjectNotFoundException, SchemaException, ExpressionEvaluationException, CommunicationException {
        Validate.notNull(object, "Object must not be null.");
        Validate.notNull(parentResult, "Result type must not be null.");

        if (!(object instanceof ResourceObjectShadowType)) {
            Validate.notEmpty(object.getName(), "Object name must not be null or empty.");
        }

        OperationResult result = parentResult.createSubresult(ADD_OBJECT);
        result.addParams(new String[]{"object"}, object);
        String oid = null;
        
        Task task = taskManager.createTaskInstance();		// in the future, this task instance will come from GUI 

        RepositoryCache.enter();
        try {
            LOGGER.trace("Entering addObject with {}", ObjectTypeUtil.toShortString(object));

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace(JAXBUtil.silentMarshalWrap(object));
            }

            ObjectDelta<T> objectDelta = null;
            Schema commonSchema = schemaRegistry.getCommonSchema();

            if (object instanceof UserType) {
                UserType userType = (UserType) object;

                SyncContext syncContext = userTypeAddToContext(userType, commonSchema, result);
                objectDelta = (ObjectDelta<T>) syncContext.getUserPrimaryDelta();

                if (executePreChangePrimary(objectDelta, task, result) != HookOperationMode.FOREGROUND)
                	return null;
                
                userSynchronizer.synchronizeUser(syncContext, result);
                
                if (executePreChangeSecondary(syncContext.getAllChanges(), task, result) != HookOperationMode.FOREGROUND)
                	return null;
                
                changeExecutor.executeChanges(syncContext, result);
                
                executePostChange(syncContext.getAllChanges(), task, result);	// here we don't care about the result (FOREGROUND/BACKGROUND)

            } else {

                objectDelta = new ObjectDelta<T>((Class<T>) object.getClass(), ChangeType.ADD);
                MidPointObject<T> mpObject = commonSchema.parseObjectType(object);
                objectDelta.setObjectToAdd(mpObject);

                if (executePreChangePrimary(objectDelta, task, result) != HookOperationMode.FOREGROUND)
                	return null;

                LOGGER.trace("Executing GENERIC change " + objectDelta);
                changeExecutor.executeChange(objectDelta, result);

                executePostChange(objectDelta, task, result);
            }

            oid = objectDelta.getOid();
                        
            result.computeStatus();

        } catch (ExpressionEvaluationException ex) {
            result.recordFatalError(ex);
            LOGGER.error("model.addObject failed: {}", ex.getMessage(), ex);
            throw ex;
        } catch (SchemaException ex) {
            result.recordFatalError(ex);
            LOGGER.error("model.addObject failed: {}", ex.getMessage(), ex);
            throw ex;
        } catch (ObjectNotFoundException ex) {
            result.recordFatalError(ex);
            LOGGER.error("model.addObject failed: {}", ex.getMessage(), ex);
            throw ex;
        } catch (ObjectAlreadyExistsException ex) {
            result.recordFatalError(ex);
            LOGGER.error("model.addObject failed: {}", ex.getMessage(), ex);
            throw ex;
        } catch (RuntimeException ex) {
            result.recordFatalError(ex);
            LOGGER.error("model.addObject failed: {}", ex.getMessage(), ex);
            throw ex;
        } finally {
            RepositoryCache.exit();
        }

        return oid;
    }
    
    /**
     * Executes preChangePrimary on all registered hooks.
     * Parameters (delta, task, result) are simply passed to these hooks.
     * 
     * @return FOREGROUND, if all hooks returns FOREGROUND; BACKGROUND if not.
     * 
     * TODO in the future, maybe some error status returned from hooks should be considered here.
     */
    private HookOperationMode executePreChangePrimary(Collection<ObjectDelta<?>> objectDeltas, Task task, OperationResult result) {
    	 
    	HookOperationMode resultMode = HookOperationMode.FOREGROUND;
        if (hookRegistry != null) {
        	for (ChangeHook hook : hookRegistry.getAllChangeHooks()) {
        		HookOperationMode mode = hook.preChangePrimary(objectDeltas, task, result);
        		if (mode == HookOperationMode.BACKGROUND)
        			resultMode = HookOperationMode.BACKGROUND;
        	}
        }
        return resultMode;
    }
    
    /**
     * A convenience method when there is only one delta.
     */
    private HookOperationMode executePreChangePrimary(ObjectDelta<?> objectDelta, Task task, OperationResult result) {
    	Collection<ObjectDelta<?>> deltas = new ArrayList<ObjectDelta<?>>();
    	deltas.add(objectDelta);
    	return executePreChangePrimary(deltas, task, result);
    }

    /**
     * Executes preChangeSecondary. See above for comments.
     */
    private HookOperationMode executePreChangeSecondary(Collection<ObjectDelta<?>> objectDeltas, Task task, OperationResult result) {
   	 
    	HookOperationMode resultMode = HookOperationMode.FOREGROUND;
        if (hookRegistry != null) {
        	for (ChangeHook hook : hookRegistry.getAllChangeHooks()) {
        		HookOperationMode mode = hook.preChangeSecondary(objectDeltas, task, result);
        		if (mode == HookOperationMode.BACKGROUND)
        			resultMode = HookOperationMode.BACKGROUND;
        	}
        }
        return resultMode;
    }
    
    /**
     * Executes postChange. See above for comments. 
     */
    private HookOperationMode executePostChange(Collection<ObjectDelta<?>> objectDeltas, Task task, OperationResult result) {
      	 
    	HookOperationMode resultMode = HookOperationMode.FOREGROUND;
        if (hookRegistry != null) {
        	for (ChangeHook hook : hookRegistry.getAllChangeHooks()) {
        		HookOperationMode mode = hook.postChange(objectDeltas, task, result);
        		if (mode == HookOperationMode.BACKGROUND)
        			resultMode = HookOperationMode.BACKGROUND;
        	}
        }
        return resultMode;
    }

    /**
     * A convenience method when there is only one delta.
     */
    private HookOperationMode executePostChange(ObjectDelta<?> objectDelta, Task task, OperationResult result) {
    	Collection<ObjectDelta<?>> deltas = new ArrayList<ObjectDelta<?>>();
    	deltas.add(objectDelta);
    	return executePostChange(deltas, task, result);
    }

    private SyncContext userTypeAddToContext(UserType userType, Schema commonSchema, OperationResult result) throws SchemaException, ObjectNotFoundException, CommunicationException {

        SyncContext syncContext = new SyncContext();

        // Convert all <account> instances to syncContext or <accountRef>s
        if (userType.getAccount() != null) {
            for (AccountShadowType accountType : userType.getAccount()) {
                String accountOid = accountType.getOid();
                if (accountOid != null) {
                    // link to existing account expressed as <account> instead of <accountRef>
                    ObjectReferenceType accountRef = ObjectTypeUtil.createObjectRef(accountType);
                    userType.getAccountRef().add(accountRef);
                } else {
                    // new account (no OID)
                    addAccountToContext(syncContext, accountType, ChangeType.ADD, commonSchema, result);
                }
                userType.getAccount().remove(accountType);
            }
        }

        ObjectDelta<UserType> userDelta = new ObjectDelta<UserType>(UserType.class, ChangeType.ADD);
        MidPointObject<UserType> mpUser = commonSchema.parseObjectType(userType);
        userDelta.setObjectToAdd(mpUser);

        syncContext.setUserOld(null);
        syncContext.setUserNew(mpUser);
        syncContext.setUserPrimaryDelta(userDelta);

        return syncContext;
    }

    @Override
    public <T extends ObjectType> ResultList<T> listObjects(Class<T> objectType, PagingType paging,
                                                            OperationResult result) {
        Validate.notNull(objectType, "Object type must not be null.");
        Validate.notNull(result, "Result type must not be null.");
        ModelUtils.validatePaging(paging);

        RepositoryCache.enter();

        ResultList<T> list = null;

        try {
            if (paging == null) {
                LOGGER.trace("Listing objects of type {} (no paging).", objectType);
            } else {
                LOGGER.trace(
                        "Listing objects of type {} offset {} count {} ordered {} by {}.",
                        new Object[]{objectType, paging.getOffset(), paging.getMaxSize(),
                                paging.getOrderDirection(), paging.getOrderBy()});
            }

            OperationResult subResult = result.createSubresult(LIST_OBJECTS);
            subResult.addParams(new String[]{"objectType", "paging"}, objectType, paging);

            try {
                if (ObjectTypes.isObjectTypeManagedByProvisioning(objectType)) {
                    LOGGER.trace("Listing objects from provisioning.");
                    list = provisioning.listObjects(objectType, paging, subResult);
                } else {
                    LOGGER.trace("Listing objects from repository.");
                    list = cacheRepositoryService.listObjects(objectType, paging, subResult);
                }
                subResult.recordSuccess();
            } catch (Exception ex) {
                LoggingUtils.logException(LOGGER, "Couldn't list objects", ex);
                subResult.recordFatalError("Couldn't list objects.", ex);
                throw new SystemException(ex.getMessage(), ex);
            } finally {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace(subResult.dump(false));
                }
            }

            if (list == null) {
                list = new ResultArrayList<T>();
                list.setTotalResultCount(0);
            }

            LOGGER.trace("Returning {} objects.", new Object[]{list.size()});

        } finally {
            RepositoryCache.exit();
        }
        return list;
    }

    @Override
    public <T extends ObjectType> ResultList<T> searchObjects(Class<T> type, QueryType query,
                                                              PagingType paging, OperationResult result) throws SchemaException, ObjectNotFoundException {
        Validate.notNull(type, "Object type must not be null.");
        Validate.notNull(query, "Query must not be null.");
        Validate.notNull(result, "Result type must not be null.");
        ModelUtils.validatePaging(paging);

        RepositoryCache.enter();

        ResultList<T> list = null;

        try {
            if (paging == null) {
                LOGGER.trace("Searching objects with null paging (query in TRACE).");
            } else {
                LOGGER.trace("Searching objects from {} to {} ordered {} by {} (query in TRACE).",
                        new Object[]{paging.getOffset(), paging.getMaxSize(), paging.getOrderDirection(),
                                paging.getOrderBy()});
            }
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace(JAXBUtil.silentMarshalWrap(query));
            }

            boolean searchInProvisioning = ObjectTypes.isClassManagedByProvisioning(type);
            String operationName = searchInProvisioning ? SEARCH_OBJECTS_IN_PROVISIONING
                    : SEARCH_OBJECTS_IN_REPOSITORY;
            OperationResult subResult = result.createSubresult(operationName);
            subResult.addParams(new String[]{"query", "paging", "searchInProvisioning"}, query, paging,
                    searchInProvisioning);

            try {
                if (searchInProvisioning) {
                    list = provisioning.searchObjects(type, query, paging, subResult);
                } else {
                    list = cacheRepositoryService.searchObjects(type, query, paging, subResult);
                }
                subResult.recordSuccess();
            } catch (Exception ex) {
                String message;
                if (!searchInProvisioning) {
                    message = "Couldn't search objects in repository";
                } else {
                    message = "Couldn't search objects in provisioning";
                }
                LoggingUtils.logException(LOGGER, message, ex);
                subResult.recordFatalError(message, ex);
            } finally {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace(subResult.dump(false));
                }
            }

            if (list == null) {
                list = new ResultArrayList<T>();
                list.setTotalResultCount(0);
            }

        } finally {
            RepositoryCache.exit();
        }

        return list;
    }

    @Override
    public <T extends ObjectType> void modifyObject(Class<T> type, ObjectModificationType change,
                                                    OperationResult parentResult) throws ObjectNotFoundException, SchemaException, ExpressionEvaluationException, CommunicationException {

        Validate.notNull(change, "Object modification must not be null.");
        Validate.notEmpty(change.getOid(), "Change oid must not be null or empty.");
        Validate.notNull(parentResult, "Result type must not be null.");

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Modifying object with oid {}",
                    new Object[]{change.getOid()});
            LOGGER.trace(JAXBUtil.silentMarshalWrap(change));
        }

        if (change.getPropertyModification().isEmpty()) {
            return;
        }

        OperationResult result = parentResult.createSubresult(MODIFY_OBJECT);
        result.addParams(new String[]{"change"}, change);

        RepositoryCache.enter();

        try {

            ObjectDelta<T> objectDelta = null;
            Schema commonSchema = schemaRegistry.getCommonSchema();

            if (UserType.class.isAssignableFrom(type)) {
                SyncContext syncContext = userTypeModifyToContext(change, commonSchema, result);

                userSynchronizer.synchronizeUser(syncContext, parentResult);

                try {
                	changeExecutor.executeChanges(syncContext, parentResult);
                    result.computeStatus();
                } catch (ObjectAlreadyExistsException e) {
                    // This should not happen
                    // TODO Better handling
                    throw new SystemException(e.getMessage(), e);
                }

            } else {
                objectDelta = ObjectDelta.createDelta(type, change, commonSchema);
                Collection<ObjectDelta<?>> changes = new HashSet<ObjectDelta<?>>();
                changes.add(objectDelta);

                try {
                	changeExecutor.executeChanges(changes, parentResult);
                    result.computeStatus();
                } catch (ObjectAlreadyExistsException e) {
                    // This should not happen
                    // TODO Better handling
                    throw new SystemException(e.getMessage(), e);
                }
            }

        } catch (ExpressionEvaluationException ex) {
            LOGGER.error("model.modifyObject failed: {}", ex.getMessage(), ex);
            result.recordFatalError(ex);
            throw ex;
        } catch (ObjectNotFoundException ex) {
            LOGGER.error("model.modifyObject failed: {}", ex.getMessage(), ex);
            result.recordFatalError(ex);
            throw ex;
        } catch (SchemaException ex) {
            LOGGER.error("model.modifyObject failed: {}", ex.getMessage(), ex);
            logDebugChange(type, change);
            result.recordFatalError(ex);
            throw ex;
        } catch (RuntimeException ex) {
            LOGGER.error("model.modifyObject failed: {}", ex.getMessage(), ex);
            logDebugChange(type, change);
            result.recordFatalError(ex);
            throw ex;
        } finally {
            RepositoryCache.exit();
        }
    }

    private void logDebugChange(Class<?> type, ObjectModificationType change) {
        if (LOGGER.isDebugEnabled()) {
            try {
                LOGGER.debug("model.modifyObject class={}, change:\n{}", type.getName(),
                        JAXBUtil.marshalWrap(change, SchemaConstants.C_OBJECT_MODIFICATION));
            } catch (JAXBException ex2) {
                LOGGER.error("model.modifyObject error marshalling the 'change' parameter to log: {}", ex2.getMessage(), ex2);
            }
        }
    }

    private SyncContext userTypeModifyToContext(ObjectModificationType change, Schema commonSchema, OperationResult result) throws SchemaException, ObjectNotFoundException, CommunicationException {
        SyncContext syncContext = new SyncContext();

        Iterator<PropertyModificationType> i = change.getPropertyModification().iterator();
        while (i.hasNext()) {
            PropertyModificationType propModType = i.next();
            XPathHolder propXPath = new XPathHolder(propModType.getPath());
            if (propXPath.isEmpty() &&
                    !propModType.getValue().getAny().isEmpty() &&
                    JAXBUtil.getElementQName(propModType.getValue().getAny().get(0)).equals(SchemaConstants.I_ACCOUNT)) {

                if (propModType.getModificationType() == PropertyModificationTypeType.add) {

                    for (Object element : propModType.getValue().getAny()) {
                        AccountShadowType accountShadowType = XsdTypeConverter.toJavaValue(element, AccountShadowType.class);
                        if (accountShadowType == null) {
                            throw new SchemaException("Unable to parse account shadow in the modification: unknown error (null was returned)");
                        }
                        ModelUtils.unresolveResourceObjectShadow(accountShadowType);

                        addAccountToContext(syncContext, accountShadowType, ChangeType.ADD, commonSchema, result);
                    }

                } else {
                    throw new UnsupportedOperationException("Modification type " + propModType.getModificationType() + " with full <account> is not supported");
                }

                i.remove();
            }

        }

        // TODO? userOld?

        syncContext.setUserOld(null);
        syncContext.setUserNew(null);
        ObjectDelta<UserType> userDelta = ObjectDelta.createDelta(UserType.class, change, commonSchema);
        syncContext.setUserPrimaryDelta(userDelta);

        return syncContext;
    }

    private void addAccountToContext(SyncContext syncContext, AccountShadowType accountType,
                                     ChangeType changeType, Schema commonSchema, OperationResult result) throws SchemaException, ObjectNotFoundException, CommunicationException {

        String resourceOid = ResourceObjectShadowUtil.getResourceOid(accountType);
        if (resourceOid == null) {
            throw new SchemaException("Account shadow does not contain resource OID");
        }
        // We don't try to use resource that may be embedded in the account. This is pure XML and does not contain
        // parsed schema. Fetching cached resource from provisioning is much more efficient
        ResourceType resourceType = provisioning.getObject(ResourceType.class, resourceOid, null, result);
        RefinedResourceSchema refinedSchema = RefinedResourceSchema.getRefinedSchema(resourceType, schemaRegistry);
        syncContext.rememberResource(resourceType);

        MidPointObject<AccountShadowType> mpAccount = refinedSchema.parseObjectType(accountType);
        ObjectDelta<AccountShadowType> accountDelta = new ObjectDelta<AccountShadowType>(AccountShadowType.class, changeType);
        accountDelta.setObjectToAdd(mpAccount);
        ResourceAccountType rat = new ResourceAccountType(resourceOid, accountType.getAccountType());
        AccountSyncContext accountSyncContext = syncContext.createAccountSyncContext(rat);
        accountSyncContext.setAccountPrimaryDelta(accountDelta);

    }

    @Override
    public <T extends ObjectType> void deleteObject(Class<T> clazz, String oid, OperationResult parentResult)
            throws ObjectNotFoundException, ConsistencyViolationException, CommunicationException {
        Validate.notNull(clazz, "Class must not be null.");
        Validate.notEmpty(oid, "Oid must not be null or empty.");
        Validate.notNull(parentResult, "Result type must not be null.");

        OperationResult result = parentResult.createSubresult(DELETE_OBJECT);
        result.addParams(new String[]{"oid"}, oid);

        RepositoryCache.enter();

        try {
            LOGGER.trace("Deleting object with oid {}.", new Object[]{oid});

            ObjectDelta<T> objectDelta = new ObjectDelta<T>(clazz, ChangeType.DELETE);
            objectDelta.setOid(oid);

            Collection<ObjectDelta<?>> changes = null;

            if (UserType.class.isAssignableFrom(clazz)) {
                SyncContext syncContext = new SyncContext();
                syncContext.setUserOld(null);
                syncContext.setUserNew(null);
                syncContext.setUserPrimaryDelta((ObjectDelta<UserType>) objectDelta);

                try {
                    userSynchronizer.synchronizeUser(syncContext, result);
                } catch (SchemaException e) {
                    // TODO Better handling
                    throw new SystemException(e.getMessage(), e);
                } catch (ExpressionEvaluationException e) {
                    // TODO Better handling
                    throw new SystemException(e.getMessage(), e);
                }

                changes = syncContext.getAllChanges();
            } else {
                changes = new HashSet<ObjectDelta<?>>();
                changes.add(objectDelta);
            }

            try {
            	changeExecutor.executeChanges(changes, result);
                result.computeStatus();
            } catch (ObjectAlreadyExistsException e) {
                // TODO Better handling
                throw new SystemException(e.getMessage(), e);
            } catch (SchemaException e) {
                // TODO Better handling
                throw new SystemException(e.getMessage(), e);
            }

        } catch (ObjectNotFoundException ex) {
            LOGGER.error("model.deleteObject failed: {}", ex.getMessage(), ex);
            result.recordFatalError(ex);
            throw ex;
        } catch (CommunicationException ex) {
            LOGGER.error("model.deleteObject failed: {}", ex.getMessage(), ex);
            result.recordFatalError(ex);
            throw ex;
        } catch (RuntimeException ex) {
            LOGGER.error("model.deleteObject failed: {}", ex.getMessage(), ex);
            result.recordFatalError(ex);
            throw ex;
        } finally {
            RepositoryCache.exit();
        }
    }


    @Override
    public PropertyAvailableValuesListType getPropertyAvailableValues(String oid,
                                                                      PropertyReferenceListType properties, OperationResult result) {
        Validate.notEmpty(oid, "Oid must not be null or empty.");
        Validate.notNull(properties, "Property reference list must not be null.");
        Validate.notNull(result, "Result type must not be null.");

        RepositoryCache.enter();
        LOGGER.trace("Getting property available values for object with oid {} (properties in TRACE).",
                new Object[]{oid});
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace(DebugUtil.prettyPrint(properties));
        }

        RepositoryCache.exit();
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    @Override
    public UserType listAccountShadowOwner(String accountOid, OperationResult result)
            throws ObjectNotFoundException {
        Validate.notEmpty(accountOid, "Account oid must not be null or empty.");
        Validate.notNull(result, "Result type must not be null.");

        RepositoryCache.enter();

        UserType user = null;

        try {
            LOGGER.trace("Listing account shadow owner for account with oid {}.", new Object[]{accountOid});

            OperationResult subResult = result.createSubresult(LIST_ACCOUNT_SHADOW_OWNER);
            subResult.addParams(new String[]{"accountOid"}, accountOid);

            try {
                user = cacheRepositoryService.listAccountShadowOwner(accountOid, subResult);
                subResult.recordSuccess();
            } catch (ObjectNotFoundException ex) {
                LoggingUtils.logException(LOGGER, "Account with oid {} doesn't exists", ex, accountOid);
                subResult.recordFatalError("Account with oid '" + accountOid + "' doesn't exists", ex);
                throw ex;
            } catch (Exception ex) {
                LoggingUtils.logException(LOGGER, "Couldn't list account shadow owner from repository"
                        + " for account with oid {}", ex, accountOid);
                subResult.recordFatalError("Couldn't list account shadow owner for account with oid '"
                        + accountOid + "'.", ex);
            } finally {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace(subResult.dump(false));
                }
            }

        } finally {
            RepositoryCache.exit();
        }

        return user;
    }

    @Override
    public <T extends ResourceObjectShadowType> ResultList<T> listResourceObjectShadows(String resourceOid,
                                                                                        Class<T> resourceObjectShadowType, OperationResult result) throws ObjectNotFoundException {
        Validate.notEmpty(resourceOid, "Resource oid must not be null or empty.");
        Validate.notNull(result, "Result type must not be null.");

        RepositoryCache.enter();

        ResultList<T> list = null;

        try {
            LOGGER.trace("Listing resource object shadows \"{}\" for resource with oid {}.", new Object[]{
                    resourceObjectShadowType, resourceOid});

            OperationResult subResult = result.createSubresult(LIST_RESOURCE_OBJECT_SHADOWS);
            subResult.addParams(new String[]{"resourceOid", "resourceObjectShadowType"}, resourceOid,
                    resourceObjectShadowType);

            try {
                list = cacheRepositoryService.listResourceObjectShadows(resourceOid, resourceObjectShadowType,
                        subResult);
                subResult.recordSuccess();
            } catch (ObjectNotFoundException ex) {
                subResult.recordFatalError("Resource with oid '" + resourceOid + "' was not found.", ex);
                RepositoryCache.exit();
                throw ex;
            } catch (Exception ex) {
                LoggingUtils.logException(LOGGER, "Couldn't list resource object shadows type "
                        + "{} from repository for resource, oid {}", ex, resourceObjectShadowType, resourceOid);
                subResult.recordFatalError(
                        "Couldn't list resource object shadows type '" + resourceObjectShadowType
                                + "' from repository for resource, oid '" + resourceOid + "'.", ex);
            } finally {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace(subResult.dump(false));
                }
            }

            if (list == null) {
                list = new ResultArrayList<T>();
                list.setTotalResultCount(0);
            }

        } finally {
            RepositoryCache.exit();
        }

        return list;
    }

    @Override
    public ResultList<? extends ResourceObjectShadowType> listResourceObjects(String resourceOid,
                                                                              QName objectClass, PagingType paging, OperationResult result) throws SchemaException,
            ObjectNotFoundException, CommunicationException {
        Validate.notEmpty(resourceOid, "Resource oid must not be null or empty.");
        Validate.notNull(objectClass, "Object type must not be null.");
        Validate.notNull(paging, "Paging must not be null.");
        Validate.notNull(result, "Result type must not be null.");
        ModelUtils.validatePaging(paging);

        RepositoryCache.enter();

        ResultList<? extends ResourceObjectShadowType> list = null;

        try {
            LOGGER.trace(
                    "Listing resource objects {} from resource, oid {}, from {} to {} ordered {} by {}.",
                    new Object[]{objectClass, resourceOid, paging.getOffset(), paging.getMaxSize(),
                            paging.getOrderDirection(), paging.getOrderDirection()});

            OperationResult subResult = result.createSubresult(LIST_RESOURCE_OBJECTS);
            subResult.addParams(new String[]{"resourceOid", "objectType", "paging"}, resourceOid, objectClass,
                    paging);

            try {

                list = provisioning.listResourceObjects(resourceOid, objectClass, paging, subResult);

            } catch (SchemaException ex) {
                RepositoryCache.exit();
                subResult.recordFatalError("Schema violation");
                throw ex;
            } catch (CommunicationException ex) {
                RepositoryCache.exit();
                subResult.recordFatalError("Communication error");
                throw ex;
            } catch (ObjectNotFoundException ex) {
                RepositoryCache.exit();
                subResult.recordFatalError("Object not found");
                throw ex;
            }
            subResult.recordSuccess();

            if (list == null) {
                list = new ResultArrayList<ResourceObjectShadowType>();
                list.setTotalResultCount(0);
            }
        } finally {
            RepositoryCache.exit();
        }
        return list;
    }

    // This returns OperationResult instead of taking it as in/out argument.
    // This is different
    // from the other methods. The testResource method is not using
    // OperationResult to track its own
    // execution but rather to track the execution of resource tests (that in
    // fact happen in provisioning).
    @Override
    public OperationResult testResource(String resourceOid) throws ObjectNotFoundException {
        Validate.notEmpty(resourceOid, "Resource oid must not be null or empty.");
        RepositoryCache.enter();
        LOGGER.trace("Testing resource OID: {}", new Object[]{resourceOid});

        OperationResult testResult = null;
        try {
            testResult = provisioning.testResource(resourceOid);
        } catch (ObjectNotFoundException ex) {
            LOGGER.error("Error testing resource OID: {}: Object not found: {} ", new Object[]{resourceOid,
                    ex.getMessage(), ex});
            RepositoryCache.exit();
            throw ex;
        } catch (SystemException ex) {
            LOGGER.error("Error testing resource OID: {}: Object not found: {} ", new Object[]{resourceOid,
                    ex.getMessage(), ex});
            RepositoryCache.exit();
            throw ex;
        } catch (Exception ex) {
            LOGGER.error("Error testing resource OID: {}: {} ", new Object[]{resourceOid, ex.getMessage(),
                    ex});
            RepositoryCache.exit();
            throw new SystemException(ex.getMessage(), ex);
        }

        if (testResult != null) {
            LOGGER.debug("Finished testing resource OID: {}, result: {} ", resourceOid,
                    testResult.getStatus());
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Test result:\n{}", testResult.dump(false));
            }
        } else {
            LOGGER.error("Test resource returned null result");
        }
        RepositoryCache.exit();
        return testResult;
    }

    // Note: The result is in the task. No need to pass it explicitly
    @Override
    public void importAccountsFromResource(String resourceOid, QName objectClass, Task task,
                                           OperationResult parentResult) throws ObjectNotFoundException, SchemaException {
        Validate.notEmpty(resourceOid, "Resource oid must not be null or empty.");
        Validate.notNull(objectClass, "Object class must not be null.");
        Validate.notNull(task, "Task must not be null.");
        RepositoryCache.enter();
        LOGGER.trace("Launching import from resource with oid {} for object class {}.", new Object[]{
                resourceOid, objectClass});

        OperationResult result = parentResult.createSubresult(IMPORT_ACCOUNTS_FROM_RESOURCE);
        result.addParams(new String[]{"resourceOid", "objectClass", "task"}, resourceOid, objectClass,
                task);
        // TODO: add context to the result

        // Fetch resource definition from the repo/provisioning
        PropertyReferenceListType resolve = new PropertyReferenceListType();
        ResourceType resource = null;
        try {
            resource = getObject(ResourceType.class, resourceOid, resolve, result);
        } catch (ObjectNotFoundException ex) {
            result.recordFatalError("Object not found");
            RepositoryCache.exit();
            throw ex;
        }

        importAccountsFromResourceTaskHandler.launch(resource, objectClass, task, result);

        // The launch should switch task to asynchronous. It is in/out, so no
        // other action is needed

        if (task.isAsynchronous()) {
            result.recordStatus(OperationResultStatus.IN_PROGRESS, "Task running in background");
        } else {
            result.recordSuccess();
        }
        RepositoryCache.exit();
    }

    @Override
    public void importObjectsFromFile(File input, ImportOptionsType options, Task task,
                                      OperationResult parentResult) {
        // OperationResult result =
        // parentResult.createSubresult(IMPORT_OBJECTS_FROM_FILE);
        // TODO Auto-generated method stub
        RepositoryCache.enter();
        RepositoryCache.exit();
        throw new NotImplementedException();
    }

    @Override
    public void importObjectsFromStream(InputStream input, ImportOptionsType options, Task task,
                                        OperationResult parentResult) {
        RepositoryCache.enter();
        OperationResult result = parentResult.createSubresult(IMPORT_OBJECTS_FROM_STREAM);
        objectImporter.importObjects(input, options, task, result, cacheRepositoryService);
        // No need to compute status. The validator inside will do it.
        // result.computeStatus("Couldn't import object from input stream.");
        RepositoryCache.exit();
    }


    /*
      * (non-Javadoc)
      *
      * @see
      * com.evolveum.midpoint.model.api.ModelService#discoverConnectors(com.evolveum
      * .midpoint.xml.ns._public.common.common_1.ConnectorHostType,
      * com.evolveum.midpoint.common.result.OperationResult)
      */
    @Override
    public Set<ConnectorType> discoverConnectors(ConnectorHostType hostType, OperationResult parentResult)
            throws CommunicationException {
        RepositoryCache.enter();
        OperationResult result = parentResult.createSubresult(DISCOVER_CONNECTORS);
        Set<ConnectorType> discoverConnectors;
        try {
            discoverConnectors = provisioning.discoverConnectors(hostType, result);
        } catch (CommunicationException e) {
            result.recordFatalError(e.getMessage(), e);
            RepositoryCache.exit();
            throw e;
        }
        result.computeStatus("Connector discovery failed");
        RepositoryCache.exit();
        return discoverConnectors;
    }


    /*
      * (non-Javadoc)
      *
      * @see
      * com.evolveum.midpoint.model.api.ModelService#initialize(com.evolveum.
      * midpoint.common.result.OperationResult)
      */
    @Override
    public void postInit(OperationResult parentResult) {
        RepositoryCache.enter();
        OperationResult result = parentResult.createSubresult(POST_INIT);
        result.addContext(OperationResult.CONTEXT_IMPLEMENTATION_CLASS, ModelController.class);

        // TODO: initialize repository
        // TODO: initialize task manager

        // Initialize provisioning
        provisioning.postInit(result);

        result.computeStatus("Error occured during post initialization process.");

        RepositoryCache.exit();
    }

}