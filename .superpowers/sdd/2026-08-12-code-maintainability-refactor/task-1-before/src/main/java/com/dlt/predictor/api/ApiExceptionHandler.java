package com.dlt.predictor.api;import org.springframework.http.*;import org.springframework.web.bind.annotation.*;
@RestControllerAdvice class ApiExceptionHandler{@ExceptionHandler(IllegalArgumentException.class)ResponseEntity<ApiResponse<Void>>bad(IllegalArgumentException e){return ResponseEntity.badRequest().body(ApiResponse.error(400,e.getMessage()));}}
