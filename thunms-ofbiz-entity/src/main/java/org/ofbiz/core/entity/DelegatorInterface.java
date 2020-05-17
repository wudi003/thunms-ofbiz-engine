/*
 * $Id: DelegatorInterface.java,v 1.1 2005/04/01 05:58:02 sfarquhar Exp $
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

import org.ofbiz.core.entity.config.EntityConfigUtil;
import org.ofbiz.core.entity.model.ModelEntity;
import org.ofbiz.core.entity.model.ModelFieldType;
import org.ofbiz.core.entity.model.ModelGroupReader;
import org.ofbiz.core.entity.model.ModelReader;
import org.ofbiz.core.util.UtilCache;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Delegator Interface
 *
 * @author <a href="mailto:plightbo@cisco.com">Patrick Lightbody</a>
 * @version $Revision: 1.1 $
 * @since 2.0
 */
@SuppressWarnings("unused")
public interface DelegatorInterface {

    String getDelegatorName();

    ModelReader getModelReader();

    ModelGroupReader getModelGroupReader();

    ModelEntity getModelEntity(String entityName);

    String getEntityGroupName(String entityName);

    List<ModelEntity> getModelEntitiesByGroup(String groupName);

    Map<String, ModelEntity> getModelEntityMapByGroup(String groupName);

    String getGroupHelperName(String groupName);

    String getEntityHelperName(String entityName);

    String getEntityHelperName(ModelEntity entity);

    GenericHelper getEntityHelper(String entityName) throws GenericEntityException;

    GenericHelper getEntityHelper(ModelEntity entity) throws GenericEntityException;

    ModelFieldType getEntityFieldType(ModelEntity entity, String type) throws GenericEntityException;

    Collection<String> getEntityFieldTypeNames(ModelEntity entity) throws GenericEntityException;

    GenericValue makeValue(String entityName, Map<String, ?> fields);

    GenericPK makePK(String entityName, Map<String, ?> fields);

    GenericValue create(String entityName, Map<String, ?> fields) throws GenericEntityException;

    GenericValue create(GenericValue value) throws GenericEntityException;

    GenericValue create(GenericValue value, boolean doCacheClear) throws GenericEntityException;

    GenericValue create(GenericPK primaryKey) throws GenericEntityException;

    GenericValue create(GenericPK primaryKey, boolean doCacheClear) throws GenericEntityException;

    GenericValue findByPrimaryKey(GenericPK primaryKey) throws GenericEntityException;

    GenericValue findByPrimaryKeyCache(GenericPK primaryKey) throws GenericEntityException;

    GenericValue findByPrimaryKey(String entityName, Map<String, ?> fields) throws GenericEntityException;

    GenericValue findByPrimaryKeyCache(String entityName, Map<String, ?> fields) throws GenericEntityException;

    GenericValue findByPrimaryKeyPartial(GenericPK primaryKey, Set<String> keys) throws GenericEntityException;

    List<GenericValue> findAllByPrimaryKeys(Collection<? extends GenericPK> primaryKeys) throws GenericEntityException;

    List<GenericValue> findAllByPrimaryKeysCache(Collection<? extends GenericPK> primaryKeys) throws GenericEntityException;

    int removeByPrimaryKey(GenericPK primaryKey) throws GenericEntityException;

    int removeByPrimaryKey(GenericPK primaryKey, boolean doCacheClear) throws GenericEntityException;

    int removeValue(GenericValue value) throws GenericEntityException;

    int removeValue(GenericValue value, boolean doCacheClear) throws GenericEntityException;

    List<GenericValue> findAll(String entityName) throws GenericEntityException;

    List<GenericValue> findAll(String entityName, List<String> orderBy) throws GenericEntityException;

    List<GenericValue> findAllCache(String entityName) throws GenericEntityException;

    List<GenericValue> findAllCache(String entityName, List<String> orderBy) throws GenericEntityException;

    List<GenericValue> findByAnd(String entityName, Map<String, ?> fields) throws GenericEntityException;

    List<GenericValue> findByOr(String entityName, Map<String, ?> fields) throws GenericEntityException;

    List<GenericValue> findByAnd(String entityName, Map<String, ?> fields, List<String> orderBy) throws GenericEntityException;

    List<GenericValue> findByAnd(ModelEntity modelEntity, Map<String, ?> fields, List<String> orderBy) throws GenericEntityException;

