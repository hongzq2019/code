package com.dlt.predictor.auth;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;

/**
 *
 * 管理端待审核用户列表的分页响应，不暴露账户密码散列等内部字段。
 *
 */
public record PendingUserPageResponse(
        List<PendingUserResponse> items,
        int page,
        int size,
        long total) {

    /**
     *
     * 将 MyBatis-Plus 的分页结果转换为前端稳定使用的 API 结构。
     *
     */
    public static PendingUserPageResponse from(Page<UserAccountEntity> source) {
        return new PendingUserPageResponse(
                source.getRecords().stream().map(PendingUserResponse::from).toList(),
                Math.toIntExact(source.getCurrent()),
                Math.toIntExact(source.getSize()),
                source.getTotal());
    }
}
