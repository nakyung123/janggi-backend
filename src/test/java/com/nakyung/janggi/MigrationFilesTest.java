package com.nakyung.janggi;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>이미 적용된 마이그레이션 파일은 한 글자도 고치지 않는다. 주석도 안 된다.</b>
 *
 * <p>2026-10-14 에 주석 한 줄을 고쳤다가 배포가 깨졌다. Flyway 는 마이그레이션을 적용할
 * 때 파일의 검사합을 DB 에 적어 두고, 뜰 때마다 파일과 맞는지 본다. 다르면 "적어 둔 그
 * 파일이 아니다" 며 <b>서버를 아예 안 띄운다.</b> 그날 바뀐 것은 낱말 하나였다.
 *
 * <p><b>다른 검사는 이것을 못 잡는다.</b> 검사는 빈 DB 에 처음부터 적용하므로 적어 둔
 * 검사합이 없고, 비교할 것이 없으니 무엇을 고치든 통과한다. <b>배포해 봐야 아는 실수였고,
 * 그래서 여기에 지문을 적어 둔다.</b>
 *
 * <p><b>새 마이그레이션을 더할 때</b>는 아래 표에 지문도 같이 적는다. 손이 한 번 더 가는
 * 것이 요점이다 - 이미 있는 파일을 건드리는 것과 새 파일을 더하는 것이 <b>구별되어야</b>
 * 한다. 앞엣것은 하면 안 되고 뒤엣것은 늘 하는 일이다.
 *
 * <p><b>Flyway 의 검사합을 그대로 쓰지 않는다.</b> 여기서는 SHA-256 을 낸다. 보려는 것은
 * "이 파일이 그때 그 파일인가" 하나뿐이라 이것으로 충분하다.
 *
 * <p><b>줄 끝을 맞춘 뒤에 낸다.</b> 여기는 윈도우라 작업 폴더의 파일이 CRLF 인데 CI 는
 * 리눅스라 LF 로 받는다. 바이트를 그대로 쓰면 <b>같은 파일인데 지문이 갈려서</b> 내
 * 자리에서는 통과하고 CI 에서만 빨간불이 난다. 실제로 그렇게 두고 한 번 깨뜨렸다.
 */
class MigrationFilesTest {

    private static final Path 마이그레이션칸 = Path.of("src/main/resources/db/migration");

    /**
     * 파일 이름과 그 파일의 지문.
     *
     * <p><b>여기 적힌 것을 바꾸려거든 멈춰야 한다.</b> 이미 운영 DB 에 적용된 파일이라면
     * 고치는 순간 서버가 안 뜬다. 고쳐야 할 일이 생기면 <b>새 마이그레이션을 하나 더</b>
     * 만든다.
     */
    private static final Map<String, String> 지문 =
            Map.of(
                    "V1__event.sql",
                    "2a08d5bb5e562ebc1e168b6ca23ce2b1fa34c062ae4e97b59316bc9388211499",
                    "V2__app_user.sql",
                    "1f405cd330b76975fdf82aa40c5f9fccd8fce7a813a065445aceb583a5dd312e",
                    "V3__game.sql",
                    "d59ea88f668f1bfb1a3a0fcdf43de159504bff326bac6544d48233319275915c");

    @Test
    @DisplayName("적용된 마이그레이션 파일이 바뀌지 않았다 - 바뀌면 서버가 안 뜬다")
    void 안_바뀌었나() throws IOException {
        assertThat(지금지문())
                .as(
                        """
                        마이그레이션 파일이 바뀌었습니다.

                        이미 적용된 파일은 주석 한 글자도 고치면 안 됩니다. Flyway 가 DB 에
                        적어 둔 검사합과 달라져서 서버가 아예 안 뜹니다. 고칠 일이 있으면
                        새 마이그레이션을 하나 더 만드세요.

                        새 파일을 더한 것이라면 MigrationFilesTest 의 표에 그 지문을 같이
                        적으세요.
                        """)
                .containsExactlyInAnyOrderEntriesOf(지문);
    }

    @Test
    @DisplayName("표에 적힌 파일이 실제로 다 있다 - 지문만 남고 파일이 사라지면 안 된다")
    void 다_있나() throws IOException {
        assertThat(지금지문().keySet()).containsExactlyInAnyOrderElementsOf(지문.keySet());
    }

    private static Map<String, String> 지금지문() throws IOException {
        Map<String, String> 모음 = new TreeMap<>();
        try (Stream<Path> 파일들 = Files.list(마이그레이션칸)) {
            List<Path> 쭉 = 파일들.filter(p -> p.getFileName().toString().endsWith(".sql")).toList();
            for (Path 하나 : 쭉) {
                모음.put(하나.getFileName().toString(), sha256(줄끝맞추기(하나)));
            }
        }
        return 모음;
    }

    /** CRLF 를 LF 로 맞춘다. 윈도우와 리눅스에서 같은 지문이 나와야 한다. */
    private static byte[] 줄끝맞추기(Path 파일) throws IOException {
        return String.join("\n", Files.readAllLines(파일)).getBytes(StandardCharsets.UTF_8);
    }

    private static String sha256(byte[] 바이트) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(바이트));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
