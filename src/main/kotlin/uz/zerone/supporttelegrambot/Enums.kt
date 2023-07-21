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
    DOCUMENT,
    VIDEO,
    AUDIO,
    ANIMATION,
    VIDEO_NOTE,
    PHOTO,
    VOICE,
    STICKER,
    TEXT
}

enum class BotStep{
    START,
    CHOOSE_LANGUAGE,
    SHARE_CONTACT,
    OFFLINE,
    SHOW_MENU,
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

enum class ErrorCode(val code: Int){
    USER_NOT_FOUND(100)
}