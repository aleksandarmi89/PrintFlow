package com.printflow.repository;

import com.printflow.entity.Translation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TranslationRepository extends JpaRepository<Translation, String> {
    List<Translation> findByCategory(String category);
    
    @Query("SELECT t FROM Translation t WHERE " +
           "LOWER(t.messageKey) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(t.sr) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(t.en) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Translation> search(@Param("query") String query);
    
    List<Translation> findByMessageKeyStartingWith(String prefix);
    
    @Query("SELECT DISTINCT t.category FROM Translation t WHERE t.category IS NOT NULL")
    List<String> findAllCategories();
}