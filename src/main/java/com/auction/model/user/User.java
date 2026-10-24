// File: src/main/java/com/auction/model/user/User.java
package com.auction.model.user;

import java.io.Serializable;
import java.util.UUID;

public abstract class User implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private String username;
    private String email;
    private String passwordHash;

    public User(String username, String email, String passwordHash) {
        this.id = UUID.randomUUID().toString();
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    // Phương thức trừu tượng thể hiện tính Đa hình (Polymorphism)
    public abstract String getRoleName();
    public abstract String getDashboardInfo();

    // Getters & Setters (Tính đóng gói - Encapsulation)
    public String getId() { return id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    @Override
    public String toString() {
        return String.format("[%s] ID: %s | User: %s | Email: %s", 
                getRoleName(), id.substring(0, 8), username, email);
    }
}