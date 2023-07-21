package uz.zerone.supporttelegrambot


/**
16/07/2023 - 1:18 PM
Created by Akhmadali
 */
enum class Role {
    USER, ADMIN, OPERATOR
}

enum class LanguageEnum(val code: String) {

    UZ("uz"), ENG("eng"), RU("ru")
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
    TEXT,
    LOCATION

}

enum class BotStep {
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

enum class ContentType {
    DOCUMENT,
    VIDEO,
    AUDIO,
    ANIMATION,
    VIDEO_NOTE,
    PHOTO,
    VOICE,
    STICKER
}

enum class ErrorCode(val code: Int) {
    USER_NOT_FOUND(101),
}

enum class LocalizationTextKey() {

    SHARE_CONTACT_BUTTON,
    SHARE_CONTACT_MESSAGE,
    CHOOSE_SECTION_MESSAGE,
    SETTINGS_BUTTON,
    QUESTION_BUTTON,
    CHOOSE_LANGUAGE_MESSAGE,
    ASSIGN_OPERATOR_MESSAGE,
    ONLINE_BUTTON,
    OFFLINE_BUTTON,
    START_MESSAGING_MESSAGE,
    CONNECT_OPERATOR_MESSAGE,
    CONNECT_USER_MESSAGE,
    OPERATOR_CLOSE_BUTTON,
    OPERATOR_CLOSE_AND_OFFLINE_BUTTON,
    ONLINE_MESSAGE,
    DISCONNECTED_MESSAGE,
    CHOOSE_RATE_MESSAGE,
    OFFLINE_MESSAGE,
    THANK_MESSAGE,
    WAIT_FOR_OPERATOR_MESSAGE
}