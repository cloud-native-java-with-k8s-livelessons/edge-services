package com.example.edge

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder
import org.springframework.cloud.gateway.route.builder.filters
import org.springframework.cloud.gateway.route.builder.routes
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.retrieveFlux
import org.springframework.stereotype.Component
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToFlux
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration

@SpringBootApplication
class EdgeApplication

fun main(args: Array<String>) {
    runApplication<EdgeApplication>(*args)
}

@Configuration
class GatewayConfiguration {

    // todo also show the rate limiter and some of the other stuff?
    @Bean
    fun gateway(rlb: RouteLocatorBuilder) =
        rlb
            .routes {
                route {
                    path("/proxy")
                    filters {
                        setPath("/customers")
                    }
                    uri("http://localhost:8080/")
                }

            }

}

@Configuration
class CrmConfiguration {

    @Bean
    fun http(wb: WebClient.Builder) = wb.build()

    @Bean
    fun rsocket(rs: RSocketRequester.Builder) = rs.tcp("localhost", 8181)
}

@Component
class CrmClient(
    private val http: WebClient,
    private val rsocket: RSocketRequester
) {

    fun orders(cid: Int): Flux<Order> =
        rsocket.route("orders.{cid}", cid).retrieveFlux<Order>()
            .retry(2)
            .timeout(Duration.ofSeconds(1))
            .switchIfEmpty(Flux.empty())

    fun customers(): Flux<Customer> =
        http.get().uri("http://localhost:8080/customers").retrieve()
            .bodyToFlux<Customer>()
            .retry(2)
            .timeout(Duration.ofSeconds(1))
            .switchIfEmpty(Flux.empty())

    fun customerOrders(): Flux<CustomerOrders> =
        this.customers()
            .flatMap { c -> Mono.zip(Mono.just(c), orders(c.id).collectList()) }
            .map { t2 -> CustomerOrders(t2.t1, t2.t2) }


}

data class CustomerOrders(val customer: Customer, val orders: Collection<Order>)
data class Customer(val id: Int, val name: String)
data class Order(val id: Int, val customerId: Int)

@Controller
@ResponseBody
class CrmRestController(private val crm: CrmClient) {

    @GetMapping("/cos")
    fun cos() = this.crm.customerOrders()
}

@Controller
class CrmGraphqlController(private val crm: CrmClient) {

    @QueryMapping
    fun orders(@Argument customerId: Int) = this.crm.orders(customerId)

    @QueryMapping
    fun customers() = this.crm.customers()

    @SchemaMapping
    fun orders(customer: Customer) = this.crm.orders(customer.id)
}