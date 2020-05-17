/*
 * $Id: GenericDAO.java,v 1.1 2005/04/01 05:58:02 sfarquhar Exp $
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

import com.atlassian.util.concurrent.CopyOnWriteMap;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.apache.log4j.Logger;
import org.ofbiz.core.entity.config.DatasourceInfo;
import org.ofbiz.core.entity.config.EntityConfigUtil;
import org.ofbiz.core.entity.jdbc.AutoCommitSQLProcessor;
import org.ofbiz.core.entity.jdbc.DatabaseUtil;
import org.ofbiz.core.entity.jdbc.ExplicitCommitSQLProcessor;
import org.ofbiz.core.entity.jdbc.PassThruSQLProcessor;
import org.ofbiz.core.entity.jdbc.ReadOnlySQLProcessor;
import org.ofbiz.core.entity.jdbc.SQLProcessor;
import org.ofbiz.core.entity.jdbc.SqlJdbcUtil;
import org.ofbiz.core.entity.jdbc.dbtype.DatabaseType;
import org.ofbiz.core.entity.model.ModelEntity;
import org.ofbiz.core.entity.model.ModelField;
import org.ofbiz.core.entity.model.ModelFieldTypeReader;
import org.ofbiz.core.entity.model.ModelKeyMap;
import org.ofbiz.core.entity.model.ModelRelation;
import org.ofbiz.core.entity.model.ModelViewEntity;
import org.ofbiz.core.util.Debug;
import org.ofbiz.core.util.UtilDateTime;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;

import static org.ofbiz.core.entity.jdbc.SqlJdbcUtil.makeWhereStringFromFields;
import static org.ofbiz.core.entity.jdbc.dbtype.DatabaseTypeFactory.MSSQL;
import static org.ofbiz.core.entity.jdbc.dbtype.DatabaseTypeFactory.ORACLE_10G;
import static org.ofbiz.core.entity.jdbc.dbtype.DatabaseTypeFactory.ORACLE_8I;
import static org.ofbiz.core.entity.jdbc.dbtype.DatabaseTypeFactory.POSTGRES_7_3;
import static org.ofbiz.core.util.UtilValidate.isNotEmpty;

/**
 * Generic Entity Data Access Object - Handles persistence for any defined entity.
 *
 * @author <a href="mailto:jonesde@ofbiz.org">David E. Jones</a>
 * @author <a href="mailto:jaz@ofbiz.org">Andy Zeneski</a>
 * @author <a href="mailto:chris_maurer@altavista.com">Chris Maurer</a>
 * @author <a href="mailto:jdonnerstag@eds.de">Juergen Donnerstag</a>
 * @author <a href="mailto:gielen@aixcept.de">Rene Gielen</a>
 * @author <a href="mailto:john_nutting@telluridetechnologies.com">John Nutting</a>
 * @version $Revision: 1.1 $
 * @since 1.0
 */
public class GenericDAO {

    public static final String module = GenericDAO.class.getName();
    public static final int ORACLE_MAX_LIST_SIZE = 1000;
    public static final int MS_SQL_MAX_PARAMETER_COUNT = 2000;
    public static final int POSTGRESQL_MAX_PARAMETER_COUNT = 30000;

    private static final Logger LOGGER = Logger.getLogger(GenericDAO.class);

    // The maximum amount of time to back off when contending with another thread for an atomic update
    private static final int MAX_BACK_OFF_MILLIS = 30;

    protected static Map<String, GenericDAO> genericDAOs = CopyOnWriteMap.newHashMap();
    protected String helperName;
    protected ModelFieldTypeReader modelFieldTypeReader;
    protected DatasourceInfo datasourceInfo;
    private final LimitHelper limitHelper;
    private final CountHelper countHelper;

    public static synchronized void removeGenericDAO(String helperName) {
        genericDAOs.remove(helperName);
    }

    public static GenericDAO getGenericDAO(String helperName) {
        GenericDAO newGenericDAO = genericDAOs.get(helperName);

        if (newGenericDAO == null)// don't want to block here
        {
            synchronized (GenericDAO.class) {
                newGenericDAO = genericDAOs.get(helperName);
                if (newGenericDAO == null) {
                    newGenericDAO = new GenericDAO(helperName);
                    genericDAOs.put(helperName, newGenericDAO);
                }
            }
        }
        return newGenericDAO;
    }

    /**
     * Returns a random number between zero and the given number, exclusive.
     *
     * @param maximum the maximum number to return (exclusive)
     * @return see above
     */
    private static long zeroTo(final long maximum) {
        return (long) (Math.random() * maximum);
    }

    public GenericDAO(String helperName) {
        this.helperName = helperName;
        this.modelFieldTypeReader = ModelFieldTypeReader.getModelFieldTypeReader(helperName);
        this.datasourceInfo = EntityConfigUtil.getInstance().getDatasourceInfo(helperName);
        this.limitHelper = new LimitHelper(datasourceInfo.getFieldTypeName());
        this.countHelper = new CountHelper();
    }

    @VisibleForTesting
    GenericDAO(String helperName, ModelFieldTypeReader modelFieldTypeReader, DatasourceInfo datasourceInfo, LimitHelper limitHelper, CountHelper countHelper) {
        this.helperName = helperName;
        this.modelFieldTypeReader = modelFieldTypeReader;
        this.datasourceInfo = datasourceInfo;
        this.limitHelper = limitHelper;
        this.countHelper = countHelper;
    }

    public int insert(GenericEntity entity) throws GenericEntityException {
        ModelEntity modelEntity = entity.getModelEntity();

        if (modelEntity == null) {
            throw new GenericModelException("Could not find ModelEntity record for entityName: " + entity.getEntityName());
        }

        SQLProcessor sqlP = new AutoCommitSQLProcessor(helperName);
        try {
            return singleInsert(entity, modelEntity, modelEntity.getFieldsCopy(), sqlP.getConnection());
        } catch (GenericDataSourceException e) {
            sqlP.rollback();
            throw new GenericDataSourceException("Exception while inserting the following entity: " + entity.toString(), e);
        } finally {
            closeSafely(entity, sqlP);
        }
    }

    private int singleInsert(GenericEntity entity, ModelEntity modelEntity, List<ModelField> fieldsToSave, Connection connection) throws GenericEntityException {
        if (modelEntity instanceof ModelViewEntity) {
            return singleUpdateView(entity, (ModelViewEntity) modelEntity, fieldsToSave, connection);
        }

        // if we have a STAMP_FIELD then set it with NOW.
        if (modelEntity.isField(ModelEntity.STAMP_FIELD)) {
            entity.set(ModelEntity.STAMP_FIELD, UtilDateTime.nowTimestamp());
        }

        final String sql = "INSERT INTO " + modelEntity.getTableName(datasourceInfo) + " (" +
                modelEntity.colNameString(fieldsToSave) + ") VALUES (" +
                modelEntity.fieldsStringList(fieldsToSave, "?", ", ") + ')';

        SQLProcessor sqlP = new PassThruSQLProcessor(helperName, connection);
        try {
            sqlP.prepareStatement(sql);
            SqlJdbcUtil.setValues(sqlP, fieldsToSave, entity, modelFieldTypeReader);
            int retVal = sqlP.executeUpdate();

            entity.modified = false;
            if (entity instanceof GenericValue) {
                ((GenericValue) entity).copyOriginalDbValues();
            }
            return retVal;
        } catch (GenericEntityException e) {
            throw new GenericEntityException("while inserting: " + entity.toString(), e);
        } finally {
            closeSafely(sql, sqlP);
        }
    }

    public int updateAll(final GenericEntity entity) throws GenericEntityException {
        ModelEntity modelEntity = entity.getModelEntity();

        if (modelEntity == null) {
            throw new GenericModelException("Could not find ModelEntity record for entityName: " + entity.getEntityName());
        }

        return customUpdate(entity, modelEntity, modelEntity.getNopksCopy(), null);
    }

    /**
     * Updates the given entity with the given non-PK values.
     *
     * @param entity the values to store (except the primary key fields, which
     *               are used to identify the row to be updated)
     * @return the number of rows updated (always 1)
     * @throws GenericEntityNotFoundException if the entity can't be found
     * @throws GenericEntityException         if some other problem occurred
     */
    public int update(final GenericEntity entity) throws GenericEntityException {
        return update(entity, null);
    }

