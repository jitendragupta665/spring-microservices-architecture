package com.example.user_service.service;


import com.example.user_service.model.User;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    public User getUser() {
        return new User("Rahul", 22);
    }
}