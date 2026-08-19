package com.dlt.predictor.auth;import java.time.Instant;
record UserAccount(String username,String passwordHash,Role role,Status status,boolean mustChangePassword,Instant createdAt){enum Role{ADMIN,USER}enum Status{PENDING,APPROVED,REJECTED,DISABLED}}
