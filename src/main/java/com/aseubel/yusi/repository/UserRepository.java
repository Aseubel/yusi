package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * @author Aseubel
 * @date 2025/5/7 上午1:14
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    User findByUserName(String userName);
}
