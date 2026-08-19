package com.dlt.predictor.prediction; import com.dlt.predictor.lottery.DltNumbers; import java.time.LocalDate;
public record SimilarDraw(Long drawId,String issueNo,LocalDate drawDate,DltNumbers numbers,double similarity){}
