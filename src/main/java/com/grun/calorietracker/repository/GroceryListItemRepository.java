package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.GroceryListEntity;
import com.grun.calorietracker.entity.GroceryListItemEntity;
import com.grun.calorietracker.entity.UserEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GroceryListItemRepository extends JpaRepository<GroceryListItemEntity, Long> {

    List<GroceryListItemEntity> findByGroceryListOrderByCategoryAscDisplayNameAscIdAsc(GroceryListEntity groceryList);

    long countByGroceryList(GroceryListEntity groceryList);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from GroceryListItemEntity item "
            + "join fetch item.groceryList groceryList "
            + "where item.id = :itemId and groceryList.id = :listId and groceryList.user = :user")
    Optional<GroceryListItemEntity> findOwnedForUpdate(
            @Param("itemId") Long itemId,
            @Param("listId") Long listId,
            @Param("user") UserEntity user
    );
}
