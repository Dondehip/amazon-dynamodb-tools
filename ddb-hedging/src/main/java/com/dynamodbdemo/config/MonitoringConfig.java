package com.dynamodbdemo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
//@EnableScheduling
@EnableAspectJAutoProxy
public class MonitoringConfig {
}
