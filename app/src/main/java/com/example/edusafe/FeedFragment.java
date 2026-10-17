package com.example.edusafe;

import android.content.Context;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class FeedFragment extends Fragment {
    private static final String TAG = "EduSafe";
    private static final String CACHE_KEY = "feed_cache";
    private static final long MAX_PHOTO_BYTES = 5L * 1024 * 1024; // 5 MB
    private static final int MAX_POST = 500;
    private static final int MAX_COMMENT = 300;

    private EditText etPost;
    private ImageView ivPreview;
    private TextView tvAddPhoto, tvEmpty, tvPost;
    private LinearLayout listContainer;
    private String photoPath = null;
    private boolean posting = false;
    private boolean warnedOffline = false;

    // State of the open comments box
    private LinearLayout dlgList;
    private TextView dlgNone, dlgReplying;
    private EditText dlgInput;
    private String dlgPostId = "";
    private String replyParent = ""; // empty = normal comment, otherwise the id of the comment being replied to
    private int dlgCount = 0;

    private final ActivityResultLauncher<PickVisualMediaRequest> pickImage =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), this::onImagePicked);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_feed, container, false);
        etPost = v.findViewById(R.id.etPost);
        ivPreview = v.findViewById(R.id.ivPreview);
        tvAddPhoto = v.findViewById(R.id.tvAddPhoto);
        tvEmpty = v.findViewById(R.id.tvEmpty);
        tvPost = v.findViewById(R.id.tvPost);
        listContainer = v.findViewById(R.id.listContainer);

        tvAddPhoto.setOnClickListener(x -> pickImage.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build()));
        tvPost.setOnClickListener(x -> post());
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadPosts();
    }

    // ---------- Load the shared feed (falls back to the last saved copy when offline) ----------
    private void loadPosts() {
        final Context app = requireContext().getApplicationContext();
        Net.bg(() -> {
            final ApiClient.Result res = ApiClient.get(app, "/posts");
            final List<FeedModels.Post> shown = postsOrCache(app, res);
            Net.main(() -> {
                if (!isAdded() || getView() == null) return;
                render(shown);
                if (!res.ok() && !warnedOffline) {
                    warnedOffline = true;
                    toast(res.offline() ? "You're offline. Showing the last posts we saved." : res.error);
                }
            });
        });
    }

    private List<FeedModels.Post> postsOrCache(Context app, ApiClient.Result res) {
        try {
            if (res.ok() && res.json != null) {
                Session.putString(app, CACHE_KEY, res.json.toString());
                return parsePosts(res.json.optJSONArray("posts"));
            }
            String cached = Session.getString(app, CACHE_KEY);
            if (cached != null) return parsePosts(new JSONObject(cached).optJSONArray("posts"));
        } catch (Exception e) {
            Log.w(TAG, "Feed cache problem", e);
        }
        return new ArrayList<>();
    }

    private List<FeedModels.Post> parsePosts(JSONArray a) {
        List<FeedModels.Post> list = new ArrayList<>();
        if (a == null) return list;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null) list.add(FeedModels.Post.from(o));
        }
        return list;
    }

    private void render(List<FeedModels.Post> posts) {
        listContainer.removeAllViews();
        for (FeedModels.Post p : posts) listContainer.addView(postView(p));
        tvEmpty.setVisibility(posts.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // ---------- Create a post ----------
    private void post() {
        if (posting) return;
        final String text = etPost.getText().toString().trim();
        if (text.length() < 5) {
            etPost.setError("Write a few words about what you saw.");
            etPost.requestFocus();
            return;
        }
        if (text.length() > MAX_POST) {
            etPost.setError("Please keep posts under 500 characters.");
            return;
        }
        posting = true;
        tvPost.setAlpha(0.5f);
        final Context app = requireContext().getApplicationContext();
        final String photo = photoPath;
        Net.bg(() -> {
            ApiClient.Result r;
            if (photo != null) {
                r = ApiClient.upload(app, "/posts", new ApiClient.Multipart()
                        .field("text", text).file("photo", photo, "image/jpeg"));
            } else {
                JSONObject body = new JSONObject();
                try {
                    body.put("text", text);
                } catch (JSONException ignored) {
                    // cannot happen
                }
                r = ApiClient.send(app, "POST", "/posts", body);
            }
            final ApiClient.Result res = r;
            Net.main(() -> {
                posting = false;
                if (!isAdded() || getView() == null) return;
                tvPost.setAlpha(1f);
                if (!res.ok()) {
                    toast(errorText(res));
                    return;
                }
                etPost.setText("");
                etPost.setError(null);
                if (photo != null) new File(photo).delete();
                photoPath = null;
                ivPreview.setImageDrawable(null);
                ivPreview.setVisibility(View.GONE);
                tvAddPhoto.setText("Add photo");
                loadPosts();
                toast("Posted");
            });
        });
    }

    // ---------- One post card ----------
    private View postView(final FeedModels.Post p) {
        View v = LayoutInflater.from(requireContext()).inflate(R.layout.item_post, listContainer, false);
        TextView tvAuthor = v.findViewById(R.id.tvAuthor);
        TextView tvTime = v.findViewById(R.id.tvTime);
        TextView tvText = v.findViewById(R.id.tvText);
        ImageView iv = v.findViewById(R.id.ivPostPhoto);
        final TextView tvLike = v.findViewById(R.id.tvLike);
        final TextView tvCommentBtn = v.findViewById(R.id.tvCommentBtn);
        TextView tvDelete = v.findViewById(R.id.tvDelete);

        tvAuthor.setText(p.mine ? "You" : p.author);
        tvTime.setText(timeAgo(p.createdAt));
        tvText.setText(p.text);
        if (p.photoUrl != null) {
            iv.setVisibility(View.VISIBLE);
            ImageLoader.load(p.photoUrl, iv);
        } else {
            iv.setVisibility(View.GONE);
        }
        bindCounts(p, tvLike, tvCommentBtn);

        tvLike.setOnClickListener(x -> {
            final Context app = requireContext().getApplicationContext();
            Net.bg(() -> {
                final ApiClient.Result res = ApiClient.send(app, "POST", "/posts/" + p.id + "/like", null);
                Net.main(() -> {
                    if (!isAdded()) return;
                    if (!res.ok() || res.json == null) {
                        toast(errorText(res));
                        return;
                    }
                    p.liked = res.json.optBoolean("liked", p.liked);
                    p.likes = res.json.optInt("likes", p.likes);
                    bindCounts(p, tvLike, tvCommentBtn);
                });
            });
        });
        tvCommentBtn.setOnClickListener(x -> showComments(p, tvLike, tvCommentBtn));

        if (p.mine) {
            tvDelete.setVisibility(View.VISIBLE);
            tvDelete.setOnClickListener(x -> confirmDeletePost(p));
        }
        return v;
    }

    private void bindCounts(FeedModels.Post p, TextView like, TextView comment) {
        like.setText(String.valueOf(p.likes));
        like.setCompoundDrawablesWithIntrinsicBounds(
                p.liked ? R.drawable.ic_heart : R.drawable.ic_heart_outline, 0, 0, 0);
        int c = requireContext().getColor(p.liked ? R.color.red : R.color.navy);
        like.setTextColor(c);
        like.setCompoundDrawableTintList(ColorStateList.valueOf(c));
        like.setCompoundDrawablePadding(dp(6));
        like.setContentDescription((p.liked ? "Unlike. " : "Like. ") + p.likes + " likes");

        comment.setText(String.valueOf(p.comments));
        comment.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_comment, 0, 0, 0);
        comment.setCompoundDrawableTintList(ColorStateList.valueOf(requireContext().getColor(R.color.navy)));
        comment.setCompoundDrawablePadding(dp(6));
        comment.setContentDescription("Comments. " + p.comments + " comments");
    }

    private void confirmDeletePost(final FeedModels.Post p) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete post?")
                .setMessage("This also removes its comments.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> {
                    final Context app = requireContext().getApplicationContext();
                    Net.bg(() -> {
                        final ApiClient.Result res = ApiClient.send(app, "DELETE", "/posts/" + p.id, null);
                        Net.main(() -> {
                            if (!isAdded()) return;
                            if (!res.ok()) toast(errorText(res));
                            loadPosts();
                        });
                    });
                })
                .show();
    }

    // ---------- Comments, replies and deleting your own comments ----------
    private void showComments(final FeedModels.Post p, final TextView like, final TextView commentBtn) {
        View dv = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_comments, null);
        dlgList = dv.findViewById(R.id.commentList);
        dlgNone = dv.findViewById(R.id.tvNoComments);
        dlgInput = dv.findViewById(R.id.etComment);
        dlgReplying = dv.findViewById(R.id.tvReplyingTo);
        dlgPostId = p.id;
        dlgCount = p.comments;
        replyParent = "";
        dlgReplying.setVisibility(View.GONE);
        dlgReplying.setOnClickListener(x -> cancelReply());
        dlgNone.setVisibility(View.GONE);
        loadComments();

        dv.findViewById(R.id.tvSend).setOnClickListener(x -> {
            final String t = dlgInput.getText().toString().trim();
            if (t.isEmpty()) {
                dlgInput.setError("Write a comment first.");
                return;
            }
            if (t.length() > MAX_COMMENT) {
                dlgInput.setError("Please keep comments under 300 characters.");
                return;
            }
            final Context app = requireContext().getApplicationContext();
            final String parent = replyParent;
            final String postId = dlgPostId;
            Net.bg(() -> {
                JSONObject body = new JSONObject();
                try {
                    body.put("text", t);
                    if (!parent.isEmpty()) body.put("parentId", parent);
                } catch (JSONException ignored) {
                    // cannot happen
                }
                final ApiClient.Result res = ApiClient.send(app, "POST", "/posts/" + postId + "/comments", body);
                Net.main(() -> {
                    if (!isAdded() || dlgInput == null) return;
                    if (!res.ok()) {
                        toast(errorText(res));
                        return;
                    }
                    dlgInput.setText("");
                    cancelReply();
                    loadComments();
                });
            });
        });

        AlertDialog d = new AlertDialog.Builder(requireContext())
                .setTitle("Comments")
                .setView(dv)
                .setPositiveButton("Close", null)
                .create();
        d.setOnDismissListener(x -> {
            replyParent = "";
            p.comments = dlgCount;
            if (isAdded()) bindCounts(p, like, commentBtn);
        });
        d.show();
    }

    private void cancelReply() {
        replyParent = "";
        if (dlgReplying != null) dlgReplying.setVisibility(View.GONE);
    }

    private void loadComments() {
        final Context app = requireContext().getApplicationContext();
        final String postId = dlgPostId;
        Net.bg(() -> {
            final ApiClient.Result res = ApiClient.get(app, "/posts/" + postId + "/comments");
            final List<FeedModels.Comment> list = new ArrayList<>();
            if (res.ok() && res.json != null) {
                JSONArray a = res.json.optJSONArray("comments");
                if (a != null) {
                    for (int i = 0; i < a.length(); i++) {
                        JSONObject o = a.optJSONObject(i);
                        if (o != null) list.add(FeedModels.Comment.from(o));
                    }
                }
            }
            Net.main(() -> {
                if (!isAdded() || dlgList == null) return;
                if (!res.ok()) {
                    toast(errorText(res));
                    return;
                }
                renderComments(list);
            });
        });
    }

    private void renderComments(List<FeedModels.Comment> all) {
        dlgCount = all.size();
        dlgList.removeAllViews();
        dlgNone.setVisibility(all.isEmpty() ? View.VISIBLE : View.GONE);
        for (FeedModels.Comment c : all) {
            if (!c.parentId.isEmpty()) continue;
            dlgList.addView(commentRow(c, false));
            for (FeedModels.Comment r : all) {
                if (r.parentId.equals(c.id)) dlgList.addView(commentRow(r, true));
            }
        }
    }

    private View commentRow(final FeedModels.Comment c, boolean isReply) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(isReply ? dp(24) : 0, dp(8), 0, 0);

        TextView meta = new TextView(requireContext());
        meta.setText((c.mine ? "You" : "Anonymous student") + " · " + timeAgo(c.createdAt));
        meta.setTextSize(12);
        meta.setTextColor(requireContext().getColor(R.color.muted));

        TextView body = new TextView(requireContext());
        body.setText(c.text);
        body.setTextSize(15);
        body.setTextColor(requireContext().getColor(R.color.ink));

        LinearLayout actions = new LinearLayout(requireContext());
        actions.setOrientation(LinearLayout.HORIZONTAL);

        if (!isReply) {
            TextView reply = actionText("Reply", R.color.navy);
            reply.setOnClickListener(x -> {
                replyParent = c.id;
                dlgReplying.setText("Replying to " + (c.mine ? "your comment" : "Anonymous student")
                        + " · tap to cancel");
                dlgReplying.setVisibility(View.VISIBLE);
                dlgInput.requestFocus();
            });
            actions.addView(reply);
        }
        if (c.mine) {
            TextView del = actionText("Delete", R.color.red);
            del.setOnClickListener(x -> confirmDeleteComment(c));
            actions.addView(del);
        }

        row.addView(meta);
        row.addView(body);
        row.addView(actions);
        return row;
    }

    private TextView actionText(String label, int colorRes) {
        TextView t = new TextView(requireContext());
        t.setText(label);
        t.setTextSize(13);
        t.setTypeface(null, Typeface.BOLD);
        t.setTextColor(requireContext().getColor(colorRes));
        t.setMinHeight(dp(48));
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(0, 0, dp(20), 0);
        t.setClickable(true);
        t.setFocusable(true);
        t.setContentDescription(label + " comment");
        return t;
    }

    private void confirmDeleteComment(final FeedModels.Comment c) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete comment?")
                .setMessage("Any replies under it are removed too.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> {
                    final Context app = requireContext().getApplicationContext();
                    Net.bg(() -> {
                        final ApiClient.Result res = ApiClient.send(app, "DELETE", "/posts/comments/" + c.id, null);
                        Net.main(() -> {
                            if (!isAdded()) return;
                            if (!res.ok()) {
                                toast(errorText(res));
                                return;
                            }
                            if (replyParent.equals(c.id)) cancelReply();
                            loadComments();
                        });
                    });
                })
                .show();
    }

    // ---------- Photo for a post ----------
    private void onImagePicked(Uri uri) {
        if (uri == null || getView() == null) return;
        try {
            if (querySize(uri) > MAX_PHOTO_BYTES) {
                toast("That photo is too large. The limit is 5 MB.");
                return;
            }
            File dir = new File(requireContext().getFilesDir(), "feed");
            dir.mkdirs();
            File out = new File(dir, "post_" + System.currentTimeMillis() + ".jpg");
            try (InputStream in = requireContext().getContentResolver().openInputStream(uri);
                 OutputStream os = new FileOutputStream(out)) {
                if (in == null) throw new IllegalStateException("No stream");
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            }
            if (out.length() > MAX_PHOTO_BYTES) {
                out.delete();
                toast("That photo is too large. The limit is 5 MB.");
                return;
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 4; // keeps memory use low
            Bitmap b = BitmapFactory.decodeFile(out.getAbsolutePath(), o);
            if (b == null) {
                out.delete();
                toast("That file isn't a valid image.");
                return;
            }
            if (photoPath != null) new File(photoPath).delete();
            photoPath = out.getAbsolutePath();
            ivPreview.setImageBitmap(b);
            ivPreview.setVisibility(View.VISIBLE);
            tvAddPhoto.setText("Change photo");
        } catch (Exception e) {
            Log.e(TAG, "Photo failed", e);
            toast("Couldn't load that photo. Please try another.");
        }
    }

    private long querySize(Uri uri) {
        try (Cursor c = requireContext().getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.SIZE);
                if (i >= 0 && !c.isNull(i)) return c.getLong(i);
            }
        } catch (Exception e) {
            Log.w(TAG, "querySize", e);
        }
        return -1;
    }

    // ---------- Helpers ----------
    private String errorText(ApiClient.Result r) {
        return r.offline() ? "You're offline. Please connect to the internet and try again." : r.error;
    }

    private String timeAgo(long ms) {
        long min = (System.currentTimeMillis() - ms) / 60000L;
        if (min < 1) return "Just now";
        if (min < 60) return min + " min ago";
        long h = min / 60;
        if (h < 24) return h + " h ago";
        long d = h / 24;
        return d + (d == 1 ? " day ago" : " days ago");
    }

    private void toast(String msg) {
        if (getContext() != null) Toast.makeText(getContext(), msg, Toast.LENGTH_LONG).show();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
