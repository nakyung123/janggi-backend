package com.nakyung.janggi.events.event;

import com.nakyung.janggi.events.event.dto.EventRequest;
import java.time.Instant;
import java.util.List;

/**
 * 받은 이벤트를 쌓아 둔다.
 *
 * <p><b>왜 떼어냈나</b> - {@code EventService} 는 "받아들일 이름인지 보고, 로그에 적고,
 * 넘긴다" 까지만 한다. 거기에 트랜잭션과 JPA 가 섞이면 그 셋을 확인하는 테스트마다
 * DB 가 따라붙는다. 실제로 그렇게 두었다가 <b>로그 한 줄의 모양을 보는 테스트가
 * Postgres 를 띄워야 하는</b> 꼴이 됐다.
 *
 * <p>구현은 하나뿐이다({@code JpaEventStore}). 인터페이스를 둔 것은 바꿔 끼울 계획이
 * 있어서가 아니라 <b>위쪽이 아래쪽을 모르게</b> 하려는 것이다.
 *
 * <p><b>여기서 터지는 것은 부르는 쪽이 삼킨다.</b> 기록은 이미 로그에 있어서, 쌓는 데
 * 실패했다고 요청을 깨뜨릴 까닭이 없다.
 */
public interface EventStore {

    /**
     * 묶음을 쌓는다. <b>전부 들어가거나 전부 안 들어간다.</b>
     *
     * @param receivedAt 받은 시각. 묶음 전체가 같은 값을 쓴다 - 기준은 받은 쪽 시계다
     */
    void 넣기(List<EventRequest> events, Instant receivedAt);
}
