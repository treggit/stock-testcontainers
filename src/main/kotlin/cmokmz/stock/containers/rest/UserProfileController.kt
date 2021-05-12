package cmokmz.stock.containers.rest

import cmokmz.stock.containers.domain.Shares
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import cmokmz.stock.containers.domain.User
import cmokmz.stock.containers.exception.NoSuchCompanyException
import cmokmz.stock.containers.exception.StockException
import cmokmz.stock.containers.exception.UserException
import cmokmz.stock.containers.repository.CompanyRepository
import cmokmz.stock.containers.repository.SharesRepository
import cmokmz.stock.containers.repository.UserRepository

@RestController
@RequestMapping("/user")
class UserProfileController(
        private val userRepository: UserRepository,
        private val sharesRepository: SharesRepository,
        private val companyRepository: CompanyRepository,
) {
    @PostMapping
    @Transactional
    fun createUser(
            @RequestParam("username") username: String,
            @RequestParam("balance") balance: Double?
    ): User {
        if (userRepository.findUserByUsername(username) != null) {
            throw UserException("Cannot create user with username \"$username\", " +
                    "as a user with this username already exists")
        }
        return userRepository.save(User(username = username, balance = balance ?: 0.0))
    }

    @PostMapping("/balance/update")
    @Transactional
    fun performDeposit(
            @RequestParam("userId") userId: Long,
            @RequestParam("delta") delta: Double
    ) {
        increaseBalance(userId, delta)
    }

    private fun setOwner(shares: List<Shares>, newUser: Long?, amount: Int) {
        shares.subList(0, amount).forEach { it.ownerId = newUser }
        sharesRepository.saveAll(shares)
    }

    @PostMapping("/shares/acquire")
    @Transactional
    fun acquireShares(
            @RequestParam("userId") userId: Long,
            @RequestParam("companyId") companyId: Long,
            @RequestParam("amount") amount: Int
    ) {
        val price = companyRepository.findById(companyId)
                .map { it.price }
                .orElseThrow { NoSuchCompanyException(companyId) } * amount

        val availableBalance = userRepository.findById(userId)
                .map { it.balance }
                .orElseThrow { UserException("User $userId does not exist") }

        if (availableBalance < price) {
            throw StockException("Failed to acquire shares of the company $companyId for the user $userId: " +
                    "the balance is not sufficient. Required $price, but have only $availableBalance")
        }
        increaseBalance(userId, -price)

        val freeShares = sharesRepository.findFreeSharesByCompanyId(companyId)
        if (amount > freeShares.size) {
            throw StockException("Required shares amount exceeds the number of free shares of the company with id $companyId." +
                    "Required $amount, but only ${freeShares.size} are available")
        }
        setOwner(freeShares, userId, amount)
    }

    @PostMapping("/shares/sell")
    @Transactional
    fun sellShares(
            @RequestParam("userId") userId: Long,
            @RequestParam("companyId") companyId: Long,
            @RequestParam("amount") amount: Int
    ) {
        val price = companyRepository.findById(companyId)
                .map { it.price }
                .orElseThrow { NoSuchCompanyException(companyId) }

        performDeposit(userId, price)

        val shares = sharesRepository.findByOwnerIdAndCompanyId(userId, companyId)

        if (amount > shares.size) {
            throw StockException("Required shares amount exceeds the number of shares, owner by the user $userId." +
                    "Required $amount, but only ${shares.size} are available")
        }

        setOwner(shares, null, amount)
    }

    @GetMapping("/balance")
    fun getBalance(@RequestParam("userId") userId: Long): Double {
        val user = userRepository.findById(userId).orElseThrow { UserException("User $userId does not exist") }

        return user.balance + sharesRepository.findByOwnerId(userId)
                .asSequence()
                .map { it.price }
                .sum()
    }

    @GetMapping("/shares")
    fun getShares(@RequestParam("userId") userId: Long): Map<String, Any> {
        val shares = sharesRepository.findByOwnerId(userId)
        return mapOf(
                "total" to shares.size,
                "shares" to shares
        )
    }

    @ExceptionHandler(StockException::class)
    fun handleGeneralException(ex: StockException): ResponseEntity<Any?> {
        return ResponseEntity.badRequest().body(ex.message)
    }

    private fun increaseBalance(userId: Long, value: Double) =
            userRepository.save(
                    userRepository.findById(userId).orElseThrow {
                        StockException("Failed to increase balance of a user $userId, as he doesn't exist")
                    }.apply { balance += value }
            )
}