    /**
     * Updates the given entity with the given non-PK values.
     *
     * @param entity         the values to store (except the primary key fields, which
     *                       are used to identify the row to be updated)
     * @param nonPkCondition an optional non-PK condition upon the update
     * @return the number of rows updated (always 1)
     * @throws GenericEntityNotFoundException if the entity wasn't found by its PK or doesn't meet the non-PK condition,
     *                                        if any
     * @throws GenericEntityException
     */
    public int update(final GenericEntity entity, final EntityConditionParam nonPkCondition)
            throws GenericEntityException {
        final ModelEntity modelEntity = entity.getModelEntity();
        if (modelEntity == null) {
            throw new GenericModelException("Could not find ModelEntity for entityName: " + entity.getEntityName());
        }

        // we don't want to update ALL fields, just the nonpk fields that are in the passed GenericEntity
        final List<ModelField> partialFields = new ArrayList<ModelField>();
        final Collection<String> keys = entity.getAllKeys();
        for (int fi = 0; fi < modelEntity.getNopksSize(); fi++) {
            final ModelField curField = modelEntity.getNopk(fi);
            if (keys.contains(curField.getName())) {
                partialFields.add(curField);
            }
        }
        return customUpdate(entity, modelEntity, partialFields, nonPkCondition);
    }

    /**
     * Updates the given entity.
     *
     * @param entity         the entity to update (required)
     * @param modelEntity    the model for this entity (required)
     * @param fieldsToSave   the fields to update (required, can be empty)
     * @param nonPkCondition any condition to place upon the update in addition
     *                       to the entity's primary keys (can be null)
     * @return the number of rows updated (always 1)
     * @throws GenericEntityNotFoundException if the entity does not exist or doesn't meet the non-PK condition, if any
     * @throws GenericEntityException
     */
    private int customUpdate(final GenericEntity entity, final ModelEntity modelEntity,
                             final List<ModelField> fieldsToSave, final EntityConditionParam nonPkCondition)
            throws GenericEntityException {
        SQLProcessor sqlP = new AutoCommitSQLProcessor(helperName);
        try {
            return singleUpdate(entity, modelEntity, fieldsToSave, sqlP.getConnection(), nonPkCondition);
        } catch (GenericDataSourceException e) {
            sqlP.rollback();
            throw new GenericDataSourceException("Exception while updating the following entity: " + entity.toString(), e);
        } finally {
            closeSafely("customUpdate (outer)", sqlP);
        }
    }

    /**
     * Updates a single entity.
     *
     * @param entity         the entity to update (required)
     * @param modelEntity    the model for this entity (required)
     * @param fieldsToSave   the fields to update (required, can be empty)
     * @param connection     the connection to use (required)
     * @param nonPkCondition any condition to place upon the update in addition
     *                       to the entity's primary keys (can be null)
     * @return the number of rows updated (always 1)
     * @throws GenericEntityNotFoundException if no rows were updated
     * @throws GenericEntityException         if something else goes wrong
     */
    private int singleUpdate(final GenericEntity entity, final ModelEntity modelEntity,
                             final List<ModelField> fieldsToSave, final Connection connection, final EntityConditionParam nonPkCondition)
            throws GenericEntityException {
        if (modelEntity instanceof ModelViewEntity) {
            return singleUpdateView(entity, (ModelViewEntity) modelEntity, fieldsToSave, connection);
        }

        if (fieldsToSave.isEmpty()) {
            // no non-primaryKey fields, update doesn't make sense, so don't do it
            if (Debug.verboseOn())
                Debug.logVerbose("Trying to do an update on an entity with no non-PK fields, returning having done nothing; entity=" + entity);
            // returning one because it was effectively updated, ie the same thing, so don't trigger any errors elsewhere
            return 1;
        }

        if (modelEntity.lock()) {
            final GenericEntity entityCopy = new GenericEntity(entity);
            select(entityCopy, connection);
            final Object stampField = entity.get(ModelEntity.STAMP_FIELD);
            if ((stampField != null) && (!stampField.equals(entityCopy.get(ModelEntity.STAMP_FIELD)))) {
                final String lockedTime = entityCopy.getTimestamp(ModelEntity.STAMP_FIELD).toString();
                throw new EntityLockedException("You tried to update an old version of this data. Version locked: (" + lockedTime + ")");
            }
        }

        // if we have a STAMP_FIELD then update it with NOW.
        if (modelEntity.isField(ModelEntity.STAMP_FIELD)) {
            entity.set(ModelEntity.STAMP_FIELD, UtilDateTime.nowTimestamp());
        }

        final List<ModelField> whereFields = modelEntity.getPksCopy();
        if (nonPkCondition != null) {
            whereFields.add(nonPkCondition.getModelField());
        }

        final String sql = String.format("UPDATE %s SET %s WHERE %s",
                modelEntity.getTableName(datasourceInfo),
                modelEntity.colNameString(fieldsToSave, "=?, ", "=?"),
                makeWhereStringFromFields(whereFields, entity, "AND"));

        final SQLProcessor sqlP = new PassThruSQLProcessor(helperName, connection);
        int retVal = 0;
        try {
            sqlP.prepareStatement(sql);
            SqlJdbcUtil.setValues(sqlP, fieldsToSave, entity, modelFieldTypeReader);
            SqlJdbcUtil.setPkValues(sqlP, modelEntity, entity, modelFieldTypeReader);
            if (nonPkCondition != null) {
                SqlJdbcUtil.setValue(sqlP, nonPkCondition.getModelField(), modelEntity.getEntityName(),
                        nonPkCondition.getFieldValue(), modelFieldTypeReader);
            }
            retVal = sqlP.executeUpdate();
            entity.modified = false;
            if (entity instanceof GenericValue) {
                ((GenericValue) entity).copyOriginalDbValues();
            }
        } catch (GenericEntityException e) {
            throw new GenericEntityException("while updating: " + entity.toString(), e);
        } finally {
            closeSafely(sql, sqlP);
        }

        if (retVal == 0) {
            throw new GenericEntityNotFoundException("Tried to update an entity that does not exist.");
        }
        return retVal;
    }

    /**
     * Store the passed entity - insert if does not exist, otherwise update
     */
    private int singleStore(GenericEntity entity, Connection connection) throws GenericEntityException {
        GenericPK tempPK = entity.getPrimaryKey();
        ModelEntity modelEntity = entity.getModelEntity();

        try {
            // must use same connection for select or it won't be in the same transaction...
            select(tempPK, connection);
        } catch (GenericEntityNotFoundException e) {
            // Debug.logInfo(e);
            // select failed, does not exist, insert
            return singleInsert(entity, modelEntity, modelEntity.getFieldsCopy(), connection);
        }
        // select did not fail, so exists, update

        List<ModelField> partialFields = new ArrayList<ModelField>();
        Collection<String> keys = entity.getAllKeys();

        for (int fi = 0; fi < modelEntity.getNopksSize(); fi++) {
            ModelField curField = modelEntity.getNopk(fi);

            // we don't want to update ALL fields, just the nonpk fields that are in the passed GenericEntity
            if (keys.contains(curField.getName())) {
                //also, only update the fields that have changed, since we have the selected values in tempPK we can compare
                if (entity.get(curField.getName()) == null) {
                    if (tempPK.get(curField.getName()) != null) {
                        //entity field is null, tempPK is not so are different
                        partialFields.add(curField);
                    }
                } else if (!entity.get(curField.getName()).equals(tempPK.get(curField.getName()))) {
                    //entity field is not null, and compared to tempPK field is different
                    partialFields.add(curField);
                }
            }
        }

        return singleUpdate(entity, modelEntity, partialFields, connection, null);
    }

    public int storeAll(List<? extends GenericEntity> entities) throws GenericEntityException {
        if (entities == null || entities.isEmpty()) {
            return 0;
        }

        final SQLProcessor sqlP = new ExplicitCommitSQLProcessor(helperName);
        try {
            int totalStored = 0;
            for (final GenericEntity entity : entities) {
                totalStored += singleStore(entity, sqlP.getConnection());
            }
            return totalStored;
        } catch (GenericDataSourceException e) {
            sqlP.rollback();
            throw new GenericDataSourceException("Exception occurred in storeAll", e);
        } finally {
            closeSafely(entities, sqlP);
        }
    }

