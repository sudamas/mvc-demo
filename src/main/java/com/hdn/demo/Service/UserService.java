package com.hdn.demo.Service;

import com.hdn.mvc.annotation.DNService;

@DNService
public class UserService implements IUserService {

    public String login(String username, String password) {
        return "[ username: " + username +
                ", password: " + password + " ]";
    }

    public String register(String username, String password) {
        return "[ username: " + username +
                ", password: " + password + " ]";
    }
}
