package cmokmz.stock.containers.rest

import cmokmz.stock.containers.domain.Company
import cmokmz.stock.containers.domain.Shares
import cmokmz.stock.containers.exception.CompanyException
import cmokmz.stock.containers.exception.NoSuchCompanyException
import cmokmz.stock.containers.exception.StockException
import cmokmz.stock.containers.repository.CompanyRepository
import cmokmz.stock.containers.repository.SharesRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/stock")
class StockController(
        private val companyRepository: CompanyRepository,
        private val sharesRepository: SharesRepository,
) {

    @PostMapping("/company")
    @Transactional
    fun addCompany(
            @RequestParam("name") name: String,
            @RequestParam("price") price: Double?
    ): Company {
        if (companyRepository.findCompanyByName(name) != null) {
            throw CompanyException("Company with name \"$name\" already exists")
        }
        return companyRepository.save(Company(name = name, price = price ?: 0.0))
    }

    @Transactional
    @PostMapping("/company/updatePrice")
    fun changePrice(
            @RequestParam("companyId") companyId: Long,
            @RequestParam("percent") percent: Double
    ) {
        val company = companyRepository.findById(companyId).orElseThrow { NoSuchCompanyException(companyId) }
        val updatedPrice = recalculatePrice(company, percent)
        companyRepository.save(company.apply { price = updatedPrice })
        sharesRepository.saveAll(sharesRepository.findByCompanyId(companyId).apply {
            forEach { it.price = updatedPrice }
        })
    }

    @PostMapping("/shares/issue")
    @Transactional
    fun issueShares(
            @RequestParam("companyId") companyId: Long,
            @RequestParam("amount") amount: Int
    ) {
        if (amount < 0) {
            throw StockException("Shares amount should be non-negative, but got $amount")
        }
        val company = companyRepository.findById(companyId).orElseThrow { NoSuchCompanyException(companyId) }

        sharesRepository.saveAll(Array(amount) { _ -> Shares(companyId = companyId, price = company.price) }.asList())
    }

    @Transactional
    @PostMapping("/company/shares/acquire")
    fun acquireShares(
            @RequestParam("userId") userId: Long,
            @RequestParam("companyId") companyId: Long,
            @RequestParam("amount") amount: Int
    ) {
        if (amount < 0) {
            throw StockException("Shares amount should be non-negative, but got $amount")
        }
        val freeShares = sharesRepository.findFreeSharesByCompanyId(companyId)

        if (amount > freeShares.size) {
            throw StockException("Required shares amount exceeds the number of free shares of the company with id $companyId." +
                    "Required $amount, but only ${freeShares.size} are available")
        }

        freeShares.subList(0, amount).forEach { it.ownerId = userId }
        sharesRepository.saveAll(freeShares)
    }

    private fun recalculatePrice(company: Company, percent: Double): Double {
        return company.price * (1 + percent / 100)
    }

    @GetMapping("/company/shares/price")
    fun getCompanySharesPrice(@RequestParam("companyId") companyId: Long): Double = companyRepository.findById(companyId)
            .map { it.price }
            .orElseThrow { NoSuchCompanyException(companyId) }


    @GetMapping("/company/shares")
    fun countShares(@RequestParam("companyId") companyId: Long) = sharesRepository.countByCompanyId(companyId)


    @ExceptionHandler(StockException::class)
    fun handleGeneralException(ex: StockException): ResponseEntity<Any?> {
        return ResponseEntity.badRequest().body(ex.message)
    }
}