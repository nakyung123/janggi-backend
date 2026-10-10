package com.nakyung.janggi.events;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 측정 수집 서버를 켠다.
 *
 * <p>{@code @ConfigurationPropertiesScan} 이 없으면 {@code @ConfigurationProperties} 를
 * 붙인 레코드가 빈으로 올라오지 않는다. 설정이 안 읽히는 게 아니라 <b>주입할 것이 없어서
 * 시작 자체가 실패</b>하므로, 조용히 기본값으로 도는 일은 없다.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class JanggiEventsApplication {

    public static void main(String[] args) {
        SpringApplication.run(JanggiEventsApplication.class, args);
    }
}
