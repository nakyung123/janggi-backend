package com.nakyung.janggi.auth;

/**
 * 지금 요청을 보낸 사람. <b>쿠키에서 읽은 것일 뿐</b>이다.
 *
 * <p><b>이것이 "이 계정이 살아 있다" 는 뜻은 아니다.</b> 서명이 맞고 아직 안 지났다는
 * 것까지만 확인된 값이다. 탈퇴했거나 세대({@code epoch})가 올라간 계정일 수 있는데,
 * 그것을 알려면 DB 를 봐야 한다.
 *
 * <p><b>그 확인을 필터에서 하지 않는다.</b> 요청마다 DB 를 한 번 더 보게 되고 그 한 번이
 * 155ms 다(재 봤다). 대신 {@code epoch} 를 들고 다니다가 <b>어차피 하는 질의에 조건으로
 * 얹는다.</b>
 *
 * <pre>
 * WHERE user_id = :uid
 *   AND EXISTS (SELECT 1 FROM app_user
 *                WHERE id = :uid AND deleted_at IS NULL AND token_epoch = :epoch)
 * </pre>
 *
 * <p>그래서 이 둘은 <b>늘 같이 다녀야 한다.</b> {@code epoch} 를 빼먹고 {@code userId}
 * 만 쓰면 탈퇴한 계정의 쿠키가 그대로 통한다.
 */
public record Caller(long userId, int epoch) {}
