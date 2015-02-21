/*
 * =========================================================================================
 * Copyright © 2013-2015 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.annotation.el.resolver;

import java.beans.FeatureDescriptor;
import java.lang.reflect.Field;
import java.util.Iterator;
import javax.el.ELContext;
import javax.el.ELException;
import javax.el.ELResolver;

/**
 * A custom {@link ELResolver} for mapping properties to public/private fields of the base object.
 */
public class PrivateFieldELResolver extends ELResolver {

    @Override
    public Object getValue(ELContext context, Object base, Object property) {
        if (base == null) {
            return null;
        }
        try {
            Field field = getField(base, (String) property);
            context.setPropertyResolved(true);
            field.setAccessible(true);
            return field.get(base);
        } catch (NoSuchFieldException exc) {
            context.setPropertyResolved(false);
            return null;
        } catch (Exception exc) {
            throw new ELException(exc);
        }
    }

    private Field getField(Object base, String property) throws NoSuchFieldException {
        try {
            return base.getClass().getDeclaredField(property);
        } catch (SecurityException exc) {
            throw new ELException(exc);
        }
    }

    @Override
    public Class<?> getType(ELContext context, Object base, Object property) {
        if (base == null) {
            return null;
        }
        try {
            Field field = getField(base, (String) property);
            if (field != null) {
                context.setPropertyResolved(true);
                return field.getType();
            } else {
                return null;
            }
        } catch (NoSuchFieldException exc) {
            context.setPropertyResolved(false);
            return null;
        }
    }

    @Override
    public void setValue(ELContext context, Object base, Object property, Object value) {
        if (base == null) {
            return;
        }
        try {
            context.setPropertyResolved(true);
            getField(base, (String) property).set(base, value);
        } catch (NoSuchFieldException exc) {
            context.setPropertyResolved(false);
        } catch (Exception exc) {
            throw new ELException(exc);
        }
    }

    @Override
    public boolean isReadOnly(ELContext context, Object base, Object property) {
        if (base == null) {
            return true;
        }
        try {
            Field field = getField(base, (String) property);
            if (field != null) {
                context.setPropertyResolved(true);
                return !field.isAccessible();
            }
        } catch (NoSuchFieldException exc) {
            context.setPropertyResolved(false);
        }
        return false;
    }

    @Override
    public Iterator<FeatureDescriptor> getFeatureDescriptors(ELContext context, Object base) {
        return null;
    }

    @Override
    public Class<?> getCommonPropertyType(ELContext context, Object base) {
        return String.class;
    }
}