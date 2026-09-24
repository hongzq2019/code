# Decayed Frequency Prediction Model Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a fourth next-draw model that recommends front and back numbers by exponentially decayed occurrence probability across all historical draws, with a 200-draw half-life.

**Architecture:** A focused calculator produces complete 35/12 probability maps, and a trainer backtests it without future leakage before persisting immutable probability maps in the existing model batch table. The atomic model batch expands from three to four types; the prediction API returns the fourth result while the existing dynamic ensemble remains logistic-plus-vector only. The Vue selector and administrator training summary gain one option/row without changing the default model.

**Tech Stack:** Java 21, Spring Boot 3.4, Spring Data JPA, Flyway, Jackson, JUnit 5/AssertJ/Mockito, Vue 3, TypeScript, Element Plus, Maven, Vite

**Spec:** `docs/superpowers/specs/2026-09-24-decayed-frequency-model-design.md`

## Global Constraints

- Model type is exactly `DECAYED_FREQUENCY`; algorithm version is exactly `decayed-frequency-v1`.
- Use every chronological PostgreSQL draw and `weight(age) = 0.5 ^ (age / 200)` with fixed half-life `200` draws.
- Front 1–35 and back 1–12 are scored independently; select 5/2 by probability descending and number ascending on ties.
- Backtests for target index `t` may use only draws in `[0, t)`.
- Persist complete production probability maps in an artifact with `artifactVersion = 1`; inference must not recompute them from newer data.
- A valid active batch contains exactly four model types with one shared batch version and waterline.
- Dynamic ensemble remains a two-component logistic/vector ensemble and stays the frontend default.
- Do not stage or overwrite the user's existing `backend/src/main/resources/application.yml` change.

## Review Focus

- Empty draw history must throw `ModelUnavailableException`, not return zero or random scores; Task 1 pins this behavior.
- A draw at the target index or later must never affect a historical validation prediction; Task 1 mutates future records and compares every score.
- Artifact maps with a missing candidate, NaN/infinite value, or value outside `[0,1]` must make prediction return model-unavailable; Task 3 tests all invalid classes.
- An existing three-model active batch after migration is intentionally incomplete and must not be mixed with a new model; Task 2 verifies `activeBatch()` is empty until all four matching types exist.
- Equal probabilities must select lower number values deterministically in both zones; Task 1 verifies exact 5/2 ordering.

---

### Task 1: Implement decayed probability calculation and leakage-free training

**Files:**
- Create: `backend/src/main/java/com/dlt/predictor/prediction/model/DecayedFrequencyCalculator.java`
- Create: `backend/src/main/java/com/dlt/predictor/prediction/model/DecayedFrequencyArtifact.java`
- Create: `backend/src/main/java/com/dlt/predictor/prediction/model/TrainedDecayedFrequencyModel.java`
- Create: `backend/src/main/java/com/dlt/predictor/prediction/model/DecayedFrequencyModelTrainer.java`
- Test: `backend/src/test/java/com/dlt/predictor/prediction/model/DecayedFrequencyCalculatorTest.java`
- Test: `backend/src/test/java/com/dlt/predictor/prediction/model/DecayedFrequencyModelTrainerTest.java`

**Interfaces:**
- Consumes: `Draw`, `NumberZone`, `CandidateScores`, `RollingFold`, `RollingPredictionSet`, and `PredictionMetricsCalculator.calculate`.
- Produces: `DecayedFrequencyCalculator.score(List<Draw>, NumberZone, int): CandidateScores`.
- Produces: `DecayedFrequencyCalculator.weight(int, int): double` for package-level deterministic verification.
- Produces: `DecayedFrequencyModelTrainer.train(List<Draw>, List<RollingFold>): TrainedDecayedFrequencyModel`.

- [ ] **Step 1: Write calculator tests for half-life, complete maps, stable ties, and empty history**

