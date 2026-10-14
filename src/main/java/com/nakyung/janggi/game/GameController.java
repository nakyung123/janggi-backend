package com.nakyung.janggi.game;

import com.nakyung.janggi.auth.Caller;
import com.nakyung.janggi.game.dto.GameDetail;
import com.nakyung.janggi.game.dto.GameSummary;
import com.nakyung.janggi.game.dto.GameUpload;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 기보를 받고 내준다. <b>얇게 둔다</b> - 규칙은 {@link GameStore} 에 있다.
 *
 * <p><b>{@code Caller} 를 꺼내 쓰지만 쿠키를 읽지 않는다.</b> 그 일은
 * {@code AuthTokenFilter} 가 이미 했다. 여기서 또 읽으면 "누구인가" 를 판단하는 자리가
 * 둘로 갈라진다.
 *
 * <p><b>{@code Caller} 가 {@code null} 일 수 없다.</b> 이 주소들은
 * {@code SecurityConfig} 에서 {@code authenticated()} 이므로 쿠키가 없으면 여기까지
 * 오지 않는다. 그래도 믿고 쓰지 않는다 - 규칙이 바뀌는 날 {@code NullPointerException}
 * 으로 500 이 나가는 것보다, 그때도 "없다" 로 답하는 쪽이 낫다.
 */
@RestController
@RequestMapping("/games")
public class GameController {

    private final GameStore 저장소;

    GameController(GameStore 저장소) {
        this.저장소 = 저장소;
    }

    /** 올린 기보의 서버 번호. 브라우저가 이걸로 하나를 열거나 지운다. */
    public record 올린결과(long id) {}

    @PostMapping
    올린결과 올리기(@AuthenticationPrincipal Caller 나, @Valid @RequestBody GameUpload 올릴것) {
        확인(나);
        return new 올린결과(저장소.올리기(나.userId(), 나.epoch(), 올릴것));
    }

    @GetMapping
    List<GameSummary> 목록(@AuthenticationPrincipal Caller 나) {
        확인(나);
        return 저장소.목록(나.userId(), 나.epoch());
    }

    /**
     * 기보 하나.
     *
     * <p><b>남의 것이면 404 다.</b> 403 은 "거기 뭔가 있다" 를 알려주는 셈이다 -
     * {@code /stats} 에서 쓴 기준과 같다.
     */
    @GetMapping("/{id}")
    ResponseEntity<GameDetail> 하나(@AuthenticationPrincipal Caller 나, @PathVariable long id) {
        확인(나);
        return 저장소.하나(나.userId(), 나.epoch(), id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void 지우기(@AuthenticationPrincipal Caller 나, @PathVariable long id) {
        확인(나);
        if (!저장소.지우기(나.userId(), 나.epoch(), id)) {
            throw new 없음();
        }
    }

    private static void 확인(Caller 나) {
        if (나 == null) {
            throw new 없음();
        }
    }

    /** 없거나 남의 것. 둘을 가르지 않는다. */
    @ResponseStatus(HttpStatus.NOT_FOUND)
    static class 없음 extends RuntimeException {}
}
