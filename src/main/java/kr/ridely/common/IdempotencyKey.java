package kr.ridely.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 클라이언트가 보낸 멱등성 키를 저장용 키로 바꾼다.
 *
 * <h3>왜 그대로 쓰지 않나</h3>
 *
 * <b>사용자를 섞어 해싱한다.</b> 클라이언트가 보낸 값을 그대로 쓰면 다른 사용자가 같은 키를 보냈을 때 남의 추천이 나간다. UUID라 우연히 겹칠 일은 없지만, 값을 만드는 것은 클라이언트이고 우리는 그것을 신뢰할 근거가 없다.
 *
 * <b>길이가 고정된다.</b> {@code cache_key}가 {@code VARCHAR(64)}인데 클라이언트가 보내는 값의 길이를 우리가 정하지 않는다. SHA256 hex가 정확히 64자다.
 *
 * <h3>비회원</h3>
 *
 * {@code userId}가 없으면 그 자리를 빈 값으로 둔다. 비회원끼리는 서로의 키를 구분할 근거가 UUID뿐인데, 추천 코스에 개인을 식별할 정보가 없고 UUID를 맞히는 것이 현실적이지 않아 그대로 둔다.
 */
public final class IdempotencyKey {

    /** 사용자와 클라이언트 키를 가르는 구분자. UUID에 나오지 않는 문자여야 한다 */
    private static final String SEPARATOR = "|";

    private static final String ALGORITHM = "SHA-256";

    private IdempotencyKey() {
    }

    /**
     * @param clientKey 클라이언트가 보낸 Idempotency-Key 헤더 값
     * @param userId    회원이면 사용자 번호, 비회원이면 null
     * @return SHA256 hex 64자
     */
    public static String of(String clientKey, Long userId) {
        String source = (userId == null ? "" : userId.toString()) + SEPARATOR + clientKey;
        return hash(source);
    }

    private static String hash(String source) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256은 모든 JVM 구현이 제공해야 하는 알고리즘이라 여기는 도달하지 않는다.
            // 그래도 삼키지 않는 이유는, 도달했다면 런타임이 깨진 것이라 조용히 넘길 상황이 아니어서다
            throw new IllegalStateException(ALGORITHM + "을 쓸 수 없다", e);
        }
    }
}
