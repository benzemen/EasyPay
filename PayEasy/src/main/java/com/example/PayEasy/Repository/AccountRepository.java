package com.example.PayEasy.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AccountRepository extends JpaRepository<com.example.PayEasy.Model.Account, String> {
    
}
