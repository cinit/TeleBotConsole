//
// Created by kinit on 2022-02-17.
//

#ifndef NEOGROUPCAPTCHABOT_CONFIGMANAGER_H
#define NEOGROUPCAPTCHABOT_CONFIGMANAGER_H

#include <string>
#include <cstdint>

#include <MMKV.h>

namespace utils::config {

class ConfigManager {
public:
    static void initialize(const std::string &configPath);

    static MMKV &getDefaultConfig();

    static MMKV &getCache();

    static MMKV &forAccount(int64_t uin);

private:
    static MMKV *sDefaultConfig;
    static MMKV *sDefaultCache;
    static std::string sConfigPath;
};

}

#endif //NEOGROUPCAPTCHABOT_CONFIGMANAGER_H
