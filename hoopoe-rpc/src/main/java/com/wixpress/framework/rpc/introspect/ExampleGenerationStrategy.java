package com.wixpress.framework.rpc.introspect;

import com.wixpress.hoopoe.reflection.genericTypes.GenericType;

/**
 * @author shaiyallin
 * @since 1/3/12
 */
public interface ExampleGenerationStrategy {
    
    public <T> T exampleFor(GenericType type);
}