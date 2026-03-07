package com.hyang.ich.user.config;

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
@MapperScan(basePackages = "com.hyang.ich.user.mapper.system",
        sqlSessionFactoryRef = "systemSqlSessionFactory")
public class SystemDataSourceConfig {

    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.system")
    public DataSource systemDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean
    public SqlSessionFactory systemSqlSessionFactory(@Qualifier("systemDataSource") DataSource dataSource) throws Exception {
        SqlSessionFactoryBean bean = new SqlSessionFactoryBean();
        bean.setDataSource(dataSource);
        bean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath:mapper/system/*.xml"));
        bean.setTypeAliasesPackage("com.hyang.ich.user.entity");
        org.apache.ibatis.session.Configuration config = new org.apache.ibatis.session.Configuration();
        config.setMapUnderscoreToCamelCase(true);
        config.setLogImpl(org.apache.ibatis.logging.stdout.StdOutImpl.class);
        bean.setConfiguration(config);
        return bean.getObject();
    }
}
