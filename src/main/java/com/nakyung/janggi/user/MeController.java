package com.nakyung.janggi.user;

import com.nakyung.janggi.auth.Caller;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * "나 로그인했나" 에 답한다.
 *
 * <p><b>이름도 이메일도 돌려주지 않는다.</b> DB 에 두지 않았고, 화면에 쓸 데도 없다 -
 * 기보 탭에 `로그인됨` 을 보여 주려는 것뿐이다.
 *
 * <p><b>이 주소는 막지 않는다.</b> "로그인했나" 를 묻는 자리를 로그인해야 열리게 두면
 * 답을 받을 수가 없다. 쿠키가 없으면 <b>거절이 아니라 {@code false}</b> 다.
 */
@RestController
public class MeController {

    /** 돌려주는 모양. 칸을 더하기만 하고 빼지 않는다 - 열린 탭에 옛 번들이 남는다. */
    public record 나(boolean loggedIn) {}

    private static final 나 아니오 = new 나(false);

    private final AppUserStore 창고;

    MeController(AppUserStore 창고) {
        this.창고 = 창고;
    }

    /**
     * <b>여기서는 DB 를 본다.</b> 다른 주소에서는 계정 상태를 질의에 접어 넣어 왕복을
     * 아끼는데, 이 주소는 접어 넣을 질의가 없다.
     *
     * <p>그 한 번(155ms)을 치르는 값이 있다 - 탈퇴했거나 세대가 올라간 쿠키를 들고
     * 있으면 화면이 <b>로그인된 줄 알고 있다가</b> 기보를 불러올 때 빈 목록을 보게 된다.
     * 쪽을 열 때 한 번 묻는 자리라 그때 바로 아는 것이 맞다.
     */
    @GetMapping("/me")
    나 나인가(@AuthenticationPrincipal Caller 나) {
        if (나 == null) {
            return 아니오;
        }
        return new 나(창고.살아있나(나.userId(), 나.epoch()));
    }
}
