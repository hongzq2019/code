package com.dlt.predictor.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 *
 * 配置账户 Mapper 扫描及 PostgreSQL 分页拦截器，不影响 JPA 管理的开奖数据。
 *
 */
@Configuration
@MapperScan("com.dlt.predictor.auth")
public class MybatisPlusConfiguration {

    /**
     *
     * 注册 MyBatis-Plus 分页插件，使 Page 查询生成 PostgreSQL 的分页 SQL。
     *
     */
    @Bean
    MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        return interceptor;
    }
}
