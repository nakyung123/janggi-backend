package com.nakyung.janggi.events.common.guard;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 문지기의 숫자.
 *
 * @param limitPerMinute 1분에 받는 최대 건수. 실제 쓰임은 한 시간에 한 건꼴이라 한참
 *     여유가 있고, 사람이 갑자기 몰려도 버티는 선으로 잡았다. 무료 한도(한 달 180,000
 *     vCPU-초)와 맞춰 둔 숫자다 - 더 올리면 몰아칠 때 한도를 넘길 수 있다.
 * @param maxBodyBytes 받는 최대 본문 크기. 50건 묶음이 넉넉히 들어가는 크기다.
 */
@ConfigurationProperties("janggi.guard")
public record GuardProperties(int limitPerMinute, long maxBodyBytes) {

    public GuardProperties {
        if (limitPerMinute < 1) {
            throw new IllegalArgumentException("limitPerMinute 는 1 이상이어야 합니다: " + limitPerMinute);
        }
        if (maxBodyBytes < 1) {
            throw new IllegalArgumentException("maxBodyBytes 는 1 이상이어야 합니다: " + maxBodyBytes);
        }
    }
}
