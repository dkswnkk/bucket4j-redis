package com.example.bucket4j.service;

import com.example.bucket4j.annotation.RateLimit;
import org.springframework.stereotype.Service;

@Service
public class RateLimitingService {

    @RateLimit(key = "someUniqueKey1")
    public String run1() {
        return "요청 성공";
    }

    @RateLimit(key = "someUniqueKey2")
    public String run2() {
        return "요청 성공";
    }

}