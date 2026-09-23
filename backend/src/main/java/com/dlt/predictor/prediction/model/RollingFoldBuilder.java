package com.dlt.predictor.prediction.model;

import java.util.ArrayList;
import java.util.List;

public class RollingFoldBuilder {

    private static final int FOLD_COUNT = 5;

    public List<RollingFold> build(int drawCount, int featureWindow) {
        int targets = drawCount - featureWindow;
        if (targets <= 0) {
            throw new IllegalArgumentException("没有可验证的目标期");
        }
        int initialTargets = (int) Math.floor(targets * 0.60);
        int remaining = targets - initialTargets;
        int baseSize = remaining / FOLD_COUNT;
        int extra = remaining % FOLD_COUNT;
        int cursor = featureWindow + initialTargets;
        List<RollingFold> folds = new ArrayList<>();
        for (int index = 0; index < FOLD_COUNT; index++) {
            int size = baseSize + (index < extra ? 1 : 0);
            folds.add(new RollingFold(cursor, cursor, cursor + size));
            cursor += size;
        }
        return List.copyOf(folds);
    }
}
