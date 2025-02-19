/*
 * Copyright (c) 2010-2013 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.notifications.impl;

import com.evolveum.midpoint.notifications.api.NotificationManager;
import com.evolveum.midpoint.notifications.api.OperationStatus;
import com.evolveum.midpoint.notifications.api.events.ResourceObjectEvent;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.provisioning.api.ChangeNotificationDispatcher;
import com.evolveum.midpoint.provisioning.api.ResourceOperationDescription;
import com.evolveum.midpoint.provisioning.api.ResourceOperationListener;
import com.evolveum.midpoint.repo.api.RepositoryService;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.LightweightIdentifierGenerator;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.UserType;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * @author mederly
 */
@Component
public class AccountOperationListener implements ResourceOperationListener {

    private static final Trace LOGGER = TraceManager.getTrace(AccountOperationListener.class);

    private static final String DOT_CLASS = AccountOperationListener.class.getName() + ".";

    @Autowired
    private LightweightIdentifierGenerator lightweightIdentifierGenerator;

    @Autowired
    private ChangeNotificationDispatcher provisioningNotificationDispatcher;

    @Autowired
    private NotificationManager notificationManager;

    @Autowired
    @Qualifier("cacheRepositoryService")
    private transient RepositoryService cacheRepositoryService;

    @Autowired
    private NotificationFunctionsImpl notificationsUtil;

    @PostConstruct
    public void init() {
        provisioningNotificationDispatcher.registerNotificationListener(this);
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Registered account operation notification listener.");
        }
    }

    @Override
    public String getName() {
        return "user notification account change listener";
    }

    @Override
    public void notifySuccess(ResourceOperationDescription operationDescription, Task task, OperationResult parentResult) {
        if (notificationsEnabled()) {
            notifyAny(OperationStatus.SUCCESS, operationDescription, task, parentResult.createMinorSubresult(DOT_CLASS + "notifySuccess"));
        }
    }

    private boolean notificationsEnabled() {
        if (notificationManager.isDisabled()) {
            LOGGER.trace("Notifications are temporarily disabled, exiting the hook.");
            return false;
        } else {
            return true;
        }
    }

    @Override
    public void notifyInProgress(ResourceOperationDescription operationDescription, Task task, OperationResult parentResult) {
        if (notificationsEnabled()) {
            notifyAny(OperationStatus.IN_PROGRESS, operationDescription, task, parentResult.createMinorSubresult(DOT_CLASS + "notifyInProgress"));
        }
    }

    @Override
    public void notifyFailure(ResourceOperationDescription operationDescription, Task task, OperationResult parentResult) {
        if (notificationsEnabled()) {
            notifyAny(OperationStatus.FAILURE, operationDescription, task, parentResult.createMinorSubresult(DOT_CLASS + "notifyFailure"));
        }
    }

    private void notifyAny(OperationStatus status, ResourceOperationDescription operationDescription, Task task, OperationResult result) {
        try {
            executeNotifyAny(status, operationDescription, task, result);
        } catch (RuntimeException e) {
            result.recordFatalError("An unexpected exception occurred when preparing and sending notifications: " + e.getMessage(), e);
            LoggingUtils.logException(LOGGER, "An unexpected exception occurred when preparing and sending notifications: " + e.getMessage(), e);
        }

        // todo work correctly with operationResult (in whole notification module)
        if (result.isUnknown()) {
            result.computeStatus();
        }
        result.recordSuccessIfUnknown();
//        if (LOGGER.isTraceEnabled()) {
//            LOGGER.trace("Returning operation result: " + result.dump());
//        }
    }

    private void executeNotifyAny(OperationStatus status, ResourceOperationDescription operationDescription, Task task, OperationResult result) {
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("AccountOperationListener.notify ({}) called with operationDescription = {}", status, operationDescription.debugDump());
        }

        if (operationDescription.getObjectDelta() == null) {
            LOGGER.warn("Object delta is null, exiting the change listener.");
            return;
        }

        if (operationDescription.getCurrentShadow() == null) {
            LOGGER.warn("Current shadow is null, exiting the change listener.");
            return;
        }

        // for the time being, we deal only with accounts here
        if (operationDescription.getObjectDelta().getObjectTypeClass() == null ||
                !ShadowType.class.isAssignableFrom(operationDescription.getObjectDelta().getObjectTypeClass())) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Object that was changed was not an account, exiting the operation listener (class = {})",
                        operationDescription.getObjectDelta().getObjectTypeClass());
            }
            return;
        }

        ResourceObjectEvent request = createRequest(status, operationDescription, task, result);
        notificationManager.processEvent(request, task, result);
    }

    private ResourceObjectEvent createRequest(OperationStatus status,
                                              ResourceOperationDescription operationDescription,
                                              Task task,
                                              OperationResult result) {

        ResourceObjectEvent event = new ResourceObjectEvent(lightweightIdentifierGenerator);
        event.setAccountOperationDescription(operationDescription);
        event.setOperationStatus(status);
        event.setChangeType(operationDescription.getObjectDelta().getChangeType());       // fortunately there's 1:1 mapping

        String accountOid = operationDescription.getObjectDelta().getOid();

        PrismObject<UserType> user = findRequestee(accountOid, task, result);
        if (user != null) {
            event.setRequestee(new SimpleObjectRefImpl(notificationsUtil, user.asObjectable()));
        }   // otherwise, appropriate messages were already logged

        if (task != null && task.getOwner() != null) {
            event.setRequester(new SimpleObjectRefImpl(notificationsUtil, task.getOwner()));
        } else {
            LOGGER.warn("No owner for task {}, therefore no requester will be set for event {}", task, event.getId());
        }

        if (task != null && task.getChannel() != null) {
            event.setChannel(task.getChannel());
        } else if (operationDescription.getSourceChannel() != null) {
            event.setChannel(operationDescription.getSourceChannel());
        }

        return event;
    }

//    private boolean isRequestApplicable(ResourceObjectEvent request, NotificationConfigurationEntryType entry) {
//
//        ResourceOperationDescription opDescr = request.getAccountOperationDescription();
//        OperationStatus status = request.getOperationStatus();
//        ChangeType type = opDescr.getObjectDelta().getChangeType();
//        return typeMatches(type, entry.getSituation(), opDescr) && statusMatches(status, entry.getSituation());
//    }

    private PrismObject<UserType> findRequestee(String shadowOid, Task task, OperationResult result) {
        // This is (still) a temporary solution. We need to rework it eventually.
        if (task != null && task.getRequestee() != null) {
            return task.getRequestee();
        } else if (shadowOid != null) {
            try {
                PrismObject<UserType> user = cacheRepositoryService.listAccountShadowOwner(shadowOid, result);
                LOGGER.trace("listAccountShadowOwner for shadow {} yields {}", shadowOid, user);
                return user;
            } catch (ObjectNotFoundException e) {
                LOGGER.trace("There's a problem finding account {}", shadowOid, e);
                return null;
            }
        } else {
            LOGGER.debug("There is no owner of account {} (in repo nor in task).", shadowOid);
            return null;
        }
    }
}