    List<GenericValue> findByOr(String entityName, Map<String, ?> fields, List<String> orderBy) throws GenericEntityException;

    List<GenericValue> findByAndCache(String entityName, Map<String, ?> fields) throws GenericEntityException;

    List<GenericValue> findByAndCache(String entityName, Map<String, ?> fields, List<String> orderBy) throws GenericEntityException;

    List<GenericValue> findByAnd(String entityName, List<? extends EntityCondition> expressions) throws GenericEntityException;

    List<GenericValue> findByOr(String entityName, List<? extends EntityCondition> expressions) throws GenericEntityException;

    List<GenericValue> findByAnd(String entityName, List<? extends EntityCondition> expressions, List<String> orderBy) throws GenericEntityException;

    List<GenericValue> findByOr(String entityName, List<? extends EntityCondition> expressions, List<String> orderBy) throws GenericEntityException;

    List<GenericValue> findByLike(String entityName, Map<String, ?> fields) throws GenericEntityException;

    List<GenericValue> findByLike(String entityName, Map<String, ?> fields, List<String> orderBy) throws GenericEntityException;

    List<GenericValue> findByCondition(String entityName, EntityCondition entityCondition, Collection<String> fieldsToSelect, List<String> orderBy) throws GenericEntityException;

    int countByAnd(String entityName, String fieldName, List<? extends EntityCondition> expressions, EntityFindOptions findOptions) throws GenericEntityException;

    int countByOr(String entityName, String fieldName, List<? extends EntityCondition> expressions, EntityFindOptions findOptions) throws GenericEntityException;

    int countByCondition(String entityName, String fieldName, EntityCondition condition, EntityFindOptions findOptions) throws GenericEntityException;

    int countAll(String entityName) throws GenericEntityException;

    EntityListIterator findListIteratorByCondition(String entityName, EntityCondition entityCondition,
                                                   Collection<String> fieldsToSelect, List<String> orderBy) throws GenericEntityException;

    EntityListIterator findListIteratorByCondition(String entityName, EntityCondition whereEntityCondition,
                                                   EntityCondition havingEntityCondition, Collection<String> fieldsToSelect, List<String> orderBy, EntityFindOptions findOptions)
            throws GenericEntityException;

    int removeByAnd(String entityName, Map<String, ?> fields) throws GenericEntityException;

    int removeByAnd(String entityName, Map<String, ?> fields, boolean doCacheClear) throws GenericEntityException;

    int removeByCondition(String entityName, EntityCondition entityCondition) throws GenericEntityException;

    int removeByCondition(String entityName, EntityCondition entityCondition, boolean doCacheClear) throws GenericEntityException;

    List<GenericValue> getMultiRelation(GenericValue value, String relationNameOne, String relationNameTwo, List<String> orderBy) throws GenericEntityException;

    List<GenericValue> getMultiRelation(GenericValue value, String relationNameOne, String relationNameTwo) throws GenericEntityException;

    List<GenericValue> getRelated(String relationName, GenericValue value) throws GenericEntityException;

    List<GenericValue> getRelatedByAnd(String relationName, Map<String, ?> byAndFields, GenericValue value) throws GenericEntityException;

    List<GenericValue> getRelatedOrderBy(String relationName, List<String> orderBy, GenericValue value) throws GenericEntityException;

    List<GenericValue> getRelated(String relationName, Map<String, ?> byAndFields, List<String> orderBy, GenericValue value) throws GenericEntityException;

    GenericPK getRelatedDummyPK(String relationName, Map<String, ?> byAndFields, GenericValue value) throws GenericEntityException;

    List<GenericValue> getRelatedCache(String relationName, GenericValue value) throws GenericEntityException;

    GenericValue getRelatedOne(String relationName, GenericValue value) throws GenericEntityException;

    GenericValue getRelatedOneCache(String relationName, GenericValue value) throws GenericEntityException;

    int removeRelated(String relationName, GenericValue value) throws GenericEntityException;

    int removeRelated(String relationName, GenericValue value, boolean doCacheClear) throws GenericEntityException;

    void refresh(GenericValue value) throws GenericEntityException;

    void refresh(GenericValue value, boolean doCacheClear) throws GenericEntityException;

    int store(GenericValue value) throws GenericEntityException;

    int store(GenericValue value, boolean doCacheClear) throws GenericEntityException;

    int storeAll(List<? extends GenericValue> values) throws GenericEntityException;

