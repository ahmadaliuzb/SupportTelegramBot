package uz.zerone.supporttelegrambot

import java.math.BigDecimal


/**
18/07/2023 - 12:34 AM
Created by Akhmadali
 */

data class BaseMessage(val code: Int, val message: String?)

data class UsersList(
    val telegramId: String,
    val username: String?,
    val phoneNumber: String?,
    val role: Role,
    val languageList:MutableList<Language>?
) {
    companion object {
        fun toDto(user: User): UsersList {
            return user.run {
                UsersList(telegramId,username,phoneNumber,role,languageList)
            }
        }
    }
}

 data class UserUpdateDto(
     val phoneNumber: String,
     val languageList:MutableList<String>
)

data class LanguageDto(
     val languageDto: String
)