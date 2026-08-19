package com.dlt.predictor.lottery;
import java.util.*;
public record DltNumbers(List<Integer> front,List<Integer> back){
 public DltNumbers{front=List.copyOf(front);back=List.copyOf(back);} public static DltNumbers of(List<Integer> f,List<Integer>b){return new DltNumbers(normalize(f,5,35,"前区"),normalize(b,2,12,"后区"));}
 private static List<Integer> normalize(List<Integer> v,int c,int max,String z){if(v==null||v.size()!=c||new HashSet<>(v).size()!=c||v.stream().anyMatch(n->n==null||n<1||n>max))throw new IllegalArgumentException(z+"号码不合法");return v.stream().sorted().toList();}
 public String frontText(){return text(front);}public String backText(){return text(back);}private static String text(List<Integer> v){return v.stream().map(n->"%02d".formatted(n)).reduce((a,b)->a+","+b).orElse("");}
}
