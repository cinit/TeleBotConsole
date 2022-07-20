//
// Created by kinit on 2022-02-25.
//

#ifndef CONFIRM_NATIVES_ENCODINGHELPER_H
#define CONFIRM_NATIVES_ENCODINGHELPER_H

#include <string>
#include <string_view>
#include <cstdint>

namespace swgui {

class EncodingHelper {
public:

    /**
     * Convert a string8 to a int32 codepoints
     * @param str the UTF-8 string in string8 format
     * @return the result in int32 format
     */
    [[nodiscard]] static std::u32string fromString8(std::string_view str);

    /**
     * Convert a string16 to a int32 codepoints
     * @param str the UTF-16 string in string16 format
     * @return the result in int32 format
     */
    [[nodiscard]] static std::u32string fromString16(std::u16string_view str);

    /**
     * Convert a UTF-16 string to a UTF-8 string
     * @param str the UTF-16 string
     * @return the result in UTF-8 format
     */
    [[nodiscard]] static std::string toString8(std::u16string_view str);

};

}

#endif //CONFIRM_NATIVES_ENCODINGHELPER_H
