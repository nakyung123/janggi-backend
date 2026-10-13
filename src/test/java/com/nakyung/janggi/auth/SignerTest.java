package com.nakyung.janggi.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 서명이 <b>틀린 것을 틀렸다고 하는지</b> 본다.
 *
 * <p>맞는 것을 통과시키는 검사만 있으면 <b>늘 통과시키는 서명</b>도 같이 통과한다.
 * 그래서 고쳐 보낸 값, 서명만 바꾼 값, 다른 열쇠로 서명한 값을 하나씩 본다.
 *
 * <p>스프링을 띄우지 않는다. 여기서 보는 것은 수학이라 컨텍스트가 보태는 것이 없다.
 */
class SignerTest {

    private final Signer 서명자 = new Signer("열쇠-하나-1234567890");

    @Test
    @DisplayName("붙였다 떼면 그대로 돌아온다")
    void 왕복() {
        String 값 = "user=7&epoch=1";
        assertThat(서명자.떼기(서명자.붙이기(값))).isEqualTo(값);
    }

    @Test
    @DisplayName("값을 고치면 null - 이게 안 되면 쿠키를 믿을 수 없다")
    void 값을_고치면() {
        String 붙인것 = 서명자.붙이기("user=7&epoch=1");
        String 고친것 = 붙인것.replace("user=7", "user=8");

        assertThat(고친것).isNotEqualTo(붙인것);
        assertThat(서명자.떼기(고친것)).isNull();
    }

    @Test
    @DisplayName("서명만 바꿔도 null")
    void 서명을_고치면() {
        String 붙인것 = 서명자.붙이기("값");
        String 고친것 = 붙인것.substring(0, 붙인것.length() - 1) + "X";

        assertThat(서명자.떼기(고친것)).isNull();
    }

    @Test
    @DisplayName("다른 열쇠로 서명한 것은 안 받는다 - 열쇠를 바꾸면 옛 쿠키가 다 죽는다")
    void 다른_열쇠() {
        Signer 남 = new Signer("열쇠-다른것-0987654321");

        assertThat(서명자.떼기(남.붙이기("값"))).isNull();
    }

    @Test
    @DisplayName("서명이 아예 없으면 null - 구분자를 지워 통과시킬 수 없다")
    void 서명이_없으면() {
        assertThat(서명자.떼기("값만있음")).isNull();
        assertThat(서명자.떼기("값만있음.")).isNull();
        assertThat(서명자.떼기(".서명만있음")).isNull();
        assertThat(서명자.떼기(null)).isNull();
    }

    @Test
    @DisplayName("값에 구분자가 들어 있어도 된다 - 마지막 점을 기준으로 가른다")
    void 값에_점이_있어도() {
        String 값 = "a.b.c";
        assertThat(서명자.떼기(서명자.붙이기(값))).isEqualTo(값);
    }

    @Test
    @DisplayName("열쇠가 없으면 뜨지 않는다 - 서명 없이 통과하는 쪽으로 기울지 않는다")
    void 열쇠가_없으면() {
        assertThatThrownBy(() -> new Signer("  "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JANGGI_TOKEN_SECRET");
    }
}
