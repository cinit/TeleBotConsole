//
// Created by kinit on 2022-02-17.
//

#include <sys/stat.h>

#include "file_utils.h"

namespace utils {

std::string getParentDirectory(const std::string &path) {
    if (path.empty()) {
        return "";
    }
    std::string::size_type pos = path.find_last_of(kPathSeparator);
    if (pos == std::string::npos) {
        return "";
    }
    return path.substr(0, pos);
}

std::string getFileName(const std::string &path) {
    if (path.empty()) {
        return "";
    }
    std::string::size_type pos = path.find_last_of(kPathSeparator);
    if (pos == std::string::npos) {
        return path;
    }
    return path.substr(pos + 1);
}

bool isDirExists(const std::string &dirPath) {
    struct stat st = {};
    if (stat(dirPath.c_str(), &st) == 0) {
        if (S_ISDIR(st.st_mode)) {
            return true;
        }
    }
    return false;
}

bool isFileExists(const std::string &filePath) {
    struct stat st = {};
    if (stat(filePath.c_str(), &st) == 0) {
        if (S_ISREG(st.st_mode)) {
            return true;
        }
    }
    return false;
}

}
