package cmokmz.stock.containers.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import cmokmz.stock.containers.domain.Company
import org.springframework.data.repository.CrudRepository

@Repository
interface CompanyRepository: CrudRepository<Company, Long> {
    fun findCompanyByName(name: String): Company?
}