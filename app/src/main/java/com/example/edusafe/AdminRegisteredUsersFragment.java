package com.example.edusafe;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.EditText;
import android.text.TextWatcher;
import android.text.Editable;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AdminRegisteredUsersFragment extends Fragment {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final OkHttpClient client = new OkHttpClient();
    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout list;
    private TextView status;
    private boolean loading;
    private JSONArray cachedUsers = new JSONArray();
    private EditText searchInput;
    private int navy = Color.rgb(13, 39, 101);

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent,
                             @Nullable Bundle savedInstanceState) {
        ScrollView scroll = new ScrollView(requireContext());
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(234, 240, 255));
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(24));
        scroll.addView(root);
        TextView title = text("Registered Users", 24, navy, true);
        root.addView(title);
        TextView sub = text("Student accounts · Ban or unban", 14, navy, false);
        sub.setPadding(0, dp(8), 0, dp(14));
        root.addView(sub);
        searchInput = new EditText(requireContext());
        searchInput.setSingleLine(true);
        searchInput.setTextSize(15);
        searchInput.setHint("Search student email...");
        searchInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        searchInput.setPadding(dp(12), dp(8), dp(12), dp(8));
        searchInput.setBackgroundResource(R.drawable.ui4_input);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, dp(48));
        searchParams.bottomMargin = dp(12);
        root.addView(searchInput, searchParams);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderUsers();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        TextView refresh = action("⟳  Refresh users", navy);
        root.addView(refresh);
        refresh.setOnClickListener(v -> loadUsers());
        status = text("Loading students...", 14, navy, false);
        status.setPadding(0, dp(14), 0, dp(8));
        root.addView(status);
        list = new LinearLayout(requireContext());
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);
        loadUsers();
        return scroll;
    }

    private void loadUsers() {
        if (loading || list == null) return;
        loading = true;
        status.setText("Loading registered students...");
        JSONObject data = new JSONObject();
        try { data.put("action", "list"); } catch (Exception ignored) {}
        call(data, new Result() {
            @Override public void success(JSONObject result) {
                loading = false;
                if (list == null) return;
                JSONArray users = result.optJSONArray("users");
                cachedUsers = users != null ? users : new JSONArray();
                renderUsers();
            }
            @Override public void failure(String error) {
                loading = false;
                if (status != null) status.setText(error);
            }
        });
    }

    private void renderUsers() {
        if (list == null || status == null) return;
        String query = searchInput == null ? "" : searchInput.getText().toString()
                .trim().toLowerCase(java.util.Locale.ROOT);
        list.removeAllViews();
        int matches = 0;
        for (int i = 0; i < cachedUsers.length(); i++) {
            JSONObject user = cachedUsers.optJSONObject(i);
            if (user == null) continue;
            String email = user.optString("email", "").toLowerCase(java.util.Locale.ROOT);
            if (email.contains(query)) {
                addUser(user);
                matches++;
            }
        }
        if (cachedUsers.length() == 0) {
            status.setText("No registered student accounts found.");
        } else if (matches == 0) {
            status.setText("No student emails match your search.");
        } else if (query.isEmpty()) {
            status.setText(cachedUsers.length() + " registered student(s)");
        } else {
            status.setText(matches + " matching student(s)");
        }
    }

    private void addUser(JSONObject user) {
        String id = user.optString("id");
        String email = user.optString("email", "No email");
        boolean banned = user.optBoolean("is_banned", false);
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setBackgroundResource(R.drawable.ui4_white_card);
        card.setElevation(dp(3));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.bottomMargin = dp(10);
        list.addView(card, cardParams);
        LinearLayout details = new LinearLayout(requireContext());
        details.setOrientation(LinearLayout.VERTICAL);
        card.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
        TextView address = text(email, 14, navy, true);
        address.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_SIMPLE);
        details.addView(address);
        TextView state = text(banned ? "Banned" : "Active", 13,
                banned ? Color.rgb(198, 40, 40) : Color.rgb(33, 131, 72), false);
        details.addView(state);
        TextView button = action(banned ? "Unban" : "Ban",
                banned ? Color.rgb(33, 131, 72) : Color.rgb(198, 40, 40));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(88), dp(44));
        bp.leftMargin = dp(10);
        card.addView(button, bp);
        button.setOnClickListener(v -> new AlertDialog.Builder(requireContext())
                .setTitle(banned ? "Unban student?" : "Ban student?")
                .setMessage(email + (banned ? " will regain access to EduSafe." :
                        " will be blocked from EduSafe until unbanned."))
                .setNegativeButton("Cancel", null)
                .setPositiveButton(banned ? "Unban" : "Ban", (d, which) -> changeBan(id, !banned))
                .show());
    }

    private void changeBan(String id, boolean ban) {
        JSONObject data = new JSONObject();
        try { data.put("action", ban ? "ban" : "unban"); data.put("user_id", id); }
        catch (Exception ignored) {}
        status.setText(ban ? "Banning student..." : "Unbanning student...");
        call(data, new Result() {
            @Override public void success(JSONObject result) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), ban ? "Student banned" : "Student unbanned",
                        Toast.LENGTH_SHORT).show();
                String warning = result.optString("warning", "");
                if (!warning.isEmpty()) Toast.makeText(requireContext(), warning, Toast.LENGTH_LONG).show();
                loadUsers();
            }
            @Override public void failure(String error) {
                if (status != null) status.setText(error);
            }
        });
    }

    private interface Result { void success(JSONObject result); void failure(String message); }
    private void call(JSONObject data, Result result) {
        if (getContext() == null) return;
        SharedPreferences prefs = requireContext().getSharedPreferences("edusafe_supabase_session", Context.MODE_PRIVATE);
        String token = prefs.getString("access_token", "");
        if (token == null || token.isEmpty()) { result.failure("Please log in again."); return; }
        Request req = new Request.Builder()
                .url(SupabaseConfig.SUPABASE_URL + "/functions/v1/admin-users")
                .addHeader("apikey", SupabaseConfig.SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + token)
                .post(RequestBody.create(data.toString(), JSON)).build();
        client.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                main.post(() -> { if (isAdded()) result.failure("Network error: " + e.getMessage()); });
            }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    String body = r.body() != null ? r.body().string() : "";
                    JSONObject json;
                    try { json = new JSONObject(body); } catch (Exception e) {
                        json = new JSONObject();
                    }
                    final JSONObject value = json;
                    final boolean ok = r.isSuccessful();
                    final String error = json.optString("error", "Request failed (HTTP " + r.code() + ")");
                    main.post(() -> { if (isAdded()) {
                        if (ok) result.success(value); else result.failure(error);
                    }});
                }
            }
        });
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(requireContext());
        view.setText(value); view.setTextSize(sp); view.setTextColor(color);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        return view;
    }
    private TextView action(String value, int background) {
        TextView view = text(value, 14, Color.WHITE, true);
        view.setGravity(Gravity.CENTER); view.setMinHeight(dp(44));
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setColor(background); shape.setCornerRadius(dp(16));
        view.setBackground(shape); view.setPadding(dp(10), 0, dp(10), 0);
        view.setClickable(true); view.setFocusable(true);
        return view;
    }
    private int dp(int n) { return (int) (n * getResources().getDisplayMetrics().density); }
    @Override public void onDestroyView() { list = null; status = null; searchInput = null; super.onDestroyView(); }
}

