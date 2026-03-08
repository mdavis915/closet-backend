package com.closet.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "users")
public class User {
    @Id
    private Long id;

    private String name;
    private String email;
    private String vibe; // "clean_girl", "streetwear", "casual", "business_casual"
}