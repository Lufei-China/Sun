package com.inmobi.supply.sun.model.runtime;

import org.slf4j.Marker;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

public class RequestMarker implements Marker {
    private final String name;
    private final String requestId;
    private List<Marker> referenceList;

    public RequestMarker(String name, String requestId) {
        if (name == null) {
            throw new IllegalArgumentException("A marker name cannot be null");
        }
        this.name = name;
        this.requestId = requestId;
    }

    public String getName() {
        return name;
    }

    public synchronized void add(Marker reference) {
        if (reference == null) {
            throw new IllegalArgumentException("A null value cannot be added to a Marker as reference.");
        }
        if (this.contains(reference)) {
            return;

        } else if (reference.contains(this)) { // avoid recursion
            return;
        } else {
            if (referenceList == null) {
                referenceList = new Vector<>();
            }
            referenceList.add(reference);
        }
    }

    public synchronized boolean hasReferences() {
        return (referenceList != null) && (referenceList.size() > 0);
    }

    public boolean hasChildren() {
        return hasReferences();
    }

    public synchronized Iterator<Marker> iterator() {
        if (referenceList != null) {
            return referenceList.iterator();
        } else {
            List<Marker> emptyList = Collections.emptyList();
            return emptyList.iterator();
        }
    }

    public synchronized boolean remove(Marker referenceToRemove) {
        if (referenceList == null) {
            return false;
        }

        int size = referenceList.size();
        for (int i = 0; i < size; i++) {
            Marker m = referenceList.get(i);
            if (referenceToRemove.equals(m)) {
                referenceList.remove(i);
                return true;
            }
        }
        return false;
    }

    public boolean contains(Marker other) {
        if (other == null) {
            throw new IllegalArgumentException("Other cannot be null");
        }

        if (this.equals(other)) {
            return true;
        }

        if (hasReferences()) {
            for (int i = 0; i < referenceList.size(); i++) {
                Marker ref = (Marker) referenceList.get(i);
                if (ref.contains(other)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean contains(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Other cannot be null");
        }

        if (this.name.equals(name)) {
            return true;
        }
        if (hasReferences()) {
            for (int i = 0; i < referenceList.size(); i++) {
                Marker ref = (Marker) referenceList.get(i);
                if (ref.contains(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean equals(Object obj) {
        if (obj instanceof Marker) {
            final Marker other = (Marker) obj;
            return name.equals(other.getName());
        }
        return false;
    }

    public int hashCode() {
        return this.name.hashCode();
    }

    public String toString() {
        return this.name;
    }
}
