package com.example.edge

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cloud.client.loadbalancer.LoadBalanced
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder
import org.springframework.cloud.gateway.route.builder.filters
import org.springframework.cloud.gateway.route.builder.routes
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.http.HttpHeaders
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.retrieveFlux
import org.springframework.messaging.simp.annotation.SubscribeMapping
import org.springframework.stereotype.Component
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
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


    @Bean
    fun gateway(rlb: RouteLocatorBuilder) =
        rlb.routes {
            route {
                path("/proxy") and host("*.spring.io")
                filters {
                    setPath("/customers")
                    addResponseHeader(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "*"
                    )
                }
                uri("lb://customers")
            }
        }
}

@Configuration
class CrmConfiguration {

    @LoadBalanced
    @Bean
    fun httpBuilder() = WebClient.builder()

    @Bean
    fun http(wc: WebClient.Builder) = wc.build()

    @Bean
    fun rsocket(rc: RSocketRequester.Builder) = rc.tcp("localhost", 8181)

}

data class CustomerOrders(val customer: Customer, val orders: List<Order>)
data class Order(val id: Int, val customerId: Int)
data class Customer(val id: Int, val name: String)

@Component
class CrmClient(
    private val http: WebClient,
    private val rsocket: RSocketRequester
) {


    fun customerOrders(): Flux<CustomerOrders> = this.customers()
        .flatMap {
            Mono.zip(
                Mono.just(it),
                this.orders(it.id).collectList()
            )
        }
        .map { CustomerOrders(it.t1, it.t2) }

    fun orders(customerId: Int) = this.rsocket
        .route("orders.{customerId}", customerId)
        .retrieveFlux<Order>()
        .retry(10)
        .onErrorResume { Flux.empty<Order>() }
        .timeout(Duration.ofSeconds(10))

    fun customers() =
        this.http.get().uri("http://customers/customers").retrieve()
            .bodyToFlux<Customer>()
            .retry(10)
            .onErrorResume { Flux.empty<Customer>() }
            .timeout(Duration.ofSeconds(10))


}

@Controller
class CrmClientGraphqlController(private val crmClient: CrmClient) {
//
//    @QueryMapping
//    fun orders(@Argument customerId: Int) = this.crmClient.orders(customerId)

    @MutationMapping
    fun update(@Argument id: Int) : Int  {
        println("the id is ${id}.")
        return 42
    }

    @SubscribeMapping
    fun numbers() = Flux.just(1, 2, 3).delayElements(Duration.ofSeconds(1))

    @QueryMapping
    fun customers(): Flux<Customer> = this.crmClient.customers()

    @SchemaMapping
    fun orders(customer: Customer) = this.crmClient.orders(customer.id)


//    @SchemaMapping(typeName = "Query", field = "customers")
//    fun customers() = this.crmClient.customers()
}

@Controller
@ResponseBody
class CrmClientRestController(private val crmClient: CrmClient) {

    @GetMapping("/customers")
    fun customers() = this.crmClient.customers()

    @GetMapping("/orders/{cid}")
    fun orders(@PathVariable cid: Int) = this.crmClient.orders(cid)

    @GetMapping("/cos")
    fun customerOrders() = this.crmClient.customerOrders()
}