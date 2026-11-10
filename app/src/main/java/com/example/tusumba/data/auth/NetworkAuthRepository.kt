package com.example.tusumba.data.auth

import com.example.tusumba.data.identity.api.DefaultApi
import com.example.tusumba.data.identity.model.LoginRequest
import com.example.tusumba.data.identity.model.RegisterRequest
import com.example.tusumba.data.local.TokenManager
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkAuthRepository @Inject constructor(
    private val authApi: DefaultApi,
    private val tokenManager: TokenManager
) : AuthRepository {

    override suspend fun signIn(login: String, password: String): AuthResult {
        return try {
            val response = authApi.login(LoginRequest(login = login, password = password))
            if (response.isSuccessful) {
                val body = response.body() ?: return AuthResult.SignInUnavailable
                tokenManager.saveTokens(body.accessToken, body.refreshToken)
                AuthResult.Success(
                    AuthUser(
                        id = body.user.id.toString(),
                        email = body.user.email,
                        nickname = body.user.nickname,
                        globalRole = if (body.user.globalRole.name == "MODERATOR") GlobalRole.MODERATOR else GlobalRole.USER
                    )
                )
            } else {
                when (response.code()) {
                    401 -> AuthResult.InvalidCredentials
                    500 -> AuthResult.SignInUnavailable
                    else -> AuthResult.SignInUnavailable
                }
            }
        } catch (e: IOException) {
            AuthResult.SignInUnavailable
        } catch (e: Exception) {
            AuthResult.SignInUnavailable
        }
    }

    override suspend fun register(email: String, password: String, nickname: String): AuthResult {
        return try {
            val response = authApi.register(RegisterRequest(email = email, password = password, nickname = nickname))
            if (response.isSuccessful) {
                val body = response.body() ?: return AuthResult.RegistrationUnavailable
                tokenManager.saveTokens(body.accessToken, body.refreshToken)
                AuthResult.Success(
                    AuthUser(
                        id = body.user.id.toString(),
                        email = body.user.email,
                        nickname = body.user.nickname,
                        globalRole = if (body.user.globalRole.name == "MODERATOR") GlobalRole.MODERATOR else GlobalRole.USER
                    )
                )
            } else {
                when (response.code()) {
                    409 -> AuthResult.UserAlreadyExists
                    422 -> AuthResult.RegistrationUnavailable // Или создай отдельный Result для валидации
                    else -> AuthResult.RegistrationUnavailable
                }
            }
        } catch (e: IOException) {
            AuthResult.RegistrationUnavailable
        } catch (e: Exception) {
            AuthResult.RegistrationUnavailable
        }
    }
}