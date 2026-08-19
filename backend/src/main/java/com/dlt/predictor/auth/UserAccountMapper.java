package com.dlt.predictor.auth;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 *
 * sys_user 表的 MyBatis-Plus Mapper，供认证和待审核用户分页共同使用。
 *
 */
@Mapper
interface UserAccountMapper extends BaseMapper<UserAccountEntity> {
}
