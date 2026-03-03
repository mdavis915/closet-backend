package com.closet.repository;

import com.closet.model.ClothingItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ClothingItemRepository extends JpaRepository<ClothingItem, Long> {
    List<ClothingItem> findByUserId(Long userId);

    @Query("SELECT c FROM ClothingItem c WHERE c.userId = :userId " +
            "AND (:category IS NULL OR c.category = :category) " +
            "AND (:color IS NULL OR c.color LIKE %:color%) " +
            "AND (:style IS NULL OR c.style = :style) " +
            "AND (:season IS NULL OR c.season = :season)")
    List<ClothingItem> filterItems(@Param("userId") Long userId,
                                   @Param("category") String category,
                                   @Param("color") String color,
                                   @Param("style") String style,
                                   @Param("season") String season);
}