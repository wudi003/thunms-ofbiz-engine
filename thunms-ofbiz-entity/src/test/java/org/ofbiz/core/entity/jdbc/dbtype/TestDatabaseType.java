package org.ofbiz.core.entity.jdbc.dbtype;

import org.junit.Test;

import static org.junit.Assert.assertEquals;


public class TestDatabaseType {

    @Test
    public void testDatabaseTypes() {
        // This test will break when a new database type is added.
        // This is to make sure that if new oracle type is added the if clause inside
        // DatabaseUtil#getIndexInfo is updated.
        // See comments in the DatabaseUtil#getIndexInfo for details
        assertEquals(16, DatabaseTypeFactory.DATABASE_TYPES.size());
    }
}
