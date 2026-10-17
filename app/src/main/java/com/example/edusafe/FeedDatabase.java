package com.example.edusafe;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FeedDatabase extends SQLiteOpenHelper {

    private static final String TAG = "EduSafe";
    private static final String DB_NAME = "edusafe_feed.db";
    private static final int DB_VERSION = 3;

    private static FeedDatabase instance;
    private final Context appContext;

    public static synchronized FeedDatabase get(Context context) {
        if (instance == null) {
            instance = new FeedDatabase(
                    context.getApplicationContext()
            );
        }
        return instance;
    }

    private FeedDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        appContext = context.getApplicationContext();
    }

    // DATA MODELS

    public static class Post {
        public long id, createdAt;
        public String author, text, photoPath;
        public int likes, comments;
        public boolean liked, mine;
    }

    public static class Comment {
        public long id, createdAt, parentId;
        public String text;
        public boolean mine;
    }

    // CREATE EMPTY TABLES

    @Override
    public void onCreate(SQLiteDatabase db) {

        db.execSQL(
                "CREATE TABLE posts (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "author TEXT NOT NULL, " +
                        "text TEXT NOT NULL, " +
                        "photo_path TEXT, " +
                        "created_at INTEGER NOT NULL, " +
                        "likes INTEGER NOT NULL DEFAULT 0, " +
                        "liked INTEGER NOT NULL DEFAULT 0, " +
                        "mine INTEGER NOT NULL DEFAULT 0)"
        );

        db.execSQL(
                "CREATE TABLE comments (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "post_id INTEGER NOT NULL, " +
                        "parent_id INTEGER NOT NULL DEFAULT 0, " +
                        "text TEXT NOT NULL, " +
                        "created_at INTEGER NOT NULL, " +
                        "mine INTEGER NOT NULL DEFAULT 0)"
        );

        Log.d(TAG, "Empty feed database created");
    }

    // UPGRADE DATABASE WITHOUT DELETING USER DATA

    @Override
    public void onUpgrade(
            SQLiteDatabase db,
            int oldVersion,
            int newVersion) {

        if (oldVersion < 2) {
            db.execSQL(
                    "ALTER TABLE comments " +
                            "ADD COLUMN parent_id INTEGER NOT NULL DEFAULT 0"
            );
        }

        // Version 3 does not change the tables.
        // No sample data is inserted.
    }

    // SQL QUERY FOR POSTS

    private static final String POST_SQL =
            "SELECT p.id, p.author, p.text, " +
                    "p.photo_path, p.created_at, " +
                    "p.likes, p.liked, p.mine, " +
                    "(SELECT COUNT(*) FROM comments c " +
                    "WHERE c.post_id = p.id) AS comment_count " +
                    "FROM posts p";

    // GET ALL POSTS

    public List<Post> getPosts() {

        List<Post> posts = new ArrayList<>();

        try (Cursor cursor = getReadableDatabase().rawQuery(
                POST_SQL + " ORDER BY p.created_at DESC",
                null
        )) {
            while (cursor.moveToNext()) {
                posts.add(readPost(cursor));
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to load posts", e);
        }

        return posts;
    }

    // GET ONE POST

    public Post getPost(long id) {

        try (Cursor cursor = getReadableDatabase().rawQuery(
                POST_SQL + " WHERE p.id=?",
                new String[]{String.valueOf(id)}
        )) {
            if (cursor.moveToFirst()) {
                return readPost(cursor);
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to load post", e);
        }

        return null;
    }

    // READ POST FROM DATABASE

    private Post readPost(Cursor cursor) {

        Post post = new Post();

        post.id = cursor.getLong(
                cursor.getColumnIndexOrThrow("id"));

        post.author = cursor.getString(
                cursor.getColumnIndexOrThrow("author"));

        post.text = cursor.getString(
                cursor.getColumnIndexOrThrow("text"));

        post.photoPath = cursor.getString(
                cursor.getColumnIndexOrThrow("photo_path"));

        post.createdAt = cursor.getLong(
                cursor.getColumnIndexOrThrow("created_at"));

        post.likes = cursor.getInt(
                cursor.getColumnIndexOrThrow("likes"));

        post.liked = cursor.getInt(
                cursor.getColumnIndexOrThrow("liked")) == 1;

        post.mine = cursor.getInt(
                cursor.getColumnIndexOrThrow("mine")) == 1;

        post.comments = cursor.getInt(
                cursor.getColumnIndexOrThrow("comment_count"));

        return post;
    }

    // ADD NEW POST

    public boolean addPost(String text, String photoPath) {

        if (text == null || text.trim().isEmpty()) {
            return false;
        }

        ContentValues values = new ContentValues();

        values.put("author", "Anonymous student");
        values.put("text", text.trim());

        if (photoPath != null) {
            values.put("photo_path", photoPath);
        } else {
            values.putNull("photo_path");
        }

        values.put("created_at", System.currentTimeMillis());
        values.put("likes", 0);
        values.put("liked", 0);
        values.put("mine", 1);

        return getWritableDatabase().insert(
                "posts", null, values
        ) != -1;
    }

    // LIKE OR UNLIKE A POST

    public void toggleLike(long id) {

        getWritableDatabase().execSQL(
                "UPDATE posts SET " +
                        "likes = CASE liked " +
                        "WHEN 1 THEN MAX(likes - 1, 0) " +
                        "ELSE likes + 1 END, " +
                        "liked = CASE liked " +
                        "WHEN 1 THEN 0 ELSE 1 END " +
                        "WHERE id=?",
                new Object[]{id}
        );
    }

    // DELETE OWN POST

    public void deletePost(long id) {

        Post post = getPost(id);

        if (post == null || !post.mine) {
            return;
        }

        SQLiteDatabase db = getWritableDatabase();
        boolean deleted = false;

        db.beginTransaction();

        try {
            int count = db.delete(
                    "posts",
                    "id=? AND mine=1",
                    new String[]{String.valueOf(id)}
            );

            if (count > 0) {
                db.delete(
                        "comments",
                        "post_id=?",
                        new String[]{String.valueOf(id)}
                );
                deleted = true;
            }

            db.setTransactionSuccessful();

        } finally {
            db.endTransaction();
        }

        // Delete only local photos inside the app's files folder.
        if (deleted && post.photoPath != null) {
            deleteLocalPhoto(post.photoPath);
        }
    }

    // SAFE LOCAL PHOTO DELETION

    private void deleteLocalPhoto(String photoPath) {

        File photo = new File(photoPath);
        File filesDirectory = appContext.getFilesDir();

        try {
            String photoCanonical = photo.getCanonicalPath();
            String filesCanonical =
                    filesDirectory.getCanonicalPath();

            if (photoCanonical.startsWith(
                    filesCanonical + File.separator)) {

                if (photo.exists() && !photo.delete()) {
                    Log.w(TAG, "Could not delete local photo");
                }
            }

        } catch (IOException e) {
            Log.e(TAG, "Photo deletion failed", e);
        }
    }

    // GET COMMENTS AND REPLIES

    public List<Comment> getComments(long postId) {

        List<Comment> comments = new ArrayList<>();

        try (Cursor cursor = getReadableDatabase().query(
                "comments",
                null,
                "post_id=?",
                new String[]{String.valueOf(postId)},
                null,
                null,
                "created_at ASC"
        )) {

            while (cursor.moveToNext()) {

                Comment comment = new Comment();

                comment.id = cursor.getLong(
                        cursor.getColumnIndexOrThrow("id"));

                comment.parentId = cursor.getLong(
                        cursor.getColumnIndexOrThrow("parent_id"));

                comment.text = cursor.getString(
                        cursor.getColumnIndexOrThrow("text"));

                comment.createdAt = cursor.getLong(
                        cursor.getColumnIndexOrThrow("created_at"));

                comment.mine = cursor.getInt(
                        cursor.getColumnIndexOrThrow("mine")) == 1;

                comments.add(comment);
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to load comments", e);
        }

        return comments;
    }

    // ADD NORMAL COMMENT

    public boolean addComment(long postId, String text) {
        return addComment(postId, 0, text);
    }

    // ADD COMMENT OR REPLY

    public boolean addComment(
            long postId,
            long parentId,
            String text) {

        if (text == null || text.trim().isEmpty()) {
            return false;
        }

        if (getPost(postId) == null) {
            return false;
        }

        // Check that the parent comment belongs to this post.
        if (parentId != 0) {

            try (Cursor cursor = getReadableDatabase().query(
                    "comments",
                    new String[]{"id"},
                    "id=? AND post_id=?",
                    new String[]{
                            String.valueOf(parentId),
                            String.valueOf(postId)
                    },
                    null,
                    null,
                    null
            )) {

                if (!cursor.moveToFirst()) {
                    return false;
                }
            }
        }

        ContentValues values = new ContentValues();

        values.put("post_id", postId);
        values.put("parent_id", parentId);
        values.put("text", text.trim());
        values.put("created_at", System.currentTimeMillis());
        values.put("mine", 1);

        return getWritableDatabase().insert(
                "comments", null, values
        ) != -1;
    }

    // DELETE OWN COMMENT AND ITS REPLIES

    public void deleteComment(long id) {

        SQLiteDatabase db = getWritableDatabase();

        db.beginTransaction();

        try {
            int deleted = db.delete(
                    "comments",
                    "id=? AND mine=1",
                    new String[]{String.valueOf(id)}
            );

            if (deleted > 0) {
                db.delete(
                        "comments",
                        "parent_id=?",
                        new String[]{String.valueOf(id)}
                );
            }

            db.setTransactionSuccessful();

        } finally {
            db.endTransaction();
        }
    }
}