    int storeAll(List<? extends GenericValue> values, boolean doCacheClear) throws GenericEntityException;

    int removeAll(List<? extends GenericEntity> dummyPKs) throws GenericEntityException;

    int removeAll(List<? extends GenericEntity> dummyPKs, boolean doCacheClear) throws GenericEntityException;

    void clearAllCaches();

    void clearAllCaches(boolean distribute);

    void clearCacheLine(String entityName, Map<String, ?> fields);

    void clearCacheLineFlexible(GenericEntity dummyPK);

    void clearCacheLineFlexible(GenericEntity dummyPK, boolean distribute);

    void clearCacheLine(GenericPK primaryKey);

    void clearCacheLine(GenericPK primaryKey, boolean distribute);

    void clearCacheLine(GenericValue value);

    void clearCacheLine(GenericValue value, boolean distribute);

    Set<Set<String>> getFieldNameSetsCopy(String entityName);

    void clearAllCacheLinesByDummyPK(Collection<? extends GenericEntity> dummyPKs);

    void clearAllCacheLinesByValue(Collection<? extends GenericValue> values);

    GenericValue getFromPrimaryKeyCache(GenericPK primaryKey);

    List<GenericValue> getFromAllCache(String entityName);

    List<GenericValue> getFromAndCache(String entityName, Map<String, ?> fields);

    List<GenericValue> getFromAndCache(ModelEntity entity, Map<String, ?> fields);

    void putInPrimaryKeyCache(GenericPK primaryKey, GenericValue value);

    void putAllInPrimaryKeyCache(List<? extends GenericValue> values);

    void putInAllCache(String entityName, List<? extends GenericValue> values);

    void putInAllCache(ModelEntity entity, List<? extends GenericValue> values);

    void putInAndCache(String entityName, Map<String, ?> fields, List<? extends GenericValue> values);

    void putInAndCache(ModelEntity entity, Map<String, ?> fields, List<? extends GenericValue> values);

    /**
     * Get the next guaranteed unique seq id from the sequence with the given sequence name;
     * if the named sequence doesn't exist, it will be created.
     * <p>
     * From v1.3.0 this is equivalent of calling {@link #getNextSeqId(String, boolean)} with true as second parameter.
     *
     * @param seqName The name of the sequence to get the next seq id from
     * @return Long with the next seq id for the given sequence name
     */
    default Long getNextSeqId(String seqName) {
        return getNextSeqId(seqName, true);
    }

    /**
     * Get the next guaranteed unique seq id from the sequence with the given sequence name;
     * if the named sequence doesn't exist, it will be created.
     * <p>
     * If the instance should be running in cluster mode then it will try a SELECT FOR UPDATE approach to retrieving
     * the next bank of sequence ids, to avoid potential collisions with other nodes.
     * <p>
     * If not in cluster mode, this is not required, and the existing JVM synchronization locking is enough.
     * <p>
     * It will be in clusterMode if true is passed as a parameter, or if {@link EntityConfigUtil.DelegatorInfo#useDistributedCacheClear}
     * is enabled
     *
     * @param seqName     The name of the sequence to get the next seq id from
     * @param clusterMode Is this a node in a clustered instance or not
     * @return Long with the next seq id for the given sequence name
     * @since v1.3.0
     */
    Long getNextSeqId(String seqName, boolean clusterMode);

    void setSequencer(SequenceUtil sequencer);

    void refreshSequencer();

    UtilCache<GenericEntity, GenericValue> getPrimaryKeyCache();

    UtilCache<GenericPK, List<GenericValue>> getAndCache();

    UtilCache<String, List<GenericValue>> getAllCache();

    /**
     * Applies the given transformation to any entities matching the given condition.
     *
     * @param entityName      the type of entity to transform (required)
     * @param entityCondition the condition that selects the entities to transform (null means transform all)
     * @param orderBy         the order in which the entities should be selected for updating (null means no ordering)
     * @param lockField       the entity field to use for optimistic locking; the value of this field will be read
     *                        between the SELECT and the UPDATE to determine whether another process has updated one of the target records in
     *                        the meantime; if so, the transformation will be reapplied and another UPDATE attempted
     * @param transformation  the transformation to apply (required)
     * @return the transformed entities in the order they were selected (never null)
     * @since 1.0.41
     */
    List<GenericValue> transform(String entityName, EntityCondition entityCondition, List<String> orderBy,
                                 String lockField, Transformation transformation)
            throws GenericEntityException;
}
