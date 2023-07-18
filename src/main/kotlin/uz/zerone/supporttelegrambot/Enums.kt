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
    VIDEO, AUDIO, PHOTO, DOCUMENT,TEXT
}

enum class BotStep{
    START,
    CHOOSE_LANGUAGE,
    SHARE_CONTACT,
    OFFLINE,
    ONLINE,
    IN_SESSION,
    FULL_SESSION,
    ASSESSMENT
}

enum class ContentType{
    DOCUMENT,
    VIDEO,
    AUDIO,
    ANIMATION,
    VIDEO_NOTE,
    PHOTO,
    VOICE,
    STICKER
}