package com.prateek.androidprintsample.utils;

import android.content.Context;
import android.widget.ArrayAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by thakurprateek on 16-02-2016.
 */
public class AdapterForPint<T> extends ArrayAdapter<T>{

    private ArrayList<T> objects;

    public AdapterForPint(Context context, int resource, ArrayList<T> objects) {
        super(context, resource, objects);
        setObjects(objects);
    }

    public ArrayList<T> getObjects() {
        return objects;
    }

    public void setObjects(ArrayList<T> objects) {
        this.objects = objects;
    }

    public List<T> cloneItems() {
        return new ArrayList<T>(objects);
    }
}
