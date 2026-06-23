package com.yzarc.aiapimonitor.data.repository

import com.yzarc.aiapimonitor.data.db.AppDatabase
import com.yzarc.aiapimonitor.model.ApiAccount

/**
 * P3-⑨: 账号管理 Repository，从 BalanceRepository 拆分
 * 职责：账号 CRUD、Key 状态管理
 */
class AccountRepository(private val db: AppDatabase) {
    private val accountDao = db.accountDao()

    suspend fun getAccounts(): List<ApiAccount> = accountDao.getAll()
    suspend fun getAccount(id: Int): ApiAccount? = accountDao.getById(id)
    suspend fun save(account: ApiAccount) = accountDao.insert(account)
    suspend fun update(account: ApiAccount) = accountDao.update(account)
    suspend fun delete(account: ApiAccount) = accountDao.delete(account)
    suspend fun count(): Int = accountDao.count()
    suspend fun updateKeyStatus(id: Int, status: Int, error: String? = null) =
        accountDao.updateKeyStatus(id, status, error)
}