    /**
     * Try to update the given ModelViewEntity by trying to insert/update on the entities of which the view is composed.
     * <p>
     * Works fine with standard O/R mapped models, but has some restrictions meeting more complicated view entities.
     * <li>A direct link is required, which means that one of the ModelViewLink field entries must have a value found
     * in the given view entity, for each ModelViewLink</li>
     * <li>For now, each member entity is updated iteratively, so if eg. the second member entity fails to update,
     * the first is written although. See code for details. Try to use "clean" views, until code is more robust ...</li>
     * <li>For now, aliased field names in views are not processed correctly, I guess. To be honest, I did not
     * find out how to construct such a view - so view fieldnames must have same named fields in member entities.</li>
     * <li>A new exception, e.g. GenericViewNotUpdatable, should be defined and thrown if the update fails</li>
     */
    private int singleUpdateView(GenericEntity entity, ModelViewEntity modelViewEntity, List<ModelField> fieldsToSave, Connection connection) throws GenericEntityException {
        GenericDelegator delegator = entity.getDelegator();

        int retVal = 0;
        ModelEntity memberModelEntity = null;

        // Construct insert/update for each model entity
        Iterator<? extends Map.Entry<?, ModelViewEntity.ModelMemberEntity>> meIter = modelViewEntity.getMemberModelMemberEntities().entrySet().iterator();

        while (meIter != null && meIter.hasNext()) {
            Map.Entry<?, ModelViewEntity.ModelMemberEntity> meMapEntry = meIter.next();
            ModelViewEntity.ModelMemberEntity modelMemberEntity = meMapEntry.getValue();
            String meName = modelMemberEntity.getEntityName();
            String meAlias = modelMemberEntity.getEntityAlias();

            if (Debug.verboseOn())
                Debug.logVerbose("[singleUpdateView]: Processing MemberEntity " + meName + " with Alias " + meAlias);
            try {
                memberModelEntity = delegator.getModelReader().getModelEntity(meName);
            } catch (GenericEntityException e) {
                throw new GenericEntityException("Failed to get model entity for " + meName, e);
            }

            Map<String, Object> findByMap = new Hashtable<String, Object>();

            // Now iterate the ModelViewLinks to construct the "WHERE" part for update/insert
            Iterator<ModelViewEntity.ModelViewLink> linkIter = modelViewEntity.getViewLinksIterator();

            while (linkIter != null && linkIter.hasNext()) {
                ModelViewEntity.ModelViewLink modelViewLink = linkIter.next();

                if (modelViewLink.getEntityAlias().equals(meAlias) || modelViewLink.getRelEntityAlias().equals(meAlias)) {

                    Iterator<ModelKeyMap> kmIter = modelViewLink.getKeyMapsIterator();

                    while (kmIter != null && kmIter.hasNext()) {
                        ModelKeyMap keyMap = kmIter.next();

                        String fieldName = "";

                        if (modelViewLink.getEntityAlias().equals(meAlias)) {
                            fieldName = keyMap.getFieldName();
                        } else {
                            fieldName = keyMap.getRelFieldName();
                        }

                        if (Debug.verboseOn())
                            Debug.logVerbose("[singleUpdateView]: --- Found field to set: " + meAlias + "." + fieldName);
                        Object value = null;

                        if (modelViewEntity.isField(keyMap.getFieldName())) {
                            value = entity.get(keyMap.getFieldName());
                            if (Debug.verboseOn())
                                Debug.logVerbose("[singleUpdateView]: --- Found map value: " + value.toString());
                        } else if (modelViewEntity.isField(keyMap.getRelFieldName())) {
                            value = entity.get(keyMap.getRelFieldName());
                            if (Debug.verboseOn())
                                Debug.logVerbose("[singleUpdateView]: --- Found map value: " + value.toString());
                        } else {
                            throw new GenericNotImplementedException("Update on view entities: no direct link found, unable to update");
                        }

                        findByMap.put(fieldName, value);
                    }
                }
            }

            // Look what there already is in the database
            List<GenericValue> meResult = null;

            try {
                meResult = delegator.findByAnd(meName, findByMap);
            } catch (GenericEntityException e) {
                throw new GenericEntityException("Error while retrieving partial results for entity member: " + meName, e);
            }
            if (Debug.verboseOn())
                Debug.logVerbose("[singleUpdateView]: --- Found " + meResult.size() + " results for entity member " + meName);

            // Got results 0 -> INSERT, 1 -> UPDATE, >1 -> View is nor updatable
            GenericValue meGenericValue = null;

            if (meResult.size() == 0) {
                // Create new value to insert
                try {
                    // Create new value to store
                    meGenericValue = delegator.makeValue(meName, findByMap);
                } catch (Exception e) {
                    throw new GenericEntityException("Could not create new value for member entity" + meName + " of view " + modelViewEntity.getEntityName(), e);
                }
            } else if (meResult.size() == 1) {
                // Update existing value
                meGenericValue = meResult.iterator().next();
            } else {
                throw new GenericEntityException("Found more than one result for member entity " + meName + " in view " + modelViewEntity.getEntityName() + " - this is no updatable view");
            }

            // Construct fieldsToSave list for this member entity
            List<ModelField> meFieldsToSave = new Vector<ModelField>();
            Iterator<ModelField> fieldIter = fieldsToSave.iterator();

            while (fieldIter != null && fieldIter.hasNext()) {
                ModelField modelField = fieldIter.next();

                if (memberModelEntity.isField(modelField.getName())) {
                    ModelField meModelField = memberModelEntity.getField(modelField.getName());

                    if (meModelField != null) {
                        meGenericValue.set(meModelField.getName(), entity.get(modelField.getName()));
                        meFieldsToSave.add(meModelField);
                        if (Debug.verboseOn())
                            Debug.logVerbose("[singleUpdateView]: --- Added field to save: " + meModelField.getName() + " with value " + meGenericValue.get(meModelField.getName()));
                    } else {
                        throw new GenericEntityException("Could not get field " + modelField.getName() + " from model entity " + memberModelEntity.getEntityName());
                    }
                }
            }

            /*
             * Finally, do the insert/update
             * TODO:
             * Do the real inserts/updates outside the memberEntity-loop,
             * only if all of the found member entities are updatable.
             * This avoids partial creation of member entities, which would mean data inconsistency:
             * If not all member entities can be updated, then none should be updated
             */
            if (meResult.size() == 0) {
                retVal += singleInsert(meGenericValue, memberModelEntity, memberModelEntity.getFieldsCopy(), connection);
            } else {
                if (meFieldsToSave.size() > 0) {
                    retVal += singleUpdate(meGenericValue, memberModelEntity, meFieldsToSave, connection, null);
                } else {
                    if (Debug.verboseOn())
                        Debug.logVerbose("[singleUpdateView]: No update on member entity " + memberModelEntity.getEntityName() + " needed");
                }
            }
        }

        return retVal;
    }

    /* ====================================================================== */

    /* ====================================================================== */

    public void select(GenericEntity entity) throws GenericEntityException {
        SQLProcessor sqlP = new ReadOnlySQLProcessor(helperName);
        try {
            select(entity, sqlP.getConnection());
        } finally {
            closeSafely(entity, sqlP);
        }
    }

