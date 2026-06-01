package com.aezer0.initialization;

import org.dromara.x.file.storage.spring.EnableFileStorage;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@EnableFileStorage
@SpringBootApplication
@MapperScan("com.aezer0.initialization.mapper")
public class InitializationApplication {

    public static void main(String[] args) {
        SpringApplication.run(InitializationApplication.class, args);
    }

}
