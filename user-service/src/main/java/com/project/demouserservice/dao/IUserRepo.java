package com.project.demouserservice.dao;

import com.project.demouserservice.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IUserRepo extends JpaRepository<UserEntity, Long> {

     Optional<UserEntity> findByEmail(String email);
     boolean existsByEmail(String email);
}
