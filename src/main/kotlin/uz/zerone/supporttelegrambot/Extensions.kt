package uz.zerone.supporttelegrambot


/**
20/07/2023 - 11:51 PM
Created by Akhmadali
 */
import java.util.Locale

fun Language.toLocale() = Locale(this.languageEnum.name)