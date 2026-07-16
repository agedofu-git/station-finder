package org.example;

import java.util.Set;

public record RouteSearchOptions(
        String date,
        String time,
        String type,
        int maxTransfers,
        boolean railOnly,
        boolean avoidWalk) {

    private static final Set<String> SEARCH_TYPES = Set.of("departure", "arrival", "first", "last");

    public RouteSearchOptions {
        if (date != null && !date.matches("\\d{8}")) {
            throw new IllegalArgumentException("日付はYYYY-MM-DD形式で指定してください。");
        }
        if (time != null && !time.matches("\\d{1,2}:\\d{2}(:\\d{2})?")) {
            throw new IllegalArgumentException("時刻はHH:mm形式で指定してください。");
        }
        if (!SEARCH_TYPES.contains(type)) {
            throw new IllegalArgumentException("検索種別が正しくありません。");
        }
        if (maxTransfers < 0 || maxTransfers > 8) {
            throw new IllegalArgumentException("最大乗換回数は0～8回で指定してください。");
        }
    }

    public static RouteSearchOptions defaults() {
        return new RouteSearchOptions(null, null, "departure", 3, false, false);
    }
}
