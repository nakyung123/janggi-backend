package com.nakyung.janggi.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * {@code event} 표 한 줄.
 *
 * <p><b>이 클래스로 넣지는 않는다.</b> 넣는 것은 {@code EventRepository} 의 네이티브
 * SQL 이 한다 - {@code ON CONFLICT DO NOTHING} 이 필요한데 JPA 에는 그것이 없다.
 * 그러면 이 클래스는 왜 있나.
 *
 * <ul>
 *   <li><b>표와 어긋나면 시작할 때 걸린다.</b> {@code ddl-auto=validate} 가 이 클래스와
 *       Flyway 가 만든 표를 맞춰 본다. 칸 이름을 한쪽만 고치면 <b>뜨지도 않는다</b> -
 *       넣다가 터지는 것보다 훨씬 낫다. 넣다가 터지면 그 사이 기록이 버려진다.
 *   <li>읽을 때 쓴다. {@code GET /stats} 가 여기서부터 자란다.
 * </ul>
 *
 * <p>필드가 전부 묶음 생성자 없이 열려 있는 까닭은 <b>JPA 가 빈 생성자와 setter 를
 * 요구하기 때문</b>이다. record 로 못 만든다.
 */
@Entity
@Table(name = "event")
public class EventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 받은 시각. <b>기준은 늘 이쪽이다.</b> */
    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "name", nullable = false, length = 32)
    private String name;

    @Column(name = "sid", nullable = false, length = 16)
    private String sid;

    /** 보낸 쪽 시각. 사용자 시계는 틀릴 수 있어 참고용이다. */
    @Column(name = "sent_at")
    private Long sentAt;

    @Column(name = "env", length = 8)
    private String env;

    @Column(name = "ver", length = 16)
    private String ver;

    @Column(name = "browser", length = 64)
    private String browser;

    @Column(name = "os", length = 32)
    private String os;

    @Column(name = "in_app", length = 32)
    private String inApp;

    @Column(name = "isolated")
    private Boolean isolated;

    @Column(name = "cores")
    private Short cores;

    @Column(name = "memory")
    private Short memory;

    @Column(name = "screen", length = 16)
    private String screen;

    /**
     * 이벤트마다 다른 칸. <b>글자로 들고 있는다.</b>
     *
     * <p>Map 으로 받으면 하이버네이트가 JSON 으로 바꿔 주는데, 그 변환기는 우리가 쓰는
     * 잭슨 3 과 따로 논다. 어차피 넣는 쪽은 네이티브 SQL 이 {@code CAST(? AS jsonb)} 로
     * 보내고 있어서, 여기서 한 겹 더 변환할 까닭이 없다.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data")
    private String data;

    protected EventEntity() {
        // JPA 가 쓴다.
    }

    public Long getId() {
        return id;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public String getName() {
        return name;
    }

    public String getSid() {
        return sid;
    }

    public Long getSentAt() {
        return sentAt;
    }

    public String getEnv() {
        return env;
    }

    public String getVer() {
        return ver;
    }

    public String getBrowser() {
        return browser;
    }

    public String getOs() {
        return os;
    }

    public String getInApp() {
        return inApp;
    }

    public Boolean getIsolated() {
        return isolated;
    }

    public Short getCores() {
        return cores;
    }

    public Short getMemory() {
        return memory;
    }

    public String getScreen() {
        return screen;
    }

    public String getData() {
        return data;
    }
}
