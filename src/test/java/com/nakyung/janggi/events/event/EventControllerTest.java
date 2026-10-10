package com.nakyung.janggi.events.event;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
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
@Import(EventService.class)
class EventControllerTest {

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
}
