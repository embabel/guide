package com.embabel.agent.rag.neo.drivine

import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus

class UselessTemporaryTransactionManager : PlatformTransactionManager {
    override fun getTransaction(definition: TransactionDefinition?): TransactionStatus {
        return SimpleTransactionStatus()
    }

    override fun commit(status: TransactionStatus) {
        // No-op implementation
    }

    override fun rollback(status: TransactionStatus) {
        TODO("Not yet implemented")
    }
}