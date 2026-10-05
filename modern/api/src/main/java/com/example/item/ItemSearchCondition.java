package com.example.item;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.util.MultiValueMap;

/**
 * 문항 검색 조건. 레거시 {@code legacy/item-bank-php/search.php} 의 {@code buildSearchQuery} 가
 * GET 파라미터를 해석하던 방식을 그대로 옮겼다(BR-03~BR-21, docs/item-bank/BUSINESS-RULES.md).
 *
 * <p>레거시 동작이 이상해 보여도 고치지 않는다 — 예: 난이도를 비우면 난이도 5 가 빠지고(BR-06),
 * {@code 3abc} 는 난이도 3 으로 조회된다(BR-08). 경고 문구는 응답에 넣지 않는다.
 *
 * @param keyword 키워드. 빈 문자열이면 조건 없음. {@code %} · {@code _} 는 와일드카드 그대로(BR-15)
 * @param unitCode 단원 코드. 빈 문자열이면 조건 없음. 대소문자를 바꾸지 않는다(BR-09)
 * @param level 난이도. {@code null} 이면 난이도 5 미만만(BR-06), 아니면 {@code level = 값}(BR-07, BR-08)
 * @param tag 태그 이름. 빈 문자열이면 조건 없음
 * @param sort 정렬 기준 — {@code id | title | unit | level | created}, 빈 문자열이면 기본 정렬
 * @param dir 정렬 방향 — {@code asc | desc}, 빈 문자열이면 기준별 기본 방향
 * @param page 페이지 번호 1~999
 */