```java
class DecayedFrequencyCalculatorTest {
    private final DecayedFrequencyCalculator calculator = new DecayedFrequencyCalculator();

    @Test
    void usesTwoHundredDrawHalfLifeAndReturnsEveryCandidate() {
        assertThat(calculator.weight(0, 200)).isEqualTo(1.0);
        assertThat(calculator.weight(200, 200)).isCloseTo(0.5, within(1.0e-12));

        CandidateScores front = calculator.score(draws(201), NumberZone.FRONT, 200);
        CandidateScores back = calculator.score(draws(201), NumberZone.BACK, 200);

        assertThat(front.values()).hasSize(35).containsKeys(1, 35);
        assertThat(back.values()).hasSize(12).containsKeys(1, 12);
    }

    @Test
    void rejectsEmptyHistoryAndOrdersEqualProbabilitiesByNumber() {
        assertThatThrownBy(() -> calculator.score(List.of(), NumberZone.FRONT, 200))
                .isInstanceOf(ModelUnavailableException.class);
        CandidateScores scores = calculator.score(equalFrequencyDraws(), NumberZone.FRONT, 200);
        assertThat(PredictionMetricsCalculator.topNumbers(scores.values(), 5))
                .containsExactly(1, 2, 3, 4, 5);
    }
}
```

- [ ] **Step 2: Run calculator tests and verify the red state**

Run: `cd backend; mvn -Dtest=DecayedFrequencyCalculatorTest test`

Expected: test compilation fails because `DecayedFrequencyCalculator` does not exist.

- [ ] **Step 3: Implement the calculator with normalized per-draw weights**

```java
public final class DecayedFrequencyCalculator {
    public CandidateScores score(List<Draw> history, NumberZone zone, int halfLifeDraws) {
        if (history.isEmpty()) {
            throw new ModelUnavailableException("没有可用于历史概率统计的开奖记录");
        }
        if (halfLifeDraws <= 0) {
            throw new IllegalArgumentException("半衰期必须大于 0");
        }
        Map<Integer, Double> weightedHits = new LinkedHashMap<>();
        IntStream.rangeClosed(1, zone.candidateCount()).forEach(number -> weightedHits.put(number, 0.0));
        double totalWeight = 0;
        for (int index = history.size() - 1; index >= 0; index--) {
            int age = history.size() - 1 - index;
            double drawWeight = weight(age, halfLifeDraws);
            totalWeight += drawWeight;
            List<Integer> numbers = zone == NumberZone.FRONT
                    ? history.get(index).numbers().front()
                    : history.get(index).numbers().back();
            numbers.forEach(number -> weightedHits.merge(number, drawWeight, Double::sum));
        }
        double divisor = totalWeight;
        weightedHits.replaceAll((number, hits) -> hits / divisor);
        return new CandidateScores(weightedHits);
    }

    double weight(int age, int halfLifeDraws) {
        return Math.pow(0.5, age / (double) halfLifeDraws);
    }
}
```

- [ ] **Step 4: Run calculator tests and verify they pass**

Run: `cd backend; mvn -Dtest=DecayedFrequencyCalculatorTest test`

Expected: PASS, including exact candidate counts and half-life tolerance.

- [ ] **Step 5: Write trainer tests for future isolation and production artifact contents**

```java
@Test
void targetPredictionUsesOnlyEarlierDrawsAndArtifactContainsProductionScores() {
    List<Draw> original = draws(100);
    List<RollingFold> folds = new RollingFoldBuilder().build(100, TrainingDatasetBuilder.FEATURE_WINDOW);
    TrainedDecayedFrequencyModel first = new DecayedFrequencyModelTrainer().train(original, folds);

    List<Draw> mutated = new ArrayList<>(original);
    mutated.set(99, draw(99, List.of(31, 32, 33, 34, 35), List.of(11, 12)));
    TrainedDecayedFrequencyModel second = new DecayedFrequencyModelTrainer().train(mutated, folds);

    RollingPredictionSet.PeriodPrediction beforeTarget = first.validationPredictions().periods().getFirst();
    RollingPredictionSet.PeriodPrediction afterMutation = second.validationPredictions().periods().getFirst();
    assertThat(afterMutation.front().values()).isEqualTo(beforeTarget.front().values());
    assertThat(afterMutation.back().values()).isEqualTo(beforeTarget.back().values());
    assertThat(first.artifact().artifactVersion()).isEqualTo(1);
    assertThat(first.artifact().halfLifeDraws()).isEqualTo(200);
    assertThat(first.artifact().frontProbabilities()).hasSize(35);
    assertThat(first.artifact().backProbabilities()).hasSize(12);
}
```

