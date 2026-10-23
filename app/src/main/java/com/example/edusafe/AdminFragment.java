package com.example.edusafe;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class AdminFragment extends Fragment {
    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_admin, container, false);
        v.findViewById(R.id.btnFeedModeration).setOnClickListener(x -> open(new AdminFeedModerationFragment()));
        v.findViewById(R.id.btnRegisteredUsers).setOnClickListener(x -> open(new AdminRegisteredUsersFragment()));
        return v;
    }

    private void open(Fragment fragment) {
        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, fragment).addToBackStack(null).commit();
    }
}


