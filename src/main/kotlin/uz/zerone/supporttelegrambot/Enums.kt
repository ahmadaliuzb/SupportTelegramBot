package uz.zerone.supporttelegrambot


/**
16/07/2023 - 1:18 PM
Created by Akhmadali
 */
enum class Role {
    USER, ADMIN, OPERATOR
}

enum class LanguageEnum {

    UZ, ENG, RU
}

enum class MessageType {
    VIDEO, AUDIO, PHOTO, DOCUMENT
}

enum class BotStep{
    START,
    CHOOSE_LANGUAGE,
    SHARE_CONTACT,
    OFFLINE,
    ONLINE,
    IN_SESSION,
    ASSESSMENT
}