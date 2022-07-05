//
// Created by kinit on 2022-02-17.
//

#ifndef NEOGROUPCAPTCHABOT_FILE_UTILS_H
#define NEOGROUPCAPTCHABOT_FILE_UTILS_H

#include <string>

namespace utils {

constexpr char kPathSeparator = '/';

std::string getParentDirectory(const std::string &path);

std::string getFileName(const std::string &path);

bool isDirExists(const std::string &dirPath);

bool isFileExists(const std::string &filePath);

}

#endif //NEOGROUPCAPTCHABOT_FILE_UTILS_H