- [ ] **Step 6: Implement artifact, trained result, and fold trainer**

```java
public record DecayedFrequencyArtifact(
        int artifactVersion,
        int halfLifeDraws,
        Map<Integer, Double> frontProbabilities,
        Map<Integer, Double> backProbabilities) {
}

public record TrainedDecayedFrequencyModel(
        DecayedFrequencyArtifact artifact,
        RollingPredictionSet validationPredictions,
        ModelMetrics metrics,
        int sampleCount) {
}
```

`DecayedFrequencyModelTrainer` must use `HALF_LIFE_DRAWS = 200`, score `draws.subList(0, target)` for every validation target, compute metrics with `PredictionMetricsCalculator.calculate`, then score the full draw list for the persisted artifact. Set `sampleCount` to `validationPeriodCount * 47`.

- [ ] **Step 7: Run focused model tests**

Run: `cd backend; mvn -Dtest=DecayedFrequencyCalculatorTest,DecayedFrequencyModelTrainerTest test`

Expected: PASS with no future leakage and complete 35/12 production probabilities.

- [ ] **Step 8: Commit the independent algorithm**

```powershell
git add backend/src/main/java/com/dlt/predictor/prediction/model backend/src/test/java/com/dlt/predictor/prediction/model
git commit -m "feat: add decayed frequency prediction model"
```

### Task 2: Expand persistence and training coordination to four-model batches

**Files:**
- Modify: `backend/src/main/java/com/dlt/predictor/prediction/model/PredictionModelType.java`
- Modify: `backend/src/main/java/com/dlt/predictor/prediction/model/PredictionModelStore.java`
- Modify: `backend/src/main/java/com/dlt/predictor/prediction/model/ModelTrainingCoordinator.java`
- Create: `backend/src/main/resources/db/migration/V4__add_decayed_frequency_model_type.sql`
- Modify: `backend/src/main/resources/db/complete_schema_and_seed.sql`
- Modify: `backend/src/test/java/com/dlt/predictor/prediction/model/PredictionModelStoreTest.java`
- Create: `backend/src/test/java/com/dlt/predictor/prediction/model/ModelTrainingCoordinatorTest.java`

**Interfaces:**
- Consumes: `DecayedFrequencyModelTrainer.train` and Jackson serialization of `DecayedFrequencyArtifact`.
- Produces: complete active batches containing all four `PredictionModelType` values.
- Produces: coordinator result lists with four `ModelTrainingResult` entries sharing one batch and waterline.

- [ ] **Step 1: Update store tests to require four matching types**

```java
assertThat(store.activeBatch()).isEmpty(); // repository contains only the three legacy active rows

List<ModelSnapshot> complete = List.of(logistic, vector, ensemble, decayedFrequency);
when(repository.findByStatusOrderByModelTypeAsc(ACTIVE)).thenReturn(entities(complete));
assertThat(store.activeBatch()).contains(complete);

assertThatThrownBy(() -> store.activateBatch(List.of(logistic, vector, ensemble)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("四种模型");
```

- [ ] **Step 2: Write coordinator test for a single four-model activation**

Define the test against this package-private constructor, which Step 5 will add while the existing Spring constructor remains the production entry point:

