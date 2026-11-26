package com.dlt.predictor.prediction.model;

import com.dlt.predictor.lottery.Draw;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** 指纹覆盖全部业务开奖内容，不包含不影响拟合的来源 URL/采集元数据。 */
final class TrainingHistoryFingerprint {
    static String calculate(List<Draw> draws) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DataOutputStream output = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
                output.writeInt(draws.size());
                for (Draw draw : draws) {
                    output.writeLong(draw.id());
                    byte[] issue = draw.issueNo().getBytes(StandardCharsets.UTF_8);
                    output.writeInt(issue.length);
                    output.write(issue);
                    output.writeLong(draw.drawDate().toEpochDay());
                    for (int number : draw.numbers().front()) output.writeInt(number);
                    for (int number : draw.numbers().back()) output.writeInt(number);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException | IOException exception) {
            throw new IllegalStateException("无法计算训练历史指纹", exception);
        }
    }
}
