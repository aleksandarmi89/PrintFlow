package com.printflow.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Data
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;
    
    private String title;
    private String message;
    private String type; // "ORDER_UPDATE", "TASK_ASSIGNED", "SYSTEM", etc.
    @Column(name = "is_read")
    private boolean read;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "read_at")
    private LocalDateTime readAt;
    public Notification() {
		// TODO Auto-generated constructor stub
	}
	public Notification(Long id, User user, String title, String message, String type, boolean read,
			LocalDateTime createdAt, LocalDateTime readAt) {
		
		this.id = id;
		this.user = user;
		this.title = title;
		this.message = message;
		this.type = type;
		this.read = read;
		this.createdAt = createdAt;
		this.readAt = readAt;
	}
	public Long getId() {
		return id;
	}
	public void setId(Long id) {
		this.id = id;
	}
	public User getUser() {
		return user;
	}
	public void setUser(User user) {
		this.user = user;
	}
	public String getTitle() {
		return title;
	}
	public void setTitle(String title) {
		this.title = title;
	}
	public String getMessage() {
		return message;
	}
	public void setMessage(String message) {
		this.message = message;
	}
	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}
	public boolean isRead() {
		return read;
	}
	public void setRead(boolean read) {
		this.read = read;
	}
	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}
	public LocalDateTime getReadAt() {
		return readAt;
	}
	public void setReadAt(LocalDateTime readAt) {
		this.readAt = readAt;
	}
    
}