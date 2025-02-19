/*
 * Copyright (c) 2010-2017 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.notifications.impl.api.transports;

import com.evolveum.midpoint.notifications.api.NotificationManager;
import com.evolveum.midpoint.notifications.api.events.Event;
import com.evolveum.midpoint.notifications.api.transports.Message;
import com.evolveum.midpoint.notifications.api.transports.Transport;
import com.evolveum.midpoint.prism.polystring.PolyString;
import com.evolveum.midpoint.repo.api.RepositoryService;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.FileConfigurationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.UserType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

import static com.evolveum.midpoint.notifications.impl.api.transports.TransportUtil.formatToFileNew;

/**
 * @author mederly
 */
@Component
public class FileTransport implements Transport {

    private static final Trace LOGGER = TraceManager.getTrace(FileTransport.class);

    private static final String NAME = "file";

    private static final String DOT_CLASS = FileTransport.class.getName() + ".";
    private static final String DEFAULT_FILE_NAME = "notifications.txt";

    @Autowired @Qualifier("cacheRepositoryService") private transient RepositoryService cacheRepositoryService;
    @Autowired private NotificationManager notificationManager;

    @PostConstruct
    public void init() {
        notificationManager.registerTransport(NAME, this);
    }

    @Override
    public void send(Message message, String transportName, Event event, Task task, OperationResult parentResult) {
        OperationResult result = parentResult.createMinorSubresult(DOT_CLASS + "send");
        FileConfigurationType fileConfig = TransportUtil.getTransportConfiguration(transportName, NAME, (c) -> c.getFile(), cacheRepositoryService, result);
        String fileName;
        if (fileConfig != null && fileConfig.getFile() != null) {
            fileName = fileConfig.getFile();
        } else {
            LOGGER.info("Notification transport configuration for '{}' was not found or has no file name configured: using default of '{}'",
                    transportName, DEFAULT_FILE_NAME);
            fileName = DEFAULT_FILE_NAME;
        }
        TransportUtil.appendToFile(fileName, formatToFileNew(message, transportName), LOGGER, result);
    }

    @Override
    public String getDefaultRecipientAddress(UserType recipient) {
        return PolyString.getOrig(recipient.getName()) + " <" + recipient.getEmailAddress() + ">";
    }

    @Override
    public String getName() {
        return NAME;
    }
}
