package com.example.edusafe;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.MediaType;
import okhttp3.Response;

/** Admin-only moderation screen. The database RLS enforces deletion rights. */
public class AdminFeedModerationFragment extends Fragment {
    private final OkHttpClient client = new OkHttpClient();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private LinearLayout items;
    private TextView status;
    private boolean loading;

    @Nullable @Override public View onCreateView(@NonNull android.view.LayoutInflater inflater,
                                                 @Nullable ViewGroup parent, @Nullable Bundle state) {
        View root = inflater.inflate(R.layout.fragment_admin_feed_moderation, parent, false);
        items = root.findViewById(R.id.moderationItems);
        status = root.findViewById(R.id.moderationStatus);
        root.findViewById(R.id.moderationRefresh).setOnClickListener(v -> refresh());
        root.findViewById(R.id.moderationBack).setOnClickListener(v -> {
            if (getParentFragmentManager().getBackStackEntryCount() > 0) {
                getParentFragmentManager().popBackStack();
            } else {
                requireActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.container, new AdminFragment()).commit();
            }
        });
        refresh();
        return root;
    }

    private void refresh() {
        if (loading || items == null) return;
        loading = true;
        status.setText("Loading posts...");
        worker.execute(() -> {
            try {
                JSONObject moderation = getModerationFeed();
                JSONArray posts = moderation.optJSONArray("posts");
                JSONArray comments = moderation.optJSONArray("comments");
                if (posts == null) posts = new JSONArray();
                if (comments == null) comments = new JSONArray();
                final JSONArray loadedPosts = posts;
                final JSONArray loadedComments = comments;
                ui(() -> {
                    loading = false;
                    if (items == null) return;
                    items.removeAllViews();
                    status.setText(loadedPosts.length() + (loadedPosts.length() == 1 ? " post to review" : " posts to review"));
                    if (loadedPosts.length() == 0) {
                        TextView empty = text("No posts to moderate right now.", 15, false);
                        empty.setPadding(dp(16), dp(24), dp(16), dp(24));
                        empty.setBackgroundResource(R.drawable.mod_white_card);
                        items.addView(empty);
                    }
                    for (int i = 0; i < loadedPosts.length(); i++) {
                        JSONObject post = loadedPosts.optJSONObject(i);
                        if (post != null) addPost(post, loadedComments);
                    }
                });
            } catch (Exception e) {
                ui(() -> { loading = false; if (status != null) status.setText("Unable to load posts: " + e.getMessage()); });
            }
        });
    }

    private void addPost(JSONObject post, JSONArray comments) {
        Context ctx = requireContext();
        String postId = post.optString("id");
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackgroundResource(R.drawable.mod_white_card);
        card.setElevation(dp(2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(14);
        items.addView(card, lp);

        TextView tag = text("STUDENT POST", 11, true);
        tag.setTextColor(android.graphics.Color.rgb(43, 82, 171));
        card.addView(tag);
        TextView author = text(emailFor(post), 14, true);
        author.setPadding(0, dp(7), 0, dp(6));
        card.addView(author);
        TextView body = text(post.optString("body"), 15, false);
        body.setPadding(0, 0, 0, dp(12));
        card.addView(body);

        TextView deletePost = action("Delete Post", true);
        deletePost.setOnClickListener(v -> confirmDelete("post", postId));
        card.addView(deletePost);

        int count = 0;
        for (int i = 0; i < comments.length(); i++) {
            JSONObject comment = comments.optJSONObject(i);
            if (comment != null && postId.equals(comment.optString("post_id"))) count++;
        }
        if (count > 0) {
            TextView label = text("COMMENTS (" + count + ")", 11, true);
            label.setTextColor(android.graphics.Color.rgb(43, 82, 171));
            label.setPadding(0, dp(16), 0, dp(8));
            card.addView(label);
        }
        for (int i = 0; i < comments.length(); i++) {
            JSONObject comment = comments.optJSONObject(i);
            if (comment == null || !postId.equals(comment.optString("post_id"))) continue;
            LinearLayout commentCard = new LinearLayout(ctx);
            commentCard.setOrientation(LinearLayout.VERTICAL);
            commentCard.setPadding(dp(12), dp(12), dp(12), dp(12));
            commentCard.setBackgroundResource(R.drawable.mod_comment_card);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
            cp.bottomMargin = dp(8);
            card.addView(commentCard, cp);
            TextView commentAuthor = text(emailFor(comment), 13, true);
            commentCard.addView(commentAuthor);
            TextView commentBody = text(comment.optString("body"), 14, false);
            commentBody.setPadding(0, dp(4), 0, dp(8));
            commentCard.addView(commentBody);
            TextView deleteComment = action("Delete Comment", false);
            final String commentId = comment.optString("id");
            deleteComment.setOnClickListener(v -> confirmDelete("comment", commentId));
            commentCard.addView(deleteComment);
        }
    }

    private TextView action(String label, boolean primary) {
        TextView v = text(label, 13, true);
        v.setTextColor(android.graphics.Color.rgb(195, 24, 64));
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(14), dp(9), dp(14), dp(9));
        v.setMinHeight(dp(44));
        v.setBackgroundResource(primary ? R.drawable.mod_delete_button : R.drawable.mod_delete_outline);
        v.setClickable(true);
        v.setFocusable(true);
        return v;
    }

    private void confirmDelete(String type, String id) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete " + type + "?")
                .setMessage("This action cannot be undone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> delete(type, id)).show();
    }

    private void delete(String type, String id) {
        if (loading) return;
        loading = true;
        status.setText("Deleting...");
        worker.execute(() -> {
            try {
                String table = type.equals("post") ? "feed_posts" : "feed_comments";
                String url = SupabaseConfig.SUPABASE_URL + "/rest/v1/" + table + "?id=eq." + id;
                Request request = new Request.Builder().url(url).delete()
                        .header("apikey", SupabaseConfig.SUPABASE_KEY)
                        .header("Authorization", "Bearer " + token())
                        .header("Prefer", "return=representation").build();
                try (Response response = client.newCall(request).execute()) {
                    String body = response.body() == null ? "" : response.body().string();
                    if (!response.isSuccessful()) throw new IOException("HTTP " + response.code() + ": " + body);
                    if (new JSONArray(body).length() == 0) throw new IOException("Nothing deleted. Check admin permissions.");
                }
                ui(() -> { loading = false; toast("Deleted successfully"); refresh(); });
            } catch (Exception e) {
                ui(() -> { loading = false; if (status != null) status.setText("Delete failed: " + e.getMessage()); });
            }
        });
    }

    private String emailFor(JSONObject entry) {
        String email = entry.optString("author_email", "").trim();
        return email.isEmpty() || "null".equalsIgnoreCase(email)
                ? "Email unavailable" : email;
    }

    private JSONObject getModerationFeed() throws Exception {
        Request request = new Request.Builder()
                .url(SupabaseConfig.SUPABASE_URL + "/rest/v1/rpc/admin_moderation_feed")
                .post(RequestBody.create("{}", MediaType.parse("application/json; charset=utf-8")))
                .header("apikey", SupabaseConfig.SUPABASE_KEY)
                .header("Authorization", "Bearer " + token())
                .header("Content-Type", "application/json")
                .build();
        try (Response response = client.newCall(request).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful())
                throw new IOException("Unable to load admin feed (HTTP " + response.code() + "): " + body);
            return new JSONObject(body);
        }
    }

    private String token() {
        return requireContext().getSharedPreferences("edusafe_supabase_session", Context.MODE_PRIVATE)
                .getString("access_token", "");
    }
    private TextView text(String value, int size, boolean bold) {
        TextView v = new TextView(requireContext());
        v.setText(value); v.setTextSize(size); v.setTextColor(requireContext().getColor(R.color.ink));
        if (bold) v.setTypeface(null, android.graphics.Typeface.BOLD);
        return v;
    }
    private int dp(int n) { return (int) (n * getResources().getDisplayMetrics().density); }
    private void toast(String s) { if (getContext() != null) Toast.makeText(getContext(), s, Toast.LENGTH_SHORT).show(); }
    private void ui(Runnable r) { if (getActivity() != null) getActivity().runOnUiThread(r); }
    @Override public void onDestroyView() { items = null; status = null; super.onDestroyView(); }
    @Override public void onDestroy() { worker.shutdown(); super.onDestroy(); }
}




