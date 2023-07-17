package uz.zerone.supporttelegrambot


/**
16/07/2023 - 1:11 PM
Created by Akhmadali
 */


import org.hibernate.annotations.ColumnDefault
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.repository.Temporal
import java.util.*
import javax.persistence.*

@MappedSuperclass
class BaseEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @CreatedDate @Temporal(TemporalType.TIMESTAMP) var createdDate: Date? = null,
    @LastModifiedDate @Temporal(TemporalType.TIMESTAMP) var modifiedDate: Date? = null,
    @Column(nullable = false) @ColumnDefault(value = "false") var deleted: Boolean = false,
)

@Entity(name = "users")
class User(
    var telegramId: String,
    var username: String?,
    var phoneNumber: String,
    @Enumerated(EnumType.STRING)
    var role: Role,
    var online: Boolean,
    @Enumerated(EnumType.STRING)
    var language: Language
) : BaseEntity()

@Entity
class Session(
    @ManyToOne val user: User,
    @ManyToOne val operator: User,
    var active: Boolean
) : BaseEntity()

@Entity
class Message(
    var telegramMessageId: String,
    @ManyToOne val session: Session,
    @ManyToOne val sender: User,
    var messageType: MessageType,
    var active: Boolean
) : BaseEntity()


@Entity
class File(
    var name: String,
    var contentType: String?,
    @ManyToOne val message: Message,
) : BaseEntity()

//
@Entity
class Content(
    var data: ByteArray,
    @ManyToOne val file: File,
) : BaseEntity()
