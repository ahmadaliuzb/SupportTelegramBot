package uz.zerone.supporttelegrambot

import org.springframework.context.support.ResourceBundleMessageSource
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*


@ControllerAdvice
class ExceptionHandlers(
    private val errorMessageSource: ResourceBundleMessageSource
){
    @ExceptionHandler(DemoException::class)
    fun handleException(exception: DemoException): ResponseEntity<*>{
        return when(exception){
            is UserNotFoundException -> ResponseEntity.badRequest()
                .body(exception.getErrorMessage(errorMessageSource, exception.phoneNumber))
        }
    }
}



@RestController
@RequestMapping("api/user")
class UserController(
    private val userService: UserService
) {

    @GetMapping
    fun getAll(pageable: Pageable): Page<UsersList> = userService.getAll(pageable)

    @PutMapping
    fun update(@RequestBody dto: UserUpdateDto) = userService.update(dto)


}