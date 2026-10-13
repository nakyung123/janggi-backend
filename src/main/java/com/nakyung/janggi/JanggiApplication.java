package com.nakyung.janggi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;

/**
 * 측정 수집 서버를 켠다.
 *
 * <p>{@code @ConfigurationPropertiesScan} 이 없으면 {@code @ConfigurationProperties} 를
 * 붙인 레코드가 빈으로 올라오지 않는다. 설정이 안 읽히는 게 아니라 <b>주입할 것이 없어서
 * 시작 자체가 실패</b>하므로, 조용히 기본값으로 도는 일은 없다.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class JanggiApplication {

    private static final Logger log = LoggerFactory.getLogger(JanggiApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(JanggiApplication.class, args);
    }

    /**
     * 뜰 때 <b>무엇이 돌고 있는지</b> 한 줄 적는다.
     *
     * <p>이게 없으면 "지금 실서버가 어느 코드인가" 에 답하려고 Cloud Run 리비전 →
     * 이미지 digest → Artifact Registry 태그로 <b>세 번 건너뛰어야</b> 한다. 로그 한
     * 줄이면 검색으로 끝난다. 쉬다 깨어날 때마다 한 번씩 찍히는데, 그래서 오히려
     * <b>언제 깨어났는지</b>도 같이 보인다.
     *
     * <p>{@code /actuator/info} 로 열지 않는다. 그 주소는 공개라 아무나 볼 수 있고,
     * 바깥에 열어 둔 것은 상태 확인 하나뿐이라는 규칙을 깨고 싶지 않다. 이 줄은
     * Cloud Logging 안에만 있다.
     *
     * <p>커밋은 {@code JANGGI_COMMIT} 환경 변수로 들어온다. CI 가 이미지를 지을 때
     * 넣어 주고, 손으로 지으면 {@code unknown} 이다 - <b>모르는 것을 아는 척하지 않는다.</b>
     *
     * <p>{@code ObjectProvider} 로 받는 까닭 - {@code build-info.properties} 는 그래들이
     * 만든다. IDE 에서 바로 돌리면 없을 수 있는데, 그것 때문에 서버가 안 뜨면 안 된다.
     */
    @Bean
    ApplicationRunner 뜰때_무엇인지_적기(ObjectProvider<BuildProperties> 빌드) {
        return args -> {
            BuildProperties b = 빌드.getIfAvailable();
            String 커밋 = System.getenv().getOrDefault("JANGGI_COMMIT", "unknown");
            log.info(
                    "janggi-server 떴습니다. 판 {} · 커밋 {} · 지은 때 {}",
                    b == null ? "unknown" : b.getVersion(),
                    커밋,
                    b == null ? "unknown" : b.getTime());
        };
    }
}
