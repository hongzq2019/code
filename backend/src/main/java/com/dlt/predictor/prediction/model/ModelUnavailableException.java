package com.dlt.predictor.prediction.model;

/**
 *
 * 当前数据不足以生成可靠模型结果时抛出的业务异常。
 *
 */
public class ModelUnavailableException extends RuntimeException {

    public ModelUnavailableException(String message) {
        super(message);
    }
}
