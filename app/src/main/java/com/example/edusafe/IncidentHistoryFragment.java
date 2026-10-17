package com.example.edusafe;

import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class IncidentHistoryFragment extends Fragment {

    private static final String[] FILTERS = {
            "All",
            "Robbery",
            "Theft",
            "Assault",
            "Harassment",
            "Suspicious Activity",
            "Other"
    };

    private String filter = "All";

    private final List<TextView> chips = new ArrayList<>();

    private TextView tvEmpty;
    private TextView tvFooter;

    private LinearLayout listContainer;
    private LinearLayout chipPanel;

    private DatabaseHelper db;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(
                R.layout.fragment_history,
                container,
                false
        );

        db = DatabaseHelper.get(requireContext());

        tvEmpty = v.findViewById(R.id.tvEmpty);
        tvFooter = v.findViewById(R.id.tvFooter);
        listContainer = v.findViewById(R.id.listContainer);
        chipPanel = v.findViewById(R.id.chipPanel);

        setupFilters();

        // Show or hide category filters
        v.findViewById(R.id.btnFilter).setOnClickListener(x -> {
            if (chipPanel.getVisibility() == View.VISIBLE) {
                chipPanel.setVisibility(View.GONE);
            } else {
                chipPanel.setVisibility(View.VISIBLE);
            }
        });

        // Open Report Incident page
        v.findViewById(R.id.btnNewReport).setOnClickListener(x -> {
            requireActivity()
                    .getSupportFragmentManager()
                    .beginTransaction()
                    .replace(
                            R.id.container,
                            new ReportIncidentFragment()
                    )
                    .addToBackStack(null)
                    .commit();
        });

        return v;
    }

    @Override
    public void onResume() {
        super.onResume();

        load();

        SyncManager.syncAll(requireContext(), () -> {
            if (isAdded() && getView() != null) {
                requireActivity().runOnUiThread(this::load);
            }
        });
    }

    // CREATE CATEGORY FILTERS
    private void setupFilters() {

        chips.clear();
        chipPanel.removeAllViews();

        LinearLayout row = null;

        for (int i = 0; i < FILTERS.length; i++) {

            if (i % 3 == 0) {

                row = new LinearLayout(requireContext());
                row.setOrientation(LinearLayout.HORIZONTAL);

                chipPanel.addView(
                        row,
                        new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                );
            }

            TextView chip = makeChip(FILTERS[i]);

            if (row != null) {
                row.addView(chip);
            }

            chips.add(chip);
        }

        // Keep the last row aligned
        int filler = (3 - FILTERS.length % 3) % 3;

        for (int k = 0; k < filler; k++) {

            View blank = new View(requireContext());

            LinearLayout.LayoutParams lp =
                    new LinearLayout.LayoutParams(
                            0,
                            1,
                            1f
                    );

            lp.setMargins(
                    0,
                    dp(4),
                    dp(8),
                    dp(4)
            );

            blank.setLayoutParams(lp);

            if (row != null) {
                row.addView(blank);
            }
        }
    }

    // CREATE ONE FILTER BUTTON
    private TextView makeChip(String label) {

        TextView tv = new TextView(requireContext());

        tv.setText(label);
        tv.setTextSize(12);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setMinHeight(dp(44));
        tv.setContentDescription("Filter by " + label);

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );

        lp.setMargins(
                0,
                dp(4),
                dp(8),
                dp(4)
        );

        tv.setLayoutParams(lp);

        styleChip(tv, label.equals(filter));

        tv.setOnClickListener(x -> {

            filter = label;

            for (TextView chip : chips) {

                boolean selected =
                        chip.getText().toString().equals(filter);

                styleChip(chip, selected);
            }

            load();
        });

        return tv;
    }

    // FILTER BUTTON APPEARANCE
    private void styleChip(TextView tv, boolean selected) {

        tv.setBackgroundResource(
                selected
                        ? R.drawable.bg_chip_selected
                        : R.drawable.bg_chip
        );

        tv.setTextColor(
                requireContext().getColor(
                        selected
                                ? R.color.white
                                : R.color.navy
                )
        );

        tv.setPadding(
                dp(8),
                dp(8),
                dp(8),
                dp(8)
        );
    }

    // LOAD ONLY THE USER'S SAVED REPORTS
    private void load() {

        if (!isAdded() || getView() == null) {
            return;
        }

        listContainer.removeAllViews();

        int count = 0;
        boolean failed = false;

        try {

            // Uses the existing locally stored reports.
            // No campus-wide incident query.
            for (DatabaseHelper.Report report :
                    db.getReports(filter)) {

                boolean pending =
                        "pending".equalsIgnoreCase(
                                report.syncStatus
                        );

                String extra =
                        "Code " + report.trackingCode
                                + (pending
                                ? " · waiting to send (tap to edit)"
                                : "");

                View reportCard = card(
                        report.type,
                        report.location,
                        report.createdAt,
                        report.status,
                        report.photoUri,
                        extra
                );

                // Only unsent reports can be edited/deleted
                if (pending) {

                    reportCard.setContentDescription(
                            "Report " + report.trackingCode
                                    + ". Tap to edit or delete"
                    );

                    reportCard.setOnClickListener(x ->
                            showOptions(report));
                }

                listContainer.addView(reportCard);

                count++;
            }

        } catch (Exception e) {

            failed = true;
            toast("Couldn't load reports. Please try again.");
        }

        tvEmpty.setVisibility(
                count == 0 ? View.VISIBLE : View.GONE
        );

        if (failed) {
            tvEmpty.setText("Couldn't load reports. Please try again.");
        } else if ("All".equals(filter)) {
            tvEmpty.setText(
                    "You haven't submitted any reports yet."
            );
        } else {
            tvEmpty.setText(
                    "No reports of this type."
            );
        }

        tvFooter.setVisibility(
                count == 0 ? View.GONE : View.VISIBLE
        );

        tvFooter.setText("You have reached the end.");
    }

    // CREATE REPORT CARD
    private View card(
            String title,
            String location,
            long createdAt,
            String status,
            String media,
            String extra) {

        View view = LayoutInflater
                .from(requireContext())
                .inflate(
                        R.layout.item_incident,
                        listContainer,
                        false
                );

        TextView tvTitle =
                view.findViewById(R.id.tvTitle);

        TextView tvLocation =
                view.findViewById(R.id.tvLocation);

        TextView tvTime =
                view.findViewById(R.id.tvTime);

        TextView tvExtra =
                view.findViewById(R.id.tvExtra);

        TextView tvStatus =
                view.findViewById(R.id.tvStatus);

        ImageView ivThumb =
                view.findViewById(R.id.ivThumb);

        tvTitle.setText(
                title == null ? "" : title
        );

        tvLocation.setText(
                location == null ? "" : location
        );

        tvTime.setText(when(createdAt));

        tvExtra.setVisibility(
                extra == null || extra.isEmpty()
                        ? View.GONE
                        : View.VISIBLE
        );

        if (extra != null) {
            tvExtra.setText(extra);
        }

        stylePill(tvStatus, status);
        setThumb(ivThumb, media);

        return view;
    }

    // REPORT STATUS APPEARANCE
    private void stylePill(
            TextView tv,
            String status) {

        int bg;
        int fg;

        if ("Verified".equalsIgnoreCase(status)) {

            bg = requireContext().getColor(R.color.navy);
            fg = requireContext().getColor(R.color.white);

        } else if ("Under Review".equalsIgnoreCase(status)) {

            bg = requireContext().getColor(R.color.light_blue);
            fg = requireContext().getColor(R.color.navy);

        } else if ("Resolved".equalsIgnoreCase(status)) {

            bg = requireContext().getColor(R.color.ink);
            fg = requireContext().getColor(R.color.white);

        } else {

            bg = Color.parseColor("#EEF1F6");
            fg = requireContext().getColor(R.color.muted);
        }

        tv.setText(
                status == null ? "Pending" : status
        );

        tv.setBackgroundTintList(
                ColorStateList.valueOf(bg)
        );

        tv.setTextColor(fg);
    }

    // DISPLAY REPORT PHOTO IF AVAILABLE
    private void setThumb(
            ImageView image,
            String path) {

        image.setVisibility(View.GONE);

        if (path == null || path.isEmpty()) {
            return;
        }

        File file = new File(path);

        if (!file.isFile()) {
            return;
        }

        if (path.toLowerCase(Locale.ROOT).endsWith(".mp4")) {

            image.setImageResource(
                    android.R.drawable.ic_media_play
            );

            image.setVisibility(View.VISIBLE);
            return;
        }

        BitmapFactory.Options options =
                new BitmapFactory.Options();

        options.inSampleSize = 8;

        Bitmap bitmap =
                BitmapFactory.decodeFile(path, options);

        if (bitmap != null) {

            image.setImageBitmap(bitmap);
            image.setVisibility(View.VISIBLE);
        }
    }

    // FORMAT REPORT DATE
    private String when(long ms) {

        long days = Math.round(
                (startOfDay(
                        Calendar.getInstance(),
                        System.currentTimeMillis()
                ) - startOfDay(
                        Calendar.getInstance(),
                        ms
                )) / 86400000.0
        );

        String time =
                new SimpleDateFormat(
                        "HH:mm",
                        Locale.getDefault()
                ).format(new Date(ms));

        if (days <= 0) {
            return "Today > " + time;
        }

        if (days == 1) {
            return "Yesterday > " + time;
        }

        return days + " days ago";
    }

    private long startOfDay(
            Calendar calendar,
            long ms) {

        calendar.setTimeInMillis(ms);

        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        return calendar.getTimeInMillis();
    }

    // OPTIONS FOR UNSENT REPORTS
    private void showOptions(DatabaseHelper.Report report) {

        new AlertDialog.Builder(requireContext())
                .setTitle(report.trackingCode)
                .setItems(
                        new String[]{
                                "Edit description",
                                "Delete report"
                        },
                        (dialog, which) -> {

                            if (which == 0) {
                                editDialog(report);
                            } else {
                                confirmDelete(report);
                            }
                        }
                )
                .show();
    }

    // EDIT UNSENT REPORT DESCRIPTION
    private void editDialog(DatabaseHelper.Report report) {

        EditText input = new EditText(requireContext());

        input.setText(report.description);
        input.setMinLines(3);

        input.setPadding(
                dp(20),
                dp(16),
                dp(20),
                dp(16)
        );

        AlertDialog dialog =
                new AlertDialog.Builder(requireContext())
                        .setTitle("Edit description")
                        .setView(input)
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Save", null)
                        .create();

        dialog.setOnShowListener(ignored ->

                dialog.getButton(
                        AlertDialog.BUTTON_POSITIVE
                ).setOnClickListener(x -> {

                    String text =
                            input.getText().toString().trim();

                    if (text.length() < 10) {

                        input.setError(
                                "Please write at least 10 characters."
                        );

                        return;
                    }

                    if (!db.updateReport(
                            report.id,
                            report.type,
                            text)) {

                        toast(
                                "This report can no longer be edited."
                        );

                        load();
                        return;
                    }

                    dialog.dismiss();
                    load();
                })
        );

        dialog.show();
    }

    // CONFIRM REPORT DELETION
    private void confirmDelete(DatabaseHelper.Report report) {

        new AlertDialog.Builder(requireContext())
                .setTitle("Delete report?")
                .setMessage("This can't be undone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton(
                        "Delete",
                        (dialog, which) -> {

                            if (db.deleteReport(report.id)) {

                                deleteOwnedPhoto(report.photoUri);

                            } else {

                                toast(
                                        "This report can no longer be deleted."
                                );
                            }

                            load();
                        }
                )
                .show();
    }

    // DELETE PHOTO STORED IN APP DIRECTORY
    private void deleteOwnedPhoto(String path) {

        if (path == null || path.isEmpty()) {
            return;
        }

        try {

            File root = requireContext()
                    .getFilesDir()
                    .getCanonicalFile();

            File photo = new File(path)
                    .getCanonicalFile();

            if (photo.getPath().startsWith(
                    root.getPath() + File.separator
            ) && photo.isFile()) {

                photo.delete();
            }

        } catch (Exception ignored) {

            // Never delete files outside app storage.
        }
    }

    private void toast(String message) {

        if (getContext() != null) {

            Toast.makeText(
                    getContext(),
                    message,
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private int dp(int value) {

        return (int) (
                value *
                        getResources()
                                .getDisplayMetrics()
                                .density
        );
    }
}

