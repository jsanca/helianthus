package helianthus.core.config

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class SecurityConfig(
    @Value("\${helianthus.security.oauth2.enabled:true}")
    private val oauth2Enabled: Boolean,
    @Value("\${spring.security.oauth2.resourceserver.jwt.issuer-uri:}")
    private val issuerUri: String,
    @Value("\${helianthus.web.allowed-origins:http://localhost:5173}")
    private val allowedOrigins: List<String>
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests { auth ->
                auth.requestMatchers(HttpMethod.GET, "/health").permitAll()
                auth.requestMatchers("/actuator/health").permitAll()
                auth.requestMatchers("/actuator/info").permitAll()
                if (oauth2Enabled) {
                    auth.requestMatchers("/api/**").authenticated()
                } else {
                    auth.requestMatchers("/api/**").permitAll()
                }
                auth.anyRequest().permitAll()
            }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .cors { }
            .csrf { it.disable() }  // security based on jwt, do not need since no cookies

        if (oauth2Enabled && issuerUri.isNotBlank()) {
            http.oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwt ->
                    jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())
                }
            }
        } else if (oauth2Enabled) {
            http.httpBasic { }
        }

        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            this.allowedOrigins = this@SecurityConfig.allowedOrigins
            allowedMethods = listOf("GET", "OPTIONS")
            allowedHeaders = listOf("Authorization", "Content-Type", "Accept")
            exposedHeaders = listOf("Content-Type")
            allowCredentials = false
            maxAge = 3600
        }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", configuration)
        }
    }

    /*
     Key clock anatomy token:
     {
          "sub": "1234567890",
          "name": "John Doe",
          "realm_access": {
            "roles": [
              "admin",
              "user",
              "developer"
            ]
          }
        }
     */
    private fun jwtAuthenticationConverter(): JwtAuthenticationConverter {
        val converter = JwtAuthenticationConverter()
        converter.setJwtGrantedAuthoritiesConverter { jwt: Jwt ->
            @Suppress("UNCHECKED_CAST")
            val realmAccess = jwt.claims["realm_access"] as? Map<String, Any>
            @Suppress("UNCHECKED_CAST")
            val roles = realmAccess?.get("roles") as? List<String> ?: emptyList()
            roles.map { role -> SimpleGrantedAuthority("ROLE_$role") }
        }
        return converter
    }
}
