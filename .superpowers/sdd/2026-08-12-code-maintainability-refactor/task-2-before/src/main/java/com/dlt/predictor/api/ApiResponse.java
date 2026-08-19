package com.dlt.predictor.api; import java.util.UUID;
public record ApiResponse<T>(int code,String message,T data,String traceId){public static <T> ApiResponse<T> ok(T data){return new ApiResponse<>(200,"OK",data,UUID.randomUUID().toString());}public static <T> ApiResponse<T> error(int code,String msg){return new ApiResponse<>(code,msg,null,UUID.randomUUID().toString());}}
