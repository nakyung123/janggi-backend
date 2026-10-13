package com.nakyung.janggi.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * "이 사람은 누구다" 를 쿠키 하나에 담고, 돌아오면 읽는다.
 *
 * <p><b>서버에 아무것도 적지 않는다.</b> 담는 것은 {@code 번호|세대|만료} 셋뿐이고
 * {@link Signer} 가 서명한다. 내준 토큰 목록을 어디 적어 두면 요청마다 그것을 읽어야
 * 하는데, DB 가 싱가포르에 있어서 <b>한 번 읽는 데 155ms</b>(재 봤다)다.
 *
 * <p><b>그럼 어떻게 로그아웃시키나</b> - {@code app_user.token_epoch} 이다. 그 숫자를
 * 올리면 그 전에 내준 쿠키의 {@code 세대} 가 안 맞게 되어 전부 못 쓴다. 그리고 그
 * 확인은 <b>어차피 하는 질의에 조건으로 얹을 수 있어서</b> 왕복이 늘지 않는다.
 *
 * <p><b>만료를 쿠키 안에도 적는다.</b> {@code Max-Age} 는 브라우저가 지키는 약속이라,
 * 쿠키를 그대로 베껴 간 쪽은 안 지킨다. 서명된 값 안에 넣으면 우리가 거절할 수 있다.
 */
@Component
public class TokenIssuer {

    /** 밖에서도 가리킨다 - 검사가 이 이름으로 쿠키를 싣는다. */
    public static final String 이름 = "jg_auth";

    /**
     * 한 달.
     *
     * <p>짧게 두면 기보를 보려고 올 때마다 다시 로그인해야 한다 - 강제가 아닌 로그인에
     * 그만한 값을 요구할 까닭이 없다. 길게 두는 위험은 {@code token_epoch} 이 받는다.
     */
    private static final Duration 수명 = Duration.ofDays(30);

    private final Signer 서명자;
    private final Clock 시계;

    // 생성자가 둘이라 어느 것을 쓸지 적어 줘야 한다. 안 적으면 스프링이 기본 생성자를
    // 찾다가 "No default constructor found" 로 컨텍스트가 통째로 안 뜬다.
    @Autowired
    TokenIssuer(Signer 서명자) {
        this(서명자, Clock.systemUTC());
    }

    /** 만료를 검사에서 보려고 시계를 갈아 끼울 수 있게 둔다. */
    TokenIssuer(Signer 서명자, Clock 시계) {
        this.서명자 = 서명자;
        this.시계 = 시계;
    }

    /** 쿠키에서 읽은 사람. 둘이 다 있어야 뜻이 있다. */
    public record 들어온사람(long id, int epoch) {}

    /**
     * 로그인했으니 쿠키를 심는다.
     *
     * <p><b>{@code SameSite} 가 {@code state} 쿠키와 다르다.</b> 저쪽은 구글에서 오는
     * 최상위 이동에 실려야 해서 {@code Lax} 가 필수지만, 이 쿠키는 우리 쪽 화면이
     * {@code fetch} 로 부를 때만 필요하다. 그건 같은 출처라 {@code Strict} 로도 실린다.
     * <b>더 조일 수 있는 자리에서 느슨하게 둘 까닭이 없다.</b>
     */
    public void 심기(HttpServletResponse 답, long userId, int epoch) {
        long 만료 = 시계.millis() + 수명.toMillis();
        String 값 = 서명자.붙이기(userId + "|" + epoch + "|" + 만료);
        답.addHeader(HttpHeaders.SET_COOKIE, 쿠키(값, 수명).toString());
    }

    /** 로그아웃. 같은 성질로 빈 값을 심어야 브라우저가 같은 쿠키로 알아본다. */
    public void 지우기(HttpServletResponse 답) {
        답.addHeader(HttpHeaders.SET_COOKIE, 쿠키("", Duration.ZERO).toString());
    }

    /**
     * 쿠키를 읽는다. 없거나 틀리거나 지났으면 {@code null}.
     *
     * <p><b>셋을 가르지 않는다.</b> "쿠키가 없다" 와 "서명이 틀렸다" 와 "지났다" 를
     * 다른 답으로 알려 주면, 맞혀 보는 쪽에 어디까지 맞았는지를 가르쳐 준다. 쓰는
     * 쪽에서도 할 일은 하나다 - 로그인 안 한 것으로 본다.
     */
    public 들어온사람 읽기(HttpServletRequest 요청) {
        Cookie[] 쿠키들 = 요청.getCookies();
        if (쿠키들 == null) {
            return null;
        }
        for (Cookie c : 쿠키들) {
            if (이름.equals(c.getName())) {
                return 풀기(서명자.떼기(c.getValue()));
            }
        }
        return null;
    }

    private 들어온사람 풀기(String 값) {
        if (값 == null) {
            return null;
        }
        String[] 조각 = 값.split("\\|");
        if (조각.length != 3) {
            return null;
        }
        try {
            long id = Long.parseLong(조각[0]);
            int epoch = Integer.parseInt(조각[1]);
            long 만료 = Long.parseLong(조각[2]);
            if (만료 <= 시계.millis()) {
                return null;
            }
            return new 들어온사람(id, epoch);
        } catch (NumberFormatException e) {
            // 서명을 통과한 값이라 여기까지 오기 어렵지만, 모양이 바뀌면 숫자가 아닐
            // 수 있다. 없는 것으로 보면 다시 로그인해서 저절로 풀린다.
            return null;
        }
    }

    private ResponseCookie 쿠키(String 값, Duration 수명) {
        return ResponseCookie.from(이름, 값)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                // 브라우저는 자기가 보는 주소로 판단한다. Vercel 이 /api 를 벗겨서
                // 넘기므로 서버가 보는 경로로 두면 쿠키가 오지 않는다.
                .path("/")
                .maxAge(수명)
                .build();
    }
}
