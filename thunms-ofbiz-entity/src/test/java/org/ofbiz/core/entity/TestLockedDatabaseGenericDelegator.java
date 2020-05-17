package org.ofbiz.core.entity;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static java.util.Collections.emptyList;
import static org.mockito.Mockito.mock;

public class TestLockedDatabaseGenericDelegator {
    private DelegatorInterface delegatorInterface;

    @Before
    public void setUp() throws Exception {
        this.delegatorInterface = new LockedDatabaseGenericDelegator();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void transformShouldNotBeSupported() throws Exception {
        // Set up
        final EntityCondition mockEntityCondition = mock(EntityCondition.class);
        final Transformation mockTransformation = mock(Transformation.class);
        final List<String> orderBy = emptyList();

        // Invoke
        delegatorInterface.transform("Anything", mockEntityCondition, orderBy, "AnyField", mockTransformation);
    }
}
