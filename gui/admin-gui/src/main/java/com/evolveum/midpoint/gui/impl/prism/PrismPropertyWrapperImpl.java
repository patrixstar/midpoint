/*
 * Copyright (c) 2010-2018 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.gui.impl.prism;

import java.util.Collection;
import java.util.List;

import javax.xml.namespace.QName;

import org.apache.commons.collections4.CollectionUtils;

import com.evolveum.midpoint.gui.api.prism.ItemStatus;
import com.evolveum.midpoint.prism.MutablePrismPropertyDefinition;
import com.evolveum.midpoint.prism.PrismProperty;
import com.evolveum.midpoint.prism.PrismPropertyDefinition;
import com.evolveum.midpoint.prism.PrismPropertyValue;
import com.evolveum.midpoint.prism.delta.PropertyDelta;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.util.DisplayableValue;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.LookupTableType;

/**
 * @author katka
 *
 */
public class PrismPropertyWrapperImpl<T> extends ItemWrapperImpl<PrismPropertyValue<T>, PrismProperty<T>, PrismPropertyDefinition<T>, PrismPropertyValueWrapper<T>> implements PrismPropertyWrapper<T> {

    private static final long serialVersionUID = 1L;

    private LookupTableType predefinedValues;

    public PrismPropertyWrapperImpl(PrismContainerValueWrapper<?> parent, PrismProperty<T> item, ItemStatus status) {
        super(parent, item, status);
    }

    @Override
    public Collection<? extends DisplayableValue<T>> getAllowedValues() {
        return getItemDefinition().getAllowedValues();
    }

    @Override
    public T defaultValue() {
        return getItemDefinition().defaultValue();
    }

    @Override
    @Deprecated
    public QName getValueType() {
        return getItemDefinition().getValueType();
    }

    @Override
    public Boolean isIndexed() {
        return getItemDefinition().isIndexed();
    }

    @Override
    public QName getMatchingRuleQName() {
        return getItemDefinition().getMatchingRuleQName();
    }


    @Override
    public PropertyDelta<T> createEmptyDelta(ItemPath path) {
        return getItemDefinition().createEmptyDelta(path);
    }

    @Override
    public PrismPropertyDefinition<T> clone() {
        return getItemDefinition().clone();
    }

    @Override
    public MutablePrismPropertyDefinition<T> toMutable() {
        return getItemDefinition().toMutable();
    }

    @Override
    public PrismProperty<T> instantiate() {
        return getItemDefinition().instantiate();
    }

    @Override
    public PrismProperty<T> instantiate(QName name) {
        return getItemDefinition().instantiate(name);
    }

    @Override
    public LookupTableType getPredefinedValues() {
        return predefinedValues;
    }

    public void setPredefinedValues(LookupTableType predefinedValues) {
        this.predefinedValues = predefinedValues;
    }

    @Override
    protected boolean isEmpty() {
        if (super.isEmpty()) return true;
        List<PrismPropertyValue<T>> pVals = getItem().getValues();
        boolean allEmpty = true;
        for (PrismPropertyValue<T> pVal : pVals) {
            if (pVal.getRealValue() != null) {
                allEmpty = false;
                break;
            }
        }

        return allEmpty;
    }
}
