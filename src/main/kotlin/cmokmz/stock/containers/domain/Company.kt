package cmokmz.stock.containers.domain

import java.math.BigDecimal
import java.util.*
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id

@Entity
data class Company(
        @Id
        @GeneratedValue
        var id: Long = 0,
        var name: String? = null,
        var price: Double = 0.0
)