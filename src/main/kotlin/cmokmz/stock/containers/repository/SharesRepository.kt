package cmokmz.stock.containers.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import cmokmz.stock.containers.domain.Shares
import org.springframework.data.repository.CrudRepository

@Repository
interface SharesRepository: CrudRepository<Shares, Long> {

    fun countByCompanyId(companyId: Long): Long

    @Query("""
       SELECT new cmokmz.stock.containers.domain.Shares(s.id, s.companyId, s.ownerId, s.price) 
        FROM Shares s 
        WHERE s.companyId = :companyId AND s.ownerId IS NULL
    """)
    fun findFreeSharesByCompanyId(companyId: Long): List<Shares>

    fun findByCompanyId(companyId: Long): List<Shares>

    fun findByOwnerId(userId: Long): List<Shares>

    fun findByOwnerIdAndCompanyId(ownerId: Long, companyId: Long): List<Shares>
}