package com.example.edusafe;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.List;

public class AdminFragment extends Fragment {
    private TextView tvReports, tvReview, tvResolved;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_admin, container, false);
        tvReports = v.findViewById(R.id.tvReports);
        tvReview = v.findViewById(R.id.tvReview);
        tvResolved = v.findViewById(R.id.tvResolved);
        v.findViewById(R.id.btnDashboard).setOnClickListener(x -> open(new SecurityDashboardFragment()));
        v.findViewById(R.id.btnSettings).setOnClickListener(x -> open(new SettingsFragment()));
        loadCounts();
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadCounts();
    }

    private void loadCounts() {
        if (tvReports == null || getContext() == null) return;
        List<DatabaseHelper.Report> reports = DatabaseHelper.get(requireContext()).getReports("All");
        int review = 0, resolved = 0;
        for (DatabaseHelper.Report report : reports) {
            if ("Under Review".equals(report.status)) review++;
            if ("Resolved".equals(report.status)) resolved++;
        }
        tvReports.setText(String.valueOf(reports.size()));
        tvReview.setText(String.valueOf(review));
        tvResolved.setText(String.valueOf(resolved));
    }

    private void open(Fragment fragment) {
        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, fragment).addToBackStack(null).commit();
    }
}
