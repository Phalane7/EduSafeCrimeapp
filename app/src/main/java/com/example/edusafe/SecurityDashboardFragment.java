package com.example.edusafe;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SecurityDashboardFragment extends Fragment {
    private static final String[] FILTERS = {"All", "Received", "Under Review", "Resolved"};
    private final List<TextView> filterChips = new ArrayList<>();
    private String filter = "All";
    private DatabaseHelper db;
    private LinearLayout listContainer, filterRow;
    private TextView tvTotal, tvReceived, tvReview, tvResolved, tvEmpty;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_security_dashboard, container, false);
        db = DatabaseHelper.get(requireContext());
        listContainer = v.findViewById(R.id.listContainer);
        filterRow = v.findViewById(R.id.filterRow);
        tvTotal = v.findViewById(R.id.tvTotal);
        tvReceived = v.findViewById(R.id.tvReceived);
        tvReview = v.findViewById(R.id.tvReview);
        tvResolved = v.findViewById(R.id.tvResolved);
        tvEmpty = v.findViewById(R.id.tvEmpty);
        buildFilters();
        loadReports();
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadReports();
    }

    private void buildFilters() {
        filterRow.removeAllViews();
        filterChips.clear();
        for (String label : FILTERS) {
            TextView chip = new TextView(requireContext());
            chip.setText(label);
            chip.setTextSize(13);
            chip.setTypeface(null, Typeface.BOLD);
            chip.setGravity(Gravity.CENTER);
            chip.setMinHeight(dp(44));
            chip.setPadding(dp(14), dp(8), dp(14), dp(8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, dp(8), 0);
            filterRow.addView(chip, lp);
            filterChips.add(chip);
            chip.setOnClickListener(x -> {
                filter = label;
                styleFilters();
                loadReports();
            });
        }
        styleFilters();
    }

    private void styleFilters() {
        if (getContext() == null) return;
        for (TextView chip : filterChips) {
            boolean selected = filter.equals(chip.getText().toString());
            chip.setBackgroundResource(selected ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
            chip.setTextColor(requireContext().getColor(selected ? R.color.white : R.color.navy));
        }
    }

    private void loadReports() {
        if (listContainer == null || getContext() == null) return;
        List<DatabaseHelper.Report> reports = db.getReports("All");
        int received = 0, review = 0, resolved = 0, visible = 0;
        listContainer.removeAllViews();
        for (DatabaseHelper.Report report : reports) {
            if ("Received".equals(report.status)) received++;
            else if ("Under Review".equals(report.status)) review++;
            else if ("Resolved".equals(report.status)) resolved++;
            if (!"All".equals(filter) && !filter.equals(report.status)) continue;
            listContainer.addView(reportCard(report));
            visible++;
        }
        tvTotal.setText(String.valueOf(reports.size()));
        tvReceived.setText(String.valueOf(received));
        tvReview.setText(String.valueOf(review));
        tvResolved.setText(String.valueOf(resolved));
        tvEmpty.setVisibility(visible == 0 ? View.VISIBLE : View.GONE);
        tvEmpty.setText(reports.isEmpty()
                ? "No incident submissions on this device yet."
                : "No reports in this status. Choose another filter.");
    }

    private View reportCard(DatabaseHelper.Report report) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, dp(10), 0, 0);
        card.setLayoutParams(cardLp);

        LinearLayout heading = new LinearLayout(requireContext());
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(requireContext());
        title.setText(report.type == null ? "Incident report" : report.type);
        title.setTextColor(requireContext().getColor(R.color.ink));
        title.setTextSize(16);
        title.setTypeface(null, Typeface.BOLD);
        heading.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView status = new TextView(requireContext());
        status.setText(report.status);
        status.setTextSize(11);
        status.setTypeface(null, Typeface.BOLD);
        status.setPadding(dp(10), dp(6), dp(10), dp(6));
        styleStatus(status, report.status);
        heading.addView(status);
        card.addView(heading);

        addLine(card, "Code " + report.trackingCode, true);
        addLine(card, report.location == null || report.location.trim().isEmpty()
                ? "Location not added" : report.location, false);
        addLine(card, report.description == null ? "" : report.description, false);
        String time = new SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.getDefault())
                .format(new Date(report.createdAt));
        addLine(card, time, false);

        TextView action = new TextView(requireContext());
        action.setText("Review report  ›");
        action.setTextColor(requireContext().getColor(R.color.navy));
        action.setTextSize(14);
        action.setTypeface(null, Typeface.BOLD);
        action.setGravity(Gravity.CENTER_VERTICAL);
        action.setMinHeight(dp(44));
        action.setContentDescription("Review report " + report.trackingCode);
        card.addView(action);
        card.setClickable(true);
        card.setFocusable(true);
        card.setContentDescription(report.type + ", " + report.status + ", tracking " + report.trackingCode);
        card.setOnClickListener(x -> showReport(report));
        return card;
    }

    private void addLine(LinearLayout card, String text, boolean emphasized) {
        if (text == null || text.trim().isEmpty()) return;
        TextView line = new TextView(requireContext());
        line.setText(text);
        line.setTextColor(requireContext().getColor(emphasized ? R.color.navy : R.color.muted));
        line.setTextSize(emphasized ? 13 : 14);
        if (emphasized) line.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(7);
        card.addView(line, lp);
    }

    private void styleStatus(TextView tv, String value) {
        int bg = "Resolved".equals(value) ? R.color.ink
                : "Under Review".equals(value) ? R.color.light_blue : R.color.glass_red;
        int fg = "Under Review".equals(value) ? R.color.navy
                : "Received".equals(value) ? R.color.red : R.color.white;
        tv.setBackgroundResource(R.drawable.bg_pill);
        tv.setBackgroundTintList(ColorStateList.valueOf(requireContext().getColor(bg)));
        tv.setTextColor(requireContext().getColor(fg));
    }

    private void showReport(DatabaseHelper.Report report) {
        String location = report.location == null || report.location.trim().isEmpty()
                ? "Not provided" : report.location;
        String message = "Code: " + report.trackingCode
                + "\nStatus: " + report.status
                + "\nLocation: " + location
                + "\nSubmitted: " + new SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.getDefault())
                .format(new Date(report.createdAt))
                + "\n\n" + report.description;
        new AlertDialog.Builder(requireContext())
                .setTitle(report.type)
                .setMessage(message)
                .setNegativeButton("Close", null)
                .setPositiveButton("Update status", (dialog, which) -> chooseStatus(report))
                .show();
    }

    private void chooseStatus(DatabaseHelper.Report report) {
        String[] statuses = {"Received", "Under Review", "Resolved"};
        new AlertDialog.Builder(requireContext())
                .setTitle("Update report status")
                .setItems(statuses, (dialog, which) -> {
                    if (db.updateReportStatus(report.id, statuses[which])) {
                        Toast.makeText(requireContext(), "Status updated", Toast.LENGTH_SHORT).show();
                        loadReports();
                    } else {
                        Toast.makeText(requireContext(), "Couldn't update this report", Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
