package com.dlt.predictor.lottery;import java.time.LocalDate;
public record Draw(Long id,String issueNo,LocalDate drawDate,DltNumbers numbers,String sourceUrl){}
