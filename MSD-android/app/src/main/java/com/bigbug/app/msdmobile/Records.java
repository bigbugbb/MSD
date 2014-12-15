package com.bigbug.app.msdmobile;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Created by bigbug on 12/11/14.
 */
public class Records<E> implements Iterable<E>, Serializable {

    public List<E> mRecords;

    public Records() {
        mRecords = Collections.synchronizedList(new ArrayList<E>());
    }

    public Records(int initialCapacity) {
        mRecords = Collections.synchronizedList(new ArrayList<E>(initialCapacity));
    }

    @Override
    public Iterator<E> iterator() {
        return mRecords.iterator();
    }

    public int size() {
        return mRecords.size();
    }

    public E get(int index) {
        return mRecords.get(index);
    }

    public boolean add(E object) {
        return mRecords.add(object);
    }

    public boolean addAll(Records<? extends E> records) {
        return mRecords.addAll(records.mRecords);
    }

    public void clear() {
        mRecords.clear();
    }
}