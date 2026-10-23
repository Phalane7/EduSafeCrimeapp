package com.example.edusafe;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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
import androidx.fragment.app.Fragment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HelpSafetyFragment extends Fragment {
    private static final String CACHE_KEY = "help_cache";
    private static final String[] CATEGORIES = {"All", "Personal", "Campus"};

    private static class Tip {
        final String title, text, category;
        Tip(String title, String text, String category) {
            this.title = title;
            this.text = text;
            this.category = category;
        }
    }

    private final List<Tip> tips = new ArrayList<>();
    private final List<String[]> numbers = new ArrayList<>();
    private final List<String> tipsOfDay = new ArrayList<>();
    private final List<TextView> chips = new ArrayList<>();
    private String category = "All";
    private String query = "";
    private LinearLayout tipsContainer, emergencyContainer;
    private TextView tvNoTips, tvTipOfDay;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_help, container, false);
        tipsContainer = view.findViewById(R.id.tipsContainer);
        emergencyContainer = view.findViewById(R.id.emergencyContainer);
        tvNoTips = view.findViewById(R.id.tvNoTips);
        tvTipOfDay = view.findViewById(R.id.tvTipOfDay);

        view.findViewById(R.id.btnBack).setOnClickListener(
                v -> getParentFragmentManager().popBackStack());

        LinearLayout chipPanel = view.findViewById(R.id.chipPanel);
        chips.clear();
        chipPanel.removeAllViews();
        for (String name : CATEGORIES) {
            TextView chip = makeChip(name);
            chips.add(chip);
            chipPanel.addView(chip);
        }

        EditText search = view.findViewById(R.id.etSearch);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                query = s.toString().trim();
                renderTips();
            }
        });

        loadDefaults();
        String cached = Session.getString(requireContext(), CACHE_KEY);
        if (cached != null) {
            try {
                apply(new JSONObject(cached));
            } catch (Exception ignored) {
                // Keep safe built-in information if the cache cannot be read.
            }
        }
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        renderAll();
        fetch();
    }

    // General safety guidance is intentionally available even without internet.
    // No example incidents, posts or fake phone numbers are inserted.
    private void loadDefaults() {
        tips.clear();
        tipsOfDay.clear();
        numbers.clear();

        tips.add(new Tip("Walk in groups after dark",
                "Choose well-lit routes and travel with someone you trust.", "Campus"));
        tips.add(new Tip("Share your trip",
                "Tell a trusted contact where you are going and when you arrive.", "Personal"));
        tips.add(new Tip("Keep your phone charged",
                "Make sure you can contact someone if you need help.", "Personal"));

        tipsOfDay.add("If you feel unsafe, move to a populated area and ask for help.");
        tipsOfDay.add("Keep your trusted contacts up to date.");
        tipsOfDay.add("Use well-lit routes whenever possible.");

        // South African national emergency numbers, not invented campus contacts.
        numbers.add(new String[]{"Emergency services (mobile)", "112"});
        numbers.add(new String[]{"SAPS - Police", "10111"});
    }

    private void apply(JSONObject data) {
        JSONArray serverTips = data.optJSONArray("tips");
        if (serverTips != null) {
            tips.clear();
            for (int i = 0; i < serverTips.length(); i++) {
                JSONObject item = serverTips.optJSONObject(i);
                if (item == null) continue;
                tips.add(new Tip(item.optString("title"), item.optString("text"),
                        item.optString("category", "Personal")));
            }
        }

        JSONArray serverNumbers = data.optJSONArray("emergencyNumbers");
        if (serverNumbers != null) {
            // Keep national emergency numbers and add verified, server-provided contacts.
            numbers.clear();
            numbers.add(new String[]{"Emergency services (mobile)", "112"});
            numbers.add(new String[]{"SAPS - Police", "10111"});
            for (int i = 0; i < serverNumbers.length(); i++) {
                JSONObject item = serverNumbers.optJSONObject(i);
                if (item == null) continue;
                String label = item.optString("label").trim();
                String number = item.optString("number").trim();
                if (label.isEmpty() || !number.matches("[+0-9 ()-]{3,25}")) continue;
                if (number.replaceAll("[^0-9]", "").equals("0111112222")) continue;
                numbers.add(new String[]{label, number});
            }
        }

        JSONArray serverDaily = data.optJSONArray("tipsOfTheDay");
        if (serverDaily != null && serverDaily.length() > 0) {
            tipsOfDay.clear();
            for (int i = 0; i < serverDaily.length(); i++) {
                String text = serverDaily.optString(i).trim();
                if (!text.isEmpty()) tipsOfDay.add(text);
            }
        }
    }

    private void fetch() {
        Context app = requireContext().getApplicationContext();
        Net.bg(() -> {
            ApiClient.Result result = ApiClient.getPublic(app, "/help");
            if (result.ok() && result.json != null) {
                Session.putString(app, CACHE_KEY, result.json.toString());
            }
            Net.main(() -> {
                if (!isAdded() || getView() == null || !result.ok() || result.json == null) return;
                // Rebuild from defaults so stale server contacts are not retained.
                loadDefaults();
                apply(result.json);
                renderAll();
            });
        });
    }

    private void renderAll() {
        if (getView() == null) return;
        if (!tipsOfDay.isEmpty()) {
            int index = (int) ((System.currentTimeMillis() / 86400000L) % tipsOfDay.size());
            tvTipOfDay.setText(tipsOfDay.get(index));
        } else {
            tvTipOfDay.setText("");
        }
        renderTips();
        emergencyContainer.removeAllViews();
        for (String[] contact : numbers) {
            emergencyContainer.addView(emergencyRow(contact[0], contact[1]));
        }
    }

    private TextView makeChip(String name) {
        TextView chip = new TextView(requireContext());
        chip.setText(name);
        chip.setTextSize(12);
        chip.setTypeface(null, Typeface.BOLD);
        chip.setGravity(Gravity.CENTER);
        chip.setMinHeight(dp(36));
        chip.setContentDescription("Show " + name + " tips");
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(dp(2), dp(3), dp(2), dp(3));
        chip.setLayoutParams(params);
        styleChip(chip, name.equals(category));
        chip.setOnClickListener(v -> {
            category = name;
            for (TextView other : chips) {
                styleChip(other, other.getText().toString().equals(category));
            }
            renderTips();
        });
        return chip;
    }

    private void styleChip(TextView chip, boolean selected) {
        GradientDrawable bg = rounded(selected ? Color.rgb(35, 77, 183) : Color.TRANSPARENT, 12);
        chip.setBackground(bg);
        chip.setTextColor(selected ? Color.WHITE : Color.rgb(32, 61, 113));
    }

    private void renderTips() {
        if (tipsContainer == null) return;
        tipsContainer.removeAllViews();
        String search = query.toLowerCase(Locale.ROOT);
        int count = 0;
        for (Tip tip : tips) {
            if (!category.equals("All") && !category.equalsIgnoreCase(tip.category)) continue;
            if (!search.isEmpty() && !(tip.title + " " + tip.text)
                    .toLowerCase(Locale.ROOT).contains(search)) continue;
            View row = tipRow(tipsContainer, iconFor(tip.title), tip.title, tip.text);
            row.setContentDescription(tip.title + ". " + tip.text);
            styleTipRow(row);
            tipsContainer.addView(row);
            count++;
        }
        tvNoTips.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
    }

    private int iconFor(String title) {
        String lower = title.toLowerCase(Locale.ROOT);
        if (lower.contains("walk")) return R.drawable.ic_walk;
        if (lower.contains("number") || lower.contains("call")) return R.drawable.ic_call;
        if (lower.contains("security") || lower.contains("emergency")) return R.drawable.ic_shield;
        return R.drawable.ic_location;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private void styleTipRow(View row) {
        row.setBackground(rounded(Color.WHITE, 18));
        row.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        row.setLayoutParams(lp);
        TextView title = row.findViewById(R.id.tvTipTitle);
        TextView detail = row.findViewById(R.id.tvTipText);
        title.setTextColor(Color.rgb(16, 39, 83));
        detail.setTextColor(Color.rgb(99, 119, 155));
        title.setTextSize(14);
        detail.setTextSize(12);
    }

    private View emergencyRow(String label, String number) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(14), dp(10), dp(14), dp(10));
        row.setBackground(rounded(Color.rgb(207, 20, 83), 18));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(65));
        lp.bottomMargin = dp(9);
        row.setLayoutParams(lp);

        ImageView icon = new ImageView(requireContext());
        icon.setImageResource(R.drawable.ic_call);
        icon.setImageTintList(ColorStateList.valueOf(Color.rgb(207, 20, 83)));
        icon.setPadding(dp(11), dp(11), dp(11), dp(11));
        icon.setBackground(rounded(Color.WHITE, 50));
        row.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout labels = new LinearLayout(requireContext());
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelsLp = new LinearLayout.LayoutParams(0, -2, 1f);
        labelsLp.leftMargin = dp(12);
        row.addView(labels, labelsLp);
        TextView title = new TextView(requireContext());
        title.setText(label);
        title.setTextSize(12);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.WHITE);
        labels.addView(title);
        TextView phone = new TextView(requireContext());
        phone.setText(number);
        phone.setTextSize(19);
        phone.setTypeface(null, Typeface.BOLD);
        phone.setTextColor(Color.WHITE);
        labels.addView(phone);
        TextView arrow = new TextView(requireContext());
        arrow.setText("›");
        arrow.setTextSize(28);
        arrow.setTextColor(Color.WHITE);
        row.addView(arrow);
        row.setContentDescription("Dial " + label + " on " + number);
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> dial(number));
        return row;
    }

    private void dial(String number) {
        try {
            startActivity(new Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)));
        } catch (Exception e) {
            if (getContext() != null) {
                Toast.makeText(getContext(), "Could not open the dialer. Number: " + number,
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private View tipRow(ViewGroup parent, int iconRes, String title, String detail) {
        View row = LayoutInflater.from(requireContext()).inflate(R.layout.item_tip, parent, false);
        ((ImageView) row.findViewById(R.id.tvIcon)).setImageResource(iconRes);
        ((TextView) row.findViewById(R.id.tvTipTitle)).setText(title);
        ((TextView) row.findViewById(R.id.tvTipText)).setText(detail);
        return row;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
