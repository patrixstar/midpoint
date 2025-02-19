/*
 * Copyright (c) 2010-2014 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.web.util;

import com.evolveum.midpoint.util.exception.SystemException;
import org.apache.commons.codec.binary.Base64;
import org.apache.wicket.model.IModel;

import java.io.UnsupportedEncodingException;

/**
 * @author lazyman
 */
public class Base64Model implements IModel<String> {

    private IModel<byte[]> model;

    public Base64Model(IModel<byte[]> model) {
        this.model = model;
    }

    @Override
    public String getObject() {
        byte[] obj = model.getObject();
        if (obj == null) {
            return null;
        }

        try {
            return new String(Base64.decodeBase64(obj), "utf-8");
        } catch (UnsupportedEncodingException e) {
            throw new SystemException(e);
        }
    }

    @Override
    public void setObject(String object) {
        if (object == null) {
            model.setObject(null);
        }

        try {
            byte[] val = Base64.encodeBase64(object.getBytes("utf-8"));
            model.setObject(val);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void detach() {
    }
}
