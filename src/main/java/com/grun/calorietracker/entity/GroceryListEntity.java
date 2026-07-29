package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.GroceryListSourceType;
import com.grun.calorietracker.enums.GroceryListStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "grocery_lists")
@Getter
@Setter
@NoArgsConstructor
public class GroceryListEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private GroceryListSourceType sourceType = GroceryListSourceType.MEAL_PLAN;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_meal_plan_id", nullable = false)
    private MealPlanEntity sourceMealPlan;

    @Column(name = "source_updated_at", nullable = false)
    private LocalDateTime sourceUpdatedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private GroceryListStatus status = GroceryListStatus.ACTIVE;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "groceryList", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("category ASC, displayName ASC, id ASC")
    private List<GroceryListItemEntity> items = new ArrayList<>();

    public void addItem(GroceryListItemEntity item) {
        item.setGroceryList(this);
        items.add(item);
    }

    public void removeItem(GroceryListItemEntity item) {
        items.remove(item);
        item.setGroceryList(null);
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (sourceType == null) sourceType = GroceryListSourceType.MEAL_PLAN;
        if (status == null) status = GroceryListStatus.ACTIVE;
        if (version == null) version = 0L;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
