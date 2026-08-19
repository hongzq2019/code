package com.dlt.predictor.prediction; import com.dlt.predictor.lottery.DltNumbers; import java.time.Instant;import java.util.*;
public record RecommendationResult(DltNumbers numbers,Map<Integer,Double> frontScores,Map<Integer,Double> backScores,List<SimilarDraw> similarDraws,String modelVersion,Instant generatedAt,String disclaimer){}
