package com.example.paySplitter.View;
    
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.example.paySplitter.R;
//Delete confirmation message
public class DeleteConfirmation extends DialogFragment {

    public interface DeleteConfirmationListener {
        void onConfirmDelete();
        void onCancelDelete();
    }

    private static final String ARG_NAME = "arg_name";
    private DeleteConfirmationListener listener;

    public static DeleteConfirmation newInstance(String name) {
        DeleteConfirmation fragment = new DeleteConfirmation();
        Bundle args = new Bundle();
        args.putString(ARG_NAME, name);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof DeleteConfirmationListener) {
            listener = (DeleteConfirmationListener) context;
        } else {
            throw new ClassCastException(context
                    + " must implement DeleteConfirmationListener");
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        String name = getArguments().getString(ARG_NAME);
        return new AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete_confirmation_title)
                .setMessage(getText(R.string.delete_confirmation_message) + " " + name + "?")
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        listener.onConfirmDelete();
                    }
                })
                .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        listener.onCancelDelete();
                    }
                })
                .create();
    }
}
