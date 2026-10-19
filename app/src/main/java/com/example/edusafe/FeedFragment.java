package com.example.edusafe;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FeedFragment extends Fragment {

    private static final String PREFS = "edusafe_local_feed";
    private static final String POSTS_KEY = "posts";
    private static final int MAX_PHOTO_BYTES = 5 * 1024 * 1024;

    private EditText etPost;
    private ImageView ivPreview;
    private TextView tvAddPhoto, tvPost, tvEmpty;
    private LinearLayout listContainer;

    private SharedPreferences prefs;
    private String selectedPhotoPath;

    private final ActivityResultLauncher<String> pickPhoto =
            registerForActivityResult(
                    new ActivityResultContracts.GetContent(),
                    this::handleSelectedPhoto
            );

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(
                R.layout.fragment_feed, container, false
        );

        prefs = requireContext().getSharedPreferences(
                PREFS, Context.MODE_PRIVATE
        );

        etPost = view.findViewById(R.id.etPost);
        ivPreview = view.findViewById(R.id.ivPreview);
        tvAddPhoto = view.findViewById(R.id.tvAddPhoto);
        tvPost = view.findViewById(R.id.tvPost);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        listContainer = view.findViewById(R.id.listContainer);

        tvAddPhoto.setOnClickListener(v ->
                pickPhoto.launch("image/*")
        );

        tvPost.setOnClickListener(v -> createPost());

        displayPosts();

        return view;
    }

    // ----------------------------------------------------
    // CREATE POST
    // ----------------------------------------------------

    private void createPost() {

        String message = etPost.getText().toString().trim();

        if (message.length() < 5) {
            etPost.setError("Please write at least 5 characters.");
            etPost.requestFocus();
            return;
        }

        try {
            JSONArray posts = getPosts();

            JSONObject post = new JSONObject();

            post.put("id", UUID.randomUUID().toString());
            post.put("text", message);
            post.put("photo", selectedPhotoPath == null
                    ? "" : selectedPhotoPath);
            post.put("createdAt", System.currentTimeMillis());
            post.put("likes", 0);
            post.put("liked", false);
            post.put("comments", new JSONArray());
            post.put("mine", true);

            JSONArray updated = new JSONArray();

            // Newest post appears first.
            updated.put(post);

            for (int i = 0; i < posts.length(); i++) {
                updated.put(posts.get(i));
            }

            savePosts(updated);

            etPost.setText("");
            selectedPhotoPath = null;

            ivPreview.setImageDrawable(null);
            ivPreview.setVisibility(View.GONE);
            tvAddPhoto.setText("Add photo");

            displayPosts();

            toast("Post published successfully.");

        } catch (Exception e) {
            toast("Unable to publish your post.");
        }
    }

    // ----------------------------------------------------
    // DISPLAY POSTS
    // ----------------------------------------------------

    private void displayPosts() {

        if (listContainer == null) return;

        listContainer.removeAllViews();

        JSONArray posts = getPosts();

        tvEmpty.setVisibility(
                posts.length() == 0 ? View.VISIBLE : View.GONE
        );

        for (int i = 0; i < posts.length(); i++) {

            JSONObject post = posts.optJSONObject(i);

            if (post != null) {
                listContainer.addView(createPostCard(post));
            }
        }
    }

    private View createPostCard(JSONObject post) {

        View card = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_post, listContainer, false);

        TextView tvAuthor = card.findViewById(R.id.tvAuthor);
        TextView tvTime = card.findViewById(R.id.tvTime);
        TextView tvText = card.findViewById(R.id.tvText);
        TextView tvLike = card.findViewById(R.id.tvLike);
        TextView tvCommentBtn =
                card.findViewById(R.id.tvCommentBtn);
        TextView tvDelete = card.findViewById(R.id.tvDelete);
        ImageView ivPhoto =
                card.findViewById(R.id.ivPostPhoto);

        String postId = post.optString("id");

        tvAuthor.setText(
                post.optBoolean("mine", false)
                        ? "You"
                        : "Anonymous student"
        );

        tvTime.setText(timeAgo(post.optLong("createdAt")));
        tvText.setText(post.optString("text"));

        String photo = post.optString("photo", "");

        if (!photo.isEmpty()) {
            showPhoto(ivPhoto, photo);
        } else {
            ivPhoto.setVisibility(View.GONE);
        }

        updateLikeButton(tvLike, post);

        JSONArray comments = post.optJSONArray("comments");

        int commentCount = comments == null
                ? 0 : comments.length();

        tvCommentBtn.setText("♧ " + commentCount);
        tvCommentBtn.setContentDescription(
                "View " + commentCount + " comments"
        );

        tvLike.setOnClickListener(v -> toggleLike(postId));

        tvCommentBtn.setOnClickListener(v ->
                showComments(postId)
        );

        if (post.optBoolean("mine", false)) {

            tvDelete.setVisibility(View.VISIBLE);

            tvDelete.setOnClickListener(v ->
                    confirmDeletePost(postId)
            );

        } else {
            tvDelete.setVisibility(View.GONE);
        }

        return card;
    }

    // ----------------------------------------------------
    // LIKES
    // ----------------------------------------------------

    private void toggleLike(String postId) {

        JSONArray posts = getPosts();

        JSONObject post = findPost(posts, postId);

        if (post == null) return;

        try {
            boolean liked = post.optBoolean("liked", false);
            int likes = post.optInt("likes", 0);

            post.put("liked", !liked);
            post.put("likes", liked
                    ? Math.max(0, likes - 1)
                    : likes + 1);

            savePosts(posts);
            displayPosts();

        } catch (Exception e) {
            toast("Unable to update like.");
        }
    }

    private void updateLikeButton(
            TextView button, JSONObject post) {

        boolean liked = post.optBoolean("liked", false);
        int likes = post.optInt("likes", 0);

        button.setText((liked ? "♥ " : "♡ ") + likes);

        button.setTextColor(
                liked
                        ? Color.rgb(220, 40, 65)
                        : requireContext().getColor(R.color.navy)
        );

        button.setContentDescription(
                (liked ? "Unlike post" : "Like post")
                        + ", " + likes + " likes"
        );
    }

    // ----------------------------------------------------
    // COMMENTS
    // ----------------------------------------------------

    private void showComments(String postId) {

        JSONObject post = findPost(getPosts(), postId);

        if (post == null) return;

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(18), dp(12), dp(18), dp(12));

        android.widget.ScrollView scroll =
                new android.widget.ScrollView(requireContext());

        LinearLayout commentList =
                new LinearLayout(requireContext());

        commentList.setOrientation(LinearLayout.VERTICAL);

        scroll.addView(commentList);

        layout.addView(
                scroll,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                )
        );

        EditText input = new EditText(requireContext());
        input.setHint("Write a comment...");
        input.setMaxLines(4);
        input.setMaxEms(300);
        input.setSingleLine(false);
        input.setInputType(
                android.text.InputType.TYPE_CLASS_TEXT
                        | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        );
        input.setFilters(new android.text.InputFilter[]{
                new android.text.InputFilter.LengthFilter(300)
        });

        layout.addView(input);

        TextView send = new TextView(requireContext());
        send.setText("Send comment");
        send.setTextColor(
                requireContext().getColor(R.color.white)
        );
        send.setTypeface(null, Typeface.BOLD);
        send.setGravity(Gravity.CENTER);
        send.setMinHeight(dp(48));
        send.setBackgroundResource(R.drawable.bg_chip_selected);

        LinearLayout.LayoutParams sendParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        sendParams.topMargin = dp(8);
        layout.addView(send, sendParams);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Comments")
                .setView(layout)
                .setNegativeButton("Close", null)
                .create();

        dialog.setOnShowListener(ignored -> {

            // Give the comment area space inside the dialog.
            scroll.getLayoutParams().height = dp(260);
            scroll.getLayoutParams().width =
                    ViewGroup.LayoutParams.MATCH_PARENT;
            scroll.requestLayout();

            renderComments(postId, commentList);

            send.setOnClickListener(v -> {

                String message =
                        input.getText().toString().trim();

                if (message.isEmpty()) {
                    input.setError("Write a comment first.");
                    return;
                }

                if (addComment(postId, message)) {
                    input.setText("");
                    renderComments(postId, commentList);
                    displayPosts();
                }
            });
        });

        dialog.show();
    }

    private boolean addComment(String postId, String message) {

        JSONArray posts = getPosts();
        JSONObject post = findPost(posts, postId);

        if (post == null) return false;

        try {
            JSONArray comments = post.optJSONArray("comments");

            if (comments == null) {
                comments = new JSONArray();
            }

            JSONObject comment = new JSONObject();

            comment.put("id", UUID.randomUUID().toString());
            comment.put("text", message);
            comment.put("createdAt", System.currentTimeMillis());
            comment.put("mine", true);

            comments.put(comment);

            post.put("comments", comments);

            savePosts(posts);

            return true;

        } catch (Exception e) {
            toast("Unable to save comment.");
            return false;
        }
    }

    private void renderComments(
            String postId, LinearLayout container) {

        container.removeAllViews();

        JSONObject post = findPost(getPosts(), postId);

        if (post == null) return;

        JSONArray comments = post.optJSONArray("comments");

        if (comments == null || comments.length() == 0) {

            TextView empty = new TextView(requireContext());
            empty.setText("No comments yet. Be the first.");
            empty.setTextColor(
                    requireContext().getColor(R.color.muted)
            );
            empty.setPadding(0, dp(12), 0, dp(12));

            container.addView(empty);
            return;
        }

        for (int i = 0; i < comments.length(); i++) {

            JSONObject comment = comments.optJSONObject(i);

            if (comment == null) continue;

            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, dp(10), 0, dp(10));

            TextView author = new TextView(requireContext());
            author.setText(
                    (comment.optBoolean("mine")
                            ? "You"
                            : "Anonymous student")
                            + " · "
                            + timeAgo(comment.optLong("createdAt"))
            );
            author.setTypeface(null, Typeface.BOLD);
            author.setTextColor(
                    requireContext().getColor(R.color.navy)
            );
            author.setTextSize(13);

            TextView body = new TextView(requireContext());
            body.setText(comment.optString("text"));
            body.setTextColor(
                    requireContext().getColor(R.color.ink)
            );
            body.setTextSize(15);
            body.setPadding(0, dp(4), 0, dp(4));

            row.addView(author);
            row.addView(body);

            if (comment.optBoolean("mine")) {

                String commentId = comment.optString("id");

                TextView delete = new TextView(requireContext());
                delete.setText("Delete comment");
                delete.setTextColor(
                        requireContext().getColor(R.color.red)
                );
                delete.setTextSize(13);
                delete.setMinHeight(dp(40));
                delete.setGravity(Gravity.CENTER_VERTICAL);

                delete.setOnClickListener(v ->
                        new AlertDialog.Builder(requireContext())
                                .setTitle("Delete comment?")
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton("Delete", (d, w) -> {
                                    deleteComment(postId, commentId);
                                    renderComments(postId, container);
                                    displayPosts();
                                })
                                .show()
                );

                row.addView(delete);
            }

            container.addView(row);
        }
    }

    private void deleteComment(
            String postId, String commentId) {

        JSONArray posts = getPosts();
        JSONObject post = findPost(posts, postId);

        if (post == null) return;

        JSONArray oldComments =
                post.optJSONArray("comments");

        if (oldComments == null) return;

        JSONArray updated = new JSONArray();

        for (int i = 0; i < oldComments.length(); i++) {

            JSONObject comment =
                    oldComments.optJSONObject(i);

            if (comment != null
                    && !commentId.equals(
                    comment.optString("id"))) {
                updated.put(comment);
            }
        }

        try {
            post.put("comments", updated);
            savePosts(posts);
        } catch (Exception e) {
            toast("Unable to delete comment.");
        }
    }

    // ----------------------------------------------------
    // DELETE POST
    // ----------------------------------------------------

    private void confirmDeletePost(String postId) {

        new AlertDialog.Builder(requireContext())
                .setTitle("Delete post?")
                .setMessage(
                        "This post and all its comments will be deleted."
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) ->
                        deletePost(postId)
                )
                .show();
    }

    private void deletePost(String postId) {

        JSONArray oldPosts = getPosts();
        JSONArray updated = new JSONArray();

        String photoToDelete = "";

        for (int i = 0; i < oldPosts.length(); i++) {

            JSONObject post = oldPosts.optJSONObject(i);

            if (post == null) continue;

            if (postId.equals(post.optString("id"))) {
                photoToDelete = post.optString("photo", "");
            } else {
                updated.put(post);
            }
        }

        savePosts(updated);

        if (!photoToDelete.isEmpty()) {
            deleteOwnedPhoto(photoToDelete);
        }

        displayPosts();
        toast("Post deleted.");
    }

    // ----------------------------------------------------
    // PHOTO PICKER
    // ----------------------------------------------------

    private void handleSelectedPhoto(Uri uri) {

        if (uri == null || getContext() == null) return;

        try {
            File directory = new File(
                    requireContext().getFilesDir(),
                    "feed_photos"
            );

            if (!directory.exists() && !directory.mkdirs()) {
                throw new Exception("Unable to create photo folder.");
            }

            File file = new File(
                    directory,
                    UUID.randomUUID().toString() + ".jpg"
            );

            try (InputStream input =
                         requireContext().getContentResolver()
                                 .openInputStream(uri);
                 FileOutputStream output =
                         new FileOutputStream(file)) {

                if (input == null) {
                    throw new Exception("Unable to open photo.");
                }

                byte[] buffer = new byte[8192];
                int bytes;
                long total = 0;

                while ((bytes = input.read(buffer)) != -1) {

                    total += bytes;

                    if (total > MAX_PHOTO_BYTES) {
                        throw new Exception("Photo exceeds 5 MB.");
                    }

                    output.write(buffer, 0, bytes);
                }

            } catch (Exception e) {
                file.delete();
                throw e;
            }

            BitmapFactory.Options options =
                    new BitmapFactory.Options();

            options.inSampleSize = 4;

            Bitmap bitmap = BitmapFactory.decodeFile(
                    file.getAbsolutePath(),
                    options
            );

            if (bitmap == null) {
                file.delete();
                throw new Exception("Invalid photo.");
            }

            // Delete a previous unsent selection.
            if (selectedPhotoPath != null) {
                deleteOwnedPhoto(selectedPhotoPath);
            }

            selectedPhotoPath = file.getAbsolutePath();

            ivPreview.setImageBitmap(bitmap);
            ivPreview.setVisibility(View.VISIBLE);
            tvAddPhoto.setText("Change photo");

        } catch (Exception e) {
            toast("Unable to add photo. Use an image under 5 MB.");
        }
    }

    private void showPhoto(ImageView image, String path) {

        File file = new File(path);

        if (!file.isFile()) {
            image.setVisibility(View.GONE);
            return;
        }

        BitmapFactory.Options options =
                new BitmapFactory.Options();

        options.inSampleSize = 4;

        Bitmap bitmap = BitmapFactory.decodeFile(
                file.getAbsolutePath(),
                options
        );

        if (bitmap != null) {
            image.setImageBitmap(bitmap);
            image.setVisibility(View.VISIBLE);
        } else {
            image.setVisibility(View.GONE);
        }
    }

    private void deleteOwnedPhoto(String path) {

        try {
            File directory = new File(
                    requireContext().getFilesDir(),
                    "feed_photos"
            ).getCanonicalFile();

            File file = new File(path).getCanonicalFile();

            if (file.getParentFile() != null
                    && file.getParentFile().equals(directory)) {
                file.delete();
            }

        } catch (Exception ignored) {
        }
    }

    // ----------------------------------------------------
    // SHARED PREFERENCES STORAGE
    // ----------------------------------------------------

    private JSONArray getPosts() {

        try {
            String saved = prefs.getString(POSTS_KEY, "[]");
            return new JSONArray(saved);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private void savePosts(JSONArray posts) {

        prefs.edit()
                .putString(POSTS_KEY, posts.toString())
                .apply();
    }

    private JSONObject findPost(
            JSONArray posts, String postId) {

        for (int i = 0; i < posts.length(); i++) {

            JSONObject post = posts.optJSONObject(i);

            if (post != null
                    && postId.equals(post.optString("id"))) {
                return post;
            }
        }

        return null;
    }

    // ----------------------------------------------------
    // HELPERS
    // ----------------------------------------------------

    private String timeAgo(long timestamp) {

        long difference = Math.max(
                0,
                System.currentTimeMillis() - timestamp
        );

        long minutes = difference / 60000;

        if (minutes < 1) return "Just now";
        if (minutes < 60) return minutes + " min ago";

        long hours = minutes / 60;

        if (hours < 24) return hours + " h ago";

        long days = hours / 24;

        return days == 1
                ? "1 day ago"
                : days + " days ago";
    }

    private void toast(String message) {

        if (getContext() != null) {
            Toast.makeText(
                    getContext(),
                    message,
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private int dp(int value) {

        return (int) (
                value * getResources()
                        .getDisplayMetrics().density
        );
    }

    @Override
    public void onDestroyView() {
        etPost = null;
        ivPreview = null;
        tvAddPhoto = null;
        tvPost = null;
        tvEmpty = null;
        listContainer = null;
        super.onDestroyView();
    }
}

