package com.hmall.userservice;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootApplication
@MapperScan("com.hmall.userservice.mapper")
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }

    @Bean
    public CommandLineRunner testRedis(StringRedisTemplate redisTemplate) {
        return args -> {
            // 测试Redis连接
            try {
                String result = redisTemplate.opsForValue().get("info");
                System.out.println("result = " + result);
                System.out.println("Redis连接正常工作！");
                System.out.println("====================================");
            } catch (Exception e) {
                System.err.println("=========== Redis测试失败 ===========");
                System.err.println("Redis连接异常: " + e.getMessage());
                System.err.println("====================================");
            }
        };
    }
}
