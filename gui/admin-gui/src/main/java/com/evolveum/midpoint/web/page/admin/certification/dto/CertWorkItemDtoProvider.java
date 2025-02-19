/*
 * Copyright (c) 2010-2015 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.web.page.admin.certification.dto;

import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.model.api.AccessCertificationService;
import com.evolveum.midpoint.prism.query.ObjectOrdering;
import com.evolveum.midpoint.prism.query.ObjectPaging;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.data.BaseSortableDataProvider;
import com.evolveum.midpoint.web.page.error.PageError;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AccessCertificationWorkItemType;
import org.apache.wicket.Component;
import org.apache.wicket.RestartResponseException;
import org.apache.wicket.extensions.markup.html.repeater.util.SortParam;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import static com.evolveum.midpoint.schema.GetOperationOptions.createResolveNames;
import static com.evolveum.midpoint.schema.SelectorOptions.createCollection;

/**
 * @author lazyman
 * @author mederly
 */
public class CertWorkItemDtoProvider extends BaseSortableDataProvider<CertWorkItemDto> {

    private static final Trace LOGGER = TraceManager.getTrace(CertWorkItemDtoProvider.class);
    private static final String DOT_CLASS = CertWorkItemDtoProvider.class.getName() + ".";
    private static final String OPERATION_SEARCH_OBJECTS = DOT_CLASS + "searchObjects";
    private static final String OPERATION_COUNT_OBJECTS = DOT_CLASS + "countObjects";

    private ObjectQuery campaignQuery;
    // case query is stored in super.query

    private boolean notDecidedOnly;
    private String reviewerOid;

    public CertWorkItemDtoProvider(Component component) {
        super(component, false);        // TODO make this cache-able
    }

    @Override
    public Iterator<CertWorkItemDto> internalIterator(long first, long count) {
        LOGGER.trace("begin::iterator() from {} count {}.", first, count);
        getAvailableData().clear();

        OperationResult result = new OperationResult(OPERATION_SEARCH_OBJECTS);
        try {
            ObjectPaging paging = createPaging(first, count);
            Task task = getPage().createSimpleTask(OPERATION_SEARCH_OBJECTS);

            ObjectQuery caseQuery = getQuery();
            caseQuery = caseQuery != null ? caseQuery.clone() : getPrismContext().queryFactory().createQuery();
            caseQuery.setPaging(paging);

            Collection<SelectorOptions<GetOperationOptions>> resolveNames = createCollection(createResolveNames());
            AccessCertificationService acs = getPage().getCertificationService();
            List<AccessCertificationWorkItemType> workitems = acs.searchOpenWorkItems(caseQuery, notDecidedOnly, resolveNames, task, result);
            for (AccessCertificationWorkItemType workItem : workitems) {
                getAvailableData().add(new CertWorkItemDto(workItem, getPage()));
            }
        } catch (Exception ex) {
            result.recordFatalError(getPage().createStringResource("CertCaseDtoProvider.message.internalIterator.fatalError").getString(), ex);
            LoggingUtils.logUnexpectedException(LOGGER, "Couldn't list decisions", ex);
        } finally {
            result.computeStatusIfUnknown();
        }

        getPage().showResult(result, false);

        LOGGER.trace("end::iterator()");
        return getAvailableData().iterator();
    }

    private void handleNotSuccessOrHandledErrorInIterator(OperationResult result) {
        getPage().showResult(result);
        throw new RestartResponseException(PageError.class);
    }

    @Override
    protected int internalSize() {
        LOGGER.trace("begin::internalSize()");
        int count = 0;
        OperationResult result = new OperationResult(OPERATION_COUNT_OBJECTS);
        try {
            Task task = getPage().createSimpleTask(OPERATION_COUNT_OBJECTS);
            AccessCertificationService acs = getPage().getCertificationService();
            ObjectQuery query = getQuery().clone();
            count = acs.countOpenWorkItems(query, notDecidedOnly, null, task, result);
        } catch (Exception ex) {
            result.recordFatalError(
                    getPage().createStringResource("CertWorkItemDtoProvider.message.internalSize.fatalError", ex.getMessage()).getString(), ex);
            LoggingUtils.logUnexpectedException(LOGGER, "Couldn't count objects: " + ex.getMessage(), ex);
        } finally {
            result.computeStatusIfUnknown();
        }

        getPage().showResult(result, false);

        LOGGER.trace("end::internalSize()");
        return count;
    }

    @SuppressWarnings("unused")
    public ObjectQuery getCampaignQuery() {
        return campaignQuery;
    }

    public void setCampaignQuery(ObjectQuery campaignQuery) {
        this.campaignQuery = campaignQuery;
    }

    @SuppressWarnings("unused")
    public String getReviewerOid() {
        return reviewerOid;
    }

    public void setReviewerOid(String reviewerOid) {
        this.reviewerOid = reviewerOid;
    }

    @SuppressWarnings("unused")
    public boolean isNotDecidedOnly() {
        return notDecidedOnly;
    }

    public void setNotDecidedOnly(boolean notDecidedOnly) {
        this.notDecidedOnly = notDecidedOnly;
    }

    @NotNull
    @Override
    protected List<ObjectOrdering> createObjectOrderings(SortParam<String> sortParam) {
        return SearchingUtils.createObjectOrderings(sortParam, true, getPrismContext());
    }

}
