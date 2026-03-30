package org.example.domain;

/**
 * In-app notification for students, staff, or authors.
 */
public final class AppNotification {

    private final long id;
    private final long userId;
    private final String category;
    private final String title;
    private final String body;
    private final String createdAt;
    private final String readAt;
    private final String archivedAt;
    private final int priority;

    public AppNotification(long id, long userId, String category, String title, String body,
                          String createdAt, String readAt, String archivedAt, int priority) {
        this.id = id;
        this.userId = userId;
        this.category = category;
        this.title = title;
        this.body = body;
        this.createdAt = createdAt;
        this.readAt = readAt;
        this.archivedAt = archivedAt;
        this.priority = priority;
    }

    public long getId() { return id; }
    public long getUserId() { return userId; }
    public String getCategory() { return category; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getCreatedAt() { return createdAt; }
    public String getReadAt() { return readAt; }
    public String getArchivedAt() { return archivedAt; }
    public int getPriority() { return priority; }

    public boolean isRead() {
        return readAt != null && !readAt.isEmpty();
    }

    public boolean isArchived() {
        return archivedAt != null && !archivedAt.isEmpty();
    }
}
