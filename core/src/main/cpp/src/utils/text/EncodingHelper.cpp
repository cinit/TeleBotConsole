//
// Created by kinit on 2022-02-25.
//

#include "EncodingHelper.h"

#include <vector>

namespace swgui {

// UTF-8 to Unicode int32_t
std::u32string EncodingHelper::fromString8(std::string_view str) {
    if (str.empty()) {
        return {};
    }
    std::vector<int> codePoints;
    // source may have multiple code points, treat as UTF-8
    size_t i = 0;
    while (i < str.size()) {
        if ((str[i] & 0b10000000) == 0) {
            // 1 byte code point, ASCII
            int c = (str[i] & 0b01111111);
            codePoints.push_back(c);
            i++;
        } else if ((str[i] & 0b11100000) == 0b11000000) {
            // 2 byte code point
            int c = (str[i] & 0b00011111) << 6 | (str[i + 1] & 0b00111111);
            codePoints.push_back(c);
            i += 2;
        } else if ((str[i] & 0b11110000) == 0b11100000) {
            // 3 byte code point
            int c = (str[i] & 0b00001111) << 12 | (str[i + 1] & 0b00111111) << 6 | (str[i + 2] & 0b00111111);
            codePoints.push_back(c);
            i += 3;
        } else {
            // 4 byte code point
            int c = (str[i] & 0b00000111) << 18 | (str[i + 1] & 0b00111111) << 12 | (str[i + 2] & 0b00111111) << 6 | (str[i + 3] & 0b00111111);
            codePoints.push_back(c);
            i += 4;
        }
    }
    return {codePoints.begin(), codePoints.end()};
}

// UTF-16 to Unicode int32_t
std::u32string EncodingHelper::fromString16(std::u16string_view str) {
    if (str.empty()) {
        return {};
    }
    std::vector<int> codePoints;
    for (size_t i = 0; i < str.size(); ++i) {
        if (str[i] < 0xD800 || str[i] > 0xDFFF) {
            codePoints.push_back(str[i]);
        } else if (i + 1 < str.size()) {
            codePoints.push_back(0x10000 + ((str[i] - 0xD800) << 10) + (str[i + 1] - 0xDC00));
            ++i;
        } else {
            codePoints.push_back(0xFFFD);
        }
    }
    return {codePoints.begin(), codePoints.end()};
}

std::string EncodingHelper::toString8(std::u16string_view str) {
    if (str.empty()) {
        return {};
    }
    std::u32string u32str = fromString16(str);
    std::string result;
    for (uint32_t c: u32str) {
        if (c < 0x80) {
            result += (char) c;
        } else if (c < 0x800) {
            result += (char) (0xC0 | (c >> 6));
            result += (char) (0x80 | (c & 0x3F));
        } else if (c < 0x10000) {
            result += (char) (0xE0 | (c >> 12));
            result += (char) (0x80 | ((c >> 6) & 0x3F));
            result += (char) (0x80 | (c & 0x3F));
        } else if (c < 0x110000) {
            result += (char) (0xF0 | (c >> 18));
            result += (char) (0x80 | ((c >> 12) & 0x3F));
            result += (char) (0x80 | ((c >> 6) & 0x3F));
            result += (char) (0x80 | (c & 0x3F));
        } else {
            result += (char) 0xFFFD;
        }
    }
    return result;
}

}
