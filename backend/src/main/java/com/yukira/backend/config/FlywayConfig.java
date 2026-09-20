package com.yukira.backend.config;

import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class FlywayConfig {

    @Bean(name = "flyway")
    public Flyway flyway(DataSource dataSource) {
        Flyway flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .baselineOnMigrate(false)
            .load();
        flyway.repair();
        flyway.migrate();
        return flyway;
    }

    @Bean
    public static org.springframework.beans.factory.config.BeanFactoryPostProcessor entityManagerDependsOnFlyway() {
        return beanFactory -> {
            String[] emBeanNames = beanFactory.getBeanNamesForType(jakarta.persistence.EntityManagerFactory.class, true, false);
            for (String emBeanName : emBeanNames) {
                org.springframework.beans.factory.config.BeanDefinition bd = beanFactory.getBeanDefinition(emBeanName);
                String[] dependsOn = bd.getDependsOn();
                if (dependsOn == null || dependsOn.length == 0) {
                    bd.setDependsOn("flyway");
                } else {
                    String[] newDependsOn = java.util.Arrays.copyOf(dependsOn, dependsOn.length + 1);
                    newDependsOn[dependsOn.length] = "flyway";
                    bd.setDependsOn(newDependsOn);
                }
            }
        };
    }
}
