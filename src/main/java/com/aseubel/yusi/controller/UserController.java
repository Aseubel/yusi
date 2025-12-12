package com.aseubel.yusi.controller;

import com.aseubel.yusi.common.Response;
import com.aseubel.yusi.pojo.dto.LoginRequest;
import com.aseubel.yusi.pojo.dto.RegisterRequest;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * @author Aseubel
 * @date 2025/5/7 上午9:52
 */
@Slf4j
@RestController()
@CrossOrigin("*")
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping("/register")
    public Response<User> register(@RequestBody RegisterRequest request) {
        User user = userService.register(request.converToUser());
        return Response.success(user);
    }

    @PostMapping("/login")
    public Response<User> login(@RequestBody LoginRequest request) {
        User user = userService.login(request.getUserName(), request.getPassword());
        return Response.success(user);
    }

}