    public void select(GenericEntity entity, Connection connection) throws GenericEntityException {
        ModelEntity modelEntity = entity.getModelEntity();

        if (modelEntity == null) {
            throw new GenericModelException("Could not find ModelEntity record for entityName: " + entity.getEntityName());
        }

        if (modelEntity.getPksSize() <= 0) {
            throw new GenericEntityException("Entity has no primary keys, cannot select by primary key");
        }

        StringBuilder sqlBuffer = new StringBuilder(256).append("SELECT ");
        if (modelEntity.getNopksSize() > 0) {
            sqlBuffer.append(modelEntity.colNameString(modelEntity.getNopksCopy(), ", ", ""));
        } else {
            sqlBuffer.append('*');
        }
        sqlBuffer.append(SqlJdbcUtil.makeFromClause(modelEntity, datasourceInfo));
        sqlBuffer.append(SqlJdbcUtil.makeWhereClause(modelEntity, modelEntity.getPksCopy(), entity, "AND", datasourceInfo.getJoinStyle()));

        final String sql = sqlBuffer.toString();
        final SQLProcessor sqlP = new PassThruSQLProcessor(helperName, connection);
        try {
            sqlP.prepareStatement(sql, true, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            SqlJdbcUtil.setPkValues(sqlP, modelEntity, entity, modelFieldTypeReader);
            sqlP.executeQuery();

            if (sqlP.next()) {
                for (int j = 0; j < modelEntity.getNopksSize(); j++) {
                    ModelField curField = modelEntity.getNopk(j);

                    SqlJdbcUtil.getValue(sqlP.getResultSet(), j + 1, curField, entity, modelFieldTypeReader);
                }

                entity.modified = false;
                if (entity instanceof GenericValue) {
                    ((GenericValue) entity).copyOriginalDbValues();
                }
            } else {
                // Debug.logWarning("[GenericDAO.select]: select failed, result set was empty for entity: " + entity.toString());
                throw new GenericEntityNotFoundException("Result set was empty for entity: " + entity.toString());
            }
        } finally {
            closeSafely(sql, sqlP);
        }
    }

    public void partialSelect(GenericEntity entity, Set<String> keys) throws GenericEntityException {
        ModelEntity modelEntity = entity.getModelEntity();

        if (modelEntity == null) {
            throw new GenericModelException("Could not find ModelEntity record for entityName: " + entity.getEntityName());
        }

        if (modelEntity instanceof ModelViewEntity) {
            throw new org.ofbiz.core.entity.GenericNotImplementedException("Operation partialSelect not supported yet for view entities");
        }

        /*
         if(entity == null || entity.<%=modelEntity.pkNameString(" == null || entity."," == null")%>) {
         Debug.logWarning("[GenericDAO.select]: Cannot select GenericEntity: required primary key field(s) missing.");
         return false;
         }
         */
        // we don't want to select ALL fields, just the nonpk fields that are in the passed GenericEntity
        List<ModelField> partialFields = new ArrayList<ModelField>();

        Set<String> tempKeys = new TreeSet<String>(keys);

        for (int fi = 0; fi < modelEntity.getNopksSize(); fi++) {
            ModelField curField = modelEntity.getNopk(fi);

            if (tempKeys.contains(curField.getName())) {
                partialFields.add(curField);
                tempKeys.remove(curField.getName());
            }
        }

        if (tempKeys.size() > 0) {
            throw new GenericModelException("In partialSelect invalid field names specified: " + tempKeys.toString());
        }

        StringBuilder sqlBuffer = new StringBuilder("SELECT ");
        if (partialFields.size() > 0) {
            sqlBuffer.append(modelEntity.colNameString(partialFields, ", ", ""));
        } else {
            sqlBuffer.append('*');
        }
        sqlBuffer.append(SqlJdbcUtil.makeFromClause(modelEntity, datasourceInfo));
        sqlBuffer.append(SqlJdbcUtil.makeWhereClause(modelEntity, modelEntity.getPksCopy(), entity, "AND", datasourceInfo.getJoinStyle()));

        final String sql = sqlBuffer.toString();
        final SQLProcessor sqlP = new ReadOnlySQLProcessor(helperName);
        try {
            sqlP.prepareStatement(sql, true, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            SqlJdbcUtil.setPkValues(sqlP, modelEntity, entity, modelFieldTypeReader);
            sqlP.executeQuery();

            if (sqlP.next()) {
                for (int j = 0; j < partialFields.size(); j++) {
                    ModelField curField = partialFields.get(j);

                    SqlJdbcUtil.getValue(sqlP.getResultSet(), j + 1, curField, entity, modelFieldTypeReader);
                }

                entity.modified = false;
                if (entity instanceof GenericValue) {
                    ((GenericValue) entity).copyOriginalDbValues();
                }
            } else {
                // Debug.logWarning("[GenericDAO.select]: select failed, result set was empty.");
                throw new GenericEntityNotFoundException("Result set was empty for entity: " + entity.toString());
            }
        } finally {
            closeSafely(sql, sqlP);
        }
    }

    public List<GenericValue> selectByAnd(ModelEntity modelEntity, Map<String, ?> fields, List<String> orderBy) throws GenericEntityException {
        if (modelEntity == null) {
            return null;
        }

        EntityCondition entityCondition = null;

        if (fields != null) {
            entityCondition = new EntityFieldMap(fields, EntityOperator.AND);
        }

        EntityListIterator entityListIterator = null;

        try {
            entityListIterator = selectListIteratorByCondition(modelEntity, entityCondition, null, null, orderBy, null);
            return entityListIterator.getCompleteList();
        } finally {
            if (entityListIterator != null) {
                entityListIterator.close();
            }
        }
    }

    public List<GenericValue> selectByOr(ModelEntity modelEntity, Map<String, ?> fields, List<String> orderBy) throws GenericEntityException {
        if (modelEntity == null) {
            return null;
        }

        EntityCondition entityCondition = null;

        if (fields != null) {
            entityCondition = new EntityFieldMap(fields, EntityOperator.OR);
        }

        EntityListIterator entityListIterator = null;

        try {
            entityListIterator = selectListIteratorByCondition(modelEntity, entityCondition, null, null, orderBy, null);
            return entityListIterator.getCompleteList();
        } finally {
            if (entityListIterator != null) {
                entityListIterator.close();
            }
        }
    }

    /**
     * Finds GenericValues by the conditions specified in the EntityCondition object, see the EntityCondition javadoc
     * for more details.
     *
     * @param modelEntity     The ModelEntity of the Entity as defined in the entity XML file
     * @param entityCondition The EntityCondition object that specifies how to constrain this query
     * @param fieldsToSelect  The fields of the named entity to get from the database; if empty or null all fields will be retreived
     * @param orderBy         The fields of the named entity to order the query by; optionally add a " ASC" for ascending or
     *                        " DESC" for descending
     * @return List of GenericValue objects representing the result
     */
    public List<GenericValue> selectByCondition(final ModelEntity modelEntity, final EntityCondition entityCondition,
                                                final Collection<String> fieldsToSelect, final List<String> orderBy)
            throws GenericEntityException {
        return selectByCondition(modelEntity, entityCondition, fieldsToSelect, orderBy, null);
    }

    /**
     * Finds GenericValues by the conditions specified in the EntityCondition object, see the EntityCondition javadoc
     * for more details.
     *
     * @param modelEntity     The ModelEntity of the Entity as defined in the entity XML file
     * @param entityCondition The EntityCondition object that specifies how to constrain this query
     * @param fieldsToSelect  The fields of the named entity to get from the database; if empty or null all fields will be retreived
     * @param orderBy         The fields of the named entity to order the query by; optionally add a " ASC" for ascending or
     * @param findOptions     if null, the default options will be used
     *                        " DESC" for descending
     * @return List of GenericValue objects representing the result
     */
    public List<GenericValue> selectByCondition(final ModelEntity modelEntity, final EntityCondition entityCondition,
                                                final Collection<String> fieldsToSelect, final List<String> orderBy, final EntityFindOptions findOptions)
            throws GenericEntityException {
        EntityListIterator entityListIterator = null;
        try {
            entityListIterator = selectListIteratorByCondition(
                    modelEntity, entityCondition, null, fieldsToSelect, orderBy, findOptions);
            return entityListIterator.getCompleteList();
        } finally {
            if (entityListIterator != null) {
                entityListIterator.close();
            }
        }
    }

    /**
     * Finds GenericValues by the conditions specified in the EntityCondition object, the the EntityCondition javadoc for more details.
     *
     * @param modelEntity           The ModelEntity of the Entity as defined in the entity XML file
     * @param whereEntityCondition  The EntityCondition object that specifies how to constrain this query before any groupings are done (if this is a view entity with group-by aliases)
     * @param havingEntityCondition The EntityCondition object that specifies how to constrain this query after any groupings are done (if this is a view entity with group-by aliases)
     * @param fieldsToSelect        The fields of the named entity to get from the database; if empty or null all fields will be retreived
     * @param orderBy               The fields of the named entity to order the query by; optionally add a " ASC" for ascending or " DESC" for descending
     * @param findOptions           can be null to use the default options
     * @return EntityListIterator representing the result of the query: NOTE THAT THIS MUST BE CLOSED WHEN YOU ARE
     * DONE WITH IT, AND DON'T LEAVE IT OPEN TOO LONG BECAUSE IT WILL MAINTAIN A DATABASE CONNECTION.
     */
    public EntityListIterator selectListIteratorByCondition(final ModelEntity modelEntity, EntityCondition whereEntityCondition,
                                                            final EntityCondition havingEntityCondition, final Collection<String> fieldsToSelect,
                                                            final List<String> orderBy, final EntityFindOptions findOptions)
            throws GenericEntityException {
        if (modelEntity == null) {
            return null;
        }
        final EntityFindOptions nonNullFindOptions = findOptions == null ? new EntityFindOptions() : findOptions;

        //JRA-19317: Oracle does not allow lists with more than 1000 elements ORA-01795
        // if we are on Oracle we split such long lists into equivalent expression
        // e.g. pid in (1, 2, 3, ..., 1000, 1001, 1002, ...) will be split into (pid in (1, 2, 3, ..., 1000) or pid in (1001, 1002, ...))
        final DatabaseType databaseType = datasourceInfo.getDatabaseTypeFromJDBCConnection();
        if (databaseType == ORACLE_8I || databaseType == ORACLE_10G) {
            whereEntityCondition = rewriteConditionToSplitListsLargerThan(whereEntityCondition, ORACLE_MAX_LIST_SIZE);
        }

        final InQueryRewritter inQueryRewritter = new InQueryRewritter(databaseType, whereEntityCondition, modelEntity);
        whereEntityCondition = inQueryRewritter.rewriteIfNeeded();

        if (Debug.verboseOn()) {
            Debug.logVerbose("Doing selectListIteratorByCondition with whereEntityCondition: " + whereEntityCondition);
        }

        final List<ModelField> selectFields = getSelectFields(modelEntity, fieldsToSelect);
        final List<EntityConditionParam> whereEntityConditionParams = new LinkedList<EntityConditionParam>();
        final List<EntityConditionParam> havingEntityConditionParams = new LinkedList<EntityConditionParam>();

        final String sql = getSelectQuery(selectFields, nonNullFindOptions, modelEntity, orderBy, whereEntityCondition,
                havingEntityCondition, whereEntityConditionParams, havingEntityConditionParams, databaseType);

        final SQLProcessor sqlP;
        if (inQueryRewritter.isRewritten()) {
            sqlP = new SQLProcessor(helperName);
        } else {
            sqlP = new ReadOnlySQLProcessor(helperName);
        }

        inQueryRewritter.createTemporaryTablesIfNeeded(sqlP);

        return createEntityListIterator(sqlP, sql, nonNullFindOptions, modelEntity, selectFields, whereEntityConditionParams, havingEntityConditionParams,
                inQueryRewritter.getTableCleanUpHandler());
    }

    @VisibleForTesting
    EntityListIterator createEntityListIterator(final SQLProcessor sqlP, final String sql,
                                                final EntityFindOptions nonNullFindOptions, final ModelEntity modelEntity,
                                                final List<ModelField> selectFields, final List<EntityConditionParam> whereEntityConditionParams,
                                                final List<EntityConditionParam> havingEntityConditionParams,
                                                final TableCleanUp tableCleanUp)
            throws GenericEntityException {
        try {
            // A data base connection is open when the call to prepareStatement is done (SQLProcessor's constructor does not open the connection)
            sqlP.prepareStatement(sql, nonNullFindOptions.isCustomResultSetTypeAndConcurrency(),
                    nonNullFindOptions.getResultSetType(), nonNullFindOptions.getResultSetConcurrency());

            bindParameterValues(sqlP, modelEntity, whereEntityConditionParams, "where");
            bindParameterValues(sqlP, modelEntity, havingEntityConditionParams, "having");

            setFetchSize(sqlP, nonNullFindOptions.getFetchSize());
            sqlP.executeQuery();

            //If we have any temporary tables they can be dropped after the list iterator is closed
            if (tableCleanUp == null) {
                return new EntityListIterator(sqlP, modelEntity, selectFields, modelFieldTypeReader);
            } else {
                return new EntityListIteratorWithTemporaryTableCleanup(sqlP, modelEntity, selectFields, modelFieldTypeReader, tableCleanUp);
            }

        }
        // The returned EntityListIterator must contain an SQLProcessor with an open connection to the database.
        // That's why the SQLProcessor only gets closed when an exception is thrown and the EntityListIterator
        // can't be correctly created, instead of closing it on a finally block.
        catch (GenericEntityException | RuntimeException e) {
            closeSafely(sql, sqlP);
            throw e;
        }
    }

    private void bindParameterValues(final SQLProcessor sqlP, final ModelEntity modelEntity,
                                     final Iterable<EntityConditionParam> params, final String clauseName)
            throws GenericEntityException {
        if (Debug.verboseOn()) {
            Debug.logVerbose("Setting the " + clauseName + "EntityConditionParams: " + params);
        }
        for (final EntityConditionParam param : params) {
            SqlJdbcUtil.setValue(sqlP, param.getModelField(), modelEntity.getEntityName(), param.getFieldValue(),
                    modelFieldTypeReader);
        }
    }

    private List<ModelField> getSelectFields(final ModelEntity modelEntity, final Collection<String> fieldsToSelect)
            throws GenericModelException {
        List<ModelField> selectFields = new ArrayList<ModelField>();

        if (fieldsToSelect != null && fieldsToSelect.size() > 0) {
            Set<String> tempKeys = new HashSet<String>(fieldsToSelect);

            for (int fi = 0; fi < modelEntity.getFieldsSize(); fi++) {
                ModelField curField = modelEntity.getField(fi);

                if (tempKeys.contains(curField.getName())) {
                    selectFields.add(curField);
                    tempKeys.remove(curField.getName());
                }
            }

            if (tempKeys.size() > 0) {
                throw new GenericModelException("In selectListIteratorByCondition invalid field names specified: " + tempKeys.toString());
            }
        } else {
            selectFields = modelEntity.getFieldsCopy();
        }
        return selectFields;
    }

    @VisibleForTesting
    String getSelectQuery(final List<ModelField> selectFields, final EntityFindOptions findOptions,
                          final ModelEntity modelEntity, final List<String> orderBy, final EntityCondition whereEntityCondition,
                          final EntityCondition havingEntityCondition, final List<EntityConditionParam> whereEntityConditionParams,
                          final List<EntityConditionParam> havingEntityConditionParams, final DatabaseType databaseType)
            throws GenericEntityException {
        final StringBuilder sqlBuilder = new StringBuilder("SELECT ");

        if (findOptions.getDistinct()) {
            sqlBuilder.append("DISTINCT ");
        }

        if (selectFields != null && !selectFields.isEmpty()) {
            sqlBuilder.append(modelEntity.colNameString(selectFields, ", ", ""));
        } else {
            sqlBuilder.append("*");
        }

        // FROM clause and when necessary the JOIN or LEFT JOIN clause(s) as well
        sqlBuilder.append(SqlJdbcUtil.makeFromClause(modelEntity, datasourceInfo));

        // WHERE clause
        final StringBuilder whereString = new StringBuilder();
        String entityCondWhereString = "";
        if (whereEntityCondition != null) {
            entityCondWhereString = whereEntityCondition.makeWhereString(modelEntity, whereEntityConditionParams);
        }

        final String viewClause = SqlJdbcUtil.makeViewWhereClause(modelEntity, datasourceInfo.getJoinStyle());

        if (viewClause.length() > 0) {
            if (entityCondWhereString.length() > 0) {
                whereString.append("(");
                whereString.append(entityCondWhereString);
                whereString.append(") AND ");
            }

            whereString.append(viewClause);
        } else {
            whereString.append(entityCondWhereString);
        }

        if (whereString.length() > 0) {
            sqlBuilder.append(" WHERE ");
            sqlBuilder.append(whereString.toString());
        }

        // GROUP BY clause for view-entity
        if (modelEntity instanceof ModelViewEntity) {
            final ModelViewEntity modelViewEntity = (ModelViewEntity) modelEntity;
            final String groupByString = modelViewEntity.colNameString(modelViewEntity.getGroupBysCopy(), ", ", "");
            if (isNotEmpty(groupByString)) {
                sqlBuilder.append(" GROUP BY ");
                sqlBuilder.append(groupByString);
            }
        }

        // HAVING clause
        String entityCondHavingString = "";
        if (havingEntityCondition != null) {
            entityCondHavingString = havingEntityCondition.makeWhereString(modelEntity, havingEntityConditionParams);
        }
        if (entityCondHavingString.length() > 0) {
            sqlBuilder.append(" HAVING ");
            sqlBuilder.append(entityCondHavingString);
        }

        // ORDER BY clause
        sqlBuilder.append(SqlJdbcUtil.makeOrderByClause(modelEntity, orderBy, datasourceInfo));
        String sql = sqlBuilder.toString();
        if (findOptions.getMaxResults() > 0) {
            sql = limitHelper.addLimitClause(sql, selectFields, findOptions.getOffset(), findOptions.getMaxResults());
        }

        return sql;
    }

    static private EntityCondition rewriteConditionToSplitListsLargerThan(
            final EntityCondition whereEntityCondition, final int maxListSize) {
        if (conditionContainsInClauseWithListOfSizeGreaterThanMaxSize(whereEntityCondition, maxListSize)) {
            return transformConditionSplittingInClauseListsToChunksNoLongerThanMaxSize(whereEntityCondition, maxListSize);
        }
        return whereEntityCondition;
    }

    @VisibleForTesting
    static boolean conditionContainsInClauseWithListOfSizeGreaterThanMaxSize(EntityCondition whereEntityCondition, final int maxListSize) {
        return !EntityConditionHelper.predicateTrueForEachLeafExpression(whereEntityCondition, Predicates.not(new Predicate<EntityExpr>() {
            public boolean apply(EntityExpr input) {
                return input.getOperator().equals(EntityOperator.IN) && input.getRhs() instanceof Collection && ((Collection<?>) input.getRhs()).size() > maxListSize;
            }
        }));
    }

    @VisibleForTesting
    static EntityCondition transformConditionSplittingInClauseListsToChunksNoLongerThanMaxSize(EntityCondition whereEntityCondition, final int maxListSize) {
        return EntityConditionHelper.transformCondition(whereEntityCondition, new Function<EntityExpr, EntityCondition>() {
            public EntityCondition apply(final EntityExpr input) {
                if (input.getOperator().equals(EntityOperator.IN) && input.getRhs() instanceof Collection && ((Collection<?>) input.getRhs()).size() > maxListSize) {
                    //split into list of expressions
                    final ImmutableList<EntityExpr> listOfExpressions = ImmutableList.copyOf(
                            Iterables.transform(
                                    Iterables.partition(((Collection<?>) input.getRhs()), maxListSize),
                                    new Function<List<?>, EntityExpr>() {
                                        public EntityExpr apply(@Nullable final List<?> list) {
                                            return new EntityExpr((String) input.getLhs(), input.getOperator(), list);
                                        }
                                    }));
                    return new EntityExprList(listOfExpressions, EntityOperator.OR);
                } else {
                    return input;
                }
            }
        });
    }

    private void setFetchSize(final SQLProcessor sqlP, final int fetchSize) {
        if (fetchSize != -1) {
            try {
                sqlP.getPreparedStatement().setFetchSize(fetchSize);
                if (Debug.verboseOn()) {
                    Debug.logVerbose("Set the fetch size to: " + fetchSize);
                }
            } catch (SQLException sqle) {
                if (Debug.verboseOn()) {
                    Debug.logVerbose("Unable to set the fetch size to: " + fetchSize + ": " + sqle);
                }
            }
        }
    }

    public List<GenericValue> selectByMultiRelation(GenericValue value, ModelRelation modelRelationOne, ModelEntity modelEntityOne,
                                                    ModelRelation modelRelationTwo, ModelEntity modelEntityTwo, List<String> orderBy) throws GenericEntityException {
        SQLProcessor sqlP = new ReadOnlySQLProcessor(helperName);

        // get the tables names
        String atable = modelEntityOne.getTableName(datasourceInfo);
        String ttable = modelEntityTwo.getTableName(datasourceInfo);

        // get the column name string to select
        StringBuilder selsb = new StringBuilder(256);
        List<String> collist = new ArrayList<String>();
        List<String> fldlist = new ArrayList<String>();

        for (Iterator<ModelField> iterator = modelEntityTwo.getFieldsIterator(); iterator.hasNext(); ) {
            ModelField mf = iterator.next();

            collist.add(mf.getColName());
            fldlist.add(mf.getName());
            selsb.append(ttable).append('.').append(mf.getColName());
            if (iterator.hasNext()) {
                selsb.append(", ");
            } else {
                selsb.append(' ');
            }
        }

        // construct assoc->target relation string
        int kmsize = modelRelationTwo.getKeyMapsSize();
        StringBuilder wheresb = new StringBuilder(256);
        for (int i = 0; i < kmsize; i++) {
            ModelKeyMap mkm = modelRelationTwo.getKeyMap(i);
            String lfname = mkm.getFieldName();
            String rfname = mkm.getRelFieldName();

            if (wheresb.length() > 0) {
                wheresb.append(" AND ");
            }
            wheresb.append(atable).append('.').append(modelEntityOne.getField(lfname).getColName())
                    .append(" = ")
                    .append(ttable).append('.').append(modelEntityTwo.getField(rfname).getColName());
        }

        // construct the source entity qualifier
        // get the fields from relation description
        kmsize = modelRelationOne.getKeyMapsSize();
        Map<ModelField, Object> bindMap = new HashMap<ModelField, Object>();

        for (int i = 0; i < kmsize; i++) {
            // get the equivalent column names in the relation
            ModelKeyMap mkm = modelRelationOne.getKeyMap(i);
            String sfldname = mkm.getFieldName();
            String lfldname = mkm.getRelFieldName();
            ModelField amf = modelEntityOne.getField(lfldname);
            String lcolname = amf.getColName();
            Object rvalue = value.get(sfldname);

            bindMap.put(amf, rvalue);
            // construct one condition
            if (wheresb.length() > 0) {
                wheresb.append(" AND ");
            }
            wheresb.append(atable).append('.').append(lcolname).append(" = ? ");
        }

        // construct a join sql query
        StringBuilder sqlsb = new StringBuilder(256);
        sqlsb.append("SELECT ");
        sqlsb.append(selsb.toString());
        sqlsb.append(" FROM ");
        sqlsb.append(atable).append(", ").append(ttable);
        sqlsb.append(" WHERE ");
        sqlsb.append(wheresb.toString());
        sqlsb.append(SqlJdbcUtil.makeOrderByClause(modelEntityTwo, orderBy, true, datasourceInfo));

        // now execute the query
        final String sql = sqlsb.toString();
        List<GenericValue> retlist = new ArrayList<GenericValue>();
        GenericDelegator gd = value.getDelegator();

        try {
            sqlP.prepareStatement(sql);
            Set<Map.Entry<ModelField, Object>> entrySet = bindMap.entrySet();
            for (Map.Entry<ModelField, Object> entry : entrySet) {
                ModelField mf = entry.getKey();
                Object curvalue = entry.getValue();

                SqlJdbcUtil.setValue(sqlP, mf, modelEntityOne.getEntityName(), curvalue, modelFieldTypeReader);
            }
            sqlP.executeQuery();
            int collsize = collist.size();

            while (sqlP.next()) {
                GenericValue gv = gd.makeValue(modelEntityTwo.getEntityName(), Collections.<String, Object>emptyMap());

                // loop thru all columns for in one row
                for (int j = 0; j < collsize; j++) {
                    String fldname = fldlist.get(j);
                    ModelField mf = modelEntityTwo.getField(fldname);

                    SqlJdbcUtil.getValue(sqlP.getResultSet(), j + 1, mf, gv, modelFieldTypeReader);
                }
                retlist.add(gv);
            }
        } finally {
            closeSafely(sql, sqlP);
        }
        return retlist;
    }

    /* ====================================================================== */

    /* ====================================================================== */

    public int delete(GenericEntity entity) throws GenericEntityException {
        SQLProcessor sqlP = new AutoCommitSQLProcessor(helperName);

        try {
            return deleteImpl(entity, sqlP.getConnection());
        } catch (GenericDataSourceException e) {
            sqlP.rollback();
            throw new GenericDataSourceException("Exception while deleting the following entity: " + entity.toString(), e);
        } finally {
            closeSafely(entity, sqlP);
        }
    }

    private int deleteImpl(GenericEntity entity, Connection connection) throws GenericEntityException {
        ModelEntity modelEntity = entity.getModelEntity();
        if (modelEntity == null) {
            throw new GenericModelException("Could not find ModelEntity record for entityName: " + entity.getEntityName());
        }
        if (modelEntity instanceof ModelViewEntity) {
            throw new org.ofbiz.core.entity.GenericNotImplementedException("Operation delete not supported yet for view entities");
        }

        String sql = "DELETE FROM " + modelEntity.getTableName(datasourceInfo) + " WHERE " +
                makeWhereStringFromFields(modelEntity.getPksCopy(), entity, "AND");

        SQLProcessor sqlP = new PassThruSQLProcessor(helperName, connection);
        int retVal;
        try {
            sqlP.prepareStatement(sql);
            SqlJdbcUtil.setPkValues(sqlP, modelEntity, entity, modelFieldTypeReader);
            retVal = sqlP.executeUpdate();
            entity.modified = true;
        } finally {
            closeSafely(sql, sqlP);
        }
        return retVal;
    }

    public int deleteByCondition(ModelEntity modelEntity, EntityCondition whereCondition) throws GenericEntityException {
        if (modelEntity == null) {
            throw new GenericModelException("Could not find ModelEntity record for entityName: " + modelEntity.getEntityName());
        }
        if (modelEntity instanceof ModelViewEntity) {
            throw new org.ofbiz.core.entity.GenericNotImplementedException("Operation delete not supported yet for view entities");
        }
        String whereClause = "";
        final List<EntityConditionParam> whereConditionParams = Lists.newLinkedList();
        if (whereCondition != null) {
            whereClause = " WHERE " + whereCondition.makeWhereString(modelEntity, whereConditionParams);
        }

        String sql = "DELETE FROM " + modelEntity.getTableName(datasourceInfo) + whereClause;
        SQLProcessor sqlP = new AutoCommitSQLProcessor(helperName);
        int retVal;
        try {
            sqlP.prepareStatement(sql);
            if (whereCondition != null) {
                for (EntityConditionParam param : whereConditionParams) {
                    SqlJdbcUtil.setValue(sqlP, param.getModelField(), modelEntity.getEntityName(), param.getFieldValue(), modelFieldTypeReader);
                }
            }
            retVal = sqlP.executeUpdate();
        } finally {
            closeSafely(sql, sqlP);
        }
        return retVal;
    }

    public int deleteByAnd(ModelEntity modelEntity, Map<String, ?> fields) throws GenericEntityException {
        SQLProcessor sqlP = new AutoCommitSQLProcessor(helperName);
        try {
            return deleteByAnd(modelEntity, fields, sqlP.getConnection());
        } catch (GenericDataSourceException e) {
            sqlP.rollback();
            throw new GenericDataSourceException("Generic Entity Exception occurred in deleteByAnd", e);
        } finally {
            closeSafely(fields, sqlP);
        }
    }

    private int deleteByAnd(
            final ModelEntity modelEntity, final Map<String, ?> whereFieldValues, final Connection connection)
            throws GenericEntityException {
        if (modelEntity == null || whereFieldValues == null) {
            return 0;
        }
        if (modelEntity instanceof ModelViewEntity) {
            throw new GenericNotImplementedException("Operation deleteByAnd not supported yet for view entities");
        }

        final List<ModelField> whereFields = getWhereFields(modelEntity, whereFieldValues);

        final GenericValue dummyValue = new GenericValue(modelEntity, whereFieldValues);
        String sql = "DELETE FROM " + modelEntity.getTableName(datasourceInfo);
        if (!whereFieldValues.isEmpty()) {
            sql += " WHERE " + makeWhereStringFromFields(whereFields, dummyValue, "AND");
        }

        final SQLProcessor sqlP = new PassThruSQLProcessor(helperName, connection);
        try {
            sqlP.prepareStatement(sql);
            if (!whereFieldValues.isEmpty()) {
                SqlJdbcUtil.setValuesWhereClause(sqlP, whereFields, dummyValue, modelFieldTypeReader);
            }
            return sqlP.executeUpdate();
        } finally {
            closeSafely(sql, sqlP);
        }
    }

    private List<ModelField> getWhereFields(final ModelEntity modelEntity, final Map<String, ?> fieldValues) {
        final List<ModelField> whereFields = new ArrayList<ModelField>();
        if (!fieldValues.isEmpty()) {
            for (int fieldNumber = 0; fieldNumber < modelEntity.getFieldsSize(); fieldNumber++) {
                final ModelField modelField = modelEntity.getField(fieldNumber);
                if (fieldValues.containsKey(modelField.getName())) {
                    whereFields.add(modelField);
                }
            }
        }
        return whereFields;
    }

    /**
     * Called dummyPKs because they can be invalid PKs, doing a deleteByAnd instead of a normal delete
     */
    public int deleteAll(List<? extends GenericEntity> dummyPKs) throws GenericEntityException {
        if (dummyPKs == null || dummyPKs.size() == 0) {
            return 0;
        }

        SQLProcessor sqlP = new ExplicitCommitSQLProcessor(helperName);
        try {
            Iterator<? extends GenericEntity> iter = dummyPKs.iterator();

            int numDeleted = 0;

            while (iter.hasNext()) {
                GenericEntity entity = iter.next();

                // if it contains a complete primary key, delete the one, otherwise deleteByAnd
                if (entity.containsPrimaryKey()) {
                    numDeleted += deleteImpl(entity, sqlP.getConnection());
                } else {
                    numDeleted += deleteByAnd(entity.getModelEntity(), entity.getAllFields(), sqlP.getConnection());
                }
            }
            return numDeleted;
        } catch (GenericDataSourceException e) {
            sqlP.rollback();
            throw new GenericDataSourceException("Generic Entity Exception occurred in deleteAll", e);
        } finally {
            closeSafely(dummyPKs, sqlP);
        }
    }

    public void checkDb(Map<String, ? extends ModelEntity> modelEntities, Collection<String> messages, boolean addMissing) {
        DatabaseUtil dbUtil = new DatabaseUtil(this.helperName);

        dbUtil.checkDb(modelEntities, messages, addMissing);
    }

    /**
     * Creates a list of ModelEntity objects based on meta data from the database
     */
    public List<ModelEntity> induceModelFromDb(Collection<String> messages) {
        DatabaseUtil dbUtil = new DatabaseUtil(this.helperName);

        return dbUtil.induceModelFromDb(messages);
    }

    public int count(final ModelEntity modelEntity, final String fieldName, final EntityCondition entityCondition,
                     final EntityFindOptions findOptions) throws GenericEntityException {
        int count = 0;
        if (modelEntity == null) {
            return count;
        }
        boolean distinct;
        if (findOptions == null) {
            distinct = false;
        } else {
            distinct = findOptions.getDistinct();
        }
        boolean verboseOn = Debug.verboseOn();

        if (verboseOn) {
            // put this inside an if statement so that we don't have to generate the string when not used...
            Debug.logVerbose("Doing count with whereEntityCondition: " + entityCondition);
        }
        ModelField fieldToSelect = modelEntity.getField(fieldName);
        String columnName = null;
        if (fieldToSelect != null) {
            columnName = fieldToSelect.getColName();
        }
        final String tableName = modelEntity.getTableName(datasourceInfo);
        String entityCondWhereString = null;
        List<EntityConditionParam> whereEntityConditionParams = new LinkedList<EntityConditionParam>();

        if (entityCondition != null) {
            entityCondWhereString = entityCondition.makeWhereString(modelEntity, whereEntityConditionParams);
        }
        final String sql = countHelper.buildCountSelectStatement(tableName, columnName, entityCondWhereString, distinct);

        if (verboseOn) {
            // put this inside an if statement so that we don't have to generate the string when not used...
            Debug.logVerbose("Setting the whereEntityConditionParams: " + whereEntityConditionParams);
        }
        // set all of the values from the Where EntityCondition

        ResultSet resultSet = null;
        SQLProcessor sqlP = new ReadOnlySQLProcessor(helperName);
        try {
            sqlP.prepareStatement(sql);
            for (EntityConditionParam param : whereEntityConditionParams) {
                SqlJdbcUtil.setValue(sqlP, param.getModelField(), modelEntity.getEntityName(), param.getFieldValue(), modelFieldTypeReader);
            }
            resultSet = sqlP.executeQuery();
            if (resultSet.next()) {
                count = resultSet.getInt(1);
            }
        } catch (SQLException e) {
            throw new GenericEntityException("SQL Exception while executing the following:" + sql, e);
        } finally {
            if (resultSet != null) {
                try {
                    resultSet.close();
                } catch (SQLException ignore) {
                }
            }
            closeSafely(sql, sqlP);
        }
        return count;
    }

    /**
     * Applies the given transformation to any entities matching the given
     * condition, by performing a SELECT followed by an UPDATE. Does NOT do
     * this using "SELECT ... FOR UPDATE" semantics (because of inconsistent
     * and/or missing support for this across database types), and therefore
     * does not guarantee that another transaction has not updated the relevant
     * row(s) between the SELECT and the UPDATE.
     *
     * @param modelEntity     the type of entity to transform (required)
     * @param entityCondition the condition that selects the entities to
     *                        transform (null means transform all)
     * @param orderBy         the order in which the entities should be
     *                        selected for updating (null means no ordering)
     * @param lockFieldName   the entity field to use for optimistic locking;
     *                        the value of this field will be read between the SELECT and the UPDATE
     *                        to determine whether another process has updated one of the target
     *                        records in the meantime; if so, the transformation will be reapplied and
     *                        another UPDATE attempted
     * @param transformation  the transformation to apply (required)
     * @return the transformed entities in the order they were selected (never
     * null)
     * @since 1.0.41
     */
    public List<GenericValue> transform(final ModelEntity modelEntity, final EntityCondition entityCondition,
                                        final List<String> orderBy, final String lockFieldName, final Transformation transformation)
            throws GenericEntityException {
        final EntityFindOptions findOptions = EntityFindOptions.findOptions();
        final ModelField lockField = modelEntity.getField(lockFieldName);
        try {
            final List<GenericValue> targetEntities =
                    selectByCondition(modelEntity, entityCondition, null, orderBy, findOptions);
            for (final GenericValue entity : targetEntities) {
                transformOne(modelEntity, transformation, lockFieldName, lockField, entity);
            }
            return targetEntities;
        } catch (final Exception e) {
            if (e instanceof GenericEntityException) {
                throw (GenericEntityException) e;
            }
            throw new GenericEntityException("Transformation failed", e);
        }
    }

    private void transformOne(final ModelEntity modelEntity, final Transformation transformation,
                              final String lockFieldName, final ModelField lockField, final GenericValue entity)
            throws GenericEntityException, InterruptedException {
        long totalBackOffMillis = 0;
        while (true) {
            final Object lockValue = entity.get(lockFieldName);
            transformation.transform(entity);
            try {
                update(entity, new EntityConditionParam(lockField, lockValue));
                if (totalBackOffMillis > 0 && LOGGER.isDebugEnabled()) {
                    LOGGER.debug(String.format("Total back-off time for %s.%s = %d",
                            modelEntity.getEntityName(), lockFieldName, totalBackOffMillis));
                }
                return;
            } catch (final GenericEntityNotFoundException notFound) {
                // We know this is because the nonPkCondition failed, because we only enter
                // this method for entities that were found by the original select operation.
                totalBackOffMillis += sleepForRandomAmountOfTime();
                select(entity);
            }
        }
    }

    // While sleeping is bad, it greatly reduces contention between threads trying to update the same column
    private long sleepForRandomAmountOfTime() throws InterruptedException {
        final long backOffMillis = zeroTo(MAX_BACK_OFF_MILLIS);
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Backing off for " + backOffMillis + "ms");
        }
        Thread.sleep(backOffMillis);
        return backOffMillis;
    }

    /**
     * Closes a SQLProcessor with exceptions logged and discarded.
     *
     * @param info Extra information to include in the error log.  This can be anything at all as long as its
     *             {@code toString()} implementation is useful, such as a list of {@code GenericValue}s that
     *             were being stored or the raw SQL being executed.
     * @param sqlP the SQL processor to be closed
     */
    private static void closeSafely(Object info, SQLProcessor sqlP) {
        try {
            sqlP.close();
        } catch (Exception ex) {
            Debug.logError(ex, "Error closing " + sqlP + "; info=[" + info + ']', module);
        }
    }

    @VisibleForTesting
    static class WhereRewrite {

        private final EntityCondition condition;
        private final Collection<InReplacement> inReplacements;

        public WhereRewrite(EntityCondition condition, Collection<InReplacement> inReplacements) {
            this.condition = condition;
            this.inReplacements = inReplacements;
        }

        public EntityCondition getNewCondition() {
            return condition;
        }

        public Collection<InReplacement> getInReplacements() {
            return inReplacements;
        }
    }

    @VisibleForTesting
    static class InReplacement {
        private final String temporaryTableName;
        private final Set<?> items;

        public InReplacement(String temporaryTableName, Set<?> items) {
            this.temporaryTableName = temporaryTableName;
            this.items = items;
        }

        public Set<?> getItems() {
            return items;
        }

        public String getTemporaryTableName() {
            return temporaryTableName;
        }
    }

    private static class EntityListIteratorWithTemporaryTableCleanup extends EntityListIterator {
        private TableCleanUp cleanUp;

        public EntityListIteratorWithTemporaryTableCleanup(SQLProcessor sqlp, ModelEntity modelEntity,
                                                           List<ModelField> selectFields, ModelFieldTypeReader modelFieldTypeReader, TableCleanUp cleanUp) {
            super(sqlp, modelEntity, selectFields, modelFieldTypeReader);
            this.cleanUp = cleanUp;
        }

        @Override
        public void close() throws GenericEntityException {
            try {
                cleanUp.cleanUp(sqlp);
            } finally {
                super.close();
            }
        }
    }

    interface TableCleanUp {
        void cleanUp(final SQLProcessor sqlP) throws GenericEntityException;
    }

    /**
     * JDEV-31097: SQL server does not allow more than 2000 parameter markers (?) which can happen with huge IN queries
     * (e.g. where pid in (1, 2, 3, 4, ...)
     * so in this case we:
     * - create a temporary table
     * - put all the 'IN' values into this table
     * - rewriteIfNeeded the original 'IN' part of the query to use the temporary table instead (where pid in (select item from #temp))
     * - run the query
     * - when the list iterator is closed, drop the temporary table
     */
    static class InQueryRewritter implements TableCleanUp {
        private static final AtomicInteger temporaryTableCounter = new AtomicInteger(1);

        final DatabaseType databaseType;
        final EntityCondition whereEntityCondition;
        final ModelEntity modelEntity;

        final private static String BIGINT = "bigint";

        //max supported indexed field size for varchar in MSSQL is 900 bytes
        final private static String VARCHAR_900 = "varchar(900)";

        Optional<WhereRewrite> whereRewrite;
        Collection<String> temporaryTableNames;

        InQueryRewritter(@Nonnull DatabaseType databaseType, EntityCondition whereEntityCondition, ModelEntity modelEntity) {
            this.databaseType = databaseType;
            this.whereEntityCondition = whereEntityCondition;
            this.modelEntity = modelEntity;

            this.whereRewrite = Optional.absent();
            this.temporaryTableNames = new HashSet<>();
        }

        boolean isRewritten() {
            return whereRewrite.isPresent();
        }

        EntityCondition rewriteIfNeeded() {
            whereRewrite = rewriteConditionToUseTemporaryTablesForLargeInClauses();
            if (whereRewrite.isPresent()) {
                return whereRewrite.get().getNewCondition();
            }
            return whereEntityCondition;
        }

        void createTemporaryTablesIfNeeded(SQLProcessor sqlP) throws GenericEntityException {
            //Generate any temporary tables required for the query (MS SQL Server only)
            if (whereRewrite.isPresent()) {
                for (InReplacement inReplacement : whereRewrite.get().getInReplacements()) {
                    String temporaryTableName = inReplacement.getTemporaryTableName();
                    generateTemporaryTable(temporaryTableName, inReplacement.getItems(), sqlP);
                    temporaryTableNames.add(temporaryTableName);
                }
            }
        }

        public void cleanUp(final SQLProcessor sqlP) throws GenericEntityException {
            dropTemporaryTables(sqlP);
        }

        TableCleanUp getTableCleanUpHandler() {
            if (isRewritten()) {
                return this;
            }
            return null;
        }

        @VisibleForTesting
        static void resetTemporaryTableCounter() {
            temporaryTableCounter.set(1);
        }

        @VisibleForTesting
        Optional<WhereRewrite> rewriteConditionToUseTemporaryTablesForLargeInClauses() {
            if (whereEntityCondition == null) {
                return Optional.absent();
            }

            //If we have less than the maximum, allow the query to go through unaltered
            if (!shouldRewrite()) {
                return Optional.absent();
            }

            //Otherwise change every IN fragment to use temporary tables
            final List<InReplacement> inReplacements = new ArrayList<InReplacement>();

            EntityCondition newCondition = EntityConditionHelper.transformCondition(whereEntityCondition, new Function<EntityExpr, EntityCondition>() {
                public EntityCondition apply(final EntityExpr input) {
                    if (input.getOperator().equals(EntityOperator.IN)) {
                        //Generate replacement
                        final Collection<?> items = (Collection<?>) input.getRhs();
                        final Set<?> itemSet = (items instanceof Set)? (Set<?>)items: new HashSet<>(items);
                        InReplacement inReplacement = new InReplacement(generateTemporaryTableName(databaseType), itemSet);
                        inReplacements.add(inReplacement);
                        EntityWhereString newRhs = new EntityWhereString("select item from " + inReplacement.getTemporaryTableName());
                        EntityExpr replacementCondition = new EntityExpr((String) input.getLhs(), input.isLUpper(), input.getOperator(), newRhs, input.isRUpper());
                        return replacementCondition;
                    } else {
                        return input;
                    }
                }
            });

            return Optional.of(new WhereRewrite(newCondition, inReplacements));
        }

        private boolean shouldRewrite() {
            final int parameterCount = whereEntityCondition.getParameterCount(modelEntity);
            return
                    (databaseType == MSSQL && parameterCount > MS_SQL_MAX_PARAMETER_COUNT)
                            || (databaseType == POSTGRES_7_3 && parameterCount > POSTGRESQL_MAX_PARAMETER_COUNT);
        }

        private String generateTemporaryTableName(DatabaseType databaseType) {
            //postgres has limit of 63 characters for table name
            if (databaseType == POSTGRES_7_3) {
                return "temp" + temporaryTableCounter.getAndIncrement();
            }
            //SQL server max temporary table name is 116 characters, so this should be fine even if they never clean up
            return "#temp" + temporaryTableCounter.getAndIncrement();
        }

        /**
         * Creates a temporary table (MS SQL Server only) and fills it with items that originally were from an
         * 'IN' query.
         *
         * @param tableName the name of the temporary table, for MSSQL with the leading '#' character.
         * @param items     the items from the 'IN' query that need to be inserted into the temporary table.
         * @param sqlP      SQL procesor to use.
         * @throws GenericEntityException if an error occurs.
         */
        private void generateTemporaryTable(String tableName, Set<?> items, SQLProcessor sqlP)
                throws GenericEntityException {
            //Ensure connection is created
            sqlP.getConnection();

            final String dataType = getColumnType(items);

            if (databaseType == POSTGRES_7_3) {
                sqlP.executeUpdate("create temporary table " + tableName + " (item " + dataType + " primary key)");
            } else if (databaseType == MSSQL && dataType.equals(VARCHAR_900)) {
                sqlP.executeUpdate("create table " + tableName + " (item " + dataType + " COLLATE database_default primary key)");
            } else {
                sqlP.executeUpdate("create table " + tableName + " (item " + dataType + " primary key)");
            }

            //Insert data into this temporary table
            sqlP.prepareStatement("insert into " + tableName + " (item) values (?)");
            PreparedStatement stat = sqlP.getPreparedStatement();
            try {
                for (Object item : items) {
                    if (item instanceof Number) {
                        stat.setLong(1, ((Number) item).longValue());
                    } else if (item instanceof String) {
                        stat.setString(1, (String) item);
                    } else {
                        stat.setObject(1, item);
                    }

                    stat.addBatch();
                }
                stat.executeBatch();
            } catch (SQLException e) {
                throw new GenericEntityException(e.getMessage(), e);
            } finally {
                try {
                    stat.close();
                } catch (SQLException ignore) {
                }
            }
        }

        /**
         * Determine the data type to create based on the item element type
         * Right now this only works for SQL server and Postgresql so we hardcode the SQL server data types
         * And we only support numbers and strings at this point
         *
         * @param items
         * @return
         */
        private String getColumnType(Collection<?> items) {
            final Object firstItem = items.iterator().next();
            if (firstItem instanceof Number) {
                return BIGINT;
            } else {
                return VARCHAR_900;
            }
        }

        private void dropTemporaryTables(final SQLProcessor sqlP) throws GenericEntityException {
            for (String temporaryTableName : temporaryTableNames) {
                sqlP.executeUpdate("drop table " + temporaryTableName);
            }
        }
    }
}
