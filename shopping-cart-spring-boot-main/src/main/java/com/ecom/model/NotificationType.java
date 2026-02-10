package com.ecom.model;

public enum NotificationType {
    LIKE("liked your post"),
    COMMENT("commented on your post"),
    ORDER("order update"),
    SYSTEM("system notification");

    private final String defaultMessage;

    NotificationType(String defaultMessage) {
        this.defaultMessage = defaultMessage;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}