```java
ModelTrainingCoordinator(
        DrawStore drawStore,
        PredictionModelStore modelStore,
        ObjectMapper objectMapper,
        TaskExecutor taskExecutor,
        NextDrawModelTrainer logisticTrainer,
        VectorTransitionModelTrainer vectorTrainer,
        DynamicEnsembleTrainer ensembleTrainer,
        DecayedFrequencyModelTrainer decayedTrainer)
```

Mock `DrawStore.allChronological()` with 100 draws. Stub each trainer's `train` method with deterministic trained results, capture the list passed to `PredictionModelStore.activateBatch`, and assert:

```java
assertThat(candidates).extracting(ModelSnapshot::type)
        .containsExactlyInAnyOrder(
                LOGISTIC_REGRESSION, VECTOR_TRANSITION, DYNAMIC_ENSEMBLE, DECAYED_FREQUENCY);
assertThat(candidates).extracting(ModelSnapshot::batchVersion).containsOnly(candidates.getFirst().batchVersion());
assertThat(candidates).extracting(ModelSnapshot::trainedThroughDrawId).containsOnly(latestDraw.id());
verify(modelStore, times(1)).activateBatch(candidates);
```

- [ ] **Step 3: Run persistence/coordinator tests and verify the red state**

Run: `cd backend; mvn -Dtest=PredictionModelStoreTest,ModelTrainingCoordinatorTest test`

Expected: FAIL because the enum and coordinator still produce only three models.

- [ ] **Step 4: Add the fourth enum and database migration**

```java
public enum PredictionModelType {
    LOGISTIC_REGRESSION,
    VECTOR_TRANSITION,
    DYNAMIC_ENSEMBLE,
    DECAYED_FREQUENCY
}
```

```sql
ALTER TABLE prediction_model
    DROP CONSTRAINT ck_prediction_model_type;

ALTER TABLE prediction_model
    ADD CONSTRAINT ck_prediction_model_type CHECK (
        model_type IN (
            'LOGISTIC_REGRESSION',
            'VECTOR_TRANSITION',
            'DYNAMIC_ENSEMBLE',
            'DECAYED_FREQUENCY'
        )
    );
```

Apply the identical four-value check to `complete_schema_and_seed.sql`. Do not modify existing rows.

- [ ] **Step 5: Update batch validation wording and coordinator candidates**

Keep `PredictionModelStore` enum-driven size/type validation, but change its exception text to “必须提供同一批次、同一训练水位的四种模型”. In `ModelTrainingCoordinator`, train `DecayedFrequencyModelTrainer` after vector training, serialize its artifact, and append:

```java
snapshot(
    PredictionModelType.DECAYED_FREQUENCY,
    "decayed-frequency-" + batchVersion,
    "decayed-frequency-v1",
    batchVersion,
    serialize(decayed.artifact()),
    0,
    draws.size(),
    decayed.sampleCount(),
    waterline,
    trainedAt,
    decayed.metrics())
```

Do not change `ensemble.eligible()` activation gating. A trainer or serialization exception must return `FAILED` before `activateBatch` is called.

Store the four trainers in final fields. Annotate the existing four-dependency Spring constructor with `@Autowired` and delegate to the package-private eight-dependency constructor using `new NextDrawModelTrainer()`, `new VectorTransitionModelTrainer()`, `new DynamicEnsembleTrainer()`, and `new DecayedFrequencyModelTrainer()`. The package-private constructor assigns the supplied collaborators so the coordinator lifecycle test does not execute numerical training.

- [ ] **Step 6: Run focused and full backend tests**

Run: `cd backend; mvn -Dtest=PredictionModelStoreTest,ModelTrainingCoordinatorTest test`

Expected: PASS; legacy three-row batch is incomplete, complete four-row batch activates once.

Run: `cd backend; mvn test`

Expected: all backend tests PASS.

- [ ] **Step 7: Commit four-model persistence and training**

```powershell
git add backend/src/main/java/com/dlt/predictor/prediction/model backend/src/main/resources/db backend/src/test/java/com/dlt/predictor/prediction/model
git commit -m "feat: train four-model prediction batches"
```

### Task 3: Serve and validate the decayed-frequency prediction

