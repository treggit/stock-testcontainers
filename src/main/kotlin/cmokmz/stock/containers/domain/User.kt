package cmokmz.stock.containers.domain

import java.math.BigDecimal
import java.util.*
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id

@Entity
data class User(
        @Id
        @GeneratedValue
        var id: Long = 0,
        var username: String? = null,
        var balance: Double = 0.0
)