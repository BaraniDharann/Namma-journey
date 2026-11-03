package com.travelplatform.config;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/** Wraps the application's DataSource so owner requests run in their row-level-security scope. */
@Configuration(proxyBeanMethods = false)
public class OwnerScopeDataSourcePostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof DataSource dataSource && !(bean instanceof OwnerScopedDataSource)) {
            return new OwnerScopedDataSource(dataSource);
        }
        return bean;
    }
}
