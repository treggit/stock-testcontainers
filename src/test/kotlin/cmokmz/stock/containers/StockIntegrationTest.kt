package cmokmz.stock.containers

import cmokmz.stock.containers.domain.Company
import cmokmz.stock.containers.domain.User
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.FixedHostPortGenericContainer
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals
import kotlin.test.assertTrue


open class StockIntegrationTest {

    class KGenericContainer(imageName: String) : FixedHostPortGenericContainer<KGenericContainer>(imageName)

    companion object {
        private val objectMapper = ObjectMapper()
        private const val CONTAINER_PORT = 8080
    }

    val simpleWebServer = KGenericContainer("stock:1.0-SNAPSHOT")
            .withFixedExposedPort(CONTAINER_PORT, CONTAINER_PORT)
            .withExposedPorts(CONTAINER_PORT)

    @BeforeEach
    internal fun setUp() {
        simpleWebServer.start()
    }

    @AfterEach
    internal fun tearDown() {
        simpleWebServer.stop()
    }

    private fun <T> assertRequest(
            path: String,
            params: Map<String, Any>,
            method: String = "POST",
            statusCode: Int = 200,
            resultType: Class<T>,
            checkResult: (T) -> Boolean = { true },
    ): T? {
        val query = params.entries.joinToString("&") { "${it.key}=${it.value}" }
        val request = HttpRequest.newBuilder()
                .uri(URI("http://localhost:$CONTAINER_PORT/$path?$query"))
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build()
        val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(statusCode, response.statusCode())

        if (statusCode >= 300 || response.body().isBlank()) {
            return null
        }
        return objectMapper.readValue(response.body(), resultType).also { assertTrue(checkResult(it)) }
    }

    private fun createCompany(name: String, price: Double): Company {
        return assertRequest(
                "/stock/company",
                mapOf(
                        "name" to name,
                        "price" to price
                ),
                resultType = Company::class.java,
        ) { it.name == name && it.price == price } ?: throw AssertionError("Failed to create company")
    }

    private fun checkCompanyPrice(companyId: Long, expectedPrice: Double) {
        assertRequest(
                "/stock/company/shares/price",
                mapOf(
                        "companyId" to companyId,
                ),
                method = "GET",
                resultType = Double::class.java,
                checkResult = { it == expectedPrice }
        )
    }

    private fun createUser(username: String, balance: Double): User {
        return assertRequest(
                "/user",
                mapOf(
                        "username" to username,
                        "balance" to balance
                ),
                resultType = User::class.java,
                checkResult = { it.username == username && it.balance == balance }
        ) ?: throw AssertionError("Failed to create a user")
    }

    private fun issueShares(company: Company, sharesCount: Long) {
        assertRequest(
                "/stock/shares/issue",
                mapOf(
                        "companyId" to company.id,
                        "amount" to sharesCount
                ),
                resultType = Any::class.java
        )
    }

    private fun checkUserBalance(userId: Long, expectedBalance: Double) {
        assertRequest(
                "/user/balance",
                mapOf(
                        "userId" to userId,
                ),
                method = "GET",
                resultType = Double::class.java,
                checkResult = { it == expectedBalance }
        )
    }

    private fun checkAcquiredShares(userId: Long, expectedCount: Long) {
        assertRequest(
                "/user/shares",
                mapOf(
                        "userId" to userId,
                ),
                method = "GET",
                resultType = Map::class.java,
                checkResult = { it["total"].toString().toLong() == expectedCount }
        )
    }

    @Test
    fun `create company test`() {
        val companyName = "Apple"
        val price = 10.0
        createCompany(companyName, price)
    }

    @Test
    fun `should not create two companies with the same name test`() {
        val companyName = "Apple"
        val price = 10.0
        createCompany(companyName, price)

        assertRequest(
                "/stock/company",
                mapOf(
                        "name" to companyName,
                        "price" to price
                ),
                statusCode = 400,
                resultType = Company::class.java,
        )
    }

    @Test
    fun `update prices test`() {
        val companyName = "Dell"
        val price = 5.0

        val company = createCompany(companyName, price)
        val sharesCount = 5L

        issueShares(company, sharesCount)
        checkCompanyPrice(company.id, price)
        val percent = 50
        assertRequest(
                "/stock/company/updatePrice",
                mapOf(
                        "companyId" to company.id,
                        "percent" to percent
                ),
                resultType = Any::class.java,
        )

        checkCompanyPrice(company.id, price + price * percent / 100)
    }


    @Test
    fun `acquire and sell shares test`() {
        val companyName = "HP"
        val price = 6.0
        val company = createCompany(companyName, price)

        val sharesCount = 5L
        issueShares(company, sharesCount)
        var balance = 0.0
        val andrew = createUser("Andrew", balance)

        checkUserBalance(andrew.id, balance)
        val delta = 100.0
        balance += delta
        assertRequest(
                "/user/balance/update",
                mapOf(
                        "userId" to andrew.id,
                        "delta" to delta
                ),
                resultType = Any::class.java,
        )
        checkUserBalance(andrew.id, balance)
        var amount = 2L
        assertRequest(
                "/user/shares/acquire",
                mapOf(
                        "userId" to andrew.id,
                        "companyId" to company.id,
                        "amount" to amount
                ),
                resultType = Any::class.java,
        )
        checkAcquiredShares(andrew.id, amount)
        amount -= 1
        assertRequest(
                "/user/shares/sell",
                mapOf(
                        "userId" to andrew.id,
                        "companyId" to company.id,
                        "amount" to 1
                ),
                resultType = Any::class.java,
        )
        checkAcquiredShares(andrew.id, amount)

        val percent = 10
        assertRequest(
                "/stock/company/updatePrice",
                mapOf(
                        "companyId" to company.id,
                        "percent" to percent
                ),
                resultType = Any::class.java,
        )
        checkUserBalance(andrew.id, balance + price * amount * percent / 100)
    }

    @Test
    fun `user with insufficient funds can not buy shares`() {
        val companyName = "Asus"
        val price = 8.0
        val company = createCompany(companyName, price)

        val user = createUser("Alexandr", 1.0)
        checkUserBalance(user.id, 1.0)
        assertRequest(
                "/user/shares/acquire",
                mapOf(
                        "userId" to user.id,
                        "companyId" to company.id,
                        "amount" to 1
                ),
                resultType = Any::class.java,
                statusCode = 400
        )
        checkUserBalance(user.id, 1.0)
    }

}