**Files:**
- Modify: `backend/src/main/java/com/dlt/predictor/prediction/model/NextDrawPredictionService.java`
- Create: `backend/src/test/java/com/dlt/predictor/prediction/model/NextDrawPredictionServiceTest.java`
- Modify: `backend/src/test/java/com/dlt/predictor/prediction/dto/PredictionResponseTest.java`

**Interfaces:**
- Consumes: active four-model batch and `DecayedFrequencyArtifact` JSON.
- Produces: `PredictionResponse.predictions()` ordered logistic, vector, dynamic ensemble, decayed frequency.
- Produces: fourth `ModelPredictionResponse` named `历史衰减概率` with `ensembleWeights == null`.

- [ ] **Step 1: Write response-order and artifact-validation tests**

Construct a complete active batch and stub existing logistic/vector inference inputs. Assert:

```java
PredictionResponse response = service.predict();
assertThat(response.predictions()).extracting(ModelPredictionResponse::type)
        .containsExactly(
                LOGISTIC_REGRESSION,
                VECTOR_TRANSITION,
                DYNAMIC_ENSEMBLE,
                DECAYED_FREQUENCY);
ModelPredictionResponse probability = response.predictions().get(3);
assertThat(probability.name()).isEqualTo("历史衰减概率");
assertThat(probability.numbers().front()).hasSize(5).isSorted();
assertThat(probability.numbers().back()).hasSize(2).isSorted();
assertThat(probability.frontScores()).hasSize(35);
assertThat(probability.backScores()).hasSize(12);
assertThat(probability.ensembleWeights()).isNull();
```

Add parameterized invalid artifacts for: missing key `35`, `Double.NaN`, `Double.POSITIVE_INFINITY`, `-0.01`, and `1.01`. Every case must throw `ModelUnavailableException` with a message containing `历史衰减概率模型参数`.

- [ ] **Step 2: Run prediction tests and verify the red state**

Run: `cd backend; mvn -Dtest=NextDrawPredictionServiceTest,PredictionResponseTest test`

Expected: FAIL because inference does not decode or return `DECAYED_FREQUENCY`.

- [ ] **Step 3: Decode and validate the fourth artifact**

Add a focused validator:

```java
private static void validateDecayedFrequency(DecayedFrequencyArtifact artifact) {
    if (artifact.artifactVersion() != 1 || artifact.halfLifeDraws() != 200
            || !isCompleteProbabilityMap(artifact.frontProbabilities(), 35)
            || !isCompleteProbabilityMap(artifact.backProbabilities(), 12)) {
        throw new ModelUnavailableException("历史衰减概率模型参数已损坏，请重新训练");
    }
}

private static boolean isCompleteProbabilityMap(Map<Integer, Double> values, int count) {
    return values != null && values.size() == count
            && IntStream.rangeClosed(1, count).allMatch(values::containsKey)
            && values.values().stream().allMatch(value -> value != null
                    && Double.isFinite(value) && value >= 0.0 && value <= 1.0);
}
```

Decode `DecayedFrequencyArtifact` from the fourth snapshot, wrap its maps in `CandidateScores`, and append its response after dynamic ensemble. Continue validating the ensemble's exact logistic/vector model version references; do not add the fourth model to `EnsembleWeightsResponse`.

- [ ] **Step 4: Run focused and full backend verification**

Run: `cd backend; mvn -Dtest=NextDrawPredictionServiceTest,PredictionResponseTest test`

Expected: PASS, including all corrupt-artifact cases.

Run: `cd backend; mvn test`

Expected: all backend tests PASS.

- [ ] **Step 5: Commit the fourth prediction response**

```powershell
git add backend/src/main/java/com/dlt/predictor/prediction/model/NextDrawPredictionService.java backend/src/test/java/com/dlt/predictor/prediction
git commit -m "feat: serve decayed frequency prediction"
```

### Task 4: Add the fourth model to the Vue selector and training summary

