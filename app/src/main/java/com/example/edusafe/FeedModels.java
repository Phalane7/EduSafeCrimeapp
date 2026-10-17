package com.example.edusafe;

import org.json.JSONObject;

public class FeedModels {

    public static class Post {
        public String id, text, photoUrl, author;
        public boolean mine, liked;
        public int likes, comments;
        public long createdAt;

        public static Post from(JSONObject o) {
            Post p = new Post();
            p.id = o.optString("id");
            p.text = o.optString("text");
            p.photoUrl = o.isNull("photoUrl") ? null : o.optString("photoUrl");
            p.author = o.optString("author", "Anonymous student");
            p.mine = o.optBoolean("mine");
            p.liked = o.optBoolean("liked");
            p.likes = o.optInt("likes");
            p.comments = o.optInt("comments");
            p.createdAt = o.optLong("createdAt", System.currentTimeMillis());
            return p;
        }
    }

    public static class Comment {
        public String id, parentId, text, author;
        public boolean mine;
        public long createdAt;

        public static Comment from(JSONObject o) {
            Comment c = new Comment();
            c.id = o.optString("id");
            c.parentId = o.optString("parentId", "");
            c.text = o.optString("text");
            c.author = o.optString("author", "Anonymous student");
            c.mine = o.optBoolean("mine");
            c.createdAt = o.optLong("createdAt", System.currentTimeMillis());
            return c;
        }
    }
}