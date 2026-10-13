package com.example.specproject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 로컬 확인용 시드 데이터를 넣는 곳.
 *
 * <p>이 시작 골격이 정한 시드 방식은 이 클래스 하나다. {@code ./gradlew bootRun} 은 local 프로필로 뜨므로
 * 서버가 시작될 때 {@link #run} 이 한 번 실행된다. 테스트({@code ./gradlew test})에서는 실행되지 않는다.
 *
 * <p>더미 데이터가 필요해지면 저장소(Repository)를 생성자로 주입받아 {@link #run} 안에서 저장한다.
 * 실제 학교 · 학생 이름은 쓰지 않는다.
 */
@Component
@Profile("local")
public class LocalSeedData implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalSeedData.class);

    @Override
    public void run(ApplicationArguments args) {
        // 여기에 시드 데이터를 넣는다. 지금은 비어 있다.
        log.info("local 프로필: 시드 데이터 적재 완료");
    }
}