**Files:**
- Modify: `forend/src/types/prediction.ts`
- Modify: `forend/src/views/Predict.vue`
- Modify: `forend/src/views/AdminSync.vue`

**Interfaces:**
- Consumes: API model type `DECAYED_FREQUENCY` and the existing `ModelPrediction` shape.
- Produces: prediction selector option `历史衰减概率` while preserving `DYNAMIC_ENSEMBLE` as the default.
- Produces: administrator model-name mapping and fourth summary row.

- [ ] **Step 1: Extend the TypeScript model union**

```ts
export type PredictionModelType =
  | 'LOGISTIC_REGRESSION'
  | 'VECTOR_TRANSITION'
  | 'DYNAMIC_ENSEMBLE'
  | 'DECAYED_FREQUENCY'
```

- [ ] **Step 2: Add selector and administrator labels**

Append without changing the first/default option:

```ts
const modelOptions = [
  { value: 'DYNAMIC_ENSEMBLE', label: '动态集成（主推荐）' },
  { value: 'LOGISTIC_REGRESSION', label: '逻辑回归' },
  { value: 'VECTOR_TRANSITION', label: '向量转移' },
  { value: 'DECAYED_FREQUENCY', label: '历史衰减概率' },
]
```

Add `DECAYED_FREQUENCY: '历史衰减概率'` to `AdminSync.vue`'s `modelNames`. Keep `selectedModel` initialized to `DYNAMIC_ENSEMBLE`; do not add a new request when selection changes.

- [ ] **Step 3: Run frontend static verification**

Run: `cd forend; npm run type-check`

Expected: PASS without TypeScript or Vue template errors.

Run: `cd forend; npm run build`

Expected: PASS and Vite emits production assets.

- [ ] **Step 4: Commit the frontend option**

```powershell
git add forend/src/types/prediction.ts forend/src/views/Predict.vue forend/src/views/AdminSync.vue
git commit -m "feat: select decayed frequency prediction"
```

### Task 5: Complete migration, build, and scope verification

**Files:**
- Modify only if verification exposes a defect in files listed by Tasks 1–4.

**Interfaces:**
- Verifies: Flyway V4 migration, four-row batch consistency, API order, Maven artifact, and Vue production build.

- [ ] **Step 1: Verify migration and initialization scripts agree**

Run:

```powershell
rg "DECAYED_FREQUENCY|ck_prediction_model_type" backend/src/main/resources/db -n
```

Expected: V4 drops/recreates the constraint with four values, and `complete_schema_and_seed.sql` contains the identical four-value check.

- [ ] **Step 2: Run clean backend verification and package**

Run: `cd backend; mvn clean test`

Expected: BUILD SUCCESS with all tests passing.

Run: `cd backend; mvn -DskipTests package`

Expected: BUILD SUCCESS and `backend/target/dlt-predictor-api-1.0.0.jar` exists.

- [ ] **Step 3: Run clean frontend verification**

Run: `cd forend; npm run type-check`

Expected: exit code 0.

Run: `cd forend; npm run build`

Expected: exit code 0; the existing large-chunk warning is non-fatal.

- [ ] **Step 4: Inspect final scope and preserve the user configuration change**

Run:

```powershell
git diff --check
git status --short
git diff -- backend/src/main/resources/application.yml
```

Expected: no whitespace errors; the only unrelated working-tree change remains the user's `flyway.enabled: false`, and it is not staged or committed.

- [ ] **Step 5: Perform runtime smoke checks only with explicit environment approval**

With a disposable PostgreSQL/Milvus environment, run one manual retrain and assert four response entries share a batch/waterline; call prediction and assert four ordered results and no extra request on frontend selection. If no disposable environment is authorized, report this smoke check as not run rather than connecting to the configured database.

- [ ] **Step 6: Commit verification-only fixes if any**

If Steps 1–5 require a code correction, stage only that correction and commit:

```powershell
git commit -m "fix: complete decayed frequency integration"
```

If no correction is needed, do not create an empty commit.