public record ItemSearchCondition(
    String keyword,
    String unitCode,
    Long level,
    String tag,
    String sort,
    String dir,
    int page) {

    static final int PAGE_SIZE = 20;
    static final int MAX_PAGE = 999;
    static final int KEYWORD_MAX_LENGTH = 100;
    static final int TAG_MAX_LENGTH = 50;
    /** 난이도를 비웠을 때 이 값 미만만 조회한다(BR-06). */
    static final int UNSPECIFIED_LEVEL_BELOW = 5;

    private static final List<String> SORT_KEYS = List.of("id", "title", "unit", "level", "created");
    /** search.php:337 {@code /^[0-9]+$/} — PCRE 의 {@code $} 는 끝의 줄바꿈 하나 앞에서도 맞는다. */
    private static final Pattern PAGE_DIGITS = Pattern.compile("[0-9]+\n?");
    private static final Pattern INTEGER = Pattern.compile("[+-]?[0-9]+");
    /** PHP 숫자 문자열의 앞부분(부호 · 정수 · 소수 · 지수). */
    private static final Pattern PHP_NUMERIC_PREFIX =
        Pattern.compile("^[ \t\n\r\u000B\f]*([+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?)");

    /** GET 파라미터 → 검색 조건. 파라미터가 없거나 비어 있으면 레거시처럼 조건 없음으로 본다. */
    public static ItemSearchCondition from(MultiValueMap<String, String> params) {
        // 키워드 (search.php:85-98)
        String keyword = phpTrim(param(params, "q"));
        if (keyword.codePointCount(0, keyword.length()) > KEYWORD_MAX_LENGTH) {
            keyword = cutCodePoints(keyword, KEYWORD_MAX_LENGTH);
        }

        // 단원 (search.php:109-120)
        String unitCode = phpTrim(param(params, "unit"));

        // 난이도 (search.php:211-230) — 1~5 형식이든 아니든 (int) 로 바꿔 level = 값
        String levelRaw = phpTrim(param(params, "level"));
        Long level = levelRaw.isEmpty() ? null : phpIntval(levelRaw);

        // 태그 (search.php:235-247)
        String tag = phpTrim(param(params, "tag"));
        if (tag.codePointCount(0, tag.length()) > TAG_MAX_LENGTH) {
            tag = cutCodePoints(tag, TAG_MAX_LENGTH);
        }

        // 정렬 (search.php:266-327)
        String sort = asciiLower(phpTrim(param(params, "sort")));
        String dir = asciiLower(phpTrim(param(params, "dir")));
        if (!dir.equals("asc") && !dir.equals("desc")) {
            dir = "";
        }
        if (!sort.isEmpty() && !SORT_KEYS.contains(sort)) {
            sort = "";
        }

        // 페이지 (search.php:336-348) — 페이지 번호는 trim 하지 않는다
        String pageRaw = param(params, "page");
        long page = PAGE_DIGITS.matcher(pageRaw).matches() ? phpIntval(pageRaw) : 1;
        if (page < 1) {
            page = 1;
        }
        if (page > MAX_PAGE) {
            page = MAX_PAGE;
        }

        return new ItemSearchCondition(keyword, unitCode, level, tag, sort, dir, (int) page);
    }

    /** 정렬 (BR-16~BR-20). 보조 정렬 id 는 방향과 무관하게 오름차순이다. */
    public Sort toSort() {
        Sort.Direction direction = dir.equals("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        return switch (sort) {
            case "id" -> Sort.by(direction, "id");
            case "title" -> Sort.by(direction, "title").and(Sort.by(Sort.Direction.ASC, "id"));
            case "unit" -> Sort.by(direction, "unit.code")
                .and(Sort.by(Sort.Direction.DESC, "level"))
                .and(Sort.by(Sort.Direction.ASC, "id"));
            // level · created 는 dir 이 asc 일 때만 오름차순, 그 밖(빈 값 포함)은 내림차순
            case "level" -> Sort.by(dir.equals("asc") ? Sort.Direction.ASC : Sort.Direction.DESC, "level")
                .and(Sort.by(Sort.Direction.ASC, "id"));
            case "created" -> Sort.by(dir.equals("asc") ? Sort.Direction.ASC : Sort.Direction.DESC, "createdAt")
                .and(Sort.by(Sort.Direction.ASC, "id"));
            default -> defaultSort();
        };
    }

    /** 한 페이지 20건(BR-21). */
    public Pageable toPageable() {
        return PageRequest.of(page - 1, PAGE_SIZE, toSort());
    }

    private static Sort defaultSort() {
        return Sort.by(Sort.Direction.DESC, "level").and(Sort.by(Sort.Direction.ASC, "id"));
    }

    /**
     * PHP {@code $_GET} 에서 값 하나를 꺼내는 방식을 따른다.
     * 같은 이름이 여러 번 오면({@code q=a&q=b}) 마지막 값, 배열로 오면({@code q[]=a&q[]=b}) 첫 값(search.php:53-80).
     * 두 형태가 섞여 오면 PHP 는 쿼리 문자열의 순서로 정하지만 여기서는 순서를 알 수 없어 앞의 형태를 쓴다.
     */
    static String param(MultiValueMap<String, String> params, String name) {
        List<String> plain = params.get(name);
        if (plain != null && !plain.isEmpty()) {
            String value = plain.get(plain.size() - 1);
            return value == null ? "" : value;
        }
        List<String> array = params.get(name + "[]");
        if (array != null && !array.isEmpty()) {
            String value = array.get(0);
            return value == null ? "" : value;
        }
        return "";
    }

    /** PHP {@code trim()} — 앞뒤의 공백 · 탭 · 줄바꿈 · NUL · 수직 탭만 지운다. */
    static String phpTrim(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && isPhpTrimChar(value.charAt(start))) {
            start++;
        }
        while (end > start && isPhpTrimChar(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(start, end);
    }

    private static boolean isPhpTrimChar(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\0' || c == '\u000B';
    }

    /**
     * PHP 7.4 {@code (int)$string}. 앞부분의 숫자만 읽고({@code 3abc} → 3, {@code 3.5} → 3, {@code 1e1} → 10),
     * 숫자로 시작하지 않으면 0, 범위를 넘으면 long 최댓값 · 최솟값으로 붙인다.
     */
    static long phpIntval(String value) {
        Matcher m = PHP_NUMERIC_PREFIX.matcher(value);
        if (!m.find()) {
            return 0;
        }
        String number = m.group(1);
        if (INTEGER.matcher(number).matches()) {
            try {
                return Long.parseLong(number);
            } catch (NumberFormatException overflow) {
                return number.startsWith("-") ? Long.MIN_VALUE : Long.MAX_VALUE;
            }
        }
        // 소수 · 지수 형식은 double 로 읽어 소수점 아래를 버린다(zend_dval_to_lval_cap)
        double d = Double.parseDouble(number);
        if (d >= 0x1p63) {
            return Long.MAX_VALUE;
        }
        if (d <= -0x1p63) {
            return Long.MIN_VALUE;
        }
        return (long) d;
    }

    private static String cutCodePoints(String value, int length) {
        return value.substring(0, value.offsetByCodePoints(0, length));
    }

    /** PHP {@code strtolower()} (C 로캘) — ASCII 대문자만 바꾼다. */
    private static String asciiLower(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            sb.append(c >= 'A' && c <= 'Z' ? (char) (c + ('a' - 'A')) : c);
        }
        return sb.toString();
    }
}
