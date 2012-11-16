/*
 * Copyright (C) 2011-2012 Intel Corporation
 * All rights reserved.
 */
package com.intel.mtwilson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import javax.ws.rs.core.MultivaluedMap;

/**
 *
 * @since 0.5.2
 * @author jbuhacoff
 */
public class MultivaluedMapImpl<K,V> extends HashMap<K,List<V>> implements MultivaluedMap<K,V> {

    
    @Override
    public void putSingle(K k, V v) {
        ArrayList<V> list = new ArrayList<V>();
        list.add(v);
        put(k, list);
    }

    @Override
    public void add(K k, V v) {
        List<V> list = get(k);
        if( list == null ) {
            list = new ArrayList<V>();
        }
        list.add(v);
        put(k, list);
    }

    @Override
    public V getFirst(K k) {
        List<V> list = get(k);
        if( list.isEmpty() ) {
            return null;
        }
        return list.get(0);
    }

}
