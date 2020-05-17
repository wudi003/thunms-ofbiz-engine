/*
 * $Id: EntityTypeUtil.java,v 1.1 2005/04/01 05:58:02 sfarquhar Exp $
 *
 * Copyright (c) 2001, 2002 The Open For Business Project - www.ofbiz.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT
 * OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */
package org.ofbiz.core.entity;

import org.ofbiz.core.util.Debug;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Makes it easier to deal with entities that follow the
 * extensibility pattern and that can be of various types as identified in the database.
 *
 * @author <a href="mailto:jonesde@ofbiz.org">David E. Jones</a>
 * @version $Revision: 1.1 $
 * @since 2.0
 */
public class EntityTypeUtil {

    public static boolean isType(Collection<GenericValue> thisCollection, String typeRelation, GenericValue targetType) {

        for (GenericValue value : thisCollection) {
            try {
                GenericValue related = value.getRelatedOne(typeRelation);

                if (isType(related, targetType)) {
                    return true;
                } // else keep looking
            } catch (GenericEntityException e) {
                continue;
            }
        }
        return false;
    }

    /* public static boolean isType(Collection thisTypeCollection, GenericValue targetType) {
     Iterator iter = thisTypeCollection.iterator();
     while (iter.hasNext()) {
     if (isType((GenericValue) iter.next(), targetType)) {
     return true;
     }//else keep looking
     }
     return false;
     }*/

    /* private static Object getTypeID(GenericValue typeValue) {
     Collection keys = typeValue.getAllKeys();
     if (keys.size() == 1) {
     return keys.iterator().next();
     } else {
     throw new IllegalArgumentException("getTypeID expecting value with single key");
     }
     }*/

    private static GenericValue getParentType(GenericValue typeValue) {
        // assumes Parent relation is "Parent<entityName>"
        try {
            return typeValue.getRelatedOneCache("Parent" + typeValue.getEntityName());
        } catch (GenericEntityException e) {
            Debug.logWarning(e);
            return null;
        }
    }

    public static List<GenericValue> getDescendantTypes(GenericValue typeValue) {
        // assumes Child relation is "Child<entityName>"
        List<GenericValue> descendantTypes = new ArrayList<GenericValue>();

        // first get all childrenTypes ...
        List<GenericValue> childrenTypes = null;
        try {
            childrenTypes = typeValue.getRelatedCache("Child" + typeValue.getEntityName());
        } catch (GenericEntityException e) {
            Debug.logWarning(e);
            return null;
        }
        if (childrenTypes == null)
            return null;

        // ... and add them as direct descendants
        descendantTypes.addAll(childrenTypes);

        // then add all descendants of the children
        for (GenericValue childrenType : childrenTypes) {
            List<GenericValue> childTypeDescendants = getDescendantTypes(childrenType);
            if (childTypeDescendants != null) {
                descendantTypes.addAll(childTypeDescendants);
            }
        }

        return descendantTypes;
    }

    /**
     * Description of the Method
     *
     * @param catName Description of Parameter
     * @throws java.rmi.RemoteException Description of Exception
     */
    public static boolean isType(GenericValue thisType, GenericValue targetType) {
        if (thisType == null) {
            return false;
        } else if (targetType.equals(thisType)) {
            return true;
        } else {
            return isType(getParentType(thisType), targetType);
        }
    }
}
