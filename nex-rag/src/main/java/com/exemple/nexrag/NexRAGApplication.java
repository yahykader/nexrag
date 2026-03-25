package com.exemple.nexrag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

import java.util.Date;
import java.util.List;

@SpringBootApplication
@EnableCaching  // Active le cache Spring
public class NexRAGApplication {

    public static void main(String[] args) {

        SpringApplication.run(NexRAGApplication.class, args);
    }
}
