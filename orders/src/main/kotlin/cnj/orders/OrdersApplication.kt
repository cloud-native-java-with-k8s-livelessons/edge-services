package cnj.orders

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller
import reactor.core.publisher.Flux
import java.util.concurrent.ConcurrentHashMap

@SpringBootApplication
class OrdersApplication

fun main(args: Array<String>) {
    runApplication<OrdersApplication>(*args)
}

data class Order(val id: Int, val customerId: Int)

@Controller
class OrdersRSocketController {

    private val db = ConcurrentHashMap<Int, Collection<Order>>()

    init {
        (1..8).forEach { customerId ->
            val max: Int = (Math.random() * 100).toInt()
            this.db[customerId] = (1..max).map { Order(it, customerId) }
        }
    }

    @MessageMapping("orders.{customerId}")
    fun ordersFor(@DestinationVariable customerId: Int): Flux<Order> =
        Flux.fromIterable(this.db[customerId]!!)
}