package com.nakyung.janggi.common.guard;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 문지기의 숫자.
 *
 * @param limitPerMinute 1분에 받는 최대 건수. 실제 쓰임은 한 시간에 한 건꼴이라 한참
 *     여유가 있고, 사람이 갑자기 몰려도 버티는 선으로 잡았다. 무료 한도(한 달 180,000
 *     vCPU-초)와 맞춰 둔 숫자다 - 더 올리면 몰아칠 때 한도를 넘길 수 있다.
 * @param maxBodyBytes 받는 최대 본문 크기. 50건 묶음이 넉넉히 들어가는 크기다.
 * @param gameLimitPerMinute 기보 주소에서 1분에 받는 최대 건수. 측정과 <b>따로 센다</b> -
 *     같이 세면 기보를 올리는 것이 측정 받을 몫을 깎아먹는다. 사람이 한 판 두는 데 몇 분은
 *     걸리니 1분에 서른 번이면 넉넉하고, 몰아치는 쪽에는 그만큼이 벽이 된다.
 * @param maxGameBytes 기보 하나의 최대 본문 크기. 복기까지 돌린 100수 판이 4만 자쯤이고
 *     (`archive.ts` 주석), 국면 상한(600)까지 꽉 찬 판을 생각해 넉넉히 잡았다. <b>한도가
 *     없으면 10MB 를 500번 올려 무료 저장 1GB 를 한 사람이 채울 수 있다.</b>
 */
@ConfigurationProperties("janggi.guard")
public record GuardProperties(
        int limitPerMinute, long maxBodyBytes, int gameLimitPerMinute, long maxGameBytes) {

    public GuardProperties {
        if (limitPerMinute < 1) {
            throw new IllegalArgumentException("limitPerMinute 는 1 이상이어야 합니다: " + limitPerMinute);
        }
        if (maxBodyBytes < 1) {
            throw new IllegalArgumentException("maxBodyBytes 는 1 이상이어야 합니다: " + maxBodyBytes);
        }
        if (gameLimitPerMinute < 1) {
            throw new IllegalArgumentException(
                    "gameLimitPerMinute 는 1 이상이어야 합니다: " + gameLimitPerMinute);
        }
        if (maxGameBytes < 1) {
            throw new IllegalArgumentException("maxGameBytes 는 1 이상이어야 합니다: " + maxGameBytes);
        }
    }
}
