package org.camunda.bpm.extension.keycloak.showcase.rest;

import org.camunda.bpm.engine.IdentityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.OAuth2RestTemplate;
import org.springframework.security.oauth2.client.token.grant.client.ClientCredentialsResourceDetails;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer;
import org.springframework.security.oauth2.config.annotation.web.configuration.ResourceServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configurers.ResourceServerSecurityConfigurer;
import org.springframework.security.oauth2.provider.token.DefaultTokenServices;
import org.springframework.security.oauth2.provider.token.ResourceServerTokenServices;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.security.oauth2.provider.token.store.jwk.JwkTokenStore;

import javax.inject.Inject;
import javax.ws.rs.HttpMethod;
import java.util.Properties;

/**
 * Optional Security Configuration for Camunda REST Api.
 */
@Configuration
@EnableResourceServer
@EnableWebSecurity
@Order(SecurityProperties.BASIC_AUTH_ORDER - 20)
@ConditionalOnProperty(name = "rest.security.enabled", havingValue = "true", matchIfMissing = true)
public class RestApiSecurityConfig extends ResourceServerConfigurerAdapter {

	/** Configuration for REST Api security. */
	@Inject
	private RestApiSecurityConfigurationProperties configProps;

	/** Access to Camunda's Identity Service. */
	@Inject
	private IdentityService identityService;

	@Autowired
	private Properties clientCredentialProperties;

	/**
	 * {@inheritDoc}
	 */

	@Override
	public void configure(final HttpSecurity http) throws Exception {
		http.requestMatchers().antMatchers("/engine-rest/**","/engine-rest-ext/**","/forms-flow-bpm-socket/**", "/actuator/**").
				and().authorizeRequests().antMatchers(HttpMethod.OPTIONS,"/engine-rest/**").permitAll()
				.and().authorizeRequests().antMatchers(HttpMethod.OPTIONS,"/engine-rest-ext/**").permitAll()
				.and().authorizeRequests().antMatchers(HttpMethod.OPTIONS,"/forms-flow-bpm-socket/**").permitAll()
				.antMatchers("/engine-rest/**","/engine-rest-ext/**")
				.authenticated().and().csrf().disable()
				.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void configure(final ResourceServerSecurityConfigurer config) {
		config
				.tokenServices(tokenServices())
				.resourceId(configProps.getRequiredAudience());
	}

	/**
	 * Configures the JWKS bases TokenStore.
	 */
	@Bean
	public TokenStore tokenStore() {
		return new JwkTokenStore(configProps.getJwkSetUrl());
	}

	/**
	 * Creates JWKS based TokenServices.
	 * @return DefaultTokenServices
	 */
	public ResourceServerTokenServices tokenServices() {
		DefaultTokenServices defaultTokenServices = new DefaultTokenServices();
		defaultTokenServices.setTokenStore(tokenStore());
		return defaultTokenServices;
	}

	/**
	 * Registers the REST Api Keycloak Authentication Filter.
	 * @return filter registration
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Bean
	public FilterRegistrationBean keycloakAuthenticationFilter(){
		FilterRegistrationBean filterRegistration = new FilterRegistrationBean();
		filterRegistration.setFilter(new KeycloakAuthenticationFilter(identityService));
		filterRegistration.setOrder(102); // make sure the filter is registered after the Spring Security Filter Chain
		filterRegistration.addUrlPatterns("/engine-rest/*","/engine-rest-ext/*");
		return filterRegistration;
	}

	@Bean
	public OAuth2RestTemplate getOAuth2RestTemplate() {
		ClientCredentialsResourceDetails resourceDetails = new ClientCredentialsResourceDetails ();
		resourceDetails.setClientId(clientCredentialProperties.getProperty("client-id"));
		resourceDetails.setClientSecret(clientCredentialProperties.getProperty("client-secret"));
		resourceDetails.setAccessTokenUri(clientCredentialProperties.getProperty("accessTokenUri"));
		resourceDetails.setGrantType("client_credentials");
		return new OAuth2RestTemplate(resourceDetails);
	}

}
