package com.aseubel.yusi.pojo.constant;

/** Stable names for durable low-sensitivity product events. */
public enum ProductEventName {
    MATCH_RECOMMENDED("match.recommended"),
    CONNECTION_ACCEPTED("connection.accepted"),
    CONNECTION_DECLINED("connection.declined"),
    CONNECTION_MUTUAL_RESONANCE("connection.mutual_resonance"),
    CONNECTION_ENDED("connection.ended"),
    CONNECTION_REPORTED("connection.reported"),
    CONNECTION_BLOCKED("connection.blocked"),
    CONNECTION_FEEDBACK_SUBMITTED("connection.feedback_submitted"),
    NOTIFICATION_CREATED("notification.created"),
    NOTIFICATION_ANNOUNCEMENT_PUBLISHED("notification.announcement_published"),
    CHAT_MESSAGE_CREATED("chat.message_created");

    private final String value;

    ProductEventName(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
