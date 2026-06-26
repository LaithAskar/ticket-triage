package com.example.tickettriage;

import com.example.tickettriage.security.AppSecurityProperties;
import com.example.tickettriage.triage.TriageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({TriageProperties.class, AppSecurityProperties.class})
public class TicketTriageApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketTriageApplication.class, args);
    }
}
