package edu.zzttc.backend.controller;

import edu.zzttc.backend.domain.entity.RestBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test")
public class TestController {

    @GetMapping("/hello")
    public RestBean<String> test() {
        return RestBean.success();
    }
}
