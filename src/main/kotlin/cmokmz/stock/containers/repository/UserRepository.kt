package cmokmz.stock.containers.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import cmokmz.stock.containers.domain.User
import org.springframework.data.repository.CrudRepository

@Repository
interface UserRepository: CrudRepository<User, Long> {

    fun findUserByUsername(login: String): User?
}