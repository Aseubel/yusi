package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @author Aseubel
 * @date 2025/5/7 上午1:14
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    User findByUserName(String userName);

    User findByUserId(String userId);

    User findByEmail(String email);

    List<User> findByIsMatchEnabledTrue();

    Page<User> findByUserNameContaining(String userName, Pageable pageable);

    Page<User> findByUserIdContaining(String userId, Pageable pageable);
}
