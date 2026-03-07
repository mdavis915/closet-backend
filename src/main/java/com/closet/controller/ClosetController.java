package com.closet.controller;

import com.closet.model.ClothingItem;
import com.closet.model.User;
import com.closet.repository.ClothingItemRepository;
import com.closet.repository.UserRepository;
import com.closet.service.ClothingAnalysisService;
import com.closet.service.CloudinaryService;
import com.closet.service.OutfitService;
import com.closet.service.TryOnService;
import com.closet.service.WeatherService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/closet")
@CrossOrigin(origins = "*")
public class ClosetController {

    private final UserRepository userRepository;
    private final ClothingItemRepository clothingItemRepository;
    private final ClothingAnalysisService clothingAnalysisService;
    private final OutfitService outfitService;
    private final WeatherService weatherService;
    private final TryOnService tryOnService;
    private final CloudinaryService cloudinaryService;

    public ClosetController(UserRepository userRepository, ClothingItemRepository clothingItemRepository,
                            ClothingAnalysisService clothingAnalysisService, OutfitService outfitService,
                            WeatherService weatherService, TryOnService tryOnService,
                            CloudinaryService cloudinaryService) {
        this.userRepository = userRepository;
        this.clothingItemRepository = clothingItemRepository;
        this.clothingAnalysisService = clothingAnalysisService;
        this.outfitService = outfitService;
        this.weatherService = weatherService;
        this.tryOnService = tryOnService;
        this.cloudinaryService = cloudinaryService;
    }

    @PostMapping("/users")
    public User createUser(@RequestBody User user) {
        return userRepository.save(user);
    }

    @GetMapping("/users/{id}")
    public User getUser(@PathVariable Long id) {
        return userRepository.findById(id).orElseThrow();
    }

    @PostMapping("/items")
    public ClothingItem addItem(@RequestBody ClothingItem item) {
        // Upload base64 image to Cloudinary if present
        if (item.getImageUrl() != null && item.getImageUrl().startsWith("data:")) {
            String cloudUrl = cloudinaryService.uploadBase64Image(item.getImageUrl());
            item.setImageUrl(cloudUrl);
        }
        return clothingItemRepository.save(item);
    }

    @PostMapping("/items/analyze")
    public List<ClothingItem> analyzeAndSaveItems(@RequestBody Map<String, Object> body) {
        String base64Image = (String) body.get("image");
        Long userId = ((Number) body.get("userId")).longValue();

        // Upload image to Cloudinary once, reuse URL for all items in photo
        String cloudinaryUrl = null;
        try {
            cloudinaryUrl = cloudinaryService.uploadBase64Image(base64Image);
        } catch (Exception e) {
            // If upload fails, continue without image — items still get saved
        }

        List<Map<String, String>> analyses = clothingAnalysisService.analyzeClothing(base64Image);

        List<ClothingItem> saved = new java.util.ArrayList<>();
        for (Map<String, String> analysis : analyses) {
            ClothingItem item = new ClothingItem();
            item.setUserId(userId);
            item.setCategory(analysis.get("category"));
            item.setColor(analysis.get("color"));
            item.setStyle(analysis.get("style"));
            item.setDescription(analysis.get("description"));
            item.setImageUrl(cloudinaryUrl);
            saved.add(clothingItemRepository.save(item));
        }
        return saved;
    }

    @GetMapping("/items/{userId}")
    public List<ClothingItem> getItems(@PathVariable Long userId) {
        return clothingItemRepository.findByUserId(userId);
    }

    @PostMapping("/outfit")
    public String getOutfit(@RequestBody Map<String, Object> body) {
        Long userId = ((Number) body.get("userId")).longValue();
        String occasion = (String) body.get("occasion");
        String city = (String) body.get("city");

        User user = userRepository.findById(userId).orElseThrow();
        List<ClothingItem> wardrobe = clothingItemRepository.findByUserId(userId);
        String weather = weatherService.getWeatherSummary(city);

        return outfitService.generateOutfit(wardrobe, occasion, user.getVibe(), weather);
    }

    @PutMapping("/users/{id}")
    public User updateUser(@PathVariable Long id, @RequestBody User updatedUser) {
        User user = userRepository.findById(id).orElseThrow();
        user.setVibe(updatedUser.getVibe());
        return userRepository.save(user);
    }

    @GetMapping("/items/{userId}/filter")
    public List<ClothingItem> filterItems(
            @PathVariable Long userId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String color,
            @RequestParam(required = false) String style,
            @RequestParam(required = false) String season) {
        return clothingItemRepository.filterItems(userId, category, color, style, season);
    }

    @PostMapping("/tryon")
    public Map<String, String> tryOn(@RequestBody Map<String, Object> body) {
        String humanImage = (String) body.get("humanImage");
        String garmentImage = (String) body.get("garmentImage");
        String description = (String) body.get("description");
        String resultUrl = tryOnService.tryOn(humanImage, garmentImage, description);
        return Map.of("resultUrl", resultUrl);
    }
}