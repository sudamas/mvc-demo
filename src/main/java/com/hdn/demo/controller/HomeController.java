package com.hdn.demo.controller;

import com.hdn.demo.Service.IUserService;
import com.hdn.mvc.annotation.DNAutowired;
import com.hdn.mvc.annotation.DNController;
import com.hdn.mvc.annotation.DNRequestMapping;
import com.hdn.mvc.annotation.DNRequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@DNController
@DNRequestMapping("/home")
public class HomeController {

    @DNAutowired
    IUserService userService;

    @DNRequestMapping("/login")
    public void login(HttpServletRequest request,
                      HttpServletResponse response,
                      @DNRequestParam("username") String username,
                      @DNRequestParam("password") String password) {
        String result = "登陆成功：" + userService.login(username, password);
    }

    @DNRequestMapping("/login")
    public void register(HttpServletRequest request,
                         HttpServletResponse response,
                         @DNRequestParam("username") String username,
                         @DNRequestParam("password") String password) {
        String result = "注册成功：" + userService.login(username, password);
    }
}
