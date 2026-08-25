package com.dcf.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 页面路由控制器。
 */
@Controller
public class PageController {

    @GetMapping("/")
    public String index() {
        return "index";
    }
}