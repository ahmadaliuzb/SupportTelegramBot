package uz.zerone.supporttelegrambot


/**
16/07/2023 - 1:11 PM
Created by Akhmadali
 */

import jakarta.persistence.*
import org.hibernate.annotations.ColumnDefault
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.repository.Temporal
import java.util.*

@MappedSuperclass
class BaseEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @CreatedDate @Temporal(TemporalType.TIMESTAMP) var createdDate: Date? = null,
    @LastModifiedDate @Temporal(TemporalType.TIMESTAMP) var modifiedDate: Date? = null,
    @Column(nullable = false) @ColumnDefault(value = "false") var deleted: Boolean = false,
)

@Entity
class Language(
    var name: String
) : BaseEntity()

@Entity(name = "users")
class User(
    var telegramId: String,
    var username: String?,
    @Enumerated(EnumType.STRING)
    var role: Role,
    var online: Boolean,
    @ManyToOne var languageList: Language
) : BaseEntity()

@Entity
class Session(
    val telegramChatId: String,
    @ManyToOne val user: User,
    @ManyToOne val operator: User,
    var active: Boolean
) : BaseEntity()



