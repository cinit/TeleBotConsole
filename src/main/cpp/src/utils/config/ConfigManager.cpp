//
// Created by kinit on 2022-02-17.
//
#include <sys/stat.h>
#include <stdexcept>
#include <MMKV.h>

#include "utils/file_utils.h"

#include "ConfigManager.h"

namespace utils::config {

MMKV *ConfigManager::sDefaultConfig = nullptr;
MMKV *ConfigManager::sDefaultCache = nullptr;
std::string ConfigManager::sConfigPath;

void ConfigManager::initialize(const std::string &configPath) {
    if (configPath.empty()) {
        throw std::runtime_error("config path is empty");
    }
    // check config dir exists
    if (!utils::isDirExists(configPath)) {
        // create config dir
        if (mkdir(configPath.c_str(), 0755) != 0) {
            throw std::runtime_error("create config dir failed: " + configPath + ", errno: " + std::to_string(errno));
        }
    }
    ConfigManager::sConfigPath = configPath;
    // init MMKV
    MMKV::initializeMMKV(configPath);
    sDefaultConfig = MMKV::mmkvWithID("default_config", MMKV_SINGLE_PROCESS);
    sDefaultCache = MMKV::mmkvWithID("default_cache", MMKV_SINGLE_PROCESS);
    if (sDefaultConfig == nullptr || sDefaultCache == nullptr) {
        throw std::runtime_error("init MMKV failed, see log for details");
    }
}

MMKV &ConfigManager::getDefaultConfig() {
    if (sDefaultConfig == nullptr) {
        throw std::runtime_error("config manager not initialized");
    }
    return *sDefaultConfig;
}

MMKV &ConfigManager::getCache() {
    if (sDefaultCache == nullptr) {
        throw std::runtime_error("config manager not initialized");
    }
    return *sDefaultCache;
}

MMKV &ConfigManager::forAccount(int64_t uin) {
    if (sConfigPath.empty()) {
        throw std::runtime_error("config manager not initialized");
    }
    std::string cfgName = std::string("u_") + std::to_string(uin);
    auto *kv = MMKV::mmkvWithID(cfgName, MMKV_SINGLE_PROCESS);
    if (!kv) {
        throw std::runtime_error("create kv failed for account: " + std::to_string(uin));
    }
    return *kv;
}

}
