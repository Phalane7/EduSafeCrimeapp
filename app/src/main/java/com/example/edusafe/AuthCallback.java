package com.example.edusafe;

public interface AuthCallback<T> {
    void onSuccess(T result);
    void onError(String message);
}

