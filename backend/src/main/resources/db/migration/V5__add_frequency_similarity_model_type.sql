ALTER TABLE prediction_model
    DROP CONSTRAINT ck_prediction_model_type;

ALTER TABLE prediction_model
    ADD CONSTRAINT ck_prediction_model_type CHECK (
        model_type IN (
            'LOGISTIC_REGRESSION',
            'VECTOR_TRANSITION',
            'DYNAMIC_ENSEMBLE',
            'DECAYED_FREQUENCY',
            'FREQUENCY_SIMILARITY'
        )
    );
