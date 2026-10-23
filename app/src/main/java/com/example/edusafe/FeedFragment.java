package com.example.edusafe;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.UUID;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
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

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** Student-only shared EduSafe news feed. Admin moderation uses a separate screen. */
public class FeedFragment extends Fragment {
    private static final String PHOTO_BUCKET = "feed-photos";
    private Uri selectedPhoto;
    private EditText etPost;
    private TextView tvPost, tvEmpty, tvAddPhoto;
    private ImageView ivPreview;
    private LinearLayout listContainer;
    private ActivityResultLauncher<Intent> choosePhoto;
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private final OkHttpClient http = new OkHttpClient();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Post> posts = new ArrayList<>();
    private final Map<String, List<Comment>> commentsByPost = new HashMap<>();
    private final Map<String, Integer> likesByPost = new HashMap<>();
    private final Set<String> likedByMe = new HashSet<>();
    private boolean busy = false;
    private boolean student = false;
    private String myId = "";

    private static class Post {
        String id, authorId, body, imagePath;
        long createdAt;
    }
    private static class Comment {
        String id, postId, authorId, body;
        long createdAt;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        choosePhoto = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() != android.app.Activity.RESULT_OK
                            || result.getData() == null) return;
                    Uri uri = result.getData().getData();
                    if (uri == null) return;
                    selectedPhoto = uri;
                    if (ivPreview != null) {
                        ivPreview.setImageURI(uri);
                        ivPreview.setVisibility(View.VISIBLE);
                    }
                    if (tvAddPhoto != null) tvAddPhoto.setText("Change photo");
                });
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_feed, container, false);
        etPost = v.findViewById(R.id.etPost);
        tvPost = v.findViewById(R.id.tvPost);
        tvEmpty = v.findViewById(R.id.tvEmpty);
        tvAddPhoto = v.findViewById(R.id.tvAddPhoto);
        ivPreview = v.findViewById(R.id.ivPreview);
        listContainer = v.findViewById(R.id.listContainer);
        if (selectedPhoto != null) {
            ivPreview.setImageURI(selectedPhoto);
            ivPreview.setVisibility(View.VISIBLE);
            tvAddPhoto.setText("Change photo");
        } else {
            ivPreview.setVisibility(View.GONE);
        }
        tvAddPhoto.setOnClickListener(x -> {
            if (!student) { toast("Only students can add photos."); return; }
            Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
            pick.setType("image/*");
            pick.addCategory(Intent.CATEGORY_OPENABLE);
            choosePhoto.launch(Intent.createChooser(pick, "Choose a photo"));
        });
        tvPost.setOnClickListener(x -> publish());
        refresh();
        return v;
    }

    @Override public void onResume() {
        super.onResume();
        if (listContainer != null) refresh();
    }

    private String token() {
        SharedPreferences p = requireContext().getSharedPreferences(
                "edusafe_supabase_session", Context.MODE_PRIVATE);
        String t = p.getString("access_token", "");
        if (t == null || t.isEmpty() || System.currentTimeMillis() >= p.getLong("expires_at", 0))
            return "";
        return t;
    }

    private static String userIdFromToken(String token) {
        try {
            String[] parts = token.split("\\.");
            byte[] bytes = Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            return new JSONObject(new String(bytes, StandardCharsets.UTF_8)).optString("sub", "");
        } catch (Exception ignored) { return ""; }
    }

    private static String encoded(String value) {
        try { return URLEncoder.encode(value, "UTF-8"); }
        catch (Exception e) { return value; }
    }

    private String api(String table, String query) {
        return SupabaseConfig.SUPABASE_URL + "/rest/v1/" + table + (query.isEmpty() ? "" : "?" + query);
    }

    private String request(String token, String method, String url, JSONObject body) throws Exception {
        Request.Builder b = new Request.Builder().url(url)
                .header("apikey", SupabaseConfig.SUPABASE_KEY)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .header("Prefer", "return=representation");
        if ("POST".equals(method)) b.post(RequestBody.create(JSON, body.toString()));
        else if ("DELETE".equals(method)) b.delete();
        else b.get();
        try (Response response = http.newCall(b.build()).execute()) {
            String text = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) throw new IOException("HTTP " + response.code() + ": " + text);
            return text;
        }
    }

    private void refresh() {
        if (!isAdded() || busy) return;
        student = false;
        String t = token();
        if (t.isEmpty()) { toast("Session expired. Please log in again."); return; }
        busy = true;
        myId = userIdFromToken(t);
        if (tvEmpty != null && posts.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText("Loading news feed...");
        }
        worker.execute(() -> {
            try {
                JSONArray remotePosts = new JSONArray(request(t, "GET", api("feed_posts",
                        "select=id,author_id,body,image_path,created_at&order=created_at.desc&limit=100"), null));
                JSONArray remoteComments = new JSONArray(request(t, "GET", api("feed_comments",
                        "select=id,post_id,author_id,body,created_at&order=created_at.asc&limit=2000"), null));
                JSONArray remoteLikes = new JSONArray(request(t, "GET", api("feed_likes",
                        "select=post_id,user_id&limit=10000"), null));
                JSONArray roles = new JSONArray(request(t, "GET", api("user_roles",
                        "select=role&user_id=eq." + encoded(myId)), null));
                boolean isStudent = roles.length() > 0 && "student".equalsIgnoreCase(roles.getJSONObject(0).optString("role"));
                if (!isStudent) throw new IOException("News Feed is for students only");
                List<Post> p = new ArrayList<>();
                Map<String, List<Comment>> c = new HashMap<>();
                Map<String, Integer> counts = new HashMap<>();
                Set<String> mine = new HashSet<>();
                for (int i = 0; i < remotePosts.length(); i++) {
                    JSONObject o = remotePosts.getJSONObject(i);
                    Post post = new Post();
                    post.id = o.optString("id"); post.authorId = o.optString("author_id");
                    post.body = o.optString("body"); post.imagePath = o.isNull("image_path") ? "" : o.optString("image_path", ""); post.createdAt = timestamp(o.optString("created_at"));
                    p.add(post);
                }
                for (int i = 0; i < remoteComments.length(); i++) {
                    JSONObject o = remoteComments.getJSONObject(i);
                    Comment comment = new Comment();
                    comment.id = o.optString("id"); comment.postId = o.optString("post_id");
                    comment.authorId = o.optString("author_id"); comment.body = o.optString("body");
                    comment.createdAt = timestamp(o.optString("created_at"));
                    if (!c.containsKey(comment.postId)) c.put(comment.postId, new ArrayList<>());
                    c.get(comment.postId).add(comment);
                }
                for (int i = 0; i < remoteLikes.length(); i++) {
                    JSONObject o = remoteLikes.getJSONObject(i);
                    String id = o.optString("post_id");
                    counts.put(id, counts.containsKey(id) ? counts.get(id) + 1 : 1);
                    if (myId.equals(o.optString("user_id"))) mine.add(id);
                }
                main.post(() -> {
                    busy = false;
                    if (!isAdded() || listContainer == null) return;
                    posts.clear(); posts.addAll(p);
                    commentsByPost.clear(); commentsByPost.putAll(c);
                    likesByPost.clear(); likesByPost.putAll(counts);
                    likedByMe.clear(); likedByMe.addAll(mine);
                    student = isStudent;
                    displayPosts();
                });
            } catch (Exception e) { failure("Unable to load the shared feed", e); }
        });
    }

    private static long timestamp(String iso) {
        try { return Instant.parse(iso).toEpochMilli(); }
        catch (Exception e) { return System.currentTimeMillis(); }
    }

    private void publish() {
        if (busy || !student) { toast("Only students can publish posts."); return; }
        String message = etPost.getText().toString().trim();
        Uri image = selectedPhoto;
        if (message.isEmpty() && image == null) { etPost.setError("Write something or add a photo."); return; }
        if (!message.isEmpty() && message.length() < 5) { etPost.setError("Please write at least 5 characters."); return; }
        executeMutation("Unable to publish post", t -> {
            String path = null;
            if (image != null) path = uploadPhoto(t, image);
            try {
                JSONObject o = new JSONObject().put("body", message);
                if (path != null) o.put("image_path", path);
                request(t, "POST", api("feed_posts", ""), o);
            } catch (Exception e) {
                if (path != null) try { deletePhoto(t, path); } catch (Exception ignored) { }
                throw e;
            }
        }, () -> {
            if (etPost != null) etPost.setText("");
            selectedPhoto = null;
            if (ivPreview != null) { ivPreview.setImageDrawable(null); ivPreview.setVisibility(View.GONE); }
            if (tvAddPhoto != null) tvAddPhoto.setText("Add photo");
            toast("Post published.");
        });
    }

    // Compress and normalize photos so uploads are small and always JPEG.
    private byte[] photoBytes(Uri uri) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = requireContext().getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("Invalid image");
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = 1;
        while (Math.max(bounds.outWidth / opts.inSampleSize, bounds.outHeight / opts.inSampleSize) > 1600)
            opts.inSampleSize *= 2;
        Bitmap bitmap;
        try (InputStream in = requireContext().getContentResolver().openInputStream(uri)) {
            bitmap = BitmapFactory.decodeStream(in, null, opts);
        }
        if (bitmap == null) throw new IOException("Cannot read photo");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 78, out);
        bitmap.recycle();
        byte[] bytes = out.toByteArray();
        if (bytes.length > 5 * 1024 * 1024) throw new IOException("Photo is too large (max 5 MB)");
        return bytes;
    }

    private String storageUrl(String path) {
        return SupabaseConfig.SUPABASE_URL + "/storage/v1/object/" + PHOTO_BUCKET + "/" + path;
    }

    private String uploadPhoto(String token, Uri uri) throws Exception {
        byte[] bytes = photoBytes(uri);
        String path = myId + "/" + UUID.randomUUID() + ".jpg";
        Request req = new Request.Builder().url(storageUrl(path))
                .header("apikey", SupabaseConfig.SUPABASE_KEY)
                .header("Authorization", "Bearer " + token)
                .header("x-upsert", "false")
                .post(RequestBody.create(MediaType.parse("image/jpeg"), bytes)).build();
        try (Response res = http.newCall(req).execute()) {
            if (!res.isSuccessful()) throw new IOException("Photo upload failed (" + res.code() + "): " +
                    (res.body() == null ? "" : res.body().string()));
        }
        return path;
    }

    private void deletePhoto(String token, String path) throws Exception {
        Request req = new Request.Builder().url(storageUrl(path))
                .header("apikey", SupabaseConfig.SUPABASE_KEY)
                .header("Authorization", "Bearer " + token).delete().build();
        try (Response res = http.newCall(req).execute()) {
            if (!res.isSuccessful()) throw new IOException("Could not remove photo");
        }
    }

    private void loadPostPhoto(ImageView view, String path) {
        view.setVisibility(View.VISIBLE);
        view.setImageDrawable(null);
        String t = token();
        worker.execute(() -> {
            try {
                Request req = new Request.Builder().url(storageUrl(path))
                        .header("apikey", SupabaseConfig.SUPABASE_KEY)
                        .header("Authorization", "Bearer " + t).build();
                byte[] data;
                try (Response res = http.newCall(req).execute()) {
                    if (!res.isSuccessful() || res.body() == null) throw new IOException("Photo not accessible");
                    data = res.body().bytes();
                }
                Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                main.post(() -> {
                    if (isAdded() && view.getParent() != null && bitmap != null) view.setImageBitmap(bitmap);
                });
            } catch (Exception e) { Log.w("EduSafeFeed", "Cannot load photo", e); }
        });
    }

    private interface Mutation { void run(String token) throws Exception; }
    private void executeMutation(String error, Mutation mutation, Runnable after) {
        if (!isAdded() || busy) return;
        String t = token();
        if (t.isEmpty()) { toast("Session expired. Please log in again."); return; }
        busy = true;
        worker.execute(() -> {
            try {
                mutation.run(t);
                main.post(() -> {
                    busy = false;
                    if (!isAdded()) return;
                    if (after != null) after.run();
                    refresh();
                });
            } catch (Exception e) { failure(error, e); }
        });
    }

    private void failure(String message, Exception e) {
        Log.e("EduSafeFeed", message, e);
        main.post(() -> {
            busy = false;
            if (!isAdded()) return;
            toast(message + ". Check connection or Supabase permissions.");
            if (posts.isEmpty() && tvEmpty != null) {
                tvEmpty.setVisibility(View.VISIBLE); tvEmpty.setText(message);
            }
        });
    }

    private void displayPosts() {
        if (listContainer == null) return;
        listContainer.removeAllViews();
        tvEmpty.setVisibility(posts.isEmpty() ? View.VISIBLE : View.GONE);
        tvEmpty.setText("No posts yet. Be the first to share something.");
        for (Post post : posts) {
            View card = LayoutInflater.from(requireContext()).inflate(R.layout.item_post, listContainer, false);
            TextView author = card.findViewById(R.id.tvAuthor);
            TextView time = card.findViewById(R.id.tvTime);
            TextView text = card.findViewById(R.id.tvText);
            TextView like = card.findViewById(R.id.tvLike);
            TextView comment = card.findViewById(R.id.tvCommentBtn);
            TextView delete = card.findViewById(R.id.tvDelete);
            ImageView photo = card.findViewById(R.id.ivPostPhoto);
            author.setText(myId.equals(post.authorId) ? "You" : "Anonymous user");
            time.setText(timeAgo(post.createdAt));
            text.setText(post.body);
            if (post.imagePath == null || post.imagePath.isEmpty()) photo.setVisibility(View.GONE);
            else loadPostPhoto(photo, post.imagePath);
            boolean liked = likedByMe.contains(post.id);
            like.setText((liked ? "♥ " : "♡ ") + likesByPost.getOrDefault(post.id, 0));
            like.setTextColor(liked ? Color.rgb(220, 40, 65) : requireContext().getColor(R.color.navy));
            like.setOnClickListener(v -> toggleLike(post.id, liked));
            List<Comment> list = commentsByPost.get(post.id);
            comment.setText("♧ " + (list == null ? 0 : list.size()));
            comment.setOnClickListener(v -> showComments(post.id));
            if (myId.equals(post.authorId)) {
                delete.setVisibility(View.VISIBLE);
                delete.setOnClickListener(v -> new AlertDialog.Builder(requireContext())
                        .setTitle("Delete post?")
                        .setMessage("This post and its comments will be deleted for everyone.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete", (d, w) -> executeMutation("Unable to delete post", t ->
                                {
                                    request(t, "DELETE", api("feed_posts", "id=eq." + encoded(post.id)), null);
                                    if (post.imagePath != null && !post.imagePath.isEmpty())
                                        try { deletePhoto(t, post.imagePath); } catch (Exception e) {
                                            Log.w("EduSafeFeed", "Photo cleanup deferred", e);
                                        }
                                },
                                () -> toast("Post deleted."))).show());
            } else delete.setVisibility(View.GONE);
            listContainer.addView(card);
        }
    }

    private void toggleLike(String postId, boolean liked) {
        if (!student) { toast("Only students can like posts."); return; }
        executeMutation("Unable to update like", t -> {
            if (liked) request(t, "DELETE", api("feed_likes",
                    "post_id=eq." + encoded(postId) + "&user_id=eq." + encoded(myId)), null);
            else request(t, "POST", api("feed_likes", ""), new JSONObject().put("post_id", postId));
        }, null);
    }

    private void showComments(String postId) {
        if (!isAdded() || !student) return;
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(18), dp(12), dp(18), dp(12));
        android.widget.ScrollView scroll = new android.widget.ScrollView(requireContext());
        LinearLayout list = new LinearLayout(requireContext());
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(260));
        layout.addView(scroll, scrollParams);
        EditText input = new EditText(requireContext());
        input.setHint("Write a comment...");
        input.setSingleLine(false);
        input.setMaxLines(4);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(300)});
        layout.addView(input);
        TextView send = new TextView(requireContext());
        send.setText("Send comment"); send.setTypeface(null, Typeface.BOLD);
        send.setGravity(Gravity.CENTER); send.setMinHeight(dp(48));
        send.setTextColor(requireContext().getColor(R.color.white));
        send.setBackgroundResource(R.drawable.bg_chip_selected);
        layout.addView(send);
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Comments").setView(layout).setNegativeButton("Close", null).create();
        renderComments(postId, list);
        send.setOnClickListener(v -> {
            String message = input.getText().toString().trim();
            if (message.isEmpty()) { input.setError("Write a comment first."); return; }
            executeMutation("Unable to send comment", t -> request(t, "POST", api("feed_comments", ""),
                    new JSONObject().put("post_id", postId).put("body", message)), () -> {
                toast("Comment posted."); dialog.dismiss();
            });
        });
        dialog.show();
    }

    private void renderComments(String postId, LinearLayout container) {
        container.removeAllViews();
        List<Comment> list = commentsByPost.get(postId);
        if (list == null || list.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText("No comments yet. Be the first.");
            empty.setPadding(0, dp(12), 0, dp(12));
            container.addView(empty); return;
        }
        for (Comment comment : list) {
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, dp(10), 0, dp(10));
            TextView name = new TextView(requireContext());
            name.setText((myId.equals(comment.authorId) ? "You" : "Anonymous user")
                    + " · " + timeAgo(comment.createdAt));
            name.setTypeface(null, Typeface.BOLD);
            name.setTextColor(requireContext().getColor(R.color.navy));
            TextView body = new TextView(requireContext());
            body.setText(comment.body);
            body.setPadding(0, dp(4), 0, dp(4));
            row.addView(name); row.addView(body);
            if (myId.equals(comment.authorId)) {
                TextView delete = new TextView(requireContext());
                delete.setText("Delete comment");
                delete.setTextColor(requireContext().getColor(R.color.red));
                delete.setGravity(Gravity.CENTER_VERTICAL); delete.setMinHeight(dp(40));
                delete.setOnClickListener(v -> new AlertDialog.Builder(requireContext())
                        .setTitle("Delete comment?").setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete", (d, w) -> executeMutation("Unable to delete comment", t ->
                                        request(t, "DELETE", api("feed_comments", "id=eq." + encoded(comment.id)), null),
                                () -> { toast("Comment deleted."); container.removeAllViews(); }))
                        .show());
                row.addView(delete);
            }
            container.addView(row);
        }
    }

    private String timeAgo(long timestamp) {
        long minutes = Math.max(0, System.currentTimeMillis() - timestamp) / 60000;
        if (minutes < 1) return "Just now";
        if (minutes < 60) return minutes + " min ago";
        if (minutes < 1440) return (minutes / 60) + " h ago";
        return (minutes / 1440) + " days ago";
    }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }
    private void toast(String s) { if (getContext() != null) Toast.makeText(requireContext(), s, Toast.LENGTH_SHORT).show(); }
    @Override public void onDestroyView() {
        etPost = null; tvPost = null; tvEmpty = null; tvAddPhoto = null;
        ivPreview = null; listContainer = null;
        super.onDestroyView();
    }
    @Override public void onDestroy() { worker.shutdown(); super.onDestroy(); }
}


