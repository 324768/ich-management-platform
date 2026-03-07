package com.hyang.ich.content.config;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;

@Configuration
@MapperScan(basePackages = "com.hyang.ich.content.mapper.product",
        sqlSessionFactoryRef = "productSqlSessionFactory")
public class ProductDataSourceConfig {

    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.product")
    public DataSource productDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean
    public SqlSessionFactory productSqlSessionFactory(@Qualifier("productDataSource") DataSource dataSource) throws Exception {
        SqlSessionFactoryBean bean = new SqlSessionFactoryBean();
        bean.setDataSource(dataSource);
        bean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath:mapper/product/*.xml"));
        bean.setTypeAliasesPackage("com.hyang.ich.content.entity");
        org.apache.ibatis.session.Configuration config = new org.apache.ibatis.session.Configuration();
        config.setMapUnderscoreToCamelCase(true);
        config.setLogImpl(org.apache.ibatis.logging.stdout.StdOutImpl.class);
        bean.setConfiguration(config);
        return bean.getObject();
    }
}
