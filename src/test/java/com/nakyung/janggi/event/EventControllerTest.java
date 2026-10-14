package com.nakyung.janggi.event;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 받는 자리가 제대로 거르는지 본다.
 *
 * <p>가짜 서비스를 끼우지 않고 진짜 {@link EventService} 를 불러온다. 걸러내는 규칙이
 * 거기 있어서, 가짜로 바꾸면 <b>정작 확인하고 싶은 것을 안 거치게 된다.</b>
 */
@WebMvcTest(EventController.class)
// **보안 필터를 끈다.** 한때 SecurityConfig 를 끼워 넣어 두었는데, 그 설정이 로그인 쪽
// 빈(state 저장소·성공 처리기)을 받게 되면서 조각 테스트가 줄 수 없는 것이 됐다. 억지로
// 채우면 이 테스트에 JPA 와 DataSource 까지 딸려 와서, 거르는 규칙을 보려고 만든 것이
// 통째로 무거워진다.
//
// **대신 그 보호가 더 나은 자리로 갔다.** /events 가 인증도 CSRF 토큰도 없이 들어오는
// 것은 PublicPathsTest 가 진짜 HTTP 와 전체 설정으로 본다 - 누가 CSRF 예외를 지우면
// 거기가 빨간불이 된다. 조각 테스트에서 같은 것을 한 번 더 볼 값이 없다.
@AutoConfigureMockMvc(addFilters = false)
@Import({EventService.class, EventControllerTest.저장소없이.class})
class EventControllerTest {

    /**
     * 쌓는 쪽은 끼우지 않는다.
     *
     * <p>여기서 보는 것은 <b>거르는 규칙</b>이다. 진짜 저장소를 끼우면 {@code @WebMvcTest}
     * 에 JPA 와 Postgres 가 통째로 딸려 오는데, 그것들이 이 테스트가 보려는 것에 보태는
     * 것이 없다. 쌓는 것이 제대로 되는지는 {@code EventRepositoryTest} 가 진짜 DB 로 본다.
     */
    @TestConfiguration
    static class 저장소없이 {
        @Bean
        EventStore 저장소() {
            return (events, receivedAt) -> {};
        }
    }

    @Autowired
    private MockMvc mvc;

    /** 프런트가 실제로 보내는 모양. */
    private static final String 제대로 = """
            {"events":[{
              "name":"app_open","sid":"n8q8focu","ver":"0.1.0",
              "browser":"사파리 17","os":"아이폰","inApp":"카카오톡",
              "isolated":true,"cores":6,"memory":16,"screen":"390x844",
              "at":1760000000000,"env":"prod",
              "data":{"ref":"","conn":"4g"}
            }]}
            """;

    @Test
    @DisplayName("제대로 보내면 204 로 받는다")
    void 제대로_보내면_받는다() throws Exception {
        mvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(제대로))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("모르는 이벤트 이름은 400 으로 거절한다")
    void 모르는_이름은_거절한다() throws Exception {
        String 몰라 = 제대로.replace("\"app_open\"", "\"탈취시도\"");
        mvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(몰라))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("한 건이라도 모르는 이름이면 묶음 전체를 거절한다")
    void 한_건이라도_모르면_전체를_거절한다() throws Exception {
        String 섞임 = """
                {"events":[
                  {"name":"app_open","sid":"aaaaaaaa"},
                  {"name":"이상한것","sid":"bbbbbbbb"}
                ]}
                """;
        mvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(섞임))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("빈 묶음은 400 으로 거절한다")
    void 빈_묶음은_거절한다() throws Exception {
        mvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content("{\"events\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("50건을 넘으면 400 으로 거절한다")
    void 너무_많으면_거절한다() throws Exception {
        StringBuilder sb = new StringBuilder("{\"events\":[");
        for (int i = 0; i < 51; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"name\":\"app_open\",\"sid\":\"s").append(i).append("\"}");
        }
        sb.append("]}");

        mvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(sb.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("깨진 JSON 은 400 으로 거절한다")
    void 깨진_JSON_은_거절한다() throws Exception {
        mvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content("{\"events\":["))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("있을 수도 없을 수도 있는 칸이 비어도 받는다")
    void 빈_칸이_있어도_받는다() throws Exception {
        // os · inApp · memory 는 기기에 따라 없다. 없는 것과 잘못된 것은 다르다.
        String 최소 = """
                {"events":[{"name":"engine_failed","sid":"cccccccc","isolated":false}]}
                """;
        mvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(최소))
                .andExpect(status().isNoContent());
    }

    /**
     * 프런트가 보내는 이름을 <b>손으로 다시 적어</b> 둔다.
     *
     * <p>{@code EventName.values()} 를 돌면 enum 이 자기를 보는 꼴이라 오타를 못 잡는다.
     * 여기 적힌 글자가 프런트의 {@code EventName} 타입과 같아야 하고, 한 글자라도 다르면
     * 서버가 400 으로 거절한다. 그런데 <b>프런트는 {@code sendBeacon} 이라 답을 안 읽어서
     * 조용히 버려진다</b> - 숫자가 비어 있는 것을 나중에 보고서야 안다.
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "app_open", "engine_ready", "engine_failed",
        "game_start", "first_move", "game_end", "review_open"
    })
    @DisplayName("프런트가 보내는 이름은 모두 받는다")
    void 프런트가_보내는_이름을_모두_받는다(String 이름) throws Exception {
        String 본문 = "{\"events\":[{\"name\":\"" + 이름 + "\",\"sid\":\"dddddddd\"}]}";
        mvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(본문))
                .andExpect(status().isNoContent());
    }
}
