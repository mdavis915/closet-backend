package com.closet.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "clothing_items")
public class ClothingItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private String category;  // tops, bottoms, shoes, outerwear, accessories
    private String color;
    private String style;     // casual, formal, sporty
    private String imageUrl;  // Cloudinary URL
    private String description; // AI generated description
    private String season; // spring, summer, fall, winter, all
}