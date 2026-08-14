package kr.ridely.infra.llm;

import kr.ridely.common.BusinessException;
import kr.ridely.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 프롬프트 파일 로더.
 *
 * 프롬프트를 코드에 하드코딩하지 않는 이유는 수정 주체가 다르기 때문이다. 톤과 지시문은 코드를 모르는 사람도 고칠 수 있어야 하고, 고칠 때마다 재컴파일이 필요하면 실험 주기가 느려진다.
 *
 * Spring AI의 PromptTemplate을 쓰지 않는다. StringTemplate 문법이라 중괄호를 치환자로 해석해서, 프롬프트에 JSON 예시나 중괄호가 들어가면 파싱이 깨진다. 우리는 후보 목록을 Java에서 렌더링하므로 __NAME__ 형태의 단순 치환으로 충분하다.
 *
 * 파일 내용은 한 번 읽고 캐시한다. 매 호출마다 클래스패스를 뒤질 이유가 없다.
 */
@Component
public class PromptLoader {

    private static final Logger log = LoggerFactory.getLogger(PromptLoader.class);

    private static final String PROMPT_DIR = "prompts/";

    /** 치환자 형식. Spring AI가 해석하는 중괄호를 피한다 */
    private static final String PLACEHOLDER_FORMAT = "__%s__";

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    /**
     * 프롬프트 파일을 읽는다.
     *
     * @param fileName prompts/ 아래의 파일명. 예: coach-ridely-system.txt
     */
    public String load(String fileName) {
        return cache.computeIfAbsent(fileName, this::readFromClasspath);
    }

    /**
     * 프롬프트를 읽고 치환자를 채운다.
     *
     * 채우지 못한 치환자가 남으면 실패시킨다. 그대로 LLM에 보내면 프롬프트에 __TARGET_DISTANCE_KM__ 같은 문자열이 남은 채 호출되고, 응답이 이상해져도 원인을 찾기 어렵다.
     *
     * @param values 치환자 이름(대문자, 언더스코어 없는 형태 그대로) → 값
     */
    public String render(String fileName, Map<String, String> values) {
        String rendered = load(fileName);
        for (Map.Entry<String, String> entry : values.entrySet()) {
            rendered = rendered.replace(
                    PLACEHOLDER_FORMAT.formatted(entry.getKey()),
                    entry.getValue() == null ? "" : entry.getValue());
        }
        verifyNoPlaceholderLeft(fileName, rendered);
        return rendered;
    }

    private String readFromClasspath(String fileName) {
        ClassPathResource resource = new ClassPathResource(PROMPT_DIR + fileName);
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("프롬프트 파일을 읽지 못했다: {}{}", PROMPT_DIR, fileName, e);
            throw new BusinessException(ErrorCode.COMMON_500);
        }
    }

    private void verifyNoPlaceholderLeft(String fileName, String rendered) {
        int start = rendered.indexOf("__");
        if (start < 0) {
            return;
        }
        int end = rendered.indexOf("__", start + 2);
        if (end < 0) {
            return;
        }
        log.error("프롬프트에 채우지 못한 치환자가 있다: {} — {}",
                fileName, rendered.substring(start, end + 2));
        throw new BusinessException(ErrorCode.COMMON_500);
    }
}
