package com.nakyung.janggi.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 쿠키에 담아 보낸 것이 <b>돌아올 때 그대로인지</b> 확인한다.
 *
 * <p><b>왜 서명이 필요한가</b> - 쿠키는 브라우저에 있고, 브라우저는 쓰는 사람의 것이다.
 * 거기 담긴 값은 언제든 고칠 수 있다. 그런데 우리는 거기에 "이 사람은 누구다" 와
 * "로그인을 시작할 때 내가 만든 임시값(state)" 을 담으려 한다. 고칠 수 있으면 둘 다
 * 아무 의미가 없다.
 *
 * <p><b>스프링이 쿠키 저장소를 안 만들어 준 까닭이 바로 이것이다.</b> 요청(gh-6374)이
 * 2021년에 거절됐고 사유가 "쿠키 변조 취약점이 몇 가지 있어 진행을 망설인다" 였다.
 * 그러니 직접 만들면서 <b>그 사유를 그대로 떠안는 것</b>이 된다 - 서명 없는 쿠키
 * 저장소는 만들면 안 되는 물건이다.
 *
 * <p><b>열쇠는 환경 변수로만 들어온다</b>({@code JANGGI_TOKEN_SECRET}). 코드에 박지
 * 않고, 비어 있으면 <b>서버가 아예 뜨지 않는다</b> - 깜빡했을 때 "서명 없이 그냥
 * 통과" 가 되면 막고 있는 척만 하게 된다.
 */
@Component
public class Signer {

    /** HMAC-SHA256. 자바가 늘 들고 있는 것이라 따로 받아올 것이 없다. */
    private static final String 방식 = "HmacSHA256";

    /** 값과 서명을 가르는 글자. base64url 에 안 나오는 글자여야 한다. */
    private static final char 구분 = '.';

    private final SecretKeySpec 열쇠;

    Signer(@Value("${janggi.token.secret:}") String 비밀) {
        if (!StringUtils.hasText(비밀)) {
            throw new IllegalStateException(
                    "JANGGI_TOKEN_SECRET 이 없습니다. 쿠키에 서명할 수 없으므로 뜨지 않습니다.");
        }
        this.열쇠 = new SecretKeySpec(비밀.getBytes(StandardCharsets.UTF_8), 방식);
    }

    /** 값 뒤에 서명을 붙여 돌려준다. */
    public String 붙이기(String 값) {
        return 값 + 구분 + 서명(값);
    }

    /**
     * 서명이 맞으면 값을, 아니면 {@code null} 을 돌려준다.
     *
     * <p><b>왜 예외가 아니라 {@code null} 인가</b> - 서명이 틀린 쿠키는 흔한 일이다.
     * 열쇠를 바꿨거나, 오래된 쿠키거나, 누가 장난을 쳤거나. 그때마다 예외를 던지면
     * 로그가 공격자가 쓰는 소음으로 가득 찬다. 쓰는 쪽에서는 "쿠키가 없는 것" 과
     * "쿠키가 틀린 것" 을 똑같이 다루면 되므로 모양을 하나로 둔다.
     */
    public String 떼기(String 서명된값) {
        if (서명된값 == null) {
            return null;
        }
        int 금 = 서명된값.lastIndexOf(구분);
        if (금 <= 0 || 금 == 서명된값.length() - 1) {
            return null;
        }
        String 값 = 서명된값.substring(0, 금);
        String 온서명 = 서명된값.substring(금 + 1);
        // **상수 시간으로 비교한다.** 앞에서부터 하나씩 비교하면 맞은 글자 수만큼 오래
        // 걸려서, 응답 시간만 재도 서명을 한 글자씩 알아낼 수 있다. TokenFilter 가 토큰을
        // 비교할 때와 같은 까닭이다.
        if (!MessageDigest.isEqual(
                온서명.getBytes(StandardCharsets.UTF_8),
                서명(값).getBytes(StandardCharsets.UTF_8))) {
            return null;
        }
        return 값;
    }

    private String 서명(String 값) {
        try {
            Mac mac = Mac.getInstance(방식);
            mac.init(열쇠);
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(mac.doFinal(값.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            // 방식 이름과 열쇠는 우리가 정한 상수다. 여기가 터지면 설정 문제가 아니라
            // 자바가 깨진 것이므로 삼키지 않는다.
            throw new IllegalStateException("서명할 수 없습니다", e);
        }
    }
}
