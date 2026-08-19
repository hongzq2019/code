# Task 2: Organize backend authentication, administration, and domain code

## Goal

Replace anonymous request/response payloads in authentication and administration endpoints with explicit public records, split compressed Java into conventional readable layout, and extract small pure helpers while preserving every existing behavior and external API field/path.

## Files

- Create `backend/src/main/java/com/dlt/predictor/auth/dto/CredentialsRequest.java`.
- Create `backend/src/main/java/com/dlt/predictor/auth/dto/LoginResponse.java`.
- Create `backend/src/main/java/com/dlt/predictor/auth/dto/TokenResponse.java`.
- Create `backend/src/main/java/com/dlt/predictor/auth/dto/RegistrationResponse.java`.
- Create `backend/src/main/java/com/dlt/predictor/admin/dto/ApprovalRequest.java`.
- Create `backend/src/main/java/com/dlt/predictor/admin/dto/ImportDrawRequest.java`.
- Create `backend/src/main/java/com/dlt/predictor/admin/dto/SyncResponse.java`.
- Create `backend/src/test/java/com/dlt/predictor/auth/AuthServiceTest.java`.
- Modify `backend/src/main/java/com/dlt/predictor/auth/AuthController.java`.
- Modify `backend/src/main/java/com/dlt/predictor/auth/AuthService.java`.
- Modify `backend/src/main/java/com/dlt/predictor/auth/UserAccount.java`.
- Modify `backend/src/main/java/com/dlt/predictor/admin/AdminController.java`.
- Modify `backend/src/main/java/com/dlt/predictor/lottery/DltNumbers.java`.
- Modify `backend/src/main/java/com/dlt/predictor/lottery/DrawStore.java`.
- Modify `backend/src/main/java/com/dlt/predictor/prediction/FeatureEncoder.java`.
- Modify `backend/src/main/java/com/dlt/predictor/prediction/RecommendationEngine.java`.
- Format any directly touched companion Java record/controller only if required for a compiling import move; do not expand task scope.

## Exact DTO interfaces

```java
public record CredentialsRequest(
        @NotBlank @Size(min = 3, max = 40) String username,
        @NotBlank @Size(min = 6, max = 72) String password) { }

public record LoginResponse(String accessToken, String role, boolean mustChangePassword) { }
public record TokenResponse(String accessToken) { }
public record RegistrationResponse(String message) { }
public record ApprovalRequest(boolean approved) { }
public record ImportDrawRequest(
        @NotBlank String issueNo,
        @NotNull LocalDate drawDate,
        @NotNull List<Integer> frontNumbers,
        @NotNull List<Integer> backNumbers) { }
public record SyncResponse(String result, int insertedCount, String message) { }
```

## Required characterization test

Create `AuthServiceTest` in package `com.dlt.predictor.auth`. It must execute this exact behavior and it should be green before structural extraction:

```java
@Test
void registersPendingUserThenAllowsLoginAfterApproval() {
    AuthService service = new AuthService();
    service.register("reader", "password1");

    assertThrows(IllegalArgumentException.class, () -> service.login("reader", "password1"));

    service.approve("reader", true);

    assertEquals("reader", service.login("reader", "password1").username());
}
```

Run `mvn test -q -Dtest=AuthServiceTest` before refactoring and record the green result. Then run it again, followed by `mvn test` and `mvn -q -DskipTests package` after all changes.

## Compatibility constraints

- Preserve paths `/api/v1/auth/register`, `/login`, `/refresh`, `/logout`, `/api/v1/admin/users`, `/users/{username}/approval`, `/dlt/sync`, `/dlt/import`.
- Preserve every external JSON property name and value used now: registration `data.message`; login `data.accessToken`, `data.role`, `data.mustChangePassword`; refresh `data.accessToken`; approval `approved`; import `issueNo`, `drawDate`, `frontNumbers`, `backNumbers`; sync `result`, `insertedCount`, `message`.
- The `pending` endpoint must preserve current observable data shape; do not alter user status/approval behavior or BCrypt password behavior.
- The bootstrap admin remains `admin`, gets password from `BOOTSTRAP_ADMIN_PASSWORD` defaulting to `admin`, role `ADMIN`, status `APPROVED`, and must change password flag `true`.
- `DltNumbers` keeps its range, count, distinctness, sorting, and zero-padded text rules exactly.
- `FeatureEncoder` keeps 47 dimensions, indexes 0..34 for front and 35..46 for back, and L2 per-zone values `1/sqrt(5)` and `1/sqrt(2)` exactly.
- `RecommendationEngine` retains `similarity * exp(-max(0, ageDays) / 730.0)`, descending score then ascending number ordering, `mvp-1.0`, and the exact Chinese disclaimer string.
- Do not change database configuration, Flyway, Docker or front-end files.

## Formatting

Use conventional Java package/import blocks, one annotation/member per readable line, explicit access modifiers where useful, constructor injection, descriptive names, and small private pure helpers. Do not use one-line classes or bare `Map` HTTP payloads in the touched controllers.
