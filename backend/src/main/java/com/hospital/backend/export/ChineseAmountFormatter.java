package com.hospital.backend.export;

/**
 * 金额转中文大写（如 91866.4 → 玖万壹仟捌佰陆拾陆元肆角）。
 */
public final class ChineseAmountFormatter {

    private ChineseAmountFormatter() {
    }

    public static String format(double amount) {
        if (amount < 0) {
            return "负" + format(-amount);
        }
        if (amount == 0) {
            return "零元整";
        }

        String[] digits = {"零", "壹", "贰", "叁", "肆", "伍", "陆", "柒", "捌", "玖"};
        String[] radices = {"", "拾", "佰", "仟"};
        String[] bigRadices = {"", "万", "亿"};

        long yuan = (long) amount;
        int jiao = (int) Math.round((amount - yuan) * 10);
        int fen = (int) Math.round((amount - yuan - jiao * 0.1) * 100);

        StringBuilder sb = new StringBuilder();
        if (yuan == 0) {
            sb.append("零");
        } else {
            String yuanStr = String.valueOf(yuan);
            int len = yuanStr.length();
            boolean needZero = false;
            for (int i = 0; i < len; i++) {
                int digit = yuanStr.charAt(i) - '0';
                int pos = len - i - 1;
                int radixIdx = pos % 4;
                int bigRadixIdx = pos / 4;

                if (digit == 0) {
                    needZero = true;
                } else {
                    if (needZero) {
                        sb.append("零");
                        needZero = false;
                    }
                    sb.append(digits[digit]).append(radices[radixIdx]);
                }
                if (radixIdx == 0 && bigRadixIdx > 0) {
                    int segmentStart = Math.max(0, i - 3);
                    boolean segmentAllZero = true;
                    for (int j = segmentStart; j <= i; j++) {
                        if (yuanStr.charAt(j) != '0') {
                            segmentAllZero = false;
                            break;
                        }
                    }
                    if (!segmentAllZero) {
                        sb.append(bigRadices[bigRadixIdx]);
                    }
                    needZero = false;
                }
            }
        }
        sb.append("元");

        if (jiao == 0 && fen == 0) {
            sb.append("整");
        } else {
            if (jiao > 0) {
                sb.append(digits[jiao]).append("角");
            }
            if (fen > 0) {
                sb.append(digits[fen]).append("分");
            }
        }
        return sb.toString();
    }
}
