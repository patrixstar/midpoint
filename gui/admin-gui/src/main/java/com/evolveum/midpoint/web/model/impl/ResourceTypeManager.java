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
 * Portions Copyrighted 2010 Forgerock
 */

package com.evolveum.midpoint.web.model.impl;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import javax.xml.ws.Holder;

import org.apache.commons.lang.Validate;
import org.springframework.beans.factory.annotation.Autowired;

import com.evolveum.midpoint.api.logging.LoggingUtils;
import com.evolveum.midpoint.api.logging.Trace;
import com.evolveum.midpoint.common.result.OperationResult;
import com.evolveum.midpoint.logging.TraceManager;
import com.evolveum.midpoint.schema.ObjectTypes;
import com.evolveum.midpoint.schema.PagingTypeFactory;
import com.evolveum.midpoint.web.model.ResourceManager;
import com.evolveum.midpoint.web.model.WebModelException;
import com.evolveum.midpoint.web.model.dto.PropertyAvailableValues;
import com.evolveum.midpoint.web.model.dto.PropertyChange;
import com.evolveum.midpoint.web.model.dto.ResourceDto;
import com.evolveum.midpoint.web.model.dto.ResourceObjectShadowDto;
import com.evolveum.midpoint.web.util.FacesUtils;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ObjectListType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.OperationResultType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.OrderDirectionType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.PagingType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.PropertyReferenceListType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ResourceObjectShadowListType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ResourceObjectShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ResourceType;
import com.evolveum.midpoint.xml.ns._public.model.model_1.FaultMessage;
import com.evolveum.midpoint.xml.ns._public.model.model_1.ModelPortType;

/**
 * 
 * @author katuska
 */
public class ResourceTypeManager implements ResourceManager, Serializable {

	private static final long serialVersionUID = 8238616310118713517L;
	private static final Trace LOGGER = TraceManager.getTrace(ResourceTypeManager.class);
	private Class<? extends ResourceDto> constructResourceType;

	@Autowired(required = true)
	private transient ModelPortType model;

	public ResourceTypeManager(Class<? extends ResourceDto> constructResourceType) {
		this.constructResourceType = constructResourceType;
	}

	@Override
	public Collection<ResourceDto> list() throws WebModelException {
		PagingType paging = PagingTypeFactory.createListAllPaging(OrderDirectionType.ASCENDING, "name");
		return list(paging);
	}

	@Override
	public ResourceDto get(String oid, PropertyReferenceListType resolve) throws WebModelException {
		LOGGER.info("oid = {}", new Object[] { oid });
		Validate.notNull(oid);
		try { // Call Web Service Operation
			ObjectType result = model.getObject(oid, resolve, new Holder<OperationResultType>(
					new OperationResultType()));

			ResourceDto resourceDto = constructResourceType.newInstance();
			resourceDto.setXmlObject((ResourceType) result);

			return resourceDto;
		} catch (FaultMessage ex) {
			throw new WebModelException(ex.getMessage(), "Failed to get resource with oid " + oid);
		} catch (InstantiationException ex) {
			LOGGER.error("Instantiation failed: {}", ex);
			return null;
			// throw new WebModelException(ex.getMessage(),
			// "Instatiation failed.");
		} catch (IllegalAccessException ex) {
			LOGGER.error("Class or its nullary constructor is not accessible: {}", ex);
			return null;
			// throw new WebModelException(ex.getMessage(),
			// "Class or its nullary constructor is not accessible.");
		}
	}

	@Override
	public ResourceDto create() {
		try {
			return constructResourceType.newInstance();
		} catch (Exception ex) {
			throw new IllegalStateException("Couldn't create instance of '" + constructResourceType + "'.");
		}
	}

	@Override
	public String add(ResourceDto newObject) throws WebModelException {
		Validate.notNull(newObject);

		try { // Call Web Service Operation
			String result = model.addObject(newObject.getXmlObject(), new Holder<OperationResultType>(
					new OperationResultType()));
			return result;
		} catch (FaultMessage ex) {
			throw new WebModelException(ex.getMessage(), "[Web Service Error] Add resource failed");

		}

	}

	@Override
	public Set<PropertyChange> submit(ResourceDto changedObject) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public void delete(String oid) throws WebModelException {
		Validate.notNull(oid);
		try {
			model.deleteObject(oid, new Holder<OperationResultType>(new OperationResultType()));
		} catch (FaultMessage ex) {
			throw new WebModelException(ex.getMessage(),
					"[Web Service Error] Failed to delete resource with oid " + oid);
		}

	}

	@Override
	public List<PropertyAvailableValues> getPropertyAvailableValues(String oid, List<String> properties) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public <T extends ResourceObjectShadowType> List<ResourceObjectShadowDto<T>> listObjectShadows(
			String oid, Class<T> resourceObjectShadowType) {
		Validate.notNull(oid);
		try {
			ResourceObjectShadowListType resourceObjectShadowListType = model.listResourceObjectShadows(oid,
					resourceObjectShadowType.getName(), new Holder<OperationResultType>(
							new OperationResultType()));
			List<ResourceObjectShadowDto<T>> resourceObjectShadowDtoList = new ArrayList<ResourceObjectShadowDto<T>>();
			for (ResourceObjectShadowType resourceObjectShadow : resourceObjectShadowListType.getObject()) {
				ResourceObjectShadowDto<T> resourceObjectShadowDto = new ResourceObjectShadowDto<T>(
						resourceObjectShadow);
				resourceObjectShadowDtoList.add(resourceObjectShadowDto);
			}
			return resourceObjectShadowDtoList;
		} catch (Exception ex) {
			LOGGER.error("Delete user failed for oid = {}", oid);
			LOGGER.error("Exception was: ", ex);
			return null;
		}

	}

	@Override
	public Collection<ResourceDto> list(PagingType paging) {
		LOGGER.debug("Listing resources.");

		OperationResult result = new OperationResult("List Resources");
		Holder<OperationResultType> holder = new Holder<OperationResultType>(
				result.createOperationResultType());

		Collection<ResourceDto> collection = new ArrayList<ResourceDto>();
		try {
			ObjectListType list = model.listObjects(ObjectTypes.RESOURCE.getObjectTypeUri(), paging, holder);
			if (list != null) {
				for (ObjectType o : list.getObject()) {
					ResourceDto resourceDto = create();
					resourceDto.setXmlObject((ResourceType) o);
					collection.add(resourceDto);
				}
			}

			result = OperationResult.createOperationResult(holder.value);
			result.recordSuccess();
		} catch (FaultMessage ex) {
			LoggingUtils.logException(LOGGER, "Couldn't list resources from model", ex);

			result = OperationResult.createOperationResult(holder.value);
			result.recordFatalError(ex);
		}

		if (!result.isSuccess()) {
			FacesUtils.addMessage(result);
		}
		
		LOGGER.trace(result.debugDump());

		return collection;
	}
}
