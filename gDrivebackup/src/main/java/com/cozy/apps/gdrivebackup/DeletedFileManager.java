package com.cozy.apps.gdrivebackup;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class DeletedFileManager {

    private static final String DELETED_FILE = "DELETE_FILE";
    private HashSet<String> mDeletedFileSet = new HashSet<>();
    private final Type mStringListType = new TypeToken<List<String>>() {}.getType();
    private Context context;
    private SharedPreferences mSharedPref;

    public DeletedFileManager(Context context) {
        ArrayList<String> arrayList = new Gson().fromJson(getDeletedFile(), mStringListType);
        mDeletedFileSet.addAll(arrayList);
        this.context = context;
    }

    public void setDeletedFile(String fileName) {
        mDeletedFileSet.add(fileName);
        persist();
    }

    public boolean isDeletedFile(String fileName) {
        return mDeletedFileSet.contains(fileName);
    }

    private void persist() {
        savePrefDeletedFile(new Gson().toJson(mDeletedFileSet, mStringListType));
    }
    public void savePrefDeletedFile(String value){
        mSharedPref = context.getSharedPreferences("pref", Context.MODE_PRIVATE);
        mSharedPref.edit().putString(DELETED_FILE,value).apply();
    }
    public String getDeletedFile(){
        mSharedPref = context.getSharedPreferences("pref", Context.MODE_PRIVATE);
        return mSharedPref.getString(DELETED_FILE,"[]");
    }
}
