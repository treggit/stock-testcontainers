package cmokmz.stock.containers.domain

import java.math.BigDecimal
import java.util.*
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id

@Entity
data class Shares(
        @Id
        @GeneratedValue
        var id: Long = 0,
        var companyId: Long? = null,
        var ownerId: Long? = null,
        var price: Double = 0.0
)
