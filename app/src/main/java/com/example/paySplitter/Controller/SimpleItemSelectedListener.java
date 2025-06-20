package com.example.paySplitter.Controller;

import android.view.View;
import android.widget.AdapterView;

public class SimpleItemSelectedListener implements AdapterView.OnItemSelectedListener {
    private final OnItemSelected handler;

    public interface OnItemSelected {
        void onItemSelected(int position);
    }

    public SimpleItemSelectedListener(OnItemSelected handler) {
        this.handler = handler;
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        handler.onItemSelected(position);
        parent.setSelection(position);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {}
}
