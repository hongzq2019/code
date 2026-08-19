package com.dlt.predictor.prediction; import com.dlt.predictor.lottery.DltNumbers;
public final class FeatureEncoder{private FeatureEncoder(){} public static float[] encode(DltNumbers n){float[] v=new float[47];float f=(float)(1/Math.sqrt(5)),b=(float)(1/Math.sqrt(2));n.front().forEach(x->v[x-1]=f);n.back().forEach(x->v[35+x-1]=b);return v;}}
