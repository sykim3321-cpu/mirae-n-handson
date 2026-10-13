# spec-project (Java · Spring Boot 시작 골격)

Day 4-2 spec-driven 실습용 빈 골격입니다. 스펙의 기능은 하나도 들어 있지 않고, 서버가 뜨는지 확인하는 `GET /health` 와 그 테스트 하나만 있습니다.

- Spring Boot 3.3 · Java 21 · Gradle(래퍼 포함)
- 저장: H2 메모리 DB. 외부 DB나 Docker 없이 돕니다. 서버를 끄면 데이터가 사라집니다.

## 명령

| 할 일 | 명령 |
|---|---|
| 처음 한 번 준비 | 없음 |
| 테스트 (검증 명령) | `./gradlew test` |
| 서버 실행 | `./gradlew bootRun` → `http://localhost:8080` |

서버를 띄운 뒤 다른 터미널에서 확인합니다. `200` 과 `{"status":"ok"}` 가 나오면 됩니다.

```
curl -i http://localhost:8080/health
```

## 구조

```
build.gradle                         의존성 · bootRun 설정
src/main/resources/application.yml   H2 메모리 DB · 포트 8080
src/main/java/com/example/specproject/
  SpecProjectApplication.java        진입점
  HealthController.java              GET /health
  LocalSeedData.java                 로컬 확인용 시드 데이터(아래 참고)
src/test/java/com/example/specproject/
  HealthControllerTest.java          GET /health 가 200 인지 확인
```

새 코드는 `com.example.specproject` 아래에 둡니다. 테이블은 `@Entity` 클래스로 만들고(`ddl-auto: create-drop`), 스키마 파일은 따로 두지 않습니다.

## 시드 데이터

로컬에서 직접 호출해 볼 더미 데이터는 **`LocalSeedData.java` 의 `run()` 안에** 넣습니다. 이 골격이 정한 시드 방식은 이것 하나입니다.

- `./gradlew bootRun` 은 `local` 프로필로 뜨고, `LocalSeedData` 는 `local` 프로필에서만 실행됩니다. 서버가 시작되면 로그에 `local 프로필: 시드 데이터 적재 완료` 가 한 줄 나옵니다.
- `./gradlew test` 에서는 실행되지 않습니다. 테스트에 필요한 데이터는 테스트 안에서 만듭니다.
- 실제 학교 · 학생 이름은 쓰지 않습니다.

## 안 될 때

- `./gradlew: Permission denied` → `chmod +x gradlew`
- `Port 8080 was already in use` → 다른 서버(실습 저장소의 `modern/api` 등)가 떠 있습니다. 그 터미널에서 `Ctrl` + `C` 로 끄고 다시 실행합니다.
