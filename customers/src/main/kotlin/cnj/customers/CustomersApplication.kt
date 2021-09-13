package cnj.customers

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ResponseBody
import reactor.core.publisher.Flux

@SpringBootApplication
class CustomersApplication

fun main(args: Array<String>) {
    runApplication<CustomersApplication>(*args)
}

data class Customer(val id: Int, val name: String)

@ResponseBody
@Controller
class CustomersRestController {

    private val db = mutableListOf(
        Customer(1, "Spencer"), Customer(2, "Violetta"),
        Customer(3, "Olga"), Customer(4, "Jürgen"),
        Customer(5, "Stéphane"), Customer(6, "Dr. Syer"),
        Customer(7, "Yuxin"), Customer(8, "Madhura"),
    )

    @GetMapping("/customers")
    fun get() = Flux.fromIterable(this.db)